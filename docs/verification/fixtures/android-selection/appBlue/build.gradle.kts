plugins {
    id("com.android.application")
}

android {
    namespace = "dev.example.issue150.blue"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.example.issue150.blue"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "fixture-v2"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    signingConfigs.getByName("debug") {
        storeFile = rootProject.file(".fixture-signing/debug.keystore")
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
