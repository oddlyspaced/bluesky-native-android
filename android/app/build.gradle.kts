plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    // Not Bluesky's reverse domain: a fork must not look like the official app (ASSETS.md section 3).
    namespace = "io.nativestarter.showcase.bluesky"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.nativestarter.showcase.bluesky"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The local dev network. The emulator reaches it through `adb reverse tcp:3000 tcp:3000`.
        buildConfigField("String", "PDS_URL", "\"http://localhost:3000\"")
        // Same value the RN e2e build sets from dev-env/constants.ts (E2E_APPVIEW_DID).
        // The dev network derives this DID from the AppView port, so it can change when the network restarts.
        // Override without editing code: ./gradlew -PappviewDid=did:plc:... :app:installDebug
        val appviewDid = (project.findProperty("appviewDid") as String?) ?: "did:plc:bw7ad3erl7btq6qwf66yqiov"
        buildConfigField("String", "APPVIEW_PROXY", "\"$appviewDid#bsky_appview\"")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // Used only for PullToRefreshBox; no Material components are drawn (keep-design).
    implementation("androidx.compose.material3:material3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Espresso 3.5.x (pulled in by ui-test-junit4) calls InputManager.getInstance, which API 36 removed.
    // 3.7.0 and the matching AndroidX Test releases are required on the API 36.1 emulator.
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
