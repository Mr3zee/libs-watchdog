package org.jetbrains.kotlin.libs.watchdog

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCLP
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

class WatchdogCommandLineProcessor : DevKitCommandLineProcessor(WatchdogCLP::class) {
    override val pluginId: String = PluginInfo.PLUGIN_ID
    override val pluginOptions: Collection<CliOption> = emptyList()
}

class WatchdogCLP : DevKitCLP {
    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        error("Unexpected config option: '${option.optionName}'")
    }
}
