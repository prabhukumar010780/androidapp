import java.util.Properties

// Read local.properties (gitignored) for developer-local fallback values.
val localProps: Properties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Per-build-type API key — mirrors iOS Local.xcconfig / Test.xcconfig /
// Production.xcconfig. Resolved in order:
//   1. -PAPI_KEY_<TYPE>   (CI gradle property)
//   2. API_KEY_<TYPE>     env var
//   3. API_KEY_<TYPE>     in local.properties
//   (debug uses a hardcoded local-dev key so emulator builds never block)
fun resolveApiKey(buildType: String): String {
    val upper = buildType.uppercase()
    val resolved = (project.findProperty("API_KEY_$upper") as? String)
        ?: System.getenv("API_KEY_$upper")
        ?: localProps.getProperty("API_KEY_$upper")
        ?: ""
    if (resolved.isBlank() && upper == "LOCAL") {
        return "astro_ios_G5iY3-1Z7ymE46hYwKTbK1bSz2x5Vn4BeymPOvyy3ic"
    }
    return resolved
}

val apiKeyLocal: String = resolveApiKey("LOCAL")
val apiKeyStaging: String = resolveApiKey("STAGING")
val apiKeyProduction: String = resolveApiKey("PRODUCTION")

// Fail the build when a signed-release task has no API key. Debug uses a
// hardcoded local-dev key so emulator dev never blocks.
val runningTaskNames: List<String> = gradle.startParameter.taskNames
val needsStagingKey: Boolean = runningTaskNames.any { it.contains("Staging", ignoreCase = true) }
val needsProductionKey: Boolean = runningTaskNames.any { name ->
    val lower = name.lowercase()
    lower.contains("release") && !lower.contains("staging") && !lower.contains("debug")
}
if (runningTaskNames.isNotEmpty()) {
    if (needsStagingKey && apiKeyStaging.isBlank()) {
        error("API_KEY_STAGING missing — pass -PAPI_KEY_STAGING=... (tasks: $runningTaskNames).")
    }
    if (needsProductionKey && apiKeyProduction.isBlank()) {
        error("API_KEY_PRODUCTION missing — pass -PAPI_KEY_PRODUCTION=... (tasks: $runningTaskNames).")
    }
}

// Google OAuth Web (server) client ID — used by Android Google Sign-In to
// request an ID token the backend can verify. Mirrors iOS GIDClientID.
val googleServerClientId: String = (project.findProperty("GOOGLE_SERVER_CLIENT_ID") as? String)
    ?: System.getenv("GOOGLE_SERVER_CLIENT_ID")
    ?: localProps.getProperty("GOOGLE_SERVER_CLIENT_ID")
    ?: ""

if (runningTaskNames.isNotEmpty() && (needsStagingKey || needsProductionKey) && googleServerClientId.isBlank()) {
    error("GOOGLE_SERVER_CLIENT_ID missing — pass -PGOOGLE_SERVER_CLIENT_ID=... (tasks: $runningTaskNames).")
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
        // Single applicationId across all build types — parity with iOS which uses
        // one bundle id (com.destinyai.astrology) for Local/Test/Production via
        // xcconfig-driven configurations rather than separate apps.
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

    // Build types carry the env-specific values — mirrors iOS xcconfig per
    // Build Configuration. Single applicationId across all three (parity with
    // iOS which only has one bundle id, com.destinyai.astrology). The
    // versionNameSuffix + ENV BuildConfig field tells testers which
    // environment a given install is on.
    buildTypes {
        debug {
            // Emulator-host loopback — Local.xcconfig parity. No applicationIdSuffix:
            // installing debug clobbers any staging/release install on the same device,
            // exactly like iOS where only one bundle id exists at a time.
            versionNameSuffix = "-local"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000\"")
            buildConfigField("String", "API_KEY", "\"$apiKeyLocal\"")
            buildConfigField("String", "ENV", "\"local\"")
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        }
        // Staging — mirrors iOS Test.xcconfig. Release-style (minified + shrunk +
        // release-signed) so we catch ProGuard regressions before production.
        // Same applicationId as release: only one Play Console app.
        create("staging") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-staging"
            buildConfigField("String", "API_BASE_URL", "\"https://astroapi-test-dsqvza5jza-ul.a.run.app\"")
            buildConfigField("String", "API_KEY", "\"$apiKeyStaging\"")
            buildConfigField("String", "ENV", "\"staging\"")
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "API_BASE_URL", "\"https://astroapi-prod-dsqvza5jza-ul.a.run.app\"")
            buildConfigField("String", "API_KEY", "\"$apiKeyProduction\"")
            buildConfigField("String", "ENV", "\"production\"")
            buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
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
