plugins {
    id("com.android.application")
}

android {
    namespace = "com.maxlab.motioncues"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maxlab.motioncues"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    // Тот же ключ, которым подписаны APK 1.0/1.1 — обновления ставятся поверх без удаления.
    // Ключ одноразовый и лежит в репо намеренно: личный sideload, не для Play Store.
    signingConfigs {
        create("shared") {
            storeFile = file("../keystore/motioncues.jks")
            storePassword = "android"
            keyAlias = "mc"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Activity Recognition: автовключение, когда еду (DriveReceiver)
    implementation("com.google.android.gms:play-services-location:21.4.0")
}
