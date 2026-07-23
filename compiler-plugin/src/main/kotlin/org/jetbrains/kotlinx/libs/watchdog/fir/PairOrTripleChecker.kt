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
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Reports the tuple types `Pair` and `Triple` in publicly visible signatures — return types,
 * property types, value parameter types, and type parameter bounds (`<T : Pair<Int, Int>>`
 * constrains every instantiation to the tuple shape), including their type arguments
 * (`List<Pair<Int, String>>` exposes the tuple all the same). Tuple components carry no domain
 * meaning: at the use site `first`/`second`/`third` and positional destructuring reveal nothing
 * about the values, and the fixed shape cannot evolve — adding a value means switching to a
 * different type, breaking clients. The API should expose
 * [a small class with descriptively named properties](https://kotlinlang.org/docs/data-classes.html)
 * instead. Authors acknowledge a deliberate tuple with `@IntentionallyPairOrTriple` — on the
 * whole declaration, on a single (type) parameter, or on a type usage, where it covers the
 * annotated type and everything nested in it. Type-use exemptions never reach
 * [ExemptionExplanationChecker], so this checker enforces the explanation requirement for them
 * itself.
 *
 * Deliberate exceptions:
 * - Extension receivers: an extension on a tuple provides functionality for values the client
 *   already holds instead of handing out new tuples, as in `fun <A, B> Pair<A, B>.swap()`.
 * - Overrides: their signature is fixed by the overridden declaration and is reported there.
 *
 * A `vararg` parameter needs no special treatment: the array carrying the arguments is not a
 * tuple, and a tuple element type is found in its type arguments. A `val`/`var` primary
 * constructor parameter is watched through the property it creates, so the shared
 * parameter/property text is reported once — even when only the property is public.
 */
internal class PairOrTripleChecker(
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
        reportTupleType(declaration.returnTypeRef, "property", declaration.name, declaration)
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
        reportTupleType(declaration.returnTypeRef, "function", declaration.name, declaration)
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
        if (!parameter.isExempt()) {
            reportTupleType(parameter.returnTypeRef, "parameter", parameter.name, parameter)
        }
    }

    /**
     * Reports tuple bounds: a type parameter bounded by a tuple constrains every instantiation
     * to the tuple shape, so it exposes the same shape as a direct mention of the bound.
     * Outer-class parameters reappear as [FirTypeParameterRef]s without their own declaration
     * and are skipped — they are reported on the declaring class.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkTypeParameters(typeParameters: List<FirTypeParameterRef>) {
        for (typeParameter in typeParameters.filterIsInstance<FirTypeParameter>()) {
            if (typeParameter.isExempt()) {
                continue
            }

            for (bound in typeParameter.bounds) {
                reportTupleType(bound, "type parameter", typeParameter.name, typeParameter)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportTupleType(typeRef: FirTypeRef, kind: String, name: Name, declaration: FirDeclaration) {
        val tupleType = typeRef.coneType.findExposedTupleType() ?: return
        val factory = severities[WatchdogDiagnostics.PAIR_OR_TRIPLE_PUBLIC_API] ?: return
        reporter.reportOn(
            source = typeRef.source ?: declaration.source,
            factory = factory,
            a = kind,
            b = name,
            c = tupleType,
        )
    }

    context(context: CheckerContext)
    private fun FirDeclaration.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyPairOrTriple, context.session)

    context(context: CheckerContext)
    private fun FirValueParameterSymbol.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyPairOrTriple, context.session)

    /**
     * The name of the first tuple type the (fully expanded) type mentions, or null. Flexible
     * types are inspected through their upper bound. `Pair` and `Triple` are final, so a direct
     * classifier match suffices — no subtype can smuggle the tuple shape in under another name.
     *
     * A type-use `@IntentionallyPairOrTriple` exempts the annotated type and everything nested
     * in it. [ExemptionExplanationChecker] never sees type-use annotations, so the explanation
     * requirement is enforced here, where the exemption is honored.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun ConeKotlinType.findExposedTupleType(): Name? {
        val session = context.session
        val exemption = customAnnotations.firstOrNull {
            it.toAnnotationClassIdSafe(session) == WatchdogClassIds.IntentionallyPairOrTriple
        }
        if (exemption != null) {
            exemption.unexplainedExemptionReason()?.let { reason ->
                reporter.reportOn(
                    source = exemption.source,
                    factory = WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION,
                    a = WatchdogClassIds.IntentionallyPairOrTriple.shortClassName,
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
            if (classId in tupleTypes) {
                return classId.shortClassName
            }
        }

        return type.typeArguments.firstNotNullOfOrNull { it.type?.findExposedTupleType() }
    }

    companion object {
        private val tupleTypes: Set<ClassId> = setOf(
            ClassId(FqName("kotlin"), Name.identifier("Pair")),
            ClassId(FqName("kotlin"), Name.identifier("Triple")),
        )
    }
}
