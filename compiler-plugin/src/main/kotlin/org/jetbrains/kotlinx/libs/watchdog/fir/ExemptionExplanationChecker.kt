package org.jetbrains.kotlinx.libs.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.name.Name

/**
 * Reports watchdog exemption annotations that do not explain why they are applied. Every
 * exemption defaults to `reason = ExemptionReason.OTHER` with an empty `description`, and such a
 * bare acknowledgement documents nothing: authors either pick a reason that speaks for itself or
 * spell the motivation out in `description`. Only `FOR_BACKWARDS_COMPATIBILITY` and `API_DESIGN`
 * explain an exemption on their own; the other reasons merely categorize it and keep the
 * description shorter, so they still require one. Unlike the API-surface checks, this one
 * validates the annotation call itself, so it fires on every usage regardless of the annotated
 * declaration's visibility, and it is always an error: the explanation requirement is what keeps
 * the other exemptions honest.
 */
internal class ExemptionExplanationChecker : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        for (annotation in declaration.symbol.resolvedAnnotationsWithArguments) {
            val classId = annotation.toAnnotationClassIdSafe(context.session) ?: continue
            if (classId !in WatchdogClassIds.exemptionAnnotations) {
                continue
            }

            val unexplainedReason = annotation.unexplainedReason() ?: continue

            reporter.reportOn(
                source = annotation.source,
                factory = WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION,
                a = classId.shortClassName,
                b = unexplainedReason,
            )
        }
    }

    private val reasonParameter = Name.identifier("reason")
    private val descriptionParameter = Name.identifier("description")
    private val otherReason = Name.identifier("OTHER")

    /**
     * The reasons that explain an exemption on their own. Every other entry — including ones a
     * future annotations version may add — only categorizes the exemption and requires a
     * non-empty description next to it.
     */
    private val selfSufficientReasons = setOf(
        Name.identifier("FOR_BACKWARDS_COMPATIBILITY"),
        Name.identifier("API_DESIGN"),
    )

    /**
     * The reason that fails to explain this exemption on its own ([otherReason] when the
     * argument is absent), or null when the exemption is explained — by a self-sufficient
     * reason or by a non-blank description.
     */
    private fun FirAnnotation.unexplainedReason(): Name? {
        val reasonArgument = findArgumentByName(reasonParameter, returnFirstWhenNotFound = false)
        val reason = if (reasonArgument == null) {
            otherReason
        } else {
            // An argument that resolves to no enum entry is already a compilation error, so it
            // is not reported again.
            reasonArgument.extractEnumValueArgumentInfo()?.enumEntryName ?: return null
        }
        if (reason in selfSufficientReasons) {
            return null
        }

        if (getStringArgument(descriptionParameter)?.isNotBlank() == true) {
            return null
        }

        return reason
    }
}
