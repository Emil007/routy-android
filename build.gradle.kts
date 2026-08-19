// Top-level build file — no build logic of its own, just declares plugin versions once so
// :app and :logic can `apply` them without repeating version numbers.
//
// com.android.application used to be deliberately kept out of this file and declared only in
// app/build.gradle.kts with an explicit version, so :logic:test never needed Google's Maven.
// That turned out to be the actual cause of a ten-attempt-long real Android Studio sync failure
// (NoClassDefFoundError: BaseVariant — full history in NOTES.md): standard Android Studio
// templates declare *every* plugin, including com.android.application, at the root with
// `apply false`, then apply it bare (no version) in each subproject — a different
// plugin-resolution path than a subproject requesting an explicit version directly, even though
// the final resolved artifact coordinates end up identical either way. Confirmed fixed by
// matching that pattern — do not move com.android.application's declaration back into
// app/build.gradle.kts.
plugins {
    // No org.jetbrains.kotlin.android here (deliberately — see app/build.gradle.kts): AGP 9
    // has built-in Kotlin support and applies its own Kotlin-Android integration internally,
    // which conflicts with the traditional kotlin-android plugin if both are applied at once.
    // :logic still needs org.jetbrains.kotlin.jvm and .plugin.serialization — it's a plain
    // Kotlin/JVM module, not Android, so AGP's built-in Kotlin support doesn't cover it, and it
    // has real @Serializable classes of its own (api/*Models.kt).
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}
