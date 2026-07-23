package org.jetbrains.kotlinx.libs.watchdog

/** Severity with which a watchdog diagnostic is reported. */
public enum class WatchdogSeverity {
    /** The diagnostic fails the compilation. */
    ERROR,

    /** The diagnostic is reported as a compiler warning. */
    WARNING,
}
