package org.jetbrains.kotlin.libs.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.libs.watchdog.WatchdogCompilerPluginRegistrar

open class AbstractJvmDiagnosticTest :
    DevKitJvmDiagnosticTest({ configurePlugin(WatchdogCompilerPluginRegistrar()) })
