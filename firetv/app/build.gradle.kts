import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val media3Version = "1.11.1"

android {
    namespace = "org.mandir.tvreceiver"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.mandir.tvreceiver"
        // Fire OS 6 (Android 7.1) and newer. Media3 needs API 23+.
        minSdk = 23
        targetSdk = 34
        versionCode = 10
        versionName = "1.0.9"
    }

    signingConfigs {
        // A fixed key kept in the repo so every build (local or CI) can be
        // installed over the previous one with `adb install -r`.
        // This app is only sideloaded on the mandir's own TVs; replace the key
        // if you ever publish it anywhere.
        create("shared") {
            storeFile = file("mandirtv.keystore")
            storePassword = "mandirtv"
            keyAlias = "mandirtv"
            keyPassword = "mandirtv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
}
