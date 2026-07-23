package org.jetbrains.kotlinx.libs.watchdog

import com.autonomousapps.kit.GradleBuilder.build
import com.autonomousapps.kit.GradleBuilder.buildAndFail
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
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
        result.assertDiagnosticReported("e: ", "abbreviates a function type")
        result.assertDiagnosticReported("e: ", "bakes its constructor property list")
        result.assertDiagnosticReported("e: ", "neither declares nor inherits a `toString`")
        result.assertDiagnosticReported("e: ", "exposes the mutable collection type")
        result.assertDiagnosticReported("e: ", "exposes the tuple type")
        result.assertDiagnosticReported("e: ", "takes the Boolean parameter")
        result.assertDiagnosticReported("e: ", "exposes a nullable Boolean")
        result.assertDiagnosticReported("e: ", "is required but declared after an optional parameter")
        result.assertDiagnosticReported("e: ", "appear in the opposite order in another overload")
        result.assertDiagnosticReported("e: ", "does more than delegate to a non-inline function")
        result.assertDiagnosticReported("e: ", "compiled JVM name is mangled")
        result.assertDiagnosticReported("e: ", "allows the FUNCTION annotation target")
        result.assertDiagnosticReported("e: ", "declares no explicit @Target")
        result.assertDiagnosticReported("e: ", "has no effect on this parameter type")
    }

    @Test
    fun demotedDiagnosticsAreReportedAsWarnings() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                libsWatchdog {
                    openApiWithoutSubclassOptIn.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    exhaustivePublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    undocumentedPublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    functionTypeAliasPublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    dataClassPublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    statefulClassWithoutToString.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    mutableCollectionPublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    pairOrTriplePublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    booleanParameterPublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    nullableBooleanPublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    requiredParameterAfterOptional.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    inconsistentParameterOrderInOverloads.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    inlineFunctionWithLogic.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    mangledJvmNamePublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    dslMarkerNoopTarget.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    dslMarkerWithoutExplicitTargets.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                    dslMarkerNoopTypePosition.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = build(project.rootDir, "build")
        result.assertDiagnosticReported("w: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("w: ", "can be matched exhaustively by clients")
        result.assertDiagnosticReported("w: ", "has no KDoc")
        result.assertDiagnosticReported("w: ", "abbreviates a function type")
        result.assertDiagnosticReported("w: ", "bakes its constructor property list")
        result.assertDiagnosticReported("w: ", "neither declares nor inherits a `toString`")
        result.assertDiagnosticReported("w: ", "exposes the mutable collection type")
        result.assertDiagnosticReported("w: ", "exposes the tuple type")
        result.assertDiagnosticReported("w: ", "takes the Boolean parameter")
        result.assertDiagnosticReported("w: ", "exposes a nullable Boolean")
        result.assertDiagnosticReported("w: ", "is required but declared after an optional parameter")
        result.assertDiagnosticReported("w: ", "appear in the opposite order in another overload")
        result.assertDiagnosticReported("w: ", "does more than delegate to a non-inline function")
        result.assertDiagnosticReported("w: ", "compiled JVM name is mangled")
        result.assertDiagnosticReported("w: ", "allows the FUNCTION annotation target")
        result.assertDiagnosticReported("w: ", "declares no explicit @Target")
        result.assertDiagnosticReported("w: ", "has no effect on this parameter type")
    }

    @Test
    fun severityIsConfiguredPerDiagnostic() {
        // The compiler swallows regular warnings when a compilation fails with errors, so warning
        // reporting is forced to observe the demoted diagnostic next to the remaining errors.
        val project = object : WatchdogProject(
            extraBuildScript = """
                kotlin { compilerOptions { freeCompilerArgs.add("-Xreport-all-warnings") } }
                libsWatchdog {
                    undocumentedPublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
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
    fun disabledDiagnosticsAreNotReported() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                libsWatchdog {
                    undocumentedPublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.NONE)
                    statefulClassWithoutToString.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.NONE)
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        // The remaining diagnostics still fail the build...
        result.assertDiagnosticReported("e: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("e: ", "can be matched exhaustively by clients")
        // ...while the disabled ones are not reported at all.
        assertFalse(result.output.contains("has no KDoc"))
        assertFalse(result.output.contains("neither declares nor inherits a `toString`"))
    }

    @Test
    fun unexplainedExemptionIsAlwaysAnError() {
        // The only configurable diagnostic the file triggers is demoted, so the remaining error
        // proves EXEMPTION_WITHOUT_EXPLANATION ignores severity configuration: the extension
        // deliberately offers no property for it.
        val project = object : WatchdogProject(
            extraBuildScript = """
                libsWatchdog {
                    undocumentedPublicApi.set(org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity.WARNING)
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unexplainedExemptionFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported("e: ", "exemption does not explain why it is applied")
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
        assertFalse(result.output.contains("abbreviates a function type"))
        assertFalse(result.output.contains("bakes its constructor property list"))
        assertFalse(result.output.contains("neither declares nor inherits a `toString`"))
        assertFalse(result.output.contains("exposes the mutable collection type"))
        assertFalse(result.output.contains("exposes the tuple type"))
        assertFalse(result.output.contains("takes the Boolean parameter"))
        assertFalse(result.output.contains("exposes a nullable Boolean"))
        assertFalse(result.output.contains("is required but declared after an optional parameter"))
        assertFalse(result.output.contains("appear in the opposite order in another overload"))
        assertFalse(result.output.contains("does more than delegate to a non-inline function"))
        assertFalse(result.output.contains("compiled JVM name is mangled"))
        assertFalse(result.output.contains("DSL marker"))
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
        assertFalse(result.output.contains("abbreviates a function type"))
        assertFalse(result.output.contains("bakes its constructor property list"))
        assertFalse(result.output.contains("neither declares nor inherits a `toString`"))
        assertFalse(result.output.contains("exposes the mutable collection type"))
        assertFalse(result.output.contains("exposes the tuple type"))
        assertFalse(result.output.contains("takes the Boolean parameter"))
        assertFalse(result.output.contains("exposes a nullable Boolean"))
        assertFalse(result.output.contains("is required but declared after an optional parameter"))
        assertFalse(result.output.contains("appear in the opposite order in another overload"))
        assertFalse(result.output.contains("does more than delegate to a non-inline function"))
        assertFalse(result.output.contains("compiled JVM name is mangled"))
        assertFalse(result.output.contains("DSL marker"))
    }

    @Test
    fun internalAnnotationMarkerExemptsAcrossModules() {
        // The marker annotation lives in `:lib`, so the consuming root module reads the
        // @InternalAnnotationMarker meta-annotation from the compiled dependency.
        val project = object : WatchdogProject() {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(markerConsumerFile, "consumer", "test", true, "test.lib.InternalLibApi"))
                    dependencies(implementation(":lib"))
                }
                subproject("lib") {
                    sources(source(markerLibraryFile, "markers", "test.lib"))
                }
            }
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        // The unmarked control declaration proves the checks ran in the consuming module...
        result.assertDiagnosticReported("e: ", "'WatchedClass' is part of the public API but has no KDoc")
        // ...while declarations marked with the dependency's marker annotation are exempt.
        assertFalse(result.output.contains("InternalOpenClass"))
        assertFalse(result.output.contains("memberOfInternal"))
        assertFalse(result.output.contains("InternalEnum"))
        assertFalse(result.output.contains("INTERNAL_ENTRY"))
        assertFalse(result.output.contains("internalFunction"))
        assertFalse(result.output.contains("can be subclassed outside the library"))
        assertFalse(result.output.contains("can be matched exhaustively by clients"))
    }

    /** Asserts the message was reported with the given compiler severity prefix (`e: ` or `w: `). */
    private fun BuildResult.assertDiagnosticReported(severityPrefix: String, message: String) {
        assertTrue(
            output.lineSequence().any { it.startsWith(severityPrefix) && message in it },
            "Expected a '$severityPrefix' line containing '$message' in build output:\n$output",
        )
    }
}

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val unacknowledgedFile = """
    public open class UnprotectedOpenClass

    public enum class UnmarkedEnum { A, B }

    /** An unacknowledged function type alias. */
    public typealias UnacknowledgedCallback = (Int) -> Unit

    /**
     * An unacknowledged data class.
     *
     * @param x the only coordinate.
     */
    public data class UnmarkedData(val x: Int)

    /**
     * A stateful session relying on the opaque default toString.
     *
     * @param id the session identifier.
     */
    public class UnrenderedSession(public val id: Int)

    /** A function handing out the library's mutable state. */
    public fun leakState(): MutableList<String> = mutableListOf()

    /** A function pairing coordinates without naming them. */
    public fun locateOrigin(): Pair<Int, Int> = 0 to 0

    /** A function switched by an opaque positional flag. */
    public fun toggleWork(enabled: Boolean) {}

    /** A query returning a silent three-state flag. */
    public fun lastKnownState(): Boolean? = null

    /** A function declaring a required parameter after an optional one. */
    public fun retryWork(retries: Int = 3, host: String) {}

    /** An overload setting the parameter order convention. */
    public fun drawShape(x: Int, y: Int) {}

    /** An overload breaking the parameter order convention. */
    public fun drawShape(y: Int, x: Int, scale: Double) {}

    /** An inline function computing inline instead of delegating. */
    @Suppress("NOTHING_TO_INLINE")
    public inline fun squared(value: Int): Int = value * value

    /** A DSL marker with a target on which it has no effect. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    public annotation class NoopTargetDsl

    /** A DSL marker left with the default target set. */
    @DslMarker
    public annotation class TargetlessDsl

    /** A DSL marker with tidy targets, misapplied to an inert type position below. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
    public annotation class ScopedDsl

    /** A parameter type carrying a DSL marker that restricts nothing. */
    public fun processTag(tag: @ScopedDsl UnprotectedOpenClass): Unit = Unit

    /**
     * A user handle compiled to its underlying type.
     *
     * @param raw the raw handle value.
     */
    @JvmInline
    public value class UserHandle(public val raw: String)

    /** A lookup whose JVM name is mangled by the value class parameter. */
    public fun findUser(handle: UserHandle) {}
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val unexplainedExemptionFile = """
    @IntentionallyUndocumented
    public class UnexplainedExemption
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val markerLibraryFile = """
    /** Flags declarations that are public for technical reasons but are not supported API. */
    @InternalAnnotationMarker
    @Target(
        AnnotationTarget.CLASS,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY,
    )
    public annotation class InternalLibApi
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val markerConsumerFile = """
    @InternalLibApi
    public open class InternalOpenClass {
        public fun memberOfInternal() {}
    }

    @InternalLibApi
    public enum class InternalEnum { INTERNAL_ENTRY }

    @InternalLibApi
    public fun internalFunction() {}

    public class WatchedClass
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val acknowledgedFile = """
    /** A deliberately open class. */
    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
    public open class DeliberatelyOpenClass

    /** A deliberately exhaustive enum. */
    @IntentionallyExhaustive(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    public enum class MarkedEnum {
        /** The first entry. */
        A,

        /** The second entry. */
        B,
    }

    @IntentionallyUndocumented(description = "Self-explanatory.")
    public class DeliberatelyUndocumentedClass

    /** A deliberate function type alias. */
    @IntentionallyFunctionTypeAlias(reason = ExemptionReason.API_DESIGN)
    public typealias DeliberateCallback = (Int) -> Unit

    /**
     * A deliberately stable data holder.
     *
     * @param x the only coordinate.
     */
    @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
    public data class DeliberateData(val x: Int)

    /**
     * A deliberately opaque credentials holder.
     *
     * @param token the secret that must not leak into logs.
     */
    @IntentionallyWithoutToString(reason = ExemptionReason.API_DESIGN, description = "The token must not leak into logs.")
    public class OpaqueCredentials(public val token: String)

    /** A deliberately shared mutable buffer. */
    @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
    public fun sharedBuffer(): MutableList<String> = mutableListOf()

    /** Deliberately shared mutable batches, acknowledged on the type usage. */
    public fun sharedBatches(): List<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<String>> = emptyList()

    /** A deliberately exposed coordinate pair. */
    @IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)
    public fun originPoint(): Pair<Int, Int> = 0 to 0

    /** A deliberately Boolean-switched toggle. */
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public fun setEnabled(enabled: Boolean) {}

    /** A deliberately three-state query result. */
    @IntentionallyNullableBoolean(reason = ExemptionReason.EXTERNAL_CONTRACT, description = "Mirrors the wire format's optional flag.")
    public fun consentState(): Boolean? = null

    /** A legacy signature keeping its required parameter behind an optional one. */
    @IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    public fun legacyRetry(retries: Int = 3, host: String) {}

    /** An overload setting the parameter order convention. */
    public fun renderShape(x: Int, y: Int) {}

    /** An overload with a deliberately different parameter order. */
    @IntentionallyInconsistentParameterOrder(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    public fun renderShape(y: Int, x: Int, alpha: Long) {}

    /** A deliberately inlined fast path. */
    @IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
    @Suppress("NOTHING_TO_INLINE")
    public inline fun cubed(value: Int): Int = value * value * value

    /**
     * An account handle compiled to its underlying type.
     *
     * @param raw the raw handle value.
     */
    @JvmInline
    public value class AccountHandle(public val raw: String)

    /** A deliberately Kotlin-only lookup. */
    @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
    public fun findAccount(handle: AccountHandle) {}

    /** A DSL marker with only effective targets. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
    public annotation class TidyDsl

    /** A legacy DSL marker whose wrong target set is kept for backwards compatibility. */
    @IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    public annotation class LegacyDsl
""".trimIndent()
