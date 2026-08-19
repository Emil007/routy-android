// Everything Android-framework-dependent lives here. This module could not be
// compile-verified while writing it: this sandbox's egress policy blocks dl.google.com
// entirely, which backs both the `google()` Gradle repository and every androidx.*/Play
// Services artifact, and there's no Android SDK installed either. First thing to do in
// Android Studio: let it sync, then fix whatever it flags — see NOTES.md at the repo root
// for the full rundown of what's verified vs. not.
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP jumped a major version (8.x -> 9.x) after this was first written, and 9.0 removed the
    // old Variant API wholesale (com.android.build.gradle.api.BaseVariant and friends) — first
    // attempt bumped straight to AGP 9.2.1, which broke a second way: org.jetbrains.kotlin.android
    // (the traditional Kotlin<->Android integration this project uses) still hard-references
    // BaseVariant internally, and no gradle.properties flag brought that class back — it's
    // genuinely gone from AGP 9's jar, not just hidden behind a flag. Settled on AGP 8.13.2
    // instead: the last stable 8.x release, where BaseVariant still exists for real, paired with
    // the exact Kotlin version Google's own compatibility table
    // (developer.android.com/build/kotlin-support) lists for it — see the root build.gradle.kts's
    // Kotlin plugin version comment.
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing: keystore.properties (gitignored — see .gitignore) at the repo root, written
// by .github/workflows/release.yml from repo secrets right before assembleRelease runs. Never
// committed, and simply absent for any local/debug build, which is exactly why the signingConfig
// below is conditional rather than assumed to exist.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasKeystoreProperties = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasKeystoreProperties) load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.routy.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.routy.app"
        minSdk = 26
        targetSdk = 36
        // Overridden by CI from the pushed release tag (-PappVersionName=1.2.3 -PappVersionCode=N)
        // — see .github/workflows/release.yml. Local/debug builds just get this placeholder.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "0.1.0"
    }

    signingConfigs {
        if (hasKeystoreProperties) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystoreProperties) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Off by default in AGP 8+ — turned on so the update checker (M6) can read
        // BuildConfig.VERSION_NAME to compare the running app against the latest GitHub release.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// android.kotlinOptions {} (used pre-fix) was deprecated in Kotlin 2.0 and removed outright in
// 2.2 — this top-level kotlin {} block (a sibling of android {}, not nested inside it) is the
// replacement the Kotlin Gradle plugin docs specify.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":logic"))

    // --- Compose ---
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // --- Networking ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // --- Secure token/server-URL storage ---
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Location (foreground recording, M4) ---
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // --- Map (M3) ---
    implementation("org.maplibre.gl:android-sdk:10.2.0")

    // --- Tests ---
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
