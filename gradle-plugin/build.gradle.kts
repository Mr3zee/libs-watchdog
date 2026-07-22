plugins {
    kotlin("compiler.plugin.devkit.gradle-plugin")
}

pluginDevKit {
    extraLibrary(project(":plugin-annotations"))
    compilerPlugin = project(":compiler-plugin")
}

// Functional test projects resolve these sibling artifacts from the shared functionalTestRepo.
// For :plugin-annotations only the JVM and root (metadata) publications are needed, which avoids
// building every native target just to run JVM functional tests.
tasks.named("installForFunctionalTest") {
    dependsOn(
        ":compiler-plugin:installForFunctionalTest",
        ":plugin-annotations:publishJvmPublicationToFunctionalTestRepository",
        ":plugin-annotations:publishKotlinMultiplatformPublicationToFunctionalTestRepository",
    )
}

gradlePlugin {
    plugins {
        create("LibsWatchdog") {
            id = group.toString()
            displayName = "LibsWatchdog"
            description =
                "Warns Kotlin library authors about public API declarations that are hard to evolve"
            implementationClass = "org.jetbrains.kotlin.libs.watchdog.WatchdogSupportPlugin"
        }
    }
}
