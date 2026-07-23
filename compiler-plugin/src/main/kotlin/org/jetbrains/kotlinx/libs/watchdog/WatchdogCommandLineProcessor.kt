package org.jetbrains.kotlinx.libs.watchdog

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCLP
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlinx.libs.watchdog.fir.WatchdogDiagnostics

object WatchdogConfigurationKeys {
    /** Severity overrides keyed by diagnostic name; diagnostics not listed here are errors. */
    val DIAGNOSTIC_SEVERITIES: CompilerConfigurationKey<Map<String, Severity>> =
        CompilerConfigurationKey.create("watchdog diagnostic severities")
}

class WatchdogCommandLineProcessor : DevKitCommandLineProcessor(WatchdogCLP::class) {
    override val pluginId: String = PluginInfo.PLUGIN_ID
    override val pluginOptions: Collection<CliOption> = listOf(DIAGNOSTIC_SEVERITY_OPTION)

    companion object {
        val DIAGNOSTIC_SEVERITY_OPTION: CliOption = CliOption(
            optionName = "diagnosticSeverity",
            valueDescription = "<diagnostic name>:error|warning",
            description = "Report the named watchdog diagnostic with the given severity. " +
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

    private fun parseDiagnosticSeverity(value: String): Pair<String, Severity> {
        val diagnosticName = value.substringBefore(':')
        val diagnostic = WatchdogDiagnostics.allDiagnostics.find { it.name == diagnosticName }
            ?: throw CliOptionProcessingException(
                "Unknown watchdog diagnostic '$diagnosticName'. Known diagnostics: " +
                        WatchdogDiagnostics.allDiagnostics.joinToString { it.name },
            )
        val level = value.substringAfter(':', missingDelimiterValue = "")
        val severity = when (level.lowercase()) {
            "error" -> Severity.ERROR
            "warning" -> Severity.WARNING
            else -> throw CliOptionProcessingException(
                "Invalid severity '$level' for watchdog diagnostic '$diagnosticName': " +
                        "expected 'error' or 'warning'.",
            )
        }
        return diagnostic.name to severity
    }
}
