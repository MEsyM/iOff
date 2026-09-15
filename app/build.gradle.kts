plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="cz.ioff.app"; compileSdk=35
 defaultConfig { applicationId="cz.ioff.app"; minSdk=26; targetSdk=35; versionCode=5; versionName="0.5.0"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 buildFeatures { compose=true }
 buildTypes { getByName("release") { isMinifyEnabled=true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") } }
 testOptions { unitTests.isIncludeAndroidResources=true }
}
kotlin { jvmToolchain(17) }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.08.01")); implementation("androidx.activity:activity-compose:1.10.1"); implementation("androidx.compose.material3:material3"); implementation("androidx.compose.material:material-icons-extended"); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview"); debugImplementation("androidx.compose.ui:ui-tooling"); implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2"); implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2"); testImplementation("junit:junit:4.13.2"); testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2"); testImplementation("org.robolectric:robolectric:4.14.1"); testImplementation("androidx.test:core:1.6.1")
}
