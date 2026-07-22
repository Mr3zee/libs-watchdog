package org.jetbrains.kotlin.libs.watchdog

import com.autonomousapps.kit.GradleBuilder.build
import kotlin.test.assertFalse
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.assertOutputContains
import org.junit.Test

class WatchdogProjectTest {
    @Test
    fun warnsOnUnprotectedOpenAndExhaustiveApi() {
        val project =
            object : WatchdogProject() {
                    override fun sources() = listOf(source(unacknowledgedFile))
                }
                .gradleProject
        val result = build(project.rootDir, "build")
        result.assertOutputContains("can be subclassed outside the library without restriction")
        result.assertOutputContains("can be matched exhaustively by clients")
    }

    @Test
    fun acknowledgedApiCompilesWithoutWarnings() {
        val project =
            object : WatchdogProject() {
                    override fun sources() = listOf(source(acknowledgedFile))
                }
                .gradleProject
        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("can be subclassed outside the library"))
        assertFalse(result.output.contains("can be matched exhaustively by clients"))
    }
}

@Language("kotlin")
private val unacknowledgedFile =
    """
    open class UnprotectedOpenClass

    enum class UnmarkedEnum { A, B }
    """
        .trimIndent()

@Language("kotlin")
private val acknowledgedFile =
    """
    @IntentionallyOpen
    open class DeliberatelyOpenClass

    @IntentionallyExhaustive
    enum class MarkedEnum { A, B }
    """
        .trimIndent()
