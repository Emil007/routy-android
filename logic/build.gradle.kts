// Plain Kotlin/JVM module — no Android Gradle plugin, no Android SDK dependency. Everything
// here is portable business logic shared conceptually with the web app (src/lib/geo.ts and the
// route/recording state machines in RouteGenerator.tsx / RecordTrackWizard.tsx), reimplemented
// in Kotlin so :app's ViewModels can use it directly. Kept SDK-independent on purpose so it
// builds and tests the same way regardless of whether an Android SDK is available.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    // 21 to match Android Studio's bundled JDK (and to avoid needing Gradle's toolchain
    // auto-provisioning, which needs network access this environment doesn't have to Google's
    // repo anyway).
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}
