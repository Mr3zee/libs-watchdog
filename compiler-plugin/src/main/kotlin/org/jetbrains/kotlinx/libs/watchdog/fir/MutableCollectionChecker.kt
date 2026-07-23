package org.jetbrains.kotlinx.libs.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.upperBoundIfFlexible
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Reports [mutable collection types](https://kotlinlang.org/docs/api-guidelines-predictability.html#avoid-exposing-mutable-state)
 * in publicly visible signatures — return types, property types, value parameter types, and type
 * parameter bounds (`<T : MutableList<Int>>` accepts the same mutable state as a plain
 * `MutableList<Int>` parameter), including their type arguments (`List<MutableList<Int>>` still
 * hands out mutable state). Once mutable state is shared across the API boundary, it is unclear
 * whether client-side and library-side mutations affect each other, so the API should accept and
 * return read-only types, handing out defensive copies where needed. Authors acknowledge
 * deliberate sharing with `@IntentionallyMutableCollection` — on the whole declaration, on a
 * single (type) parameter, or on a type usage, where it covers the annotated type and everything
 * nested in it. Type-use exemptions never reach [ExemptionExplanationChecker], so this checker
 * enforces the explanation requirement for them itself.
 *
 * A type counts as mutable when it is one of the `kotlin.collections` mutable interfaces, any
 * classifier implementing them (`ArrayList`, a hand-written `MutableList` subtype, ...), or an
 * array — the guideline treats arrays as mutable collections as well.
 *
 * Deliberate exceptions:
 * - `vararg` parameters: the compiler already passes a defensive copy of the array, so only the
 *   declared element type is checked, not the array itself.
 * - Extension receivers: an extension on a mutable collection provides functionality for values
 *   the client already holds instead of sharing new mutable state, as in
 *   `fun <T> MutableList<T>.sort()`.
 * - Overrides: their signature is fixed by the overridden declaration and is reported there.
 * - Flexible (Java platform) types: their mutability is not declared in Kotlin sources.
 *
 * A `val`/`var` primary constructor parameter is watched through the property it creates, so the
 * shared parameter/property text is reported once — even when only the property is public.
 */
internal class MutableCollectionChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        when (declaration) {
            // Parameters are swept from their containing function, where the public API gate and
            // the signature-wide exemption are evaluated once per callable.
            is FirValueParameter -> return
            is FirProperty -> checkProperty(declaration)
            is FirNamedFunction -> checkFunction(declaration)
            is FirConstructor -> checkConstructor(declaration)
            is FirRegularClass -> checkClass(declaration)
            else -> return
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkProperty(declaration: FirProperty) {
        if (!declaration.isWatchedPublicApi() || declaration.isOverride) {
            return
        }

        // For a val/var constructor parameter the exemption annotation lands on the parameter
        // (the default use-site target), not on the property generated from it.
        if (declaration.isExempt() ||
            declaration.correspondingValueParameterFromPrimaryConstructor?.isExempt() == true
        ) {
            return
        }

        // Extension properties can declare their own type parameters.
        checkTypeParameters(declaration.typeParameters)
        reportMutableType(declaration.returnTypeRef, "property", declaration.name, declaration)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirNamedFunction) {
        if (!declaration.isWatchedPublicApi() || declaration.isOverride) {
            return
        }

        if (declaration.isExempt()) {
            return
        }

        checkTypeParameters(declaration.typeParameters)
        reportMutableType(declaration.returnTypeRef, "function", declaration.name, declaration)
        declaration.valueParameters.forEach { checkParameter(it) }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkConstructor(declaration: FirConstructor) {
        if (!declaration.isWatchedPublicApi() || declaration.isExempt()) {
            return
        }

        // val/var parameters produce a property covering the same source text; the property
        // check owns those, keeping the report single.
        val propertyParameters = mutableSetOf<FirValueParameterSymbol>()
        if (declaration.isPrimary) {
            (context.containingDeclarations.lastOrNull() as? FirClassSymbol<*>)
                ?.processAllDeclarations(context.session) { member ->
                    if (member is FirPropertySymbol) {
                        member.correspondingValueParameterFromPrimaryConstructor
                            ?.let(propertyParameters::add)
                    }
                }
        }

        for (parameter in declaration.valueParameters) {
            if (parameter.symbol !in propertyParameters) {
                checkParameter(parameter)
            }
        }
    }

    /** Classes carry no checked signature of their own, but their type parameters have bounds. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkClass(declaration: FirRegularClass) {
        if (declaration.isWatchedPublicApi()) {
            checkTypeParameters(declaration.typeParameters)
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkParameter(parameter: FirValueParameter) {
        if (parameter.isExempt()) {
            return
        }

        // A vararg parameter receives a defensive copy of the array, so only the declared
        // element type — the array's type argument — can leak mutable state.
        if (parameter.isVararg) {
            val elementLeak = parameter.returnTypeRef.coneType.typeArguments
                .firstNotNullOfOrNull { it.type?.findExposedMutableType() }
                ?: return
            report(parameter.returnTypeRef, "parameter", parameter.name, elementLeak, parameter)
        } else {
            reportMutableType(parameter.returnTypeRef, "parameter", parameter.name, parameter)
        }
    }

    /**
     * Reports mutable bounds: a type parameter bounded by a mutable collection constrains every
     * instantiation to mutable state, so it exposes the same mutability as a direct mention of
     * the bound. Outer-class parameters reappear as [FirTypeParameterRef]s without their own
     * declaration and are skipped — they are reported on the declaring class.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkTypeParameters(typeParameters: List<FirTypeParameterRef>) {
        for (typeParameter in typeParameters.filterIsInstance<FirTypeParameter>()) {
            if (typeParameter.isExempt()) {
                continue
            }

            for (bound in typeParameter.bounds) {
                reportMutableType(bound, "type parameter", typeParameter.name, typeParameter)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportMutableType(typeRef: FirTypeRef, kind: String, name: Name, declaration: FirDeclaration) {
        val mutableType = typeRef.coneType.findExposedMutableType() ?: return
        report(typeRef, kind, name, mutableType, declaration)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun report(typeRef: FirTypeRef, kind: String, name: Name, mutableType: Name, declaration: FirDeclaration) {
        val factory = severities[WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API] ?: return
        reporter.reportOn(
            source = typeRef.source ?: declaration.source,
            factory = factory,
            a = kind,
            b = name,
            c = mutableType,
        )
    }

    context(context: CheckerContext)
    private fun FirDeclaration.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyMutableCollection, context.session)

    context(context: CheckerContext)
    private fun FirValueParameterSymbol.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyMutableCollection, context.session)

    /**
     * The name of the first mutable collection type the (fully expanded) type mentions, or null.
     * Flexible types are inspected through their read-only upper bound: a Java platform type does
     * not declare mutability in Kotlin sources, so it is not reported.
     *
     * A type-use `@IntentionallyMutableCollection` exempts the annotated type and everything
     * nested in it. [ExemptionExplanationChecker] never sees type-use annotations, so the
     * explanation requirement is enforced here, where the exemption is honored.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun ConeKotlinType.findExposedMutableType(): Name? {
        val session = context.session
        val exemption = customAnnotations.firstOrNull {
            it.toAnnotationClassIdSafe(session) == WatchdogClassIds.IntentionallyMutableCollection
        }
        if (exemption != null) {
            exemption.unexplainedExemptionReason()?.let { reason ->
                reporter.reportOn(
                    source = exemption.source,
                    factory = WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION,
                    a = WatchdogClassIds.IntentionallyMutableCollection.shortClassName,
                    b = reason,
                )
            }
            return null
        }

        val type = upperBoundIfFlexible().let {
            if (it is ConeClassLikeType) it.fullyExpandedType() else it
        }

        if (type is ConeClassLikeType) {
            val classId = type.lookupTag.classId
            if (classId.isMutableCollectionLike(type)) {
                return classId.shortClassName
            }
        }

        return type.typeArguments.firstNotNullOfOrNull { it.type?.findExposedMutableType() }
    }

    context(context: CheckerContext)
    private fun ClassId.isMutableCollectionLike(type: ConeClassLikeType): Boolean {
        if (this in mutableCollectionTypes || this in arrayTypes) {
            return true
        }

        // Concrete implementations (ArrayList, java.util.HashMap, a hand-written MutableList
        // subtype, ...) expose the same mutators as the interfaces they implement.
        val symbol = type.toClassSymbol() ?: return false
        return lookupSuperTypes(symbol, lookupInterfaces = true, deep = true, useSiteSession = context.session)
            .any { it.lookupTag.classId in mutableCollectionTypes }
    }

    private val mutableCollectionTypes: Set<ClassId> = setOf(
        StandardClassIds.MutableIterable,
        StandardClassIds.MutableIterator,
        StandardClassIds.MutableListIterator,
        StandardClassIds.MutableCollection,
        StandardClassIds.MutableList,
        StandardClassIds.MutableSet,
        StandardClassIds.MutableMap,
        StandardClassIds.MutableMapEntry,
    )

    private val arrayTypes: Set<ClassId> = buildSet {
        add(StandardClassIds.Array)
        addAll(StandardClassIds.primitiveArrayTypeByElementType.values)
        addAll(StandardClassIds.unsignedArrayTypeByElementType.values)
    }
}
