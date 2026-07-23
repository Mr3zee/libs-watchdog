package org.jetbrains.kotlin.libs.watchdog

/**
 * Acknowledges that the annotated class or interface is deliberately open for unrestricted
 * subclassing outside the library.
 *
 * The libs-watchdog compiler plugin warns about publicly visible open/abstract classes and
 * interfaces that are not protected with [kotlin.SubclassOptInRequired], because every external
 * subclass constrains how the library can evolve. Apply this annotation to suppress the warning
 * when unrestricted subclassing is an intended part of the API contract.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyOpen

/**
 * Acknowledges that the annotated enum or sealed hierarchy is deliberately exhaustive.
 *
 * The libs-watchdog compiler plugin warns about publicly visible enums and sealed hierarchies,
 * because clients can match on them exhaustively (`when` without an `else` branch), which turns
 * adding an entry or a subtype into a breaking change. Apply this annotation to suppress the
 * warning when the set of entries/subtypes is an intended, stable part of the API contract.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyExhaustive

/**
 * Acknowledges that the annotated declaration is deliberately left without KDoc.
 *
 * The libs-watchdog compiler plugin warns about publicly visible declarations that have no KDoc —
 * classifiers, type aliases, functions, properties, constructors, and enum entries — because
 * undocumented API forces clients to guess the usage contract. Apply this annotation to suppress
 * the warning when leaving the declaration undocumented is intended (for example, when it is
 * self-explanatory or documented elsewhere).
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyUndocumented

/**
 * Acknowledges that the annotated type alias deliberately exposes a bare function type.
 *
 * The libs-watchdog compiler plugin warns about publicly visible type aliases that abbreviate
 * function types, because the alias is erased from the compiled API: clients bind to the bare
 * function shape, and the type cannot grow members or constraints later without breaking them,
 * unlike a `fun interface`. Apply this annotation to suppress the warning when exposing the
 * function type is intended (for example, for lambdas that only travel through inline functions).
 */
@Target(AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyFunctionTypeAlias

/**
 * Turns the annotated annotation class into an internal API marker: declarations annotated with
 * the marked annotation, and everything nested in them, are exempt from all public API checks.
 *
 * Libraries sometimes expose declarations that are public for technical reasons but are not part
 * of the supported API surface, and flag them with a dedicated annotation (usually one that also
 * requires opt-in). Such declarations carry no compatibility contract, so the libs-watchdog
 * compiler plugin should not demand documentation or evolution safeguards for them:
 *
 * ```
 * @InternalAnnotationMarker
 * @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
 * public annotation class InternalMyLibraryApi
 *
 * @InternalMyLibraryApi // Not watched: internal API despite the public visibility.
 * public class ReflectionHelper
 * ```
 *
 * The marker annotation class itself remains part of the public API surface and is still watched.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class InternalAnnotationMarker
