plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val testKeystorePath = System.getenv("DAW_TEST_KEYSTORE_PATH")
val testKeystorePassword = System.getenv("DAW_TEST_KEYSTORE_PASSWORD")
val testKeyAlias = System.getenv("DAW_TEST_KEY_ALIAS")
val testKeyPassword = System.getenv("DAW_TEST_KEY_PASSWORD")
val ciVersionCode = System.getenv("DAW_VERSION_CODE")?.toIntOrNull()
val ciVersionName = System.getenv("DAW_VERSION_NAME")

android {
    namespace = "com.dualactionwindows.dawdrive"
    compileSdk = 36

    signingConfigs {
        if (
            testKeystorePath != null &&
            testKeystorePassword != null &&
            testKeyAlias != null &&
            testKeyPassword != null
        ) {
            create("test") {
                storeFile = file(testKeystorePath)
                storePassword = testKeystorePassword
                keyAlias = testKeyAlias
                keyPassword = testKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.dualactionwindows.dawdrive"
        minSdk = 35
        targetSdk = 36
        versionCode = ciVersionCode ?: 25
        versionName = ciVersionName ?: "0.25-test"
    }

    buildTypes {
        debug {
            signingConfigs.findByName("test")?.let {
                signingConfig = it
            }
        }
        release {
            signingConfigs.findByName("test")?.let {
                signingConfig = it
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.media:media:1.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
