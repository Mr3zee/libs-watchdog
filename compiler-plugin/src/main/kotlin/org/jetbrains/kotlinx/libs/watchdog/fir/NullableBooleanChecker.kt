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
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Reports nullable Booleans in publicly visible signatures — return types, property types, value
 * parameter types, and type parameter bounds (`<T : Boolean?>` admits the same three-state values
 * as a plain `Boolean?`), including their type arguments (`List<Boolean?>` still exposes the
 * unnamed third state). `Boolean?` models three states but names only two of them: every use site
 * has to know what `null` stands for, and three-state logic hides in two-branch `if`s, so the API
 * should name all three states with an enum class instead. A type alias does not change what
 * clients see. Authors acknowledge a deliberate nullable Boolean with
 * `@IntentionallyNullableBoolean` — on the whole declaration, on a single (type) parameter, or on
 * a type usage, where it covers the annotated type and everything nested in it. Type-use
 * exemptions never reach [ExemptionExplanationChecker], so this checker enforces the explanation
 * requirement for them itself.
 *
 * Unlike [BooleanParameterChecker], constructors are checked too: a stored three-state flag is as
 * opaque to its readers as a passed one.
 *
 * Deliberate exceptions:
 * - Extension receivers: an extension on `Boolean?` provides functionality for values the client
 *   already holds — typically a remedial helper like `fun Boolean?.orFalse()` — instead of
 *   exposing a new three-state value.
 * - Overrides: their signature is fixed by the overridden declaration and is reported there.
 * - Flexible (Java platform) types: their nullability is not declared in Kotlin sources, so only
 *   the non-nullable lower bound is inspected.
 *
 * A `val`/`var` primary constructor parameter is watched through the property it creates, so the
 * shared parameter/property text is reported once — even when only the property is public.
 */
internal class NullableBooleanChecker(
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
        reportNullableBoolean(declaration.returnTypeRef, "property", declaration.name, declaration)
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
        reportNullableBoolean(declaration.returnTypeRef, "function", declaration.name, declaration)
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

    /**
     * A `vararg` parameter needs no special casing: the array carrying the arguments is never a
     * nullable Boolean itself, and a `Boolean?` element type is found as its type argument.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkParameter(parameter: FirValueParameter) {
        if (parameter.isExempt()) {
            return
        }

        reportNullableBoolean(parameter.returnTypeRef, "parameter", parameter.name, parameter)
    }

    /**
     * Reports nullable bounds: a type parameter bounded by `Boolean?` constrains every
     * instantiation to the same three-state values as a direct mention of the bound.
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
                reportNullableBoolean(bound, "type parameter", typeParameter.name, typeParameter)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportNullableBoolean(typeRef: FirTypeRef, kind: String, name: Name, declaration: FirDeclaration) {
        if (!typeRef.coneType.exposesNullableBoolean()) {
            return
        }

        val factory = severities[WatchdogDiagnostics.NULLABLE_BOOLEAN_PUBLIC_API] ?: return
        reporter.reportOn(
            source = typeRef.source ?: declaration.source,
            factory = factory,
            a = kind,
            b = name,
        )
    }

    context(context: CheckerContext)
    private fun FirDeclaration.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyNullableBoolean, context.session)

    context(context: CheckerContext)
    private fun FirValueParameterSymbol.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyNullableBoolean, context.session)

    /**
     * Whether the (fully expanded) type mentions a nullable Boolean. Flexible types are inspected
     * through their non-nullable lower bound: a Java platform type does not declare nullability
     * in Kotlin sources, so it is not reported.
     *
     * A type-use `@IntentionallyNullableBoolean` exempts the annotated type and everything
     * nested in it. [ExemptionExplanationChecker] never sees type-use annotations, so the
     * explanation requirement is enforced here, where the exemption is honored.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun ConeKotlinType.exposesNullableBoolean(): Boolean {
        val session = context.session
        val exemption = customAnnotations.firstOrNull {
            it.toAnnotationClassIdSafe(session) == WatchdogClassIds.IntentionallyNullableBoolean
        }
        if (exemption != null) {
            exemption.unexplainedExemptionReason()?.let { reason ->
                reporter.reportOn(
                    source = exemption.source,
                    factory = WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION,
                    a = WatchdogClassIds.IntentionallyNullableBoolean.shortClassName,
                    b = reason,
                )
            }
            return false
        }

        val type = lowerBoundIfFlexible().let {
            if (it is ConeClassLikeType) it.fullyExpandedType() else it
        }

        if (type.classId == StandardClassIds.Boolean && type.isMarkedNullable) {
            return true
        }

        return type.typeArguments.any { it.type?.exposesNullableBoolean() == true }
    }
}
