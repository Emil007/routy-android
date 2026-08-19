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
    //
    // org.jetbrains.kotlin.jvm and .plugin.serialization TEMPORARILY REMOVED — diagnostic only,
    // see NOTES.md's "ninth attempt". Excluding :logic from settings.gradle.kts (the eighth
    // attempt) did NOT fix the BaseVariant crash, which ruled out multi-module-ness itself as
    // the trigger — but that test was incomplete: this file still declared these two plugins
    // (apply false) for :logic's benefit even with :logic gone, and root-level `plugins {}`
    // entries get resolved onto the build's shared plugin classpath regardless of whether
    // anything actually applies them. The verified-working template's root build file only ever
    // declared android-application + kotlin-compose, nothing else — so the previous diagnostic
    // wasn't actually testing ":app, completely alone" yet. This removes the gap: with these two
    // gone, :app's plugins {} block plus this file are now a byte-for-byte structural match for
    // the template's plugin declarations. Restore both lines together with settings.gradle.kts's
    // include(":logic") once the diagnosis is confirmed either way.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
