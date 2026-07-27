plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.destinyai.astrology.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Baseline profile generation requires API 28+ (AOT compile control).
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Baseline profiles are generated against :app.
    targetProjectPath = ":app"
}

// Generate on a connected device/emulator. CI runs this against the release
// variant to produce app/src/release/generated/baselineProfiles/.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
