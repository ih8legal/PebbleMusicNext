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

    buildTypes {
        release {
            isMinifyEnabled = false
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
