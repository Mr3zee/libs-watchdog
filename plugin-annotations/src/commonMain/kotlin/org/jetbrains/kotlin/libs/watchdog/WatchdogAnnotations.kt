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
