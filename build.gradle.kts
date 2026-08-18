// Top-level build file — no build logic of its own, just declares plugin versions once so
// :app and :logic can `apply` them without repeating version numbers.
//
// com.android.application is deliberately declared in app/build.gradle.kts instead of here,
// not at the root: AGP itself is hosted on Google's Maven repo, and root-level `plugins {}`
// entries get resolved for *every* task in the build, including ones scoped to :logic. Keeping
// it out of the root means `./gradlew :logic:test` never needs Google's repo at all — only
// building :app does.
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
