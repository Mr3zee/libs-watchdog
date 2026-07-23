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
 */
class WatchdogFirCheckers(
    session: FirSession,
    severities: WatchdogDiagnosticSeverities,
) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        private val enabled =
            session.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) != ExplicitApiMode.DISABLED

        override val classCheckers: Set<FirClassChecker> =
            if (enabled) {
                setOf(
                    OpenApiChecker(severities),
                    ExhaustiveApiChecker(severities),
                    DataClassChecker(severities),
                    DslMarkerTargetsChecker(severities),
                )
            } else {
                emptySet()
            }

        // These checkers watch every declaration kind, not just classes.
        override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> =
            if (enabled) {
                setOf(UndocumentedApiChecker(severities), ExemptionExplanationChecker())
            } else {
                emptySet()
            }

        override val typeAliasCheckers: Set<FirTypeAliasChecker> =
            if (enabled) setOf(FunctionTypeAliasChecker(severities)) else emptySet()

        // Dispatched to every callable: functions, properties, accessors, and value parameters.
        override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> =
            if (enabled) setOf(DslMarkerTypePositionChecker(severities)) else emptySet()
    }
}
