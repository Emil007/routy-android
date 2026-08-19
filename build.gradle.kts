// Top-level build file — no build logic of its own, just declares plugin versions once so
// :app (and :logic, when restored) can `apply` them without repeating version numbers.
//
// TENTH ATTEMPT, see NOTES.md: com.android.application used to be deliberately kept out of this
// file and declared only in app/build.gradle.kts with an explicit version — reasoning at the
// time was that AGP is hosted on Google's Maven, and keeping it off the root's plugins {} block
// meant `./gradlew :logic:test` never needed Google's repo. That was a genuine, real difference
// from a verified-working Android-Studio-generated template's root build file that went
// unnoticed through nine straight attempts, because the template's own root build.gradle.kts was
// never actually seen — only its app/build.gradle.kts, settings.gradle.kts, and
// gradle/libs.versions.toml were. Standard Android Studio templates declare *every* plugin,
// including com.android.application, at the root with `apply false`, then apply it bare (no
// version) in each subproject — a different plugin-resolution path than a subproject requesting
// an explicit version directly, even though the final resolved artifact coordinates end up
// identical either way. Matching that pattern now, on the theory that the resolution *path*
// itself — not just the final version — might matter to whatever is producing the BaseVariant
// class-generation failure.
plugins {
    // No org.jetbrains.kotlin.android here (deliberately — see app/build.gradle.kts): AGP 9
    // has built-in Kotlin support and applies its own Kotlin-Android integration internally,
    // which conflicts with the traditional kotlin-android plugin if both are applied at once.
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
