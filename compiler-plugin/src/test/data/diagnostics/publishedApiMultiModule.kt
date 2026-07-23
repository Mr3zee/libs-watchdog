// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

// Published declarations are watched in every module of a multimodule compilation. The
// dependency module keeps its published API documented and acknowledged, so it compiles into a
// binary cleanly; the consuming module reports its own published declarations while using the
// dependency's published API through its public inline functions.

// MODULE: lib
// FILE: lib.kt
@file:JvmName("LibApi")

package libapi

import org.jetbrains.kotlinx.libs.watchdog.ExemptionReason
import org.jetbrains.kotlinx.libs.watchdog.IntentionallyInlinedLogic
import org.jetbrains.kotlinx.libs.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.libs.watchdog.IntentionallyUndocumented

/** Documented published class. */
@PublishedApi
internal class LibPublishedClass

@IntentionallyUndocumented(description = "Implementation detail of the inline API.")
@PublishedApi
internal class LibAcknowledgedClass

/** Documented; unrestricted subclassing is acknowledged. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
@PublishedApi
internal open class LibPublishedOpenClass

/** Documented. */
@PublishedApi
internal fun libPublishedHelper(): Int = 0

/** Documented; the inlined logic exists to exercise the published declarations above. */
@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline fun libInlineApi(block: () -> Int): Int {
    return block() + libPublishedHelper()
}

// MODULE: main(lib)
// FILE: main.kt

package foo.bar

import libapi.libInlineApi
import org.jetbrains.kotlinx.libs.watchdog.ExemptionReason
import org.jetbrains.kotlinx.libs.watchdog.IntentionallyInlinedLogic

@PublishedApi
internal class <!UNDOCUMENTED_PUBLIC_API!>MainPublishedClass<!>

/** Documented. */
@PublishedApi
internal open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>MainPublishedOpenClass<!>

/** Documented. */
@PublishedApi
internal enum class <!EXHAUSTIVE_PUBLIC_API!>MainPublishedEnum<!> {
    /** Documented. */
    ENTRY,
}

/** Documented. */
@PublishedApi
internal fun mainPublishedHelper(): Int = 0

/** Documented; the inlined logic exists to exercise the published declarations above. */
@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline fun mainInlineApi(block: () -> Int): Int {
    return libInlineApi(block) + mainPublishedHelper()
}
