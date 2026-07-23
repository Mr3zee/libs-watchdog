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
- `DSL_MARKER_NOOP_TYPE_POSITION` — reports DSL markers written on type positions where they have
  no effect: a plain parameter type (`fun process(tag: @MyDsl Tag)`), a return type, or a
  property/variable type. Scope control only reacts to markers on the type of an implicit value —
  a receiver type, a context parameter type, or a function type with such implicit values (there
  the marker propagates to them). Markers on supertypes, type parameter bounds, and type alias
  expansions are effective carriers and stay exempt; markers nested in type arguments are not
  analyzed. Unlike the API-surface checks, this one also fires on non-public declarations.

## Configuring severities

Every diagnostic is reported as a compilation **error** by default. Each one can be demoted to a
warning individually through the `libsWatchdog` extension:

```kotlin
import org.jetbrains.kotlin.libs.watchdog.WatchdogSeverity

libsWatchdog {
    openApiWithoutSubclassOptIn.set(WatchdogSeverity.WARNING)
    subclassOptInWithoutMarkers.set(WatchdogSeverity.WARNING)
    exhaustivePublicApi.set(WatchdogSeverity.WARNING)
    undocumentedPublicApi.set(WatchdogSeverity.WARNING)
    functionTypeAliasPublicApi.set(WatchdogSeverity.WARNING)
    dslMarkerNoopTarget.set(WatchdogSeverity.WARNING)
    dslMarkerWithoutExplicitTargets.set(WatchdogSeverity.WARNING)
    dslMarkerNoopTypePosition.set(WatchdogSeverity.WARNING)
}
```

When invoking the compiler directly, the same configuration is available as a repeatable plugin
option: `-P plugin:org.jetbrains.kotlin.libs.watchdog:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:warning`.

Note that the Kotlin compiler hides regular warnings when a compilation fails with errors, so
demoted diagnostics only show up in failing builds with `-Xreport-all-warnings`.

## Modules

- [`:compiler-plugin`](compiler-plugin/src) — the compiler plugin (FIR checkers).
- [`:plugin-annotations`](plugin-annotations/src/commonMain/kotlin) — `@IntentionallyOpen`,
  `@IntentionallyExhaustive`, `@IntentionallyUndocumented`, `@IntentionallyFunctionTypeAlias`,
  and `@InternalAnnotationMarker`.
- [`:gradle-plugin`](gradle-plugin/src) — applies the compiler plugin and the annotations
  dependency to a Kotlin project (plugin id `org.jetbrains.kotlin.libs.watchdog`).

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
