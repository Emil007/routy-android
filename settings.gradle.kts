pluginManagement {
    repositories {
        // Content filter added to match a verified-working Android-Studio-generated template
        // exactly (ninth attempt, see NOTES.md and root build.gradle.kts): restricts google()
        // to the artifact groups it's actually authoritative for, so Kotlin Gradle Plugin
        // artifacts (org.jetbrains.kotlin.*, not covered by any of these regexes) always resolve
        // from mavenCentral()/gradlePluginPortal() below instead, with no ambiguity about which
        // repository a given plugin request should hit.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Lets Gradle auto-provision a matching JDK for jvmToolchain(...) requests (:logic's, when
// restored) instead of requiring one to already be installed — present here only because the
// verified-working template has it; :app alone doesn't currently need it.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
