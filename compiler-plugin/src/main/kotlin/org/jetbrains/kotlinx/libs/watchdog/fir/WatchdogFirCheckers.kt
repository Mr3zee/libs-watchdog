package org.jetbrains.kotlinx.libs.watchdog.fir

import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirTypeAliasChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.languageVersionSettings

/**
 * The checkers guard a library's public API surface, so they only run in modules compiled with
 * [explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode)
 * (`kotlin { explicitApi() }` / `-Xexplicit-api`), in either `strict` or `warning` variant.
 *
 * A checker whose every diagnostic is configured to [WatchdogSeverity.NONE] is not registered at
 * all, so a disabled check costs nothing per declaration.
 */
class WatchdogFirCheckers(
    session: FirSession,
    severities: WatchdogDiagnosticSeverities,
) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        private val enabled =
            session.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) != ExplicitApiMode.DISABLED

        private fun <C : Any> C.unlessDisabled(vararg diagnostics: ConfigurableWatchdogDiagnostic<*>): C? =
            takeIf { enabled && diagnostics.any(severities::isEnabled) }

        override val classCheckers: Set<FirClassChecker> = setOfNotNull(
            OpenApiChecker(severities).unlessDisabled(
                WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN,
                WatchdogDiagnostics.SUBCLASS_OPT_IN_WITHOUT_MARKERS,
            ),
            ExhaustiveApiChecker(severities).unlessDisabled(WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API),
            DataClassChecker(severities).unlessDisabled(WatchdogDiagnostics.DATA_CLASS_PUBLIC_API),
            StatefulClassWithoutToStringChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_TO_STRING),
            DslMarkerTargetsChecker(severities).unlessDisabled(
                WatchdogDiagnostics.DSL_MARKER_NOOP_TARGET,
                WatchdogDiagnostics.DSL_MARKER_WITHOUT_EXPLICIT_TARGETS,
            ),
        )

        // These checkers watch every declaration kind, not just classes. MutableCollectionChecker
        // is one of them because it also inspects class-level type parameter bounds.
        override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> = setOfNotNull(
            UndocumentedApiChecker(severities).unlessDisabled(WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API),
            // Not configurable: exemption explanations are enforced whenever the plugin runs.
            ExemptionExplanationChecker().takeIf { enabled },
            MutableCollectionChecker(severities).unlessDisabled(WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API),
        )

        override val typeAliasCheckers: Set<FirTypeAliasChecker> = setOfNotNull(
            FunctionTypeAliasChecker(severities).unlessDisabled(WatchdogDiagnostics.FUNCTION_TYPE_ALIAS_PUBLIC_API),
        )

        // Dispatched to every callable: functions, properties, accessors, and value parameters.
        override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> = setOfNotNull(
            DslMarkerTypePositionChecker(severities).unlessDisabled(WatchdogDiagnostics.DSL_MARKER_NOOP_TYPE_POSITION),
        )
    }
}
