plugins { id("com.android.application") }

android {
    namespace = "cz.ioff.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.ioff.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
