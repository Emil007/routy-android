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
    // :logic still needs org.jetbrains.kotlin.jvm and .plugin.serialization — it's a plain
    // Kotlin/JVM module, not Android, so AGP's built-in Kotlin support doesn't cover it, and it
    // has real @Serializable classes of its own (api/*Models.kt).
    //
    // 2.2.10, matching exactly what a real Android Studio "Empty Activity" project generated on
    // the same machine this project is actually being synced on (app/build.gradle.kts's
    // plugins-block comment has the full story of how many guesses that replaced) — 2.4.10 was
    // tried in between on the theory that Google's compatibility table's open-ended range for
    // 2.4.x meant it, not 2.2.10, was the version that cooperates with AGP 9's built-in Kotlin.
    // That theory was wrong: a verified-working real project uses 2.2.10, so the actual variable
    // was never the Kotlin version at all (see app/build.gradle.kts for what it really was).
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}
