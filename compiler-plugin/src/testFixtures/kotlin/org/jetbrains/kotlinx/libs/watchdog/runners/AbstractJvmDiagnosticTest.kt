package org.jetbrains.kotlinx.libs.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlinx.libs.watchdog.WatchdogCompilerPluginRegistrar

open class AbstractJvmDiagnosticTest : DevKitJvmDiagnosticTest({
    configurePlugin(WatchdogCompilerPluginRegistrar())
})
