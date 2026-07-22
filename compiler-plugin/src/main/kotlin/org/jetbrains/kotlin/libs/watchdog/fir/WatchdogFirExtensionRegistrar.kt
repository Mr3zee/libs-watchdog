package org.jetbrains.kotlin.libs.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class WatchdogFirExtensionRegistrar(
    private val severities: WatchdogDiagnosticSeverities = WatchdogDiagnosticSeverities.DEFAULT,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ session: FirSession -> WatchdogFirCheckers(session, severities) }
    }
}
