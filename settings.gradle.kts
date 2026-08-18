pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "routy-android"

// :logic is a plain Kotlin/JVM module (no Android Gradle plugin, no SDK needed) so its
// business-logic port — geo math, API models, the route/recording state machines — can be
// built and unit-tested with a bare `gradle test`, independent of whether an Android SDK is
// available in whatever environment runs the build.
include(":logic")
include(":app")
