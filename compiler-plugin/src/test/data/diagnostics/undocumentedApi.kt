// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXHAUSTIVE_PUBLIC_API

package foo.bar

import org.jetbrains.kotlin.libs.watchdog.IntentionallyUndocumented

// Public API without KDoc: should warn.

public class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedClass<!>

public interface <!UNDOCUMENTED_PUBLIC_API!>UndocumentedInterface<!>

public object <!UNDOCUMENTED_PUBLIC_API!>UndocumentedObject<!>

public enum class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedEnum<!> {
    A,
}

public annotation class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedAnnotation<!>

// Non-KDoc comments are not documentation: should warn.

// A line comment is not KDoc.
public class <!UNDOCUMENTED_PUBLIC_API!>LineCommentedClass<!>

/* A block comment is not KDoc. */
public class <!UNDOCUMENTED_PUBLIC_API!>BlockCommentedClass<!>

// Documented API: no warning.

/** Documented. */
public class DocumentedClass

/**
 * Documented with a multi-line KDoc.
 */
public interface DocumentedInterface

/** An annotation applied below. */
public annotation class Marker

/** Documented even though annotations follow the KDoc. */
@Marker
public class DocumentedAnnotatedClass

// Deliberately undocumented: no warning.

@IntentionallyUndocumented
public class DeliberatelyUndocumentedClass

@IntentionallyUndocumented
public object DeliberatelyUndocumentedObject

// The acknowledgment covers only the annotated declaration, not its nested ones.

@IntentionallyUndocumented
public class DeliberatelyUndocumentedOuter {
    public class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedNestedInAcknowledged<!>
}

// Nested public declarations are checked on their own.

/** Documented outer. */
public class DocumentedOuter {
    public class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedNested<!>

    /** Documented nested. */
    public class DocumentedNested

    private class PrivateNested
}

// Only classifiers are watched: no warning on undocumented members.

/** Documented. */
public class WithUndocumentedMembers {
    public fun undocumentedFunction() {}

    public val undocumentedProperty: Int = 0
}

// Not visible outside the library: no warning.

internal class InternalClass

private class PrivateClass

internal class InternalOuter {
    class NestedInsideInternal
}

/** Local classes are invisible to clients: no warning inside. */
public fun documentedFunction() {
    class LocalClass
}
