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
    // 2.4.10, not 2.2.10 (tried first, see app/build.gradle.kts's plugins-block comment for why
    // it failed): AGP 9.0's release notes name 2.2.10 as its runtime-dependency *floor*, but
    // that's not the same as "cooperates with built-in Kotlin". Google's own compatibility table
    // (developer.android.com/build/kotlin-support) caps Kotlin 2.2.x and 2.3.x at the *last 8.x*
    // AGP release each (8.10 and 8.13 respectively) and leaves 2.4.x open-ended starting at
    // 8.5.2+ — the first line actually validated against AGP 9. That distinction turned out to
    // be load-bearing: Kotlin Gradle Plugin has always auto-applied org.jetbrains.kotlin.android
    // the moment it sees com.android.application on a project (a decades-old convenience for
    // people who forgot to apply kotlin-android explicitly), and at 2.2.10 that auto-apply
    // doesn't know to back off when AGP's built-in Kotlin is already handling the Android target
    // — so it fires anyway, and its legacy KotlinAndroidTarget still references BaseVariant.
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
}
