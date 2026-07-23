# libs-watchdog <img src="logo.svg" width="48" align="right" alt="libs-watchdog logo"/>

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
`INTEROP`, `EXTERNAL_CONTRACT`, `IGNORE_JAVA_INTEROP`, `OTHER`) and a free-form `description`
string. Both default to an explanation-free value (`OTHER` and `""`), so a bare exemption is
rejected. Only `FOR_BACKWARDS_COMPATIBILITY` and `API_DESIGN` explain an exemption on their own;
`INTEROP`, `EXTERNAL_CONTRACT`, and `IGNORE_JAVA_INTEROP` merely categorize it (which interop
constraint or external contract applies, or why this particular declaration gets to ignore Java
callers, is not obvious from the entry alone), so they — like `OTHER` — still require a
non-empty `description`. `@InternalAnnotationMarker` is exempt from this requirement: the marked
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
- `PAIR_OR_TRIPLE_PUBLIC_API` — reports the tuple types `Pair` and `Triple` in public
  signatures: return types, property types, parameter types, and type parameter bounds,
  including their type arguments (`List<Pair<Int, String>>` exposes the tuple all the same, and
  a type alias does not change what clients see). Tuple components carry no domain meaning: at
  the use site `first`/`second`/`third` and positional destructuring reveal nothing about the
  values, and the fixed shape cannot evolve — adding a value means switching to a different
  type, breaking clients. Declare a
  [small class with descriptively named properties](https://kotlinlang.org/docs/data-classes.html)
  instead, or acknowledge the tuple with `@IntentionallyPairOrTriple` — on the whole
  declaration, on a single parameter or type parameter, or on a type usage
  (`List<@IntentionallyPairOrTriple Pair<Int, String>>`), where it covers the annotated type and
  everything nested in it. Deliberate exceptions: extension receivers (an extension on a tuple
  serves values the client already holds, as in `fun <A, B> Pair<A, B>.swap()`) and overrides
  (their signature is fixed by the overridden declaration and reported there).
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
- `NULLABLE_BOOLEAN_PUBLIC_API` — reports nullable Booleans in public signatures: return types,
  property types, parameter types, and type parameter bounds (`<T : Boolean?>` admits the same
  three-state values as a plain `Boolean?`), including their type arguments (`List<Boolean?>`
  still exposes the unnamed third state, and a type alias does not change what clients see).
  `Boolean?` models three states but names only two of them: every use site has to know what
  `null` stands for, and three-state logic hides in two-branch `if`s. Unlike
  `BOOLEAN_PARAMETER_PUBLIC_API`, constructors are checked too — a stored three-state flag is as
  opaque as a passed one. Replace the type with an enum class naming all three states, or drop
  the third state, or acknowledge it with `@IntentionallyNullableBoolean` — on the whole
  declaration, on a single parameter or type parameter, or on a type usage
  (`List<@IntentionallyNullableBoolean Boolean?>`), where it covers the annotated type and
  everything nested in it. Deliberate exceptions: extension receivers (an extension on
  `Boolean?`, typically a remedial helper like `fun Boolean?.orFalse()`, serves values the
  client already holds), overrides (their signature is fixed by the overridden declaration and
  reported there), and Java platform types (their nullability is not declared in Kotlin
  sources).
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
- `INLINE_FUNCTION_WITH_LOGIC` — reports public `inline` functions — and inline property
  accessors, which inline the same way — whose body does more than delegate to a non-inline
  function. The compiler copies an inline body into every client binary, so logic placed there
  — and its bugs — stays frozen in clients compiled against an old library version until they
  recompile (see the
  [`@PublishedApi` considerations](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#considerations-for-using-the-publishedapi-annotation)).
  A thin wrapper resolves what only the call site knows — a reified type argument, an inlined
  lambda — in a single statement that only reads or writes values: parameters, properties,
  `this`, literals, `T::class`, callable references, nested non-inline calls, an assignment to
  a property with a non-inline setter (the thin setter shape), an `as`/`as?` cast on the
  delegate's result, and lambda literals that only forward such calls (the `impl { block() }`
  shape a `crossinline` wrapper needs). Anything else counts as logic: control flow of any
  kind, operator and infix calls (arithmetic compiles inline into the client), string
  templates, local variables, multiple statements — and calls to other inline functions or
  inline accessors, whose bodies the inliner drags into the client transitively. For the same
  reason `@PublishedApi internal` inline functions are checked like public ones. Extract the
  logic into a non-inline function (`@PublishedApi internal` when it should stay out of the
  public API) and delegate to it, or acknowledge the inlined logic with
  `@IntentionallyInlinedLogic` — on the function, or on the property for its accessors.
- `MANGLED_JVM_NAME_PUBLIC_API` — reports public functions, properties, and constructors that
  Java sources cannot call because a
  [value class](https://kotlinlang.org/docs/inline-classes.html#mangling) appears in their
  signature. Value classes compile to their underlying type, so the JVM backend mangles the
  affected names with a hash suffix (`take(id: UserId)` compiles to `take-4ZD5Yi0`), and the `-`
  makes them illegal Java identifiers; a constructor with a value class parameter is hidden
  behind a synthetic one instead. A name is mangled by a value class among the value parameters,
  the extension receiver, or the context parameters — nullable types and type parameters
  bounded by a value class included — and by a value class *return* type on class members
  (top-level callables merely returning a value class, and value classes inside type arguments
  like `List<UserId>`, keep their JVM name and are not reported). Restore a Java-callable shape
  with `@JvmName` on functions and `@get:`/`@set:JvmName` on property accessors, or expose boxed
  variants with `@JvmExposeBoxed` (the only option for constructors and overridable members,
  which `@JvmName` does not accept), or acknowledge a deliberately Kotlin-only declaration with
  `@IntentionallyMangledJvmName` — on the declaration, on a primary constructor `val`/`var`
  parameter for the property made from it, or on a class, covering every declaration inside.
  Deliberate exceptions: non-JVM compilations (the checker only registers for JVM ones), members
  and constructors of the value class itself (declaring the public value class is the deliberate
  choice — `@JvmName` is not even applicable inside), `suspend` functions (not Java-friendly
  regardless of the name), overrides (their signature is fixed by the overridden declaration and
  reported there), and `@JvmSynthetic` declarations (hidden from Java on purpose).
- `KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC` — reports public functions whose shape only Kotlin
  callers can use idiomatically while the function still lands in the API surface Java sources
  see: `suspend` functions (Java sees a trailing
  [`Continuation` parameter](https://kotlinlang.org/docs/java-to-kotlin-interop.html) it cannot
  provide idiomatically), `inline` functions with a `reified` type parameter (only inlining
  Kotlin call sites can substitute it — calling the compiled method from Java fails at runtime),
  and functions taking a Kotlin-specific function type: a suspend function type (no Java lambda
  can implement it), a function type with receiver (a Java lambda has to take the receiver as an
  explicit first argument), or a `Unit`-returning function type (a Java lambda has to return the
  `Unit.INSTANCE` token explicitly). Hide the Kotlin-only shape from Java with `@JvmSynthetic`,
  or provide a Java-friendly alternative alongside — a blocking or `CompletableFuture`-returning
  bridge for a suspend function, a `fun interface` parameter in place of a Kotlin function
  type — or acknowledge the Java-visible Kotlin-only shape with `@IntentionallyKotlinOnlyApi`,
  on the function or on a class, where it covers every function inside.
  Deliberate exceptions: abstract and interface members (`@JvmSynthetic` cannot hide a member
  that implementations must provide, so there is no non-breaking fix to suggest), overrides
  (reported on the base declaration), constructors (`@JvmSynthetic` is not applicable to them),
  and signatures already invisible to Java — mangled by a value class or declared inside one,
  which `MANGLED_JVM_NAME_PUBLIC_API` reports with fitting fixes.
- `COMPANION_API_WITHOUT_JVM_STATIC` — reports public companion object functions without
  [`@JvmStatic`](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-methods): a
  companion member compiles to an instance method on the nested `Companion` class, so Java
  callers have to reach it as `Outer.Companion.member(...)`. `@JvmStatic` additionally compiles
  a static `Outer.member(...)` entry point — Kotlin call sites are unaffected — and
  `@JvmSynthetic` hides the member from Java instead; acknowledge a deliberately
  companion-instance-only access path with `@IntentionallyNonStaticCompanionApi` — on the
  member, or on a class (the companion object itself or its outer class), where it covers every
  member inside. `suspend` companion functions are exempt
  (not Java-callable regardless of placement; `KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC` reports
  them with the fitting fix), and so are overrides (their Java-facing shape is fixed by the
  overridden declaration).
- `COMPANION_CONSTANT_WITHOUT_JVM_FIELD` — reports public constant-shaped companion object
  properties — a final `val` initialized in place with the default getter, neither `const` nor
  delegated — that Java can only read through the companion instance getter. Expose the value
  on the outer class itself: as a
  [static field with `@JvmField`](https://kotlinlang.org/docs/java-to-kotlin-interop.html#static-fields),
  as a compile-time constant with `const val` (primitives and strings), or as a static getter
  with `@JvmStatic` — or hide the property from Java with `@get:JvmSynthetic`, or acknowledge
  the companion-instance access path with `@IntentionallyNonStaticCompanionApi`. `var`
  properties and properties with custom accessors or delegates expose behavior rather than a
  constant and are not checked.
- `TOP_LEVEL_API_WITHOUT_JVM_NAME` — reports files whose public top-level functions or
  properties compile into a
  [file facade class](https://kotlinlang.org/docs/java-to-kotlin-interop.html#package-level-functions)
  without an explicit `@file:JvmName`. The facade name is derived from the file name
  (`foo.kt` → `FooKt`), so the file name leaks into the Java API surface, and renaming the
  file — invisible to Kotlin callers — renames the facade and breaks Java sources and binaries
  compiled against it. Choose and pin the facade name deliberately with `@file:JvmName`, or
  acknowledge the derived name with `@file:IntentionallyDefaultFacadeName`. The
  diagnostic fires once per file, anchored on its first public top-level function or property;
  files exposing only classifiers, and files whose every top-level callable is hidden with
  `@JvmSynthetic`, produce no facade worth naming and stay silent.
- `DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS` — reports public functions and constructors that
  declare default parameter values without
  [`@JvmOverloads`](https://kotlinlang.org/docs/java-to-kotlin-interop.html#overloads-generation):
  defaults are a Kotlin-frontend feature, so only the full signature is compiled and Java
  callers must spell out every argument. `@JvmOverloads` additionally compiles the overloads
  that omit defaulted parameters from the right. The recommendation is honest about its limits:
  only right-truncated overloads are generated — a defaulted parameter in the middle of the
  list still cannot be skipped from Java (which is why `REQUIRED_PARAMETER_AFTER_OPTIONAL`
  pushes optional parameters to the end) — and `@JvmOverloads` only improves Java call sites;
  it does not make adding a parameter later binary compatible for Kotlin callers. Acknowledge
  deliberately Kotlin-only defaults with `@IntentionallyWithoutJvmOverloads`. Deliberate
  exceptions: abstract and interface members and annotation class constructors (`@JvmOverloads`
  is not applicable there), `suspend` functions and value class members (not Java-callable
  regardless of overloads), overrides (they cannot re-declare defaults), and `@JvmSynthetic`
  functions (hidden from Java on purpose).

  The six Java-interop checks — `MANGLED_JVM_NAME_PUBLIC_API` through
  `DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS` — only run in JVM compilations, and they only pay
  off for libraries that support Java consumers: a library with a Kotlin-only audience disables
  the whole group with `javaInterop { enabled = false }`. Individual declarations
  that deliberately sacrifice Java ergonomics are acknowledged in place with the matching
  `@Intentionally*` annotation and the `IGNORE_JAVA_INTEROP` reason.
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
    pairOrTriplePublicApi.set(WatchdogSeverity.WARNING)
    booleanParameterPublicApi.set(WatchdogSeverity.WARNING)
    nullableBooleanPublicApi.set(WatchdogSeverity.WARNING)
    requiredParameterAfterOptional.set(WatchdogSeverity.WARNING)
    inconsistentParameterOrderInOverloads.set(WatchdogSeverity.WARNING)
    inlineFunctionWithLogic.set(WatchdogSeverity.WARNING)
    dslMarkerNoopTarget.set(WatchdogSeverity.WARNING)
    dslMarkerWithoutExplicitTargets.set(WatchdogSeverity.WARNING)
    dslMarkerNoopTypePosition.set(WatchdogSeverity.WARNING)

    javaInterop {
        // One switch for the whole Java-interop group; it wins over the severities below.
        enabled = true
        mangledJvmNamePublicApi.set(WatchdogSeverity.WARNING)
        kotlinOnlyApiWithoutJvmSynthetic.set(WatchdogSeverity.WARNING)
        companionApiWithoutJvmStatic.set(WatchdogSeverity.WARNING)
        companionConstantWithoutJvmField.set(WatchdogSeverity.WARNING)
        topLevelApiWithoutJvmName.set(WatchdogSeverity.WARNING)
        defaultParametersWithoutJvmOverloads.set(WatchdogSeverity.WARNING)
    }
}
```

When invoking the compiler directly, the same configuration is available as a repeatable plugin
option taking `error`, `warning`, or `none`:
`-P plugin:org.jetbrains.kotlinx.libs.watchdog:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:warning`.

Note that the Kotlin compiler hides regular warnings when a compilation fails with errors, so
demoted diagnostics only show up in failing builds with `-Xreport-all-warnings`.

## Tapmoc suggestion

Watching the API shape covers only half of library evolution - the artifacts also have to stay
consumable from the oldest JDK and Kotlin versions the library supports. That part is covered by
[Tapmoc](https://github.com/GradleUp/Tapmoc) (`com.gradleup.tapmoc`, formerly CompatPatrouille),
which pins the Java and Kotlin compatibility levels a module is built against. The Gradle plugin
checks that Tapmoc is applied alongside it and prints a build warning with a setup snippet when it
is not:

```kotlin
plugins {
    id("com.gradleup.tapmoc") version "<version>"
}

tapmoc {
    java(17)        // oldest supported Java release
    kotlin("2.1.0") // oldest supported Kotlin version
}
```

Replace `<version>` with the
[latest Tapmoc release](https://github.com/GradleUp/Tapmoc/releases/latest); the
[Tapmoc documentation](https://gradleup.com/tapmoc/) has the full configuration reference.

The suggestion can be turned off through the extension:

```kotlin
libsWatchdog {
    suggestTapmoc.set(false)
}
```

## Modules

- [`:compiler-plugin`](compiler-plugin/src) — the compiler plugin (FIR checkers).
- [`:plugin-annotations`](plugin-annotations/src/commonMain/kotlin) — `@IntentionallyOpen`,
  `@IntentionallyExhaustive`, `@IntentionallyUndocumented`, `@IntentionallyFunctionTypeAlias`,
  `@IntentionallyDataClass`, `@IntentionallyWithoutToString`, `@IntentionallyMutableCollection`,
  `@IntentionallyPairOrTriple`, `@IntentionallyBooleanParameter`, `@IntentionallyNullableBoolean`,
  `@IntentionallyRequiredParameterAfterOptional`,
  `@IntentionallyInconsistentParameterOrder`, `@IntentionallyInlinedLogic`,
  `@IntentionallyMangledJvmName`, `@IntentionallyKotlinOnlyApi`,
  `@IntentionallyNonStaticCompanionApi`, `@IntentionallyDefaultFacadeName`,
  `@IntentionallyWithoutJvmOverloads`,
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
