// Top-level build file — no build logic of its own, just declares plugin versions once so
// :app and :logic can `apply` them without repeating version numbers.
//
// com.android.application is deliberately declared in app/build.gradle.kts instead of here,
// not at the root: AGP itself is hosted on Google's Maven repo, and root-level `plugins {}`
// entries get resolved for *every* task in the build, including ones scoped to :logic. Keeping
// it out of the root means `./gradlew :logic:test` never needs Google's repo at all — only
// building :app does.
plugins {
    // Pinned per developer.android.com/build/kotlin-support's official compatibility table,
    // which maps Kotlin versions to the AGP range each one actually supports — not guessed, and
    // not just "whatever's newest": that table lists Kotlin 2.3.x as supporting AGP 8.2.2-8.13
    // (app/build.gradle.kts's 8.13.2), while Kotlin 2.2.x tops out at AGP 8.10 and Kotlin 2.1.x
    // at AGP 8.7.2 — i.e. this exact version had to be looked up, not assumed, after two AGP-9
    // attempts failed on exactly this kind of unverified pairing.
    id("org.jetbrains.kotlin.jvm") version "2.3.20" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
}
