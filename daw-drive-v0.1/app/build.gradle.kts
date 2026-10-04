plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingStoreFile = System.getenv("DAW_TEST_KEYSTORE_PATH")
val signingStorePassword = System.getenv("DAW_TEST_KEYSTORE_PASSWORD")
val signingKeyAlias = System.getenv("DAW_TEST_KEY_ALIAS")
val signingKeyPassword = System.getenv("DAW_TEST_KEY_PASSWORD")
val ciVersionCode = System.getenv("LONE_RIDER_VERSION_CODE")?.toIntOrNull()
val ciVersionName = System.getenv("LONE_RIDER_VERSION_NAME")

android {
    namespace = "com.dualactionwindows.dawdrive"
    compileSdk = 36

    signingConfigs {
        if (
            signingStoreFile != null &&
            signingStorePassword != null &&
            signingKeyAlias != null &&
            signingKeyPassword != null
        ) {
            create("persistentTest") {
                storeFile = file(signingStoreFile)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.dualactionwindows.dawdrive"
        minSdk = 35
        targetSdk = 36
        versionCode = ciVersionCode ?: 100318
        versionName = ciVersionName ?: "1.12-full"
    }

    buildTypes {
        debug {
            signingConfigs.findByName("persistentTest")?.let {
                signingConfig = it
            }
        }
        release {
            signingConfigs.findByName("persistentTest")?.let {
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
        buildConfig = true
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
