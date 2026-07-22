package org.jetbrains.kotlin.libs.watchdog.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class WatchdogFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::WatchdogFirCheckers
    }
}
