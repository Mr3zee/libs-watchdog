package org.jetbrains.kotlin.libs.watchdog.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.modality

/**
 * Warns about publicly visible classes and interfaces that can be subclassed outside the library
 * without any control: every external subclass constrains how the declaration can evolve. Authors
 * either gate subclassing with [kotlin.SubclassOptInRequired] or explicitly acknowledge the
 * contract with `@IntentionallyOpen`.
 */
internal object OpenApiChecker : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (declaration !is FirRegularClass) return
        if (!declaration.isWatchedPublicApi()) return

        val openForSubclassing =
            when {
                declaration.classKind == ClassKind.CLASS ->
                    declaration.modality == Modality.OPEN ||
                        declaration.modality == Modality.ABSTRACT
                // Sealed interfaces are reported by ExhaustiveApiChecker instead.
                declaration.classKind == ClassKind.INTERFACE ->
                    declaration.modality != Modality.SEALED
                else -> false
            }
        if (!openForSubclassing) return

        val session = context.session
        if (declaration.hasAnnotation(WatchdogClassIds.SubclassOptInRequired, session)) return
        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyOpen, session)) return

        reporter.reportOn(
            declaration.source,
            WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN,
            declaration.classKind,
            declaration.name,
        )
    }
}
