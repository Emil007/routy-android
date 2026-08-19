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
    // traditional org.jetbrains.kotlin.android plugin — this is the actual fix, after four
    // failed attempts that were each solving the wrong problem:
    //   1. AGP 9.2.1 + org.jetbrains.kotlin.android + android.builtInKotlin=false: wrong flag.
    //      builtInKotlin=false doesn't opt back into the legacy DSL that kotlin-android needs —
    //      android.newDsl (a *different* flag) defaults to true on AGP 9 regardless, so AGP still
    //      exposed the new DSL's extension types, and kotlin-android's internal BaseVariant
    //      references broke: NoClassDefFoundError: com/android/build/gradle/api/BaseVariant.
    //   2. Same setup + android.enableLegacyVariantApi=true: doesn't address android.newDsl
    //      either — identical error.
    //   3. Reverted to AGP 8.13.2 to sidestep AGP 9 entirely: traded one conflict for another.
    //      Kotlin Gradle Plugin 2.3.20's own AgpWithBuiltInKotlinAppliedCheck diagnostic (which
    //      runs unconditionally whenever org.jetbrains.kotlin.android is applied, regardless of
    //      AGP major version) tried to reference com.android.build.gradle.BaseExtension and hit
    //      NoClassDefFoundError — a KGP-side check for exactly this legacy-plugin-vs-new-DSL
    //      conflict, not a real absence of BaseExtension in AGP 8.13.2's jar.
    //   4. Removed org.jetbrains.kotlin.android entirely, AGP 9.3.0, Kotlin 2.2.10 for the
    //      remaining compiler plugins (compose/serialization) — right idea, wrong Kotlin
    //      version. The *exact same* BaseVariant NoClassDefFoundError came back anyway, from a
    //      different code path: KotlinAndroidPlugin.Companion.dynamicallyApplyWhenAndroidPluginIsApplied,
    //      a decades-old Kotlin Gradle Plugin convenience that auto-applies kotlin-android the
    //      moment it sees com.android.application on a project — regardless of whether anyone
    //      declared it — for people who forgot to apply kotlin-android explicitly. At 2.2.10
    //      that mechanism doesn't yet know to back off when AGP's built-in Kotlin is already
    //      handling the target, so it fired anyway and hit the same legacy BaseVariant reference.
    // Fix: bump Kotlin to 2.4.10 (see root build.gradle.kts's comment for why — Google's own
    // compatibility table caps 2.2.x/2.3.x at the last 8.x AGP release each and only leaves
    // 2.4.x open-ended into AGP 9, which is the version where KGP's auto-apply was actually
    // taught to cooperate with built-in Kotlin). AGP 9.3.0 (current stable as of Aug 2026,
    // developer.android.com/build/releases/gradle-plugin) was never the wrong part of attempt 4.
    id("com.android.application") version "9.3.0"
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
