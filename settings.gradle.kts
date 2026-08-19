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

// Lets Gradle auto-provision a matching JDK for jvmToolchain(...) requests — :logic's
// jvmToolchain(21) — instead of requiring one to already be installed.
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
// Was temporarily excluded (attempts 8-9, see NOTES.md) to isolate whether multi-module-ness
// itself was causing the BaseVariant sync crash. It wasn't — the real cause (attempt 10) was
// com.android.application being declared inline in app/build.gradle.kts instead of at the root
// with apply false, unrelated to :logic entirely. Restored now that sync is confirmed working.
include(":logic")
include(":app")
