plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vrarengine.core"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vrarengine.core"
        minSdk = 26 // MediaPipe GPU delegate prefere API 26+
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-dev"
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Hand tracking on-device (pinça)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
}
