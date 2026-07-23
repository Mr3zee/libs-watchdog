# libs-watchdog

A Kotlin compiler plugin that helps library authors keep their public API easy to evolve. Built on
top of the [compiler-plugin-dev-kit](../compiler-plugin-dev-kit) (consumed from `mavenLocal`).

## Checkers

The plugin only runs in modules compiled with
[explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode)
(`kotlin { explicitApi() }` or `-Xexplicit-api`, in either `strict` or `warning` variant); without
it the checkers are not registered at all. Unless noted otherwise, the checkers only look at
declarations visible to library clients (public or protected API).

Declarations that are public for technical reasons only can be excluded from all API-surface
checks: annotate the library's internal-API marker annotation with `@InternalAnnotationMarker`,
and every declaration carrying that marker — along with everything nested in it — is no longer
watched:

```kotlin
@InternalAnnotationMarker
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public annotation class InternalMyLibraryApi

@InternalMyLibraryApi // Not watched: internal API despite the public visibility.
public class ReflectionHelper
```

Every `@Intentionally*` exemption annotation must explain why it is applied, in two forms: a
`reason` from the `ExemptionReason` enum (`FOR_BACKWARDS_COMPATIBILITY`, `API_DESIGN`,
`INTEROP`, `EXTERNAL_CONTRACT`, `OTHER`) and a free-form `description` string. Both default to
an explanation-free value (`OTHER` and `""`), so a bare exemption is rejected. Only
`FOR_BACKWARDS_COMPATIBILITY` and `API_DESIGN` explain an exemption on their own; `INTEROP` and
`EXTERNAL_CONTRACT` merely categorize it (which interop constraint or external contract applies
is not obvious from the entry alone), so they — like `OTHER` — still require a non-empty
`description`. `@InternalAnnotationMarker` is exempt from this requirement: the marked
annotation class documents the internal API surface itself.

- `OPEN_API_WITHOUT_SUBCLASS_OPT_IN` — reports open/abstract classes and interfaces that can
  be subclassed outside the library without restriction. Suppress by gating subclassing with
  [`@SubclassOptInRequired`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-subclass-opt-in-required/)
  or by acknowledging the contract with `@IntentionallyOpen`.
- `SUBCLASS_OPT_IN_WITHOUT_MARKERS` — reports `@SubclassOptInRequired` annotations that list no
  marker classes and therefore do not restrict external subclassing.
- `EXHAUSTIVE_PUBLIC_API` — reports enums and sealed hierarchies, which clients can match
  exhaustively (`when` without `else`), so adding an entry or a subtype later breaks client code.
  Acknowledge the contract with `@IntentionallyExhaustive`.
- `UNDOCUMENTED_PUBLIC_API` — reports public declarations of every kind that have no KDoc:
  classifiers, type aliases, functions, properties, secondary constructors, and enum entries.
  Only KDoc presence is checked, not its content. Declarations documented elsewhere are exempt:
  overrides inherit the KDoc of the declaration they override, the primary constructor is
  described by `@constructor`/`@param` tags in the class KDoc, and a property is covered by a
  matching `@property` tag there. Fix by documenting the declaration, or acknowledge the
  omission with `@IntentionallyUndocumented`.
