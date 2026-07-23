package org.jetbrains.kotlinx.libs.watchdog

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCLP
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlinx.libs.watchdog.fir.WatchdogDiagnostics
import org.jetbrains.kotlinx.libs.watchdog.fir.WatchdogSeverity

object WatchdogConfigurationKeys {
    /**
     * Severity overrides keyed by diagnostic name; diagnostics not listed here are errors, and
     * [WatchdogSeverity.NONE] disables a diagnostic entirely.
     */
    val DIAGNOSTIC_SEVERITIES: CompilerConfigurationKey<Map<String, WatchdogSeverity>> =
        CompilerConfigurationKey.create("watchdog diagnostic severities")
}

class WatchdogCommandLineProcessor : DevKitCommandLineProcessor(WatchdogCLP::class) {
    override val pluginId: String = PluginInfo.PLUGIN_ID
    override val pluginOptions: Collection<CliOption> = listOf(DIAGNOSTIC_SEVERITY_OPTION)

    companion object {
        val DIAGNOSTIC_SEVERITY_OPTION: CliOption = CliOption(
            optionName = "diagnosticSeverity",
            valueDescription = "<diagnostic name>:error|warning|none",
            description = "Report the named watchdog diagnostic with the given severity, " +
                    "or disable its check with 'none'. " +
                    "Every diagnostic not mentioned is reported as an error.",
            required = false,
            allowMultipleOccurrences = true,
        )
    }
}

class WatchdogCLP : DevKitCLP {
    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option.optionName) {
            WatchdogCommandLineProcessor.DIAGNOSTIC_SEVERITY_OPTION.optionName -> {
                val override = parseDiagnosticSeverity(value)
                val severities = configuration[WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES, emptyMap()]
                configuration.put(WatchdogConfigurationKeys.DIAGNOSTIC_SEVERITIES, severities + override)
            }
            else -> error("Unexpected config option: '${option.optionName}'")
        }
    }

    private fun parseDiagnosticSeverity(value: String): Pair<String, WatchdogSeverity> {
        val diagnosticName = value.substringBefore(':')
        val diagnostic = WatchdogDiagnostics.allDiagnostics.find { it.name == diagnosticName }
            ?: throw CliOptionProcessingException(
                "Unknown watchdog diagnostic '$diagnosticName'. Known diagnostics: " +
                        WatchdogDiagnostics.allDiagnostics.joinToString { it.name },
            )
        val level = value.substringAfter(':', missingDelimiterValue = "")
        val severity = when (level.lowercase()) {
            "error" -> WatchdogSeverity.ERROR
            "warning" -> WatchdogSeverity.WARNING
            "none" -> WatchdogSeverity.NONE
            else -> throw CliOptionProcessingException(
                "Invalid severity '$level' for watchdog diagnostic '$diagnosticName': " +
                        "expected 'error', 'warning', or 'none'.",
            )
        }
        return diagnostic.name to severity
    }
}
