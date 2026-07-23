package org.jetbrains.kotlinx.libs.watchdog

/**
 * Explains why a watchdog exemption annotation is applied.
 *
 * Every exemption annotation in this package carries a `reason` and a free-form `description`.
 * The libs-watchdog compiler plugin requires the explanation to be meaningful: the description
 * may be left empty only when the reason explains the exemption on its own
 * ([FOR_BACKWARDS_COMPATIBILITY], [API_DESIGN]). The other reasons only categorize the exemption
 * and keep the description shorter — the specific constraint still has to be spelled out there.
 */
public enum class ExemptionReason {
    /** The exempted shape is kept to stay compatible with existing clients. */
    FOR_BACKWARDS_COMPATIBILITY,

    /** The exempted shape is a deliberate part of the API design. */
    API_DESIGN,

    /**
     * The exempted shape is dictated by interoperability with another language, platform, or
     * framework. Which interop constraint applies is not obvious from the entry alone, so the
     * `description` must still name it.
     */
    INTEROP,

    /**
     * The exempted shape mirrors an externally defined contract — a specification, a protocol,
     * or a closed real-world domain. Which contract is mirrored is not obvious from the entry
     * alone, so the `description` must still name it.
     */
    EXTERNAL_CONTRACT,

    /**
     * None of the other entries fits. This is the default, and it explains nothing by itself,
     * so the exemption annotation must spell the motivation out in its `description`.
     */
    OTHER,
}

