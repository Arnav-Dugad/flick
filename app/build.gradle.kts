import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes ONLY from environment variables (set by CI from GitHub Secrets).
// See docs/RELEASING.md. Without them, release builds are unsigned and cannot be installed.
val releaseKeystore: String? = System.getenv("FLICK_KEYSTORE_PATH")

android {
    namespace = "io.github.arnavdugad.flick"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.arnavdugad.flick"
        minSdk = 26
        targetSdk = 37
        // CI passes -Pflick.versionCode / -Pflick.versionName derived from the git tag.
        versionCode = (findProperty("flick.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("flick.versionName") as String?) ?: "0.1.0"
        buildConfigField("String", "GITHUB_REPO", "\"Arnav-Dugad/flick\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("FLICK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FLICK_KEY_ALIAS")
                keyPassword = System.getenv("FLICK_KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
        release {
            // Phones only: MediaPipe's native libraries dominate APK size.
            ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // MediaPipe memory-maps model bundles straight from the APK.
        noCompress += "task"
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    lint {
        // Flick is a phone app distributed as an APK; ChromeOS x86 builds aren't a target.
        disable += "ChromeOsAbiSupport"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.mediapipe.tasks.vision)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
