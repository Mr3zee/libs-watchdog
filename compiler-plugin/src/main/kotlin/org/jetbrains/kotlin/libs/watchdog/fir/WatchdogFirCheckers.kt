package org.jetbrains.kotlin.libs.watchdog.fir

import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
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
        override val classCheckers: Set<FirClassChecker> =
            if (session.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) != ExplicitApiMode.DISABLED) {
                setOf(OpenApiChecker(severities), ExhaustiveApiChecker(severities), UndocumentedApiChecker(severities))
            } else {
                emptySet()
            }
    }
}
