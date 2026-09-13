plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="cz.ioff.app"; compileSdk=35
 defaultConfig { applicationId="cz.ioff.app"; minSdk=26; targetSdk=35; versionCode=5; versionName="0.4.0"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 buildFeatures { compose=true }
 buildTypes { getByName("release") { isMinifyEnabled=true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") } }
 testOptions { unitTests.isIncludeAndroidResources=true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.08.01")); implementation("androidx.activity:activity-compose:1.10.1"); implementation("androidx.compose.material3:material3"); implementation("androidx.compose.material:material-icons-extended"); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview"); debugImplementation("androidx.compose.ui:ui-tooling"); implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2"); testImplementation("junit:junit:4.13.2")
}
