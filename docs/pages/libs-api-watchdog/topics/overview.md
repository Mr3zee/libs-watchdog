# Get started

%product% is a Kotlin compiler plugin that warns library authors about public API declarations
that are hard to evolve. It runs as a set of Kotlin K2 compiler frontend checks, and only in
modules compiled with
[explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode)
(`kotlin { explicitApi() }` or `-Xexplicit-api`, in either `strict` or `warning` variant). Outside
explicit API mode, the checks are not registered at all.

%product% is intentionally restrictive by default: every check reports a compilation error until
it is demoted to a warning, disabled, or exempted in place.

## What it looks like

A public data class that hands out a mutable collection, without documentation, triggers three
diagnostics at once:

```kotlin
public data class Config( // DATA_CLASS_PUBLIC_API, UNDOCUMENTED_PUBLIC_API
    public val tags: MutableList<String> // MUTABLE_COLLECTION_PUBLIC_API, UNDOCUMENTED_PUBLIC_API
)
```

- The generated `copy` and `componentN` functions and the constructor bake the exact property
  list into the compiled API.
- `tags` shares a mutable collection across the API boundary: it is unclear whether client-side
  and library-side mutations affect each other.
- Neither the class nor the property has KDoc.

The fixed version documents the type, exposes a read-only collection, and drops the data class
shape:

```kotlin
/**
 * Immutable request configuration.
 *
 * @property tags Labels attached to this configuration.
 */
public class Config(public val tags: List<String>) {
    override fun toString(): String = "Config(tags=$tags)"
}
```

## All checks

### API surface checks

- [Open API without subclass opt-in](open-api-without-subclass-opt-in.md): open or abstract
  classes and interfaces that any outside code can subclass without restriction.
- [Subclass opt-in without markers](subclass-opt-in-without-markers.md): `@SubclassOptInRequired`
  annotations that list no marker classes, so they do not actually restrict subclassing.
- [Exhaustive public API](exhaustive-public-api.md): enums and sealed hierarchies, which clients
  can match exhaustively, so adding an entry or a subtype later breaks client code.
- [Undocumented public API](undocumented-public-api.md): public declarations of every kind that
  have no KDoc.
- [Function type aliases in public API](function-type-alias-public-api.md): type aliases that
  abbreviate function types; the alias erases from the compiled API, so the type cannot evolve
  into a richer abstraction later.
- [Data classes in public API](data-class-public-api.md): data classes, whose generated `copy`,
  `componentN`, and constructor bake the exact property list into the compiled API.
- [Stateful classes without toString](stateful-class-without-to-string.md): classes with a
  backing-field property that neither declare nor inherit `toString`, so instances render as an
  opaque default in logs and debuggers.
- [Mutable collections in public API](mutable-collection-public-api.md): mutable collection
  types in public signatures, which leave it unclear whether client-side and library-side
  mutations affect each other.
- [Pair and Triple in public API](pair-or-triple-public-api.md): the tuple types `Pair` and
  `Triple`, whose components carry no domain meaning and whose fixed shape cannot evolve.
- [Boolean parameters in public API](boolean-parameter-public-api.md): Boolean value parameters,
  whose positional `true`/`false` argument reveals nothing about its meaning at the call site.
- [Nullable Booleans in public API](nullable-boolean-public-api.md): nullable Booleans in public
  signatures, which model three states while naming only two.
- [Required parameters after optional ones](required-parameter-after-optional.md): required
  parameters declared after an optional one, which then cannot be passed positionally without
  restating the earlier defaults.
- [Inconsistent parameter order in overloads](inconsistent-parameter-order-in-overloads.md):
  overloads whose same-named parameters appear in a different relative order, inviting silently
  swapped arguments.
- [Inline functions with logic](inline-function-with-logic.md): public inline functions whose
  body does more than delegate, since the compiler copies that logic, and its bugs, into every
  client binary.

### Java interop checks

These checks only run in JVM compilations, and only pay off for libraries that support Java
consumers; the whole group is disabled with `javaInterop { enabled = false }`. See
[Java interop checks](java-interop.md) for the group overview.

- [Mangled JVM names in public API](mangled-jvm-name-public-api.md): public API that Java sources
  cannot call because a value class in the signature gets its JVM name mangled.
- [Kotlin-only API without JvmSynthetic](kotlin-only-api-without-jvm-synthetic.md): functions
  whose shape only Kotlin callers can use idiomatically, yet still land in the API surface Java
  sources see.
- [Companion API without JvmStatic](companion-api-without-jvm-static.md): public companion object
  functions without `@JvmStatic`, which Java callers can only reach through the `Companion`
  instance.
- [Companion constants without JvmField](companion-constant-without-jvm-field.md): constant-shaped
  companion object properties that Java can only read through the companion instance getter.
- [Top-level API without JvmName](top-level-api-without-jvm-name.md): files whose public top-level
  API compiles into a file facade class without a pinned `@file:JvmName`, so renaming the file
  breaks Java sources and binaries built against it.
- [Default parameters without JvmOverloads](default-parameters-without-jvm-overloads.md): default
  parameter values without `@JvmOverloads`, forcing Java callers to spell out every argument.

### DSL marker checks

- [DSL markers with no-op targets](dsl-marker-noop-target.md): `@DslMarker` annotation targets on
  which the marker has no effect, giving a false sense of scope control.
- [DSL markers without explicit targets](dsl-marker-without-explicit-targets.md): `@DslMarker`
  annotations without an explicit `@Target`, whose default target set allows mostly no-op targets.
- [DSL markers on no-op type positions](dsl-marker-noop-type-position.md): DSL markers written on
  type positions where scope control does not react to them.

### Exemption hygiene

- [Exemptions without explanation](exemption-without-explanation.md): `@Intentionally*` exemption
  annotations left with the default `OTHER` reason and an empty description, which explains
  nothing. Always an error; it cannot be configured like the other diagnostics.

Beyond the diagnostic catalog, the Gradle plugin warns when explicit API mode is not enabled
(see [Setup](setup.md)), and it also runs two build-level checks: the
[Tapmoc suggestion](tapmoc-suggestion.md) warns when the
[Tapmoc](https://github.com/GradleUp/Tapmoc) plugin, which pins the oldest Java and Kotlin
versions a module is built against, is not applied alongside %product%, and the
[binary compatibility validation suggestion](abi-validation-suggestion.md) warns when neither
the Kotlin Gradle plugin's built-in ABI validation nor the standalone Binary Compatibility
Validator plugin is applied alongside it.

## Next steps

- [Setup](setup.md)
- [Gradle plugin reference](gradle-plugin.md)
- [Exemptions and internal API](exemptions.md)
- [API reference](%host%/libs-api-watchdog/api/)
