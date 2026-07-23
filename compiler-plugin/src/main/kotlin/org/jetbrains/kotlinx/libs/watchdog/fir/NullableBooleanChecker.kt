package org.jetbrains.kotlinx.libs.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Reports nullable Booleans in publicly visible signatures, type parameter bounds and type
 * arguments included (`List<Boolean?>` still exposes the unnamed third state; see
 * [ExposedTypeChecker] for the shared sweep). `Boolean?` models three states but names only two
 * of them: every use site has to know what `null` stands for, and three-state logic hides in
 * two-branch `if`s, so the API should name all three states with an enum class instead. Authors
 * acknowledge a deliberate nullable Boolean with `@IntentionallyNullableBoolean`.
 *
 * Unlike [BooleanParameterChecker], constructors are checked too: a stored three-state flag is as
 * opaque to its readers as a passed one. A `vararg` parameter needs no special casing: the array
 * carrying the arguments is never a nullable Boolean itself, and a `Boolean?` element type is
 * found as its type argument.
 */
internal class NullableBooleanChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : ExposedTypeChecker(WatchdogClassIds.IntentionallyNullableBoolean) {
    /** Nullability lives on the upper bound, so the lower one is what Kotlin sources declare. */
    override fun ConeKotlinType.declaredBound(): ConeKotlinType = lowerBoundIfFlexible()

    context(context: CheckerContext)
    override fun ConeKotlinType.violatingClassifier(): Name? =
        StandardClassIds.Boolean.shortClassName
            .takeIf { classId == StandardClassIds.Boolean && isMarkedNullable }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?, kind: String, name: Name, violation: Name) {
        val factory = severities[WatchdogDiagnostics.NULLABLE_BOOLEAN_PUBLIC_API] ?: return
        reporter.reportOn(
            source = source,
            factory = factory,
            a = kind,
            b = name,
        )
    }
}
