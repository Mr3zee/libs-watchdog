package org.jetbrains.kotlinx.libs.api.watchdog

import org.gradle.api.Project
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
            |Disable this suggestion with `libsApiWatchdog { suggestTapmoc.set(false) }`.
        """.trimMargin()
    }
}
