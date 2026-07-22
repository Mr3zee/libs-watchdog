# libs-watchdog

A Kotlin compiler plugin that helps library authors keep their public API easy to evolve. Built on
top of the [compiler-plugin-dev-kit](../compiler-plugin-dev-kit) (consumed from `mavenLocal`).

## Checkers

The plugin only runs in modules compiled with
[explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode)
(`kotlin { explicitApi() }` or `-Xexplicit-api`, in either `strict` or `warning` variant); without
it the checkers are not registered at all. The checkers only look at declarations visible to
library clients (public or protected API).

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
}
```

When invoking the compiler directly, the same configuration is available as a repeatable plugin
option: `-P plugin:org.jetbrains.kotlin.libs.watchdog:diagnosticSeverity=UNDOCUMENTED_PUBLIC_API:warning`.

Note that the Kotlin compiler hides regular warnings when a compilation fails with errors, so
demoted diagnostics only show up in failing builds with `-Xreport-all-warnings`.

## Modules

- [`:compiler-plugin`](compiler-plugin/src) — the compiler plugin (FIR checkers).
- [`:plugin-annotations`](plugin-annotations/src/commonMain/kotlin) — `@IntentionallyOpen`,
  `@IntentionallyExhaustive`, and `@IntentionallyUndocumented`.
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
