# Setup

## Requirements

%product% is a Kotlin compiler plugin, built against Kotlin %kotlin-version%. It needs a Gradle
project that applies the Kotlin plugin and turns on
[explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode):

```kotlin
kotlin {
    explicitApi()
}
```

The `-Xexplicit-api` compiler flag, and the `warning` variant of either form, also count. Without
explicit API mode enabled, %product% registers no checks at all: there is no public API contract
to watch.

## Apply the Gradle plugin

```kotlin
plugins {
    id("org.jetbrains.kotlinx.libs.api.watchdog") version "%libs-api-watchdog-version%"
}
```

## What applying does

Applying the plugin:

- Registers the compiler plugin for every compilation in the project.
- Adds a dependency on `plugin-annotations`, a Kotlin Multiplatform library with the
  `@Intentionally*` exemption annotations, automatically.
- Checks whether Tapmoc and binary compatibility validation are applied alongside it, printing a
  build warning with a setup snippet for either one that is missing. See below.

## First build

Every check reports a compilation error by default. See [Gradle plugin reference](gradle-plugin.md)
for demoting individual checks to warnings or disabling them, and
[Exemptions and internal API](exemptions.md) for exempting a single declaration in place instead of
changing severity project-wide.

## Without Gradle

When invoking the compiler directly, configure severities with the repeatable plugin option
described in the README:

```
-P plugin:org.jetbrains.kotlinx.libs.api.watchdog:diagnosticSeverity=NAME:severity
```

`NAME` is a diagnostic name (for example, `UNDOCUMENTED_PUBLIC_API`) and `severity` is `error`,
`warning`, or `none`.

## Tapmoc suggestion

The Gradle plugin also checks that [Tapmoc](https://github.com/GradleUp/Tapmoc) is applied
alongside it and prints a build warning with a setup snippet when it is not. See
[Tapmoc suggestion](tapmoc-suggestion.md) for details.

## Binary compatibility validation suggestion

The Gradle plugin also checks that binary compatibility validation - either the Kotlin Gradle
plugin's built-in ABI validation or the standalone Binary Compatibility Validator plugin - is
enabled alongside it, and prints a build warning with a setup snippet when neither is. This check
runs the same way as the Tapmoc one, so both warnings can show up on the same build. See
[Binary compatibility validation suggestion](abi-validation-suggestion.md) for details.
