import java.util.Properties

// Read local.properties (gitignored) for developer-local fallback values.
// Mirrors how Android Studio's standard `localProperties` flow exposes sdk.dir.
val localProps: Properties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Per-flavor API key resolution — mirrors iOS Local.xcconfig / Test.xcconfig / Production.xcconfig
// which carry distinct API_KEY values baked at build time.
//
// Resolution chain for each flavor:
//   1. -PAPI_KEY_<FLAVOR> (CI gradle property)
//   2. <FLAVOR>_API_KEY env var (e.g. LOCAL_API_KEY) — preferred for local dev
//   3. DESTINY_API_KEY_<FLAVOR> env var (CI legacy naming)
//   4. API_KEY_<FLAVOR> in local.properties
//   5. (local only) the iOS local key as a hardcoded fallback — interchangeable with
//      the local dev backend's recognized seed key.
//   6. Legacy fallback: -PAPI_KEY / DESTINY_API_KEY / API_KEY (single key) — backward compat.
fun resolveApiKey(flavor: String): String {
    val upper = flavor.uppercase()
    val resolved = (project.findProperty("API_KEY_$upper") as? String)
        ?: System.getenv("${upper}_API_KEY")
        ?: System.getenv("DESTINY_API_KEY_$upper")
        ?: localProps.getProperty("API_KEY_$upper")
        // Legacy single-key fallback (used by older local setups + the LOCAL_API_KEY entry
        // in local.properties is also picked up via API_KEY in local.properties).
        ?: (project.findProperty("API_KEY") as? String)
        ?: System.getenv("DESTINY_API_KEY")
        ?: localProps.getProperty("API_KEY")
        ?: ""

    // Local flavor has a baked-in fallback so emulator dev never hits an empty key.
    // This matches the iOS Local.xcconfig API_KEY value and the seeded test key the
    // local backend recognizes.
    if (resolved.isBlank() && upper == "LOCAL") {
        return "astro_ios_G5iY3-1Z7ymE46hYwKTbK1bSz2x5Vn4BeymPOvyy3ic"
    }
    return resolved
}

val apiKeyLocal: String = resolveApiKey("LOCAL")
val apiKeyStaging: String = resolveApiKey("STAGING")
val apiKeyProduction: String = resolveApiKey("PRODUCTION")

// Fail the build for any non-debug task when the relevant flavor's API_KEY is
// empty so we never produce an unauthenticated APK/AAB again. Debug builds
// are allowed to proceed (developers may build without a key for unit-test
// compilation), but the runtime interceptor will Log.e and skip the malformed
// header.
val runningTaskNames: List<String> = gradle.startParameter.taskNames
val isDebugTask: Boolean = runningTaskNames.any { name ->
    val lower = name.lowercase()
    lower.contains("debug") && !lower.contains("release")
}
val needsStagingKey: Boolean = runningTaskNames.any { it.contains("Staging", ignoreCase = true) }
val needsProductionKey: Boolean = runningTaskNames.any { it.contains("Production", ignoreCase = true) }
if (!isDebugTask && runningTaskNames.isNotEmpty()) {
    if (needsStagingKey && apiKeyStaging.isBlank()) {
        error(
            "API_KEY_STAGING missing — pass -PAPI_KEY_STAGING=... or set DESTINY_API_KEY_STAGING env var. " +
                "Required for staging release builds (tasks: $runningTaskNames)."
        )
    }
    if (needsProductionKey && apiKeyProduction.isBlank()) {
        error(
            "API_KEY_PRODUCTION missing — pass -PAPI_KEY_PRODUCTION=... or set DESTINY_API_KEY_PRODUCTION env var. " +
                "Required for production release builds (tasks: $runningTaskNames)."
        )
    }
}

// Google OAuth Web (server) client ID — used by Android Google Sign-In to request
// an ID token that the backend can verify. Mirrors iOS GIDClientID in Info.plist.
// Source order: gradle property → env var → local.properties → empty (Sign-In stays disabled at runtime).
val googleServerClientId: String = (project.findProperty("GOOGLE_SERVER_CLIENT_ID") as? String)
    ?: System.getenv("GOOGLE_SERVER_CLIENT_ID")
    ?: System.getenv("DESTINY_GOOGLE_SERVER_CLIENT_ID")
    ?: localProps.getProperty("GOOGLE_SERVER_CLIENT_ID")
    ?: ""

// Fail the build for any non-debug release/staging task when GOOGLE_SERVER_CLIENT_ID
// is empty. Without it, Google Sign-In silently fails at runtime in production builds.
// Mirrors the apiKey* check above.
if (!isDebugTask && runningTaskNames.isNotEmpty()) {
    val needsGoogleClientId: Boolean = runningTaskNames.any { name ->
        val lower = name.lowercase()
        (lower.contains("staging") || lower.contains("production")) && lower.contains("release")
    }
    if (needsGoogleClientId && googleServerClientId.isBlank()) {
        error(
            "GOOGLE_SERVER_CLIENT_ID missing — pass -PGOOGLE_SERVER_CLIENT_ID=... or set " +
                "GOOGLE_SERVER_CLIENT_ID env var. Required for release builds so Google " +
                "Sign-In does not silently fail at runtime (tasks: $runningTaskNames)."
        )
    }
}

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
    compileSdk = 36

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
        // Local — mirrors iOS Local.xcconfig. Emulator host loopback (10.0.2.2 → host's localhost).
        create("local") {
            dimension = "env"
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000\"")
            buildConfigField("String", "API_KEY", "\"$apiKeyLocal\"")
            buildConfigField("String", "ENV", "\"local\"")
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        }
        // Staging — mirrors iOS Test.xcconfig. (Kept "staging" name to match existing CI
        // references to DESTINY_API_KEY_STAGING and the assembleStagingDebug task.)
        // applicationIdSuffix=".staging" — staging is a SEPARATE Play Console app
        // (com.destinyai.astrology.staging) so it never collides with production rollouts
        // on the same internal track.
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "API_BASE_URL", "\"https://astroapi-test-dsqvza5jza-ul.a.run.app\"")
            buildConfigField("String", "API_KEY", "\"$apiKeyStaging\"")
            buildConfigField("String", "ENV", "\"staging\"")
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        }
        // Production — mirrors iOS Production.xcconfig. No applicationId suffix.
        create("production") {
            dimension = "env"
            buildConfigField("String", "API_BASE_URL", "\"https://astroapi-prod-dsqvza5jza-ul.a.run.app\"")
            buildConfigField("String", "API_KEY", "\"$apiKeyProduction\"")
            buildConfigField("String", "ENV", "\"production\"")
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        }
    }

    buildTypes {
        debug {
            // Intentionally does NOT override API_BASE_URL — the productFlavor decides.
            // Previous debug-level override forced every debug build (incl. staging/production
            // debug variants) to point at 10.0.2.2 which masked real connectivity.
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
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
            }
        }
    }

    lint {
        // Baseline suppresses pre-existing issues so CI only fails on newly introduced errors.
        // Run `./gradlew updateLintBaseline` locally to update after fixing existing issues.
        baseline = file("lint-baseline.xml")
        abortOnError = true
        warningsAsErrors = false
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

    // Chrome Custom Tabs
    implementation(libs.androidx.browser)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)

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

    // Google Sign-In (legacy GMS — kept for compatibility while migrating off)
    implementation(libs.play.services.auth)

    // Credential Manager (modern Google Sign-In via AndroidX + Sign-in-with-Google helper)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

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
