package org.jetbrains.kotlin.libs.watchdog

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused") // Used via reflection.
class WatchdogSupportPlugin : DevKitSupportPlugin(PluginInfo.PLUGIN_INFO) {
    override fun apply(target: Project) {
        target.extensions.create("libsWatchdog", WatchdogGradleExtension::class.java)
    }

    override fun Project.applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> = providers.provider { emptyList() }
}
