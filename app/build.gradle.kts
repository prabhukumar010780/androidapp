val apiKey: String = (project.findProperty("API_KEY") as? String)
    ?: System.getenv("DESTINY_API_KEY")
    ?: "" // empty in unconfigured environments — calls will fail fast at runtime

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.destinyai.astrology"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.destinyai.astrology"
        minSdk = 24
        targetSdk = 36
        // CI injects versionCode via -PversionCode=<GITHUB_RUN_NUMBER>
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "1.0"

        testInstrumentationRunner = "com.destinyai.astrology.HiltTestRunner"
    }

    signingConfigs {
        create("release") {
            // Injected at build time — see android-deploy.yml
            storeFile = System.getenv("ANDROID_KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("ANDROID_STORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }

    flavorDimensions += "env"
    productFlavors {
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "API_BASE_URL", "\"https://astroapi-test-dsqvza5jza-ul.a.run.app\"")
            buildConfigField("String", "API_KEY", "\"$apiKey\"")
            buildConfigField("String", "ENV", "\"staging\"")
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"\"")
        }
        create("production") {
            dimension = "env"
            buildConfigField("String", "API_BASE_URL", "\"https://astroapi-prod-dsqvza5jza-ul.a.run.app\"")
            buildConfigField("String", "API_KEY", "\"$apiKey\"")
            buildConfigField("String", "ENV", "\"production\"")
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"\"")
        }
    }

    buildTypes {
        debug {
            // Override API URL for local dev (emulator: 10.0.2.2 maps to host localhost)
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    // JUnit5 support for unit tests
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Retrofit + OkHttp
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // Hilt DI
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Room (local DB)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // DataStore (user preferences / settings)
    implementation(libs.datastore.preferences)

    // Security Crypto (Keystore-backed encrypted prefs)
    implementation(libs.security.crypto)

    // Google Sign-In
    implementation(libs.play.services.auth)

    // Firebase / FCM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    // Google Play Billing
    implementation(libs.billing)

    // --- Unit Tests ---
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)  // discovers JUnit 4 tests (Compose UI)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit)              // JUnit 4 — required by Compose UI test rules
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.mockwebserver)
    // Compose UI tests under JVM (Robolectric-backed) — enables TDD red→green
    // cycles for view-level structure tests without requiring an emulator.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)

    // --- Instrumented Tests ---
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.android.compiler.test)
    androidTestImplementation(libs.mockk.android)
    // Compose UI tests on real devices/emulators
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
