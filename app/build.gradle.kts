// Everything Android-framework-dependent lives here. This module could not be
// compile-verified while writing it: this sandbox's egress policy blocks dl.google.com
// entirely, which backs both the `google()` Gradle repository and every androidx.*/Play
// Services artifact, and there's no Android SDK installed either. First thing to do in
// Android Studio: let it sync, then fix whatever it flags — see NOTES.md at the repo root
// for the full rundown of what's verified vs. not.
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9's built-in Kotlin support (see gradle.properties's android.builtInKotlin), not the
    // traditional org.jetbrains.kotlin.android plugin — five failed attempts before this one,
    // each solving a real but wrong problem (attempts 1-4 documented in NOTES.md; short version:
    // wrong gradle.properties flag, a flag that didn't help, an AGP-8 retreat that hit a
    // different KGP-side check, then a build-in-Kotlin setup that still crashed on BaseVariant
    // regardless of Kotlin 2.2.10 vs 2.4.10). What finally broke the loop: the user generated a
    // real, working Empty-Activity project from the *same* Android Studio install and sent back
    // its exact files — verified-working ground truth instead of another guess from docs.
    //
    // That template applies only android-application + kotlin-compose — no
    // org.jetbrains.kotlin.plugin.serialization at all, unlike this module, which had it for one
    // reason: GithubReleaseDto (update-checker DTO) was declared directly in :app. Moved to
    // :logic/api/GithubReleaseModels.kt instead (which already applies the serialization
    // compiler plugin, and isn't an Android module, so it can never hit this AGP/KGP conflict in
    // the first place) — :app only needs the kotlinx-serialization-json *runtime* dependency
    // (still declared below) to use Json{} and the Retrofit converter factory, not the compiler
    // plugin, once it has no @Serializable classes of its own. AGP/Gradle/Kotlin versions below
    // are copied exactly from the working template, not independently guessed.
    id("com.android.application") version "9.3.1"
    id("org.jetbrains.kotlin.plugin.compose")
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

// AGP's built-in Kotlin support (no org.jetbrains.kotlin.android applied — see the plugins
// block above) registers this top-level `kotlin` extension itself; compilerOptions here works
// the same as it would under the traditional plugin. Technically redundant — built-in Kotlin
// defaults jvmTarget to compileOptions.targetCompatibility (JVM_17, set below) on its own — but
// left explicit since that's what already matches this module's compileOptions.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // TEMPORARILY COMMENTED — see settings.gradle.kts's matching include(":logic") comment.
    // implementation(project(":logic"))

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
