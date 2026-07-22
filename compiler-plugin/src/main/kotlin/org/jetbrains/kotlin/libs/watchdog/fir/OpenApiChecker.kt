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
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.unwrapAndFlattenArgument
import org.jetbrains.kotlin.name.Name

/**
 * Reports publicly visible classes and interfaces that can be subclassed outside the library
 * without any control: every external subclass constrains how the declaration can evolve. Authors
 * either gate subclassing with [kotlin.SubclassOptInRequired] or explicitly acknowledge the
 * contract with `@IntentionallyOpen`. A `@SubclassOptInRequired` with no marker classes gates
 * nothing, so it is reported as well.
 */
internal class OpenApiChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common) {
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
        val subclassOptIn = declaration.getAnnotationByClassId(
            classId = WatchdogClassIds.SubclassOptInRequired,
            session = session,
        )

        if (subclassOptIn != null) {
            if (!subclassOptIn.hasMarkerClasses()) {
                reporter.reportOn(
                    subclassOptIn.source,
                    severities[WatchdogDiagnostics.SUBCLASS_OPT_IN_WITHOUT_MARKERS],
                )
            }
            return
        }

        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyOpen, session)) return

        reporter.reportOn(
            declaration.source,
            severities[WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN],
            declaration.classKind,
            declaration.name,
        )
    }

    private val markerClassParameter = Name.identifier("markerClass")

    /** `markerClass` is a vararg, so `@SubclassOptInRequired` compiles with no markers at all. */
    private fun FirAnnotation.hasMarkerClasses(): Boolean =
        findArgumentByName(markerClassParameter, returnFirstWhenNotFound = false)
            ?.unwrapAndFlattenArgument(flattenArrays = true)
            ?.isNotEmpty() == true
}
