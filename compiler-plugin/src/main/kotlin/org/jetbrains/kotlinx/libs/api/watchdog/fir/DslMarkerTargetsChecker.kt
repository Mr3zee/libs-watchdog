package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.getTargetAnnotation
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.unwrapAndFlattenArgument
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Reports DSL markers ([kotlin.DslMarker] annotations) that allow annotation targets on which the
 * marker has no effect. Receiver scope control only reacts to markers found on classifier
 * declarations, type usages, and type aliases (see the
 * [DSL marker design note](https://github.com/Kotlin/KEEP/blob/main/notes/0005-dsl-marker.md)),
 * so only the `CLASS`, `ANNOTATION_CLASS`, `TYPE`, and `TYPEALIAS` targets are effective. Every
 * other target lets users apply the marker where it silently restricts nothing, giving a false
 * sense of scope control. A DSL marker without an explicit `@Target` is reported as well: the
 * default target set allows nine such no-op targets and at the same time forbids the effective
 * `TYPE` and `TYPEALIAS` ones.
 *
 * The marker's own visibility is irrelevant: even an internal or private marker is applied
 * across the library's - possibly public - DSL classes, so every marker is checked.
 *
 * For an already-published marker, fixing the target set is a breaking change, so authors
 * acknowledge the legacy shape with `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`.
 */
internal class DslMarkerTargetsChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (declaration !is FirRegularClass || declaration.classKind != ClassKind.ANNOTATION_CLASS) {
            return
        }

        if (declaration.source?.kind != KtRealSourceElementKind) {
            return
        }

        val session = context.session
        if (!declaration.hasAnnotation(StandardClassIds.Annotations.DslMarker, session)) {
            return
        }

        if (declaration.hasAnnotation(
                WatchdogClassIds.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility,
                session,
            )
        ) {
            return
        }

        val targetAnnotation = declaration.getTargetAnnotation(session)
        if (targetAnnotation == null) {
            val factory = severities[WatchdogDiagnostics.DSL_MARKER_WITHOUT_EXPLICIT_TARGETS] ?: return
            reporter.reportOn(
                source = declaration.source,
                factory = factory,
                a = declaration.name,
            )
            return
        }

        val noopTargetFactory = severities[WatchdogDiagnostics.DSL_MARKER_NOOP_TARGET] ?: return
        val allowedTargets = targetAnnotation
            .findArgumentByName(StandardClassIds.Annotations.ParameterNames.targetAllowedTargets)
            ?.unwrapAndFlattenArgument(flattenArrays = true)
            .orEmpty()

        for (argument in allowedTargets) {
            val target = argument.extractEnumValueArgumentInfo()?.enumEntryName?.asString()
                ?.let(KotlinTarget::valueOrNull) ?: continue
            if (target !in effectiveDslMarkerTargets) {
                reporter.reportOn(
                    source = argument.source,
                    factory = noopTargetFactory,
                    a = declaration.name,
                    b = target.name,
                )
            }
        }
    }

    /**
     * The targets on which scope control reacts to a marker; `ANNOTATION_CLASS` counts because
     * it is a classifier declaration.
     */
    private val effectiveDslMarkerTargets = setOf(
        KotlinTarget.CLASS,
        KotlinTarget.ANNOTATION_CLASS,
        KotlinTarget.TYPE,
        KotlinTarget.TYPEALIAS,
    )
}
