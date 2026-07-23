package org.jetbrains.kotlinx.libs.api.watchdog

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused") // Used via reflection.
public class WatchdogSupportPlugin : DevKitSupportPlugin(PluginInfo.PLUGIN_INFO) {
    override fun apply(target: Project) {
        val extension = target.extensions.create("libsApiWatchdog", WatchdogGradleExtension::class.java)
        target.afterEvaluate { project ->
            if (extension.suggestTapmoc.get() && TAPMOC_PLUGIN_IDS.none(project.pluginManager::hasPlugin)) {
                project.logger.warn(tapmocSuggestion(project.path))
            }
            if (extension.suggestAbiValidation.get() && !project.hasAbiValidation()) {
                project.logger.warn(abiValidationSuggestion(project.path))
            }
        }
    }

    override fun Project.applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val extension = extensions.getByType(WatchdogGradleExtension::class.java)
        return providers.provider {
            extension.diagnosticSeverities().map { (diagnostic, severity) ->
                SubpluginOption("diagnosticSeverity", "$diagnostic:${severity.get().name.lowercase()}")
            }
        }
    }

    private companion object {
        /** Tapmoc's plugin id, plus the id of its former incarnation, CompatPatrouille. */
        private val TAPMOC_PLUGIN_IDS = listOf("com.gradleup.tapmoc", "com.gradleup.compat.patrouille")

        /** The standalone Binary Compatibility Validator's plugin id. */
        private const val BCV_PLUGIN_ID = "org.jetbrains.kotlinx.binary-compatibility-validator"

        /** Tasks the Kotlin Gradle plugin registers only once its ABI validation is activated. */
        private val ABI_VALIDATION_TASK_NAMES = listOf("checkKotlinAbi", "checkLegacyAbi")

        /**
         * Whether some binary compatibility validation guards this project: the standalone
         * Binary Compatibility Validator plugin (it covers subprojects when applied to a parent
         * project) or the Kotlin Gradle plugin's built-in ABI validation.
         */
        private fun Project.hasAbiValidation(): Boolean {
            val standaloneApplied = generateSequence(this) { it.parent }
                .any { it.pluginManager.hasPlugin(BCV_PLUGIN_ID) }
            if (standaloneApplied) return true
            val kotlin = extensions.findByName("kotlin") as? ExtensionAware ?: return false
            // Kotlin 2.2 and 2.3 register the DSL as a `kotlin` sub-extension whose `enabled`
            // flag is the opt-in. The flag is deprecated for removal in the API compiled
            // against, so it is read reflectively.
            val legacyAbiValidation = kotlin.extensions.findByName("abiValidation")
            if (legacyAbiValidation != null) {
                val enabled = runCatching {
                    legacyAbiValidation.javaClass.getMethod("getEnabled").invoke(legacyAbiValidation)
                }.getOrNull()
                return (enabled as? Provider<*>)?.orNull == true
            }
            // Kotlin 2.4+ activates ABI validation the moment the `abiValidation` DSL property
            // is touched (its getter has that side effect, so it must not be called here);
            // activation is observed through the tasks it registers instead.
            return ABI_VALIDATION_TASK_NAMES.any(tasks.names::contains)
        }

        private fun tapmocSuggestion(projectPath: String): String = """
            |Project '$projectPath' applies libs-api-watchdog but not Tapmoc. The watchdog guards the shape of
            |the public API, while Tapmoc pins the Java and Kotlin compatibility levels the artifacts are
            |built against, keeping them consumable from the oldest JDK and Kotlin versions the library
            |supports. Enable it in the module's build script, replacing <version> with the latest release
            |(https://github.com/GradleUp/Tapmoc/releases/latest):
            |
            |    plugins {
            |        id("com.gradleup.tapmoc") version "<version>"
            |    }
            |
            |    tapmoc {
            |        java(17)        // oldest supported Java release
            |        kotlin("2.1.0") // oldest supported Kotlin version
            |    }
            |
            |See https://gradleup.com/tapmoc/ for the full configuration reference.
            |
            |Disable this suggestion with `libsApiWatchdog { suggestTapmoc.set(false) }`.
        """.trimMargin()

        private fun abiValidationSuggestion(projectPath: String): String = """
            |Project '$projectPath' applies libs-api-watchdog but no binary compatibility validation is enabled.
            |The watchdog reviews the shape of new API declarations, while binary compatibility validation
            |compares each build against a committed dump of the released API surface and catches accidental
            |breaking changes to it. Enable the Kotlin Gradle plugin's built-in ABI validation in the
            |module's build script (on Kotlin 2.2 and 2.3, write `abiValidation { enabled.set(true) }`
            |instead):
            |
            |    import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
            |
            |    kotlin {
            |        @OptIn(ExperimentalAbiValidation::class)
            |        abiValidation()
            |    }
            |
            |See https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html for the check and
            |dump tasks and the full configuration reference. On older Kotlin versions, apply the standalone
            |Binary Compatibility Validator plugin instead: https://github.com/Kotlin/binary-compatibility-validator.
            |
            |Disable this suggestion with `libsApiWatchdog { suggestAbiValidation.set(false) }`.
        """.trimMargin()
    }
}
