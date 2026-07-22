// RUN_PIPELINE_TILL: BACKEND
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

package foo.bar

import org.jetbrains.kotlin.libs.watchdog.IntentionallyExhaustive

// Unacknowledged exhaustive API: should warn.

enum class <!EXHAUSTIVE_PUBLIC_API!>UnmarkedEnum<!> {
    A,
    B,
}

sealed class <!EXHAUSTIVE_PUBLIC_API!>UnmarkedSealedClass<!> {
    class Left : UnmarkedSealedClass()

    class Right : UnmarkedSealedClass()

    // A non-final member of a sealed hierarchy is itself unrestricted subclassable API.
    abstract class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>Middle<!> : UnmarkedSealedClass()
}

sealed interface <!EXHAUSTIVE_PUBLIC_API!>UnmarkedSealedInterface<!> {
    class Impl : UnmarkedSealedInterface
}

// Deliberately exhaustive: no warning.

@IntentionallyExhaustive
enum class MarkedEnum {
    A,
    B,
}

@IntentionallyExhaustive
sealed class MarkedSealedClass {
    class Only : MarkedSealedClass()
}

@IntentionallyExhaustive
sealed interface MarkedSealedInterface {
    class Impl : MarkedSealedInterface
}

// Not visible outside the library: no warning.

internal enum class InternalEnum {
    A,
}

private sealed class PrivateSealedClass {
    class Only : PrivateSealedClass()
}
