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
//
// TEMPORARILY EXCLUDED — diagnostic only, see NOTES.md's "eighth attempt": seven straight
// version/plugin/gradle.properties changes all hit the identical BaseVariant
// NoClassDefFoundError, with :app's plugins/versions now matching a verified-working
// single-module template exactly. The one remaining structural difference is that this is a
// multi-module build. Pulling :logic out entirely isolates whether that's the actual trigger,
// since the crash happens during Gradle's plugin-application phase — strictly before any Kotlin
// source (including :app's now-broken :logic imports) ever gets compiled, so this only needs a
// sync to attempt, not a full build. Restore this line (and app/build.gradle.kts's
// implementation(project(":logic")) line) once the diagnosis is confirmed either way.
// include(":logic")
include(":app")