- `FUNCTION_TYPE_ALIAS_PUBLIC_API` — reports type aliases that abbreviate function types
  (including suspend, nullable, and receiver variants, and aliases of such aliases). The alias is
  erased from the compiled API, so clients bind to the bare function shape and the type cannot
  evolve into a richer abstraction later. Declare a
  [`fun interface`](https://kotlinlang.org/docs/fun-interfaces.html) instead to keep lambda
  ergonomics behind a stable nominal type, or acknowledge the alias with
  `@IntentionallyFunctionTypeAlias`. Aliases of `KFunction`/`KSuspendFunction` reflection types
  are exempt: a fun interface cannot replace them.
- `DATA_CLASS_PUBLIC_API` — reports
  [data classes](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-using-data-classes-in-your-api):
  the generated `copy` and `componentN` functions and the constructor bake the exact property
  list into the compiled API, so adding, removing, or reordering a property later breaks
  clients. Declare a regular class and implement `equals`/`hashCode`/`toString` explicitly, or
  acknowledge the contract with `@IntentionallyDataClass`. `data object`s are exempt: without
  constructor properties none of the hazardous members are generated.
- `STATEFUL_CLASS_WITHOUT_TO_STRING` — reports
  [stateful classes](https://kotlinlang.org/docs/api-guidelines-debuggability.html#provide-a-tostring-method-for-stateful-types)
  — classes with at least one property that stores its value in a backing field — that neither
  declare nor inherit a `toString` implementation: their instances render as the opaque
  class-name-with-hash-code default, which reveals nothing in logs and debugger output. A
  `toString` inherited from any supertype other than `kotlin.Any` counts as provided. Only
  regular classes are checked: data and value classes receive a compiler-generated `toString`,
  enum entries render their name, interfaces and annotation classes cannot hold backing fields,
  and objects typically hold constants rather than per-instance state; delegated properties store
  their value in the delegate, so they do not make a class stateful. Override `toString` to
  render the current state, or acknowledge the opaque rendering with
  `@IntentionallyWithoutToString` (for example, when the state is sensitive and must not leak
  into logs).
- `MUTABLE_COLLECTION_PUBLIC_API` — reports
  [mutable collection types](https://kotlinlang.org/docs/api-guidelines-predictability.html#avoid-exposing-mutable-state)
  in public signatures: return types, property types, value parameter types, and type parameter
  bounds (`<T : MutableList<Int>>` accepts the same mutable state as a plain `MutableList<Int>`
  parameter), including their type arguments (`List<MutableList<Int>>` still hands out mutable
  state). A type counts as mutable when it is one of the `kotlin.collections` mutable
  interfaces, any classifier implementing them (`ArrayList`, a hand-written `MutableList`
  subtype, ...), or an array — arrays are mutable collections too. Once mutable state is shared
  across the API boundary, it is unclear whether client-side and library-side mutations affect
  each other. Accept and return read-only types instead, handing out defensive copies where
  needed, or acknowledge deliberate sharing with `@IntentionallyMutableCollection` — on the
  whole declaration, on a single parameter or type parameter, or on a type usage
  (`List<@IntentionallyMutableCollection MutableList<Int>>`), where it covers the annotated type
  and everything nested in it. Deliberate exceptions: `vararg` parameters (the compiler already passes a
  defensive copy of the array, so only the declared element type is checked), extension
  receivers (an extension on a mutable collection serves values the client already holds, as in
  `fun <T> MutableList<T>.sort()`), overrides (their signature is fixed by the overridden
  declaration and reported there), and Java platform types (their mutability is not declared in
  Kotlin sources).
- `BOOLEAN_PARAMETER_PUBLIC_API` — reports
  [Boolean value parameters](https://kotlinlang.org/docs/api-guidelines-readability.html#avoid-using-the-boolean-type-as-an-argument)
  of public functions, including nullable (`Boolean?` is a three-state flag) and `vararg` ones
  (only the declared element type is checked there, not the array carrying it). At the call site
  a positional `true`/`false` argument reveals nothing about its meaning, and clients cannot be
  forced to use named arguments. Introduce separate, descriptively named functions for each
  mode, or replace the parameter with an enum class, or acknowledge the parameter with
  `@IntentionallyBooleanParameter` — on the whole function or on a single parameter. Boolean
  results and properties are not arguments and stay exempt, and so do overrides (their signature
  is fixed by the overridden declaration and reported there), constructors, and constructor
  functions — factory functions named after the type they create, like
  `fun Widget(visible: Boolean): Widget` — because a construction site stores data in the named
  type rather than switching an operation mode.
- `REQUIRED_PARAMETER_AFTER_OPTIONAL` — reports required parameters of public functions and
  constructors that are declared after an optional (defaulted or `vararg`) parameter.
  [Parameters should go from the general to the specific](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage):
  essential inputs first, optional inputs last — a required parameter behind optional ones cannot
  be passed positionally without re-stating the defaults in front of it. A required function-type
  or `fun interface` parameter in the last position is exempt (keeping it last is what makes
  trailing-lambda call syntax available), and so are overrides (they cannot declare defaults, and
  their order is fixed by the overridden declaration). Move the required parameter in front of
  the optional ones, or acknowledge the order with `@IntentionallyRequiredParameterAfterOptional`.
- `INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS` — reports overloads whose same-named parameters
  appear in a different relative order than in another overload, as in `fun draw(x: Int, y: Int)`
  next to `fun draw(y: Int, x: Int, scale: Double)`.
  [Clients transfer their expectations between overloads](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage),
  so an inconsistent order of same-named parameters invites silently swapped arguments — while
  same-named parameters with *different types* stay legal, since conversion overloads like
  `BigDecimal(Int)`/`BigDecimal(String)` are the point of overloading. No overload is preferred
  as the canonical order: every member of an inconsistent pair is reported, and reordering
  either clears both. Overloads are compared as clients see them side by side — the members
  visible in a class, inherited ones included (for an inheritance pair only the subtype's
  declaration is reported: it is the new overload that strays from the established signature),
  or the module's top-level functions of the same package, constructors of a class among each
  other; dependencies are not compared. Overrides never report — their order is fixed by the
  overridden declaration — but they still serve as ordering references for new overloads next
  to them. Keep shared parameters in the same relative order, or acknowledge the difference
  with `@IntentionallyInconsistentParameterOrder`, which also stops the declaration from serving
  as an ordering reference.
- `EXEMPTION_WITHOUT_EXPLANATION` — reports `@Intentionally*` exemption annotations whose `reason` is `OTHER`
  (the default) while the `description` is empty: such an exemption explains nothing. Fires on
  every usage of the exemption annotations, regardless of the annotated declaration's
  visibility. Always an error — unlike the other diagnostics, its severity cannot be configured.
- `DSL_MARKER_NOOP_TARGET` — reports annotation targets of a
  [`@DslMarker`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-dsl-marker/) annotation on
  which the marker has no effect. Receiver scope control only reacts to markers on classifier
  declarations (`CLASS`, `ANNOTATION_CLASS`), type usages (`TYPE`), and type aliases
  (`TYPEALIAS`) — see the
  [DSL marker design note](https://github.com/Kotlin/KEEP/blob/main/notes/0005-dsl-marker.md).
  Any other target (`FUNCTION`, `PROPERTY`, `VALUE_PARAMETER`, ...) lets users apply the marker
  where it silently restricts nothing, giving a false sense of scope control. Fix by removing the
  no-op targets from `@Target`. Markers of any visibility are checked — even an internal marker
  is applied across the library's possibly public DSL classes.
- `DSL_MARKER_WITHOUT_EXPLICIT_TARGETS` — reports `@DslMarker` annotations without an explicit
  `@Target`: the default target set allows nine such no-op targets while forbidding the effective
  `TYPE` and `TYPEALIAS` ones. Fix by declaring `@Target(CLASS, TYPE, TYPEALIAS)` or a subset.

  For an already-published marker both fixes are breaking, so the two target checks can be
  suppressed with `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility` on the marker.
  Wrong marker targets are never good API design, so this exemption bakes its only accepted
  reason into its name and takes just an optional `description`.
- `DSL_MARKER_NOOP_TYPE_POSITION` — reports DSL markers written on type positions where they have
  no effect: a plain parameter type (`fun process(tag: @MyDsl Tag)`), a return type, or a
  property/variable type. Scope control only reacts to markers on the type of an implicit value —
  a receiver type, a context parameter type, or a function type with such implicit values (there
  the marker propagates to them). Markers on supertypes, type parameter bounds, and type alias
  expansions are effective carriers and stay exempt; markers nested in type arguments are not
  analyzed. Unlike the API-surface checks, this one also fires on non-public declarations.

## Configuring severities

Every diagnostic is reported as a compilation **error** by default. Each one can individually be
demoted to a warning (`WatchdogSeverity.WARNING`) or disabled entirely (`WatchdogSeverity.NONE`)
through the `libsWatchdog` extension — except `EXEMPTION_WITHOUT_EXPLANATION`, which is always an
error:

```kotlin
import org.jetbrains.kotlinx.libs.watchdog.WatchdogSeverity

libsWatchdog {
    openApiWithoutSubclassOptIn.set(WatchdogSeverity.WARNING)
    subclassOptInWithoutMarkers.set(WatchdogSeverity.WARNING)
    exhaustivePublicApi.set(WatchdogSeverity.WARNING)
    undocumentedPublicApi.set(WatchdogSeverity.NONE)
    functionTypeAliasPublicApi.set(WatchdogSeverity.WARNING)
    dataClassPublicApi.set(WatchdogSeverity.WARNING)
    statefulClassWithoutToString.set(WatchdogSeverity.WARNING)
    mutableCollectionPublicApi.set(WatchdogSeverity.WARNING)
    booleanParameterPublicApi.set(WatchdogSeverity.WARNING)
    requiredParameterAfterOptional.set(WatchdogSeverity.WARNING)
    inconsistentParameterOrderInOverloads.set(WatchdogSeverity.WARNING)
    dslMarkerNoopTarget.set(WatchdogSeverity.WARNING)
    dslMarkerWithoutExplicitTargets.set(WatchdogSeverity.WARNING)
    dslMarkerNoopTypePosition.set(WatchdogSeverity.WARNING)
}
```

When invoking the compiler directly, the same configuration is available as a repeatable plugin
option taking `error`, `warning`, or `none`:
`-P plugin:org.jetbrains.kotlinx.libs.watchdog:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:warning`.

Note that the Kotlin compiler hides regular warnings when a compilation fails with errors, so
demoted diagnostics only show up in failing builds with `-Xreport-all-warnings`.

## Modules

- [`:compiler-plugin`](compiler-plugin/src) — the compiler plugin (FIR checkers).
- [`:plugin-annotations`](plugin-annotations/src/commonMain/kotlin) — `@IntentionallyOpen`,
  `@IntentionallyExhaustive`, `@IntentionallyUndocumented`, `@IntentionallyFunctionTypeAlias`,
  `@IntentionallyDataClass`, `@IntentionallyWithoutToString`, `@IntentionallyMutableCollection`,
  `@IntentionallyBooleanParameter`, `@IntentionallyRequiredParameterAfterOptional`,
  `@IntentionallyInconsistentParameterOrder`,
  `@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility`, `@InternalAnnotationMarker`,
  and the `ExemptionReason` enum.
- [`:gradle-plugin`](gradle-plugin/src) — applies the compiler plugin and the annotations
  dependency to a Kotlin project (plugin id `org.jetbrains.kotlinx.libs.watchdog`).

## Prerequisites

The dev kit must be published to `mavenLocal` first:

```bash
cd ../compiler-plugin-dev-kit
./gradlew -p artifact-transform publishToMavenLocal
./gradlew -p plugins publishToMavenLocal
./gradlew publishToMavenLocal
```

## Tests

```bash
./gradlew :compiler-plugin:test               # diagnostics tests
./gradlew :compiler-plugin:generateTests      # regenerate JUnit classes from test data
./gradlew :gradle-plugin:functionalTest       # Gradle integration tests
```

Diagnostics test data lives in [compiler-plugin/src/test/data/diagnostics](compiler-plugin/src/test/data/diagnostics).
