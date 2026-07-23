package org.jetbrains.kotlinx.libs.watchdog

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlinx.libs.watchdog.fir.WatchdogDiagnosticSeverities
import org.jetbrains.kotlinx.libs.watchdog.fir.WatchdogFirExtensionRegistrar

class WatchdogCompilerPluginRegistrar : DevKitCompilerPluginRegistrar(
    registrarClass = WatchdogComponentRegistrar::class,
) {
    override val pluginId: String = PluginInfo.PLUGIN_ID
    override val supportsK2: Boolean = true
}

class WatchdogComponentRegistrar : DevKitComponentRegistrar {
    override fun CompilerPluginRegistrar.ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val severities = WatchdogDiagnosticSeverities(
            configuration[WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES, emptyMap()],
        )
        FirExtensionRegistrarAdapter.registerExtension(WatchdogFirExtensionRegistrar(severities))
    }
}
