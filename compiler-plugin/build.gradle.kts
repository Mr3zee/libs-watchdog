plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

dependencies {
    defaultRuntimeLibraries(project(":plugin-annotations"))
}

tasks.named("animalsnifferMain") {
    enabled = false
}