/**
 * Acknowledges that the annotated class or interface is deliberately open for unrestricted
 * subclassing outside the library.
 *
 * The libs-watchdog compiler plugin warns about publicly visible open/abstract classes and
 * interfaces that are not protected with [kotlin.SubclassOptInRequired], because every external
 * subclass constrains how the library can evolve. Apply this annotation to suppress the warning
 * when unrestricted subclassing is an intended part of the API contract.
 *
 * @param reason why the class is deliberately open.
 * @param description free-form explanation of the exemption; may be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyOpen(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated enum or sealed hierarchy is deliberately exhaustive.
 *
 * The libs-watchdog compiler plugin warns about publicly visible enums and sealed hierarchies,
 * because clients can match on them exhaustively (`when` without an `else` branch), which turns
 * adding an entry or a subtype into a breaking change. Apply this annotation to suppress the
 * warning when the set of entries/subtypes is an intended, stable part of the API contract.
 *
 * @param reason why the hierarchy is deliberately exhaustive.
 * @param description free-form explanation of the exemption; may be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyExhaustive(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated declaration is deliberately left without KDoc.
 *
 * The libs-watchdog compiler plugin warns about publicly visible declarations that have no KDoc —
 * classifiers, type aliases, functions, properties, constructors, and enum entries — because
 * undocumented API forces clients to guess the usage contract. Apply this annotation to suppress
 * the warning when leaving the declaration undocumented is intended (for example, when it is
 * self-explanatory or documented elsewhere).
 *
 * @param reason why the declaration is deliberately undocumented.
 * @param description free-form explanation of the exemption; may be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyUndocumented(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated type alias deliberately exposes a bare function type.
 *
 * The libs-watchdog compiler plugin warns about publicly visible type aliases that abbreviate
 * function types, because the alias is erased from the compiled API: clients bind to the bare
 * function shape, and the type cannot grow members or constraints later without breaking them,
 * unlike a `fun interface`. Apply this annotation to suppress the warning when exposing the
 * function type is intended (for example, for lambdas that only travel through inline functions).
 *
 * @param reason why the alias deliberately exposes a function type.
 * @param description free-form explanation of the exemption; may be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyFunctionTypeAlias(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated data class is deliberately part of the public API.
 *
 * The libs-watchdog compiler plugin warns about publicly visible data classes, because the
 * generated `copy` and `componentN` functions and the constructor bake the exact property list
 * into the compiled API: adding, removing, or reordering a property later breaks clients. Apply
 * this annotation to suppress the warning when the property list is an intended, stable part of
 * the API contract.
 *
 * @param reason why the class is deliberately a data class.
 * @param description free-form explanation of the exemption; may be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyDataClass(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated class deliberately provides no `toString` implementation.
 *
 * The libs-watchdog compiler plugin warns about publicly visible stateful classes — classes with
 * at least one property backed by a field — that neither declare nor inherit a `toString`
 * implementation, because their instances render as the opaque default class-name-with-hash-code,
 * which makes logs and debugger output meaningless. Apply this annotation to suppress the warning
 * when the opaque rendering is intended (for example, when the state is sensitive and must not
 * leak into logs).
 *
 * @param reason why the class deliberately has no `toString`.
 * @param description free-form explanation of the exemption; may be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWithoutToString(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated declaration deliberately exposes a mutable collection type in
 * the public API.
 *
 * The libs-watchdog compiler plugin warns about public signatures — return types, property
 * types, and parameter types, including their type arguments — that mention mutable collection
 * types (`MutableList`, `MutableMap`, ..., their implementations, and arrays, which are mutable
 * collections too), as well as mutable bounds on type parameters. Sharing mutable state across
 * the API boundary makes it unclear whether client-side and library-side mutations affect each
 * other. Apply this annotation to suppress the warning when sharing mutable state is an intended
 * part of the API contract. On a function, a property, or a constructor it covers the whole
 * signature; on a single parameter or type parameter it covers just that parameter; on a type
 * usage (`List<@IntentionallyMutableCollection MutableList<Int>>`) it covers the annotated type
 * and everything nested in it.
 *
 * @param reason why the declaration deliberately exposes a mutable collection.
 * @param description free-form explanation of the exemption; may be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyMutableCollection(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated function or parameter deliberately takes a Boolean argument.
 *
 * The libs-watchdog compiler plugin warns about
 * [Boolean value parameters](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument)
 * — including nullable and `vararg` ones — in publicly visible functions, because at the call
 * site a positional `true`/`false` argument reveals nothing about its meaning, and clients
 * cannot be forced to use named arguments. Prefer separate, descriptively named functions for
 * each mode, or an enum class naming the modes. Constructors and constructor functions —
 * factory functions named after the type they create — are not checked: a construction site
 * stores data in the named type rather than switching an operation mode. Apply this annotation
 * to suppress the warning when the Boolean parameter is intended (for example, when the
 * parameter is unmistakable from the function name alone, as in `setEnabled(enabled: Boolean)`).
 * On a function it covers every parameter; on a single parameter it covers just that parameter.
 *
 * @param reason why the Boolean parameter is intended.
 * @param description free-form explanation of the exemption; may be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyBooleanParameter(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated function or constructor deliberately declares a required
 * parameter after optional ones.
 *
 * The libs-watchdog compiler plugin warns about publicly visible functions and constructors that
 * declare a required parameter — one without a default value — after an optional (defaulted or
 * `vararg`) parameter, because
 * [parameters should go from the general to the specific](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage):
 * essential inputs first, optional inputs last. A required parameter behind optional ones cannot
 * be passed positionally without re-stating the defaults in front of it. A required function-type
 * (or `fun interface`) parameter in the last position is not reported: it keeps trailing-lambda
 * call syntax available. Apply this annotation to suppress the warning when the order is intended
 * (for example, when appending a parameter anywhere else would break existing clients).
 *
 * @param reason why the parameter order is intended.
 * @param description free-form explanation of the exemption; may be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyRequiredParameterAfterOptional(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated function or constructor deliberately orders its parameters
 * differently from its other overloads.
 *
 * The libs-watchdog compiler plugin warns about publicly visible overloads that declare the same
 * parameter names in a different relative order, because
 * [clients transfer their expectations between overloads](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage):
 * an inconsistent order of same-named parameters invites silently swapped arguments. Apply this
 * annotation to suppress the warning when the differing order is intended; the annotated
 * declaration is also no longer used as an ordering reference for other overloads.
 *
 * @param reason why the differing parameter order is intended.
 * @param description free-form explanation of the exemption; may be empty only when [reason]
 *   explains the exemption on its own ([ExemptionReason.FOR_BACKWARDS_COMPATIBILITY],
 *   [ExemptionReason.API_DESIGN]).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyInconsistentParameterOrder(
    val reason: ExemptionReason = ExemptionReason.OTHER,
    val description: String = "",
)

/**
 * Acknowledges that the annotated DSL marker deliberately keeps a wrong target set — no-op
 * targets in its `@Target`, or no explicit `@Target` at all — because fixing it would break
 * existing clients.
 *
 * The libs-watchdog compiler plugin warns about DSL marker targets on which the marker has no
 * effect. For an already-published marker the fix is breaking: removing a target rejects client
 * code that applies the marker there, and declaring an explicit `@Target` forbids the previously
 * allowed default targets. Apply this annotation to suppress the warnings for such legacy
 * markers.
 *
 * Wrong marker targets are never good API design, so unlike the other exemptions this one bakes
 * its only accepted reason — backwards compatibility — into its name and carries no
 * [ExemptionReason]. New DSL markers must declare effective targets instead.
 *
 * @param description optional free-form context for the exemption.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility(
    val description: String = "",
)

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
