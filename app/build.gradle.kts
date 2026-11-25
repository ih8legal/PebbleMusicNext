plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.pebblemusicremote"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.pebblemusicremote"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Release signing comes from environment variables (set by GitHub Actions from
    // repository secrets, or exported locally). Without them, release stays unsigned.
    val keystorePath = System.getenv("KEYSTORE_FILE")
    val hasSigning = keystorePath != null && file(keystorePath).exists()

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // If this version doesn't resolve, check the PebbleKitAndroid2 releases page
    // for the latest version and replace 1.3.2.
    implementation("io.rebble.pebblekit2:client:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
