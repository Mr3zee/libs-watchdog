package org.jetbrains.kotlin.libs.watchdog

import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitTestGenerator
import org.jetbrains.kotlin.compiler.plugin.devkit.sourceSetTestClass
import org.jetbrains.kotlin.libs.watchdog.runners.AbstractJvmDiagnosticTest

fun main(args: Array<String>) = DevKitTestGenerator.generate(args) {
    sourceSetTestClass<AbstractJvmDiagnosticTest> {
        model("diagnostics")
    }
}
