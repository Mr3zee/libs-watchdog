# Tapmoc suggestion

This is a build-level check performed by the Gradle plugin, not the compiler plugin. Watching the
shape of the public API is only half of library evolution: the compiled artifacts also have to
stay consumable from the oldest JDK and Kotlin versions the library supports.

## What happens

The Gradle plugin checks that [Tapmoc](https://github.com/GradleUp/Tapmoc) (`com.gradleup.tapmoc`,
formerly CompatPatrouille) is applied alongside it. Tapmoc pins the Java and Kotlin compatibility
levels a module is built against, so its artifacts stay usable on older runtimes and compilers.
When Tapmoc is missing, the build prints a warning with a setup snippet:

```kotlin
plugins {
    id("com.gradleup.tapmoc") version "<version>"
}

tapmoc {
    java(17)        // oldest supported Java release
    kotlin("2.1.0") // oldest supported Kotlin version
}
```

`java(...)` pins the oldest Java release the artifacts must run on, and `kotlin(...)` pins the
oldest Kotlin version they must stay compatible with.

## Disable it

Turn off the suggestion through the extension:

```kotlin
apiWatchdog {
    suggestTapmoc = false
}
```

## See also

- [Binary compatibility validation suggestion](abi-validation-suggestion.md), the other
  build-level check
- [Tapmoc repository](https://github.com/GradleUp/Tapmoc)
- [Latest Tapmoc release](https://github.com/GradleUp/Tapmoc/releases/latest)
- [Tapmoc documentation](https://gradleup.com/tapmoc/)
