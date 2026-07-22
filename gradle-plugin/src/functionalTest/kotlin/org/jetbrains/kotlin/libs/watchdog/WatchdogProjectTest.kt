package org.jetbrains.kotlin.libs.watchdog

import com.autonomousapps.kit.GradleBuilder.build
import com.autonomousapps.kit.GradleBuilder.buildAndFail
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.intellij.lang.annotations.Language
import org.junit.Test

class WatchdogProjectTest {
    @Test
    fun failsWithErrorsOnUnacknowledgedApiByDefault() {
        val project = object : WatchdogProject() {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported("e: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("e: ", "can be matched exhaustively by clients")
        result.assertDiagnosticReported("e: ", "has no KDoc")
    }

    @Test
    fun demotedDiagnosticsAreReportedAsWarnings() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                libsWatchdog {
                    openApiWithoutSubclassOptIn.set(org.jetbrains.kotlin.libs.watchdog.WatchdogSeverity.WARNING)
                    exhaustivePublicApi.set(org.jetbrains.kotlin.libs.watchdog.WatchdogSeverity.WARNING)
                    undocumentedPublicApi.set(org.jetbrains.kotlin.libs.watchdog.WatchdogSeverity.WARNING)
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = build(project.rootDir, "build")
        result.assertDiagnosticReported("w: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("w: ", "can be matched exhaustively by clients")
        result.assertDiagnosticReported("w: ", "has no KDoc")
    }

    @Test
    fun severityIsConfiguredPerDiagnostic() {
        // The compiler swallows regular warnings when a compilation fails with errors, so warning
        // reporting is forced to observe the demoted diagnostic next to the remaining errors.
        val project = object : WatchdogProject(
            extraBuildScript = """
                kotlin { compilerOptions { freeCompilerArgs.add("-Xreport-all-warnings") } }
                libsWatchdog {
                    undocumentedPublicApi.set(org.jetbrains.kotlin.libs.watchdog.WatchdogSeverity.WARNING)
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported("e: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("e: ", "can be matched exhaustively by clients")
        result.assertDiagnosticReported("w: ", "has no KDoc")
    }

    @Test
    fun acknowledgedApiCompilesWithoutDiagnostics() {
        val project = object : WatchdogProject() {
            override fun sources() = listOf(source(acknowledgedFile))
        }.gradleProject

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("can be subclassed outside the library"))
        assertFalse(result.output.contains("can be matched exhaustively by clients"))
        assertFalse(result.output.contains("has no KDoc"))
    }

    @Test
    fun silentWithoutExplicitApiMode() {
        val project = object : WatchdogProject(explicitApi = false) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("can be subclassed outside the library"))
        assertFalse(result.output.contains("can be matched exhaustively by clients"))
        assertFalse(result.output.contains("has no KDoc"))
    }

    /** Asserts the message was reported with the given compiler severity prefix (`e: ` or `w: `). */
    private fun BuildResult.assertDiagnosticReported(severityPrefix: String, message: String) {
        assertTrue(
            output.lineSequence().any { it.startsWith(severityPrefix) && message in it },
            "Expected a '$severityPrefix' line containing '$message' in build output:\n$output",
        )
    }
}

@Language("kotlin")
private val unacknowledgedFile = """
    public open class UnprotectedOpenClass

    public enum class UnmarkedEnum { A, B }
""".trimIndent()

@Language("kotlin")
private val acknowledgedFile = """
    /** A deliberately open class. */
    @IntentionallyOpen
    public open class DeliberatelyOpenClass

    /** A deliberately exhaustive enum. */
    @IntentionallyExhaustive
    public enum class MarkedEnum { A, B }

    @IntentionallyUndocumented
    public class DeliberatelyUndocumentedClass
""".trimIndent()
