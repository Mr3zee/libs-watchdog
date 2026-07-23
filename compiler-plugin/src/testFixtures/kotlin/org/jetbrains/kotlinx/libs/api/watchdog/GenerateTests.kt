package org.jetbrains.kotlinx.libs.api.watchdog

import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitTestGenerator
import org.jetbrains.kotlin.compiler.plugin.devkit.sourceSetTestClass
import org.jetbrains.kotlinx.libs.api.watchdog.runners.AbstractJvmDiagnosticTest
import org.jetbrains.kotlinx.libs.api.watchdog.runners.AbstractPsiJvmDiagnosticTest

fun main(args: Array<String>) = DevKitTestGenerator.generate(args) {
    sourceSetTestClass<AbstractJvmDiagnosticTest> {
        model("diagnostics")
    }
    sourceSetTestClass<AbstractPsiJvmDiagnosticTest> {
        model("diagnostics")
    }
}
