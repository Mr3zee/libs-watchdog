plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

pluginDevKit {
    testDataLibraries { common(project(":plugin-annotations")) }
}

tasks.named("animalsnifferMain") {
    enabled = false
}

