package org.jetbrains.kotlin.libs.watchdog

import com.autonomousapps.kit.GradleBuilder.build
import kotlin.test.assertFalse
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.assertOutputContains
import org.junit.Test

class WatchdogProjectTest {
    @Test
    fun warnsOnUnprotectedOpenAndExhaustiveApi() {
        val project = object : WatchdogProject() {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = build(project.rootDir, "build")
        result.assertOutputContains("can be subclassed outside the library without restriction")
        result.assertOutputContains("can be matched exhaustively by clients")
        result.assertOutputContains("has no KDoc")
    }

    @Test
    fun acknowledgedApiCompilesWithoutWarnings() {
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
