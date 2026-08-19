// Top-level build file — no build logic of its own, just declares plugin versions once so
// :app and :logic can `apply` them without repeating version numbers.
//
// com.android.application is deliberately declared in app/build.gradle.kts instead of here,
// not at the root: AGP itself is hosted on Google's Maven repo, and root-level `plugins {}`
// entries get resolved for *every* task in the build, including ones scoped to :logic. Keeping
// it out of the root means `./gradlew :logic:test` never needs Google's repo at all — only
// building :app does.
plugins {
    // No org.jetbrains.kotlin.android here (deliberately — see app/build.gradle.kts): AGP 9
    // has built-in Kotlin support and applies its own Kotlin-Android integration internally,
    // which conflicts with the traditional kotlin-android plugin if both are applied at once.
    // :logic still needs org.jetbrains.kotlin.jvm — it's a plain Kotlin/JVM module, not Android,
    // so AGP's built-in Kotlin support doesn't cover it.
    //
    // 2.2.10 per developer.android.com/build/releases/agp-9-0-0-release-notes: "Android Gradle
    // plugin 9.0 now has a runtime dependency on Kotlin Gradle plugin (KGP) 2.2.10 [...] if you
    // use a KGP version lower than 2.2.10, Gradle will automatically upgrade your KGP version to
    // 2.2.10" — i.e. this is the documented floor AGP 9 itself is built and tested against, so
    // pinning compose/serialization to the same version keeps everything on one known-compatible
    // Kotlin version instead of letting AGP silently upgrade just the built-in half.
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}
