# libs-watchdog

A Kotlin compiler plugin that helps library authors keep their public API easy to evolve. Built on
top of the [compiler-plugin-dev-kit](../compiler-plugin-dev-kit) (consumed from `mavenLocal`).

## Checkers

Both checkers only look at declarations visible to library clients (public or protected API).

- `OPEN_API_WITHOUT_SUBCLASS_OPT_IN` — warns about open/abstract classes and interfaces that can
  be subclassed outside the library without restriction. Suppress by gating subclassing with
  [`@SubclassOptInRequired`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-subclass-opt-in-required/)
  or by acknowledging the contract with `@IntentionallyOpen`.
- `EXHAUSTIVE_PUBLIC_API` — warns about enums and sealed hierarchies, which clients can match
  exhaustively (`when` without `else`), so adding an entry or a subtype later breaks client code.
  Acknowledge the contract with `@IntentionallyExhaustive`.

## Modules

- [`:compiler-plugin`](compiler-plugin/src) — the compiler plugin (FIR checkers).
- [`:plugin-annotations`](plugin-annotations/src/commonMain/kotlin) — `@IntentionallyOpen` and
  `@IntentionallyExhaustive`.
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
