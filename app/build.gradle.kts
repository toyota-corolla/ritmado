plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ritmo.planner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ritmo.planner"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "7.6"
    }
    signingConfigs {
        create("stableDebug") {
            val keyPath = System.getenv("LIFE_EXE_KEYSTORE_PATH")
            if (!keyPath.isNullOrBlank()) {
                storeFile = file(keyPath)
                storePassword = System.getenv("LIFE_EXE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LIFE_EXE_KEY_ALIAS")
                keyPassword = System.getenv("LIFE_EXE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // When the matching keystore is configured in repository secrets,
            // every CI update uses the same certificate. Otherwise AGP uses its
            // disposable runner debug key and Android may reject in-place updates.
            if (!System.getenv("LIFE_EXE_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
