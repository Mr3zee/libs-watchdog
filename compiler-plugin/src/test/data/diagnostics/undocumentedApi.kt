// RUN_PIPELINE_TILL: BACKEND
// DIAGNOSTICS: -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXHAUSTIVE_PUBLIC_API

package foo.bar

import org.jetbrains.kotlin.libs.watchdog.IntentionallyUndocumented

// Public API without KDoc: should warn.

class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedClass<!>

interface <!UNDOCUMENTED_PUBLIC_API!>UndocumentedInterface<!>

object <!UNDOCUMENTED_PUBLIC_API!>UndocumentedObject<!>

enum class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedEnum<!> {
    A,
}

annotation class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedAnnotation<!>

// Non-KDoc comments are not documentation: should warn.

// A line comment is not KDoc.
class <!UNDOCUMENTED_PUBLIC_API!>LineCommentedClass<!>

/* A block comment is not KDoc. */
class <!UNDOCUMENTED_PUBLIC_API!>BlockCommentedClass<!>

// Documented API: no warning.

/** Documented. */
class DocumentedClass

/**
 * Documented with a multi-line KDoc.
 */
interface DocumentedInterface

/** An annotation applied below. */
annotation class Marker

/** Documented even though annotations follow the KDoc. */
@Marker
class DocumentedAnnotatedClass

// Deliberately undocumented: no warning.

@IntentionallyUndocumented
class DeliberatelyUndocumentedClass

@IntentionallyUndocumented
object DeliberatelyUndocumentedObject

// The acknowledgment covers only the annotated declaration, not its nested ones.

@IntentionallyUndocumented
class DeliberatelyUndocumentedOuter {
    class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedNestedInAcknowledged<!>
}

// Nested public declarations are checked on their own.

/** Documented outer. */
class DocumentedOuter {
    class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedNested<!>

    /** Documented nested. */
    class DocumentedNested

    private class PrivateNested
}

// Only classifiers are watched: no warning on undocumented members.

/** Documented. */
class WithUndocumentedMembers {
    fun undocumentedFunction() {}

    val undocumentedProperty: Int = 0
}

// Not visible outside the library: no warning.

internal class InternalClass

private class PrivateClass

internal class InternalOuter {
    class NestedInsideInternal
}

/** Local classes are invisible to clients: no warning inside. */
fun documentedFunction() {
    class LocalClass
}
