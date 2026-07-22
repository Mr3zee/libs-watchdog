package org.jetbrains.kotlin.libs.watchdog

import com.autonomousapps.kit.gradle.Plugin
import com.autonomousapps.kit.gradle.Repositories
import com.autonomousapps.kit.gradle.Repository
import org.jetbrains.kotlin.compiler.plugin.devkit.test.AbstractDevKitGradleProject
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion

open class WatchdogProject(
    multiplatform: Boolean = false,
    private val explicitApi: Boolean = true,
    private val extraBuildScript: String = "",
) : AbstractDevKitGradleProject(
    multiplatform = multiplatform,
) {
    override val defaultImports: List<String> = listOf(
        "org.jetbrains.kotlin.libs.watchdog.IntentionallyOpen",
        "org.jetbrains.kotlin.libs.watchdog.IntentionallyExhaustive",
        "org.jetbrains.kotlin.libs.watchdog.IntentionallyUndocumented",
        "org.jetbrains.kotlin.libs.watchdog.IntentionallyFunctionTypeAlias",
    )

    // The watchdog only activates in explicit API mode, so tests enable it unless they
    // specifically exercise the plugin being dormant.
    override fun StringBuilder.onBuildScript() {
        if (explicitApi) appendLine("kotlin { explicitApi() }")
        if (extraBuildScript.isNotBlank()) appendLine(extraBuildScript)
    }

    override val pluginUnderTest: Plugin = Plugin("org.jetbrains.kotlin.libs.watchdog", pluginUnderTestVersion)

    // The dev kit runtime artifacts are consumed from mavenLocal rather than from included builds.
    override fun repositories(defaults: List<Repository>): Repositories =
        Repositories((defaults + Repository.MAVEN_LOCAL).toMutableList())
}
