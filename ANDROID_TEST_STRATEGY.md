# Android App — Test Strategy Document

**Project:** Destiny AI Astrology — Android  
**Date:** 2026-05-17  
**Status:** Pre-development (scaffold only)  
**Purpose:** Ensure complete iOS feature parity through test-first development

---

## 1. Overview

The Android app is a full port of the iOS app against the same AstroAPI V2 backend.
"Done" for any feature means: the feature behaves identically to iOS as verified by
all three test layers passing. No feature ships without a green row in the
[Feature Parity Matrix](#8-feature-parity-matrix).

### Core Principle

> Write the test before writing the implementation.
> The test defines what "correct" means. The implementation satisfies it.

### Source of Truth

| Source | What it defines |
|--------|----------------|
| iOS app behaviour | Expected UI flows, error handling, edge cases |
| AstroAPI V2 backend | API contracts — request shapes, response shapes, status codes |
| `ios_app/e2e/` | E2E user flows and LLM guardrail assertions |
| `ios_appTests/` | Unit-level assertions — 22 test files, 150+ assertions |

---

## 2. Test Architecture — Three Layers

```
         ┌──────────────────────────────────────┐
         │           E2E Tests (Appium)         │  30 test files
         │   Full user flows + LLM guardrails   │  Port of iOS suite
         ├──────────────────────────────────────┤
         │         Unit Tests (JUnit 5)         │  1 test file per ViewModel
         │   ViewModels, Services, Models       │  Written BEFORE implementation
         ├──────────────────────────────────────┤
         │      API Contract Tests (pytest)     │  Shared with iOS
         │   Backend endpoints, error codes     │  Platform-agnostic
         └──────────────────────────────────────┘
```

**Rule:** A feature is complete only when all three layers are green.

---

## 3. Layer 1 — API Contract Tests

### Purpose

Verify the backend behaves exactly as expected for both iOS and Android.
These tests are platform-agnostic — they run against the live backend using
`pytest` and the existing V2 venv. Both platforms must handle every contract identically.

### Location

```
astrology_api/astroapi-v2/tests/contract/
├── test_auth_contracts.py
├── test_partner_contracts.py
├── test_quota_contracts.py
├── test_notification_contracts.py
├── test_subscription_contracts.py
└── test_compatibility_contracts.py
```

### Run Command

```bash
cd astrology_api/astroapi-v2 && source venv/bin/activate
pytest tests/contract/ -v --tb=short
```

### Contracts to Cover

#### 3.1 Auth / Registration

| Scenario | Endpoint | Expected Status | Expected Body |
|----------|----------|-----------------|---------------|
| New guest registers | POST `/subscription/register` | 200 | `access_state: "granted"`, `plan_id: "free_guest"` |
| Guest upgrades to registered | POST `/subscription/upgrade` | 200 | `plan_id: "free_registered"` |
| Birth data already taken | POST `/subscription/profile` | 409 | `code: "birth_data_taken"` |
| Account deleted re-registers | POST `/subscription/register` | 409 | `code: "archived_guest"` |
| Deleted account makes any call | GET `/subscription/status` | 403 | `code: "account_deleted"` |
| Get status for unknown user | GET `/subscription/status?email=x` | 404 | error message |

#### 3.2 Partner Profiles

| Scenario | Endpoint | Expected Status | Expected Body |
|----------|----------|-----------------|---------------|
| Create partner (within quota) | POST `/subscription/partners` | 201 | partner object with `id` |
| Duplicate birth data | POST `/subscription/partners` | 409 | `code: "DUPLICATE_BIRTH_PROFILE"` |
| Free user exceeds partner limit | POST `/subscription/partners` | 403 | `code: "upgrade_required"` |
| Delete self-profile | DELETE `/subscription/partners/{id}` | 403 | `PROTECTED_MAIN_USER` |
| Delete active chart profile | DELETE `/subscription/partners/{id}` | 403 | `PROTECTED_ACTIVE_CHART` |
| Delete previously-switched profile | DELETE `/subscription/partners/{id}` | 403 | `PROTECTED_USED_PROFILE` |
| Update non-existent partner | PUT `/subscription/partners/999` | 404 | not found message |
| Batch delete — mixed protected/ok | POST `/subscription/partners/batch/delete` | 200 | `deleted: N, skipped: M` |

#### 3.3 Quota / Feature Access

| Scenario | Endpoint | Expected Status | Expected Body |
|----------|----------|-----------------|---------------|
| Free user within daily limit | GET `/subscription/can-access?feature=ai_questions` | 200 | `can_access: true` |
| Free user at daily limit | GET `/subscription/can-access?feature=ai_questions` | 200 | `can_access: false, upgrade_cta: "..."` |
| Premium user any feature | GET `/subscription/can-access?feature=ai_questions` | 200 | `can_access: true, daily_limit: -1` |
| Record usage | POST `/subscription/use` | 200 | updated `feature_usage` |
| Multi-count check (3 partners) | GET `/subscription/can-access?feature=compatibility&count=3` | 200 | `can_access: true/false` |

#### 3.4 Notifications

| Scenario | Endpoint | Expected Status | Expected Body |
|----------|----------|-----------------|---------------|
| Register Android FCM token | POST `/notifications/device-token` | 200 | `success: true, token_id: "..."` |
| Register same token again (upsert) | POST `/notifications/device-token` | 200 | same `token_id` |
| Deactivate token on logout | DELETE `/notifications/device-token?token=X` | 200 | `success: true` |
| Get empty inbox | GET `/notifications?user_email=new@x.com` | 200 | `notifications: [], total_count: 0` |
| Get unread count | GET `/notifications/unread-count?user_email=X` | 200 | `count: N` |
| Mark single as read | POST `/notifications/{id}/read` | 200 | `success: true` |
| Mark all as read | POST `/notifications/read-all?user_email=X` | 200 | `count: N` |
| Get preferences (new user defaults) | GET `/notifications/preferences?user_email=new@x.com` | 200 | `is_enabled: true` |

#### 3.5 Subscription Verification

| Scenario | Endpoint | Expected Status | Expected Body |
|----------|----------|-----------------|---------------|
| Valid Google Play purchase token | POST `/subscription/verify` (platform=google) | 200 | `success: true, plan_id: "..."` |
| Invalid/expired Google token | POST `/subscription/verify` | 400 | `success: false, error: "..."` |
| Valid Apple JWS (sandbox) | POST `/subscription/verify` (platform=apple, environment=Sandbox) | 200 | `success: true` |
| List available plans | GET `/subscription/plans` | 200 | array with `free_guest, free_registered, core, plus` plans |
| Each plan has Google product IDs | GET `/subscription/plans` | 200 | `google_product_id_monthly` and `google_product_id_yearly` fields |

#### 3.6 Error Handling (Android Must Handle All)

```python
# Every Android network call must map these to the right UI state
ERROR_CODE_MAP = {
    400: "Show inline validation error",
    401: "Force logout → auth screen",
    403: {
        "account_deleted":     "Force logout → auth screen",
        "upgrade_required":    "Show paywall sheet",
        "PROTECTED_MAIN_USER": "Show error toast",
        "PROTECTED_ACTIVE_CHART": "Show error toast",
    },
    404: "Show error, refresh list",
    409: {
        "birth_data_taken":  "Show merge/link account dialog",
        "archived_guest":    "Auto-upgrade, proceed silently",
        "DUPLICATE_BIRTH_PROFILE": "Show duplicate error inline",
    },
    500: "Show retry option, log error",
}
```

---

## 4. Layer 2 — Android Unit Tests

### Framework

```gradle
// build.gradle.kts
testImplementation("org.junit.jupiter:junit-jupiter:5.10.x")
testImplementation("io.mockk:mockk:1.13.x")
testImplementation("app.cash.turbine:turbine:1.1.x")         // StateFlow testing
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.x")
testImplementation("androidx.room:room-testing:2.6.x")
```

### Run Command

```bash
cd android_app && ./gradlew test --tests "com.destinyai.astrology.*" -i
```

### Rule

Write the test file before creating the ViewModel/Service it tests.
Tests start failing (red). Implementation makes them pass (green).

---

### 4.1 AuthViewModelTest

Mirrors: iOS `AuthViewModelTests.swift`

```kotlin
class AuthViewModelTest {
    // Session state
    fun `init loads saved session from keystore`()
    fun `unauthenticated state shown when no saved session`()

    // Google Sign-In
    fun `google sign-in success sets authenticated state`()
    fun `google sign-in failure sets error state`()

    // Guest signup
    fun `guest signup generates email and registers`()
    fun `guest signup failure shows error`()

    // Upgrade
    fun `upgrade guest to registered migrates history`()
    fun `upgrade fails on 409 conflict shows merge dialog`()

    // Logout
    fun `logout clears keystore, room, datastore`()

    // Deleted account
    fun `403 account_deleted on any call forces logout`()
}
```

---

### 4.2 HomeViewModelTest

Mirrors: iOS `HomeViewModelTests.swift` (14 assertions)

```kotlin
class HomeViewModelTest {
    // Defaults
    fun `init sets quota to plan default (10 for free registered)`()
    fun `init sets isLoading false`()

    // Greeting
    fun `greeting is Good Morning before noon`()
    fun `greeting is Good Afternoon 12pm to 5pm`()
    fun `greeting is Good Evening after 5pm`()

    // Display name
    fun `displayName returns Guest for guest user`()
    fun `displayName returns first name for registered user`()

    // Quota
    fun `quotaProgress is 0_5 when 5 of 10 used`()
    fun `decrementQuota reduces remaining by 1`()
    fun `decrementQuota does not go below 0`()
    fun `premium user quota shows unlimited`()

    // Data loading
    fun `loadHomeData sets isLoading true then false`()
    fun `loadHomeData populates suggestedQuestions`()
    fun `loadHomeData populates dailyInsight`()

    // Date formatting
    fun `renewalDateString formats as MMM d`()
}
```

---

### 4.3 ChatViewModelTest

Mirrors: iOS `ChatViewModelTests.swift` (27 assertions)

```kotlin
class ChatViewModelTest {
    // Init
    fun `init creates session`()
    fun `init has welcome message`()

    // canSend gate
    fun `canSend is false when input is empty`()
    fun `canSend is false when input is whitespace only`()
    fun `canSend is false when isLoading is true`()
    fun `canSend is false when isStreaming is true`()
    fun `canSend is true when input has text and not loading`()

    // New chat
    fun `startNewChat creates new thread`()
    fun `startNewChat clears messages`()
    fun `startNewChat updates history list`()

    // History
    fun `loadHistory populates historyList`()

    // Thread management
    fun `deleteThread removes from history`()
    fun `togglePinThread sets pinned true`()
    fun `clearChat removes all messages`()

    // Send
    fun `sendMessage without birth data sets error`()
    fun `sendMessage clears input text`()
    fun `sendMessage appends user message immediately`()
    fun `sendMessage on success appends ai message`()
    fun `sendMessage on network error sets error state`()

    // Streaming
    fun `streaming state is true during SSE stream`()
    fun `streaming state is false after stream completes`()
    fun `background interruption saves interrupted question`()
    fun `foreground return shows recovery card when interrupted`()
}
```

---

### 4.4 CompatibilityViewModelTest

Mirrors: iOS `CompatibilityViewModelTests.swift` (11 assertions)

```kotlin
class CompatibilityViewModelTest {
    // Validation
    fun `analyzeMatch with empty partner sets error`()
    fun `analyzeMatch error message is fill in all required fields`()
    fun `analyzeMatch with complete partner clears error`()

    // Score validation
    fun `score is between 0 and maxScore (36)`()
    fun `maxScore is exactly 36`()
    fun `percentage is score divided by maxScore`()  // 27/36 = 0.75

    // Kuta count
    fun `result always has exactly 8 kutas`()

    // Partner management
    fun `removePartner clears errorMessage`()
    fun `selectPartner clears errorMessage`()
    fun `addPartner disabled when activePartner is incomplete`()

    // Reset
    fun `reset clears all partner fields`()
}
```

---

### 4.5 BirthDataViewModelTest

Mirrors: iOS `BirthDataViewModelTests.swift`

```kotlin
class BirthDataViewModelTest {
    // Validation
    fun `isFormValid false when name is empty`()
    fun `isFormValid false when dateOfBirth is empty`()
    fun `isFormValid false when time is empty`()
    fun `isFormValid false when city is empty`()
    fun `isFormValid true when all fields filled`()

    // Date
    fun `dateOfBirth formats as YYYY-MM-DD`()
    fun `time formats as HH-MM (24-hour)`()

    // Coordinates
    fun `latitude is rounded to 4 decimal places`()
    fun `longitude is rounded to 4 decimal places`()
    fun `latitude range is -90 to 90`()
    fun `longitude range is -180 to 180`()

    // Submit
    fun `submit calls profile endpoint with correct body`()
    fun `submit 409 birth_data_taken shows merge dialog`()
}
```

---

### 4.6 NetworkClientTest

Mirrors: iOS `NetworkClientTests.swift` (13 assertions)

```kotlin
class NetworkClientTest {
    // Success
    fun `200 response decodes and returns model`()

    // Headers
    fun `every request includes Content-Type application-json`()
    fun `every request includes Authorization Bearer {apiKey}`()

    // Error mapping
    fun `401 response throws UnauthorizedException`()
    fun `403 account_deleted throws AccountDeletedException`()
    fun `403 upgrade_required throws UpgradeRequiredException`()
    fun `404 response throws NotFoundException`()
    fun `409 birth_data_taken throws BirthDataTakenException`()
    fun `500 response throws ServerErrorException`()
    fun `malformed JSON throws DecodingException`()
    fun `empty body throws NoDataException`()
    fun `network timeout throws NetworkTimeoutException`()
    fun `no internet throws NoConnectionException`()
}
```

---

### 4.7 DataManagerTest (Room)

Mirrors: iOS `DataManagerTests.swift` (23 assertions)

```kotlin
@RunWith(AndroidJUnit4::class)
class DataManagerTest {
    // Uses in-memory Room database

    // Sessions
    fun `getOrCreateSession is idempotent for same email`()
    fun `getSession returns null for unknown email`()

    // Threads
    fun `createThread returns thread with id`()
    fun `fetchThreads returns all threads for user`()
    fun `archiveThread sets isArchived true`()
    fun `pinThread sets isPinned true`()
    fun `deleteThread removes from database`()
    fun `fetchThreadsGroupedByDate returns Today label for today`()

    // Messages
    fun `saveMessage appends to thread`()
    fun `fetchMessages returns in chronological order`()
    fun `updateMessageCount increments thread message count`()

    // Cleanup
    fun `clearAllData wipes all tables`()
}
```

---

### 4.8 BirthDataModelTest

Mirrors: iOS `BirthDataTests.swift` (9 assertions)

```kotlin
class BirthDataModelTest {
    fun `JSON serialisation uses snake_case keys`()  // date_of_birth not dateOfBirth
    fun `JSON roundtrip preserves all fields`()
    fun `dateOfBirth format is YYYY-MM-DD`()
    fun `latitude accepts -90 to 90`()
    fun `latitude rejects values outside range`()
    fun `longitude accepts -180 to 180`()
    fun `longitude rejects values outside range`()
    fun `time format is HH-MM`()
    fun `city name is trimmed of whitespace`()
}
```

---

### 4.9 KutaTextBuilderTest

Mirrors: iOS `KutaTextBuilderTests.swift`

```kotlin
class KutaTextBuilderTest {
    // effectiveScore — cancelled dosha uses adjustedScore
    fun `effectiveScore returns adjustedScore when doshaCancelled is true`()
    fun `effectiveScore returns raw score when doshaCancelled is false`()
    fun `effectiveDisplayScore shows adjusted value in tooltip`()

    // All 8 kutas have descriptions
    fun `varna description includes spiritual orientation text`()
    fun `vashya description includes attraction text`()
    fun `tara description includes birth star text`()
    fun `yoni description includes intimate nature text`()
    fun `maitri description includes planetary friendship text`()
    fun `gana description includes temperament text`()
    fun `bhakoot description includes moon sign text`()
    fun `nadi description includes constitutional text`()

    // Dosha states
    fun `active dosha shows no cancellation found text`()
    fun `cancelled dosha shows cancellation reason`()

    // Formatting
    fun `no description contains em dash character`()
    fun `score displays as X out of Y format`()
}
```

---

### 4.10 AffirmationBuilderTest

Mirrors: iOS `AffirmationBuilderTests.swift` (17 assertions)

```kotlin
class AffirmationBuilderTest {
    // Tiers
    fun `score above 28 selects excellent tier template`()
    fun `score 23-27 selects good tier template`()
    fun `score 18-22 selects average tier template`()
    fun `score below 18 selects concern tier template`()

    // Weighting
    fun `nadi kuta is weighted highest`()
    fun `bhakoot kuta is weighted second`()
    fun `gana kuta is weighted third`()
    fun `varna excluded when max_points below 3`()

    // Perfect kutas
    fun `perfect nadi included in top kootas list`()
    fun `perfect bhakoot included in top kootas list`()
    fun `imperfect kuta excluded from top kootas`()

    // Score display
    fun `score formats as totalSlashMax (28-36)`()
    fun `adjustedScore takes precedence over raw total`()

    // Display names
    fun `graha maitri matches maitri substring`()
}
```

---

### 4.11 QuotaManagerTest

Mirrors: iOS `QuotaManagerTests.swift` (re-enabled)

```kotlin
class QuotaManagerTest {
    // Free guest (3/day)
    fun `free guest can_access true when under 3 daily`()
    fun `free guest can_access false when at 3 daily`()

    // Free registered (10/day)
    fun `free registered can_access true when under 10 daily`()
    fun `free registered can_access false at 10 daily`()

    // Premium (unlimited)
    fun `premium user can_access always true`()
    fun `premium daily_limit is -1 (unlimited)`()

    // Usage recording
    fun `recordUsage increments daily count`()
    fun `recordUsage resets at midnight`()

    // Sync
    fun `syncStatus fetches fresh quota from backend`()
    fun `syncStatus updates local state`()
}
```

---

### 4.12 SubscriptionManagerTest

```kotlin
class SubscriptionManagerTest {
    // Google Play Billing
    fun `loadProducts fetches from Google Play`()
    fun `purchase success calls backend verify`()
    fun `purchase success updates purchasedProductIds`()
    fun `purchase userCancelled returns false without error`()
    fun `purchase pending shows pending message`()

    // Environment
    fun `production build uses production billing env`()
    fun `debug build uses sandbox billing env`()

    // Restore
    fun `restorePurchases queries currentPurchases`()

    // Foreground refresh
    fun `onAppForeground calls updatePurchasedProducts`()

    // Active plan
    fun `activePlanId extracts core from product id`()
    fun `activePlanId extracts plus from product id`()
    fun `hasActiveSubscription false when purchasedIds empty`()
}
```

---

## 5. Layer 3 — E2E Tests (Appium)

### Framework

The existing iOS Appium suite is ported to Android with minimal changes:
- `conftest.py` → `conftest_android.py` (driver options only)
- `screens.py` → `screens_android.py` (same interface, UiAutomator2 backend)
- `assertions.py` — **zero changes** (pure Python string checks)
- All 30 test files — **minimal changes** (same accessibility IDs)

### Prerequisites

```bash
# Appium 2.x with UiAutomator2 driver
appium driver install uiautomator2
appium --port 4723 &

# Android emulator running
emulator -avd Pixel_7_API_35 &

# Backend running locally
cd astrology_api/astroapi-v2 && source venv/bin/activate
uvicorn app.main:app --reload --port 8000 &
```

### Driver Configuration

```python
# e2e_android/conftest.py
from appium.options import UiAutomator2Options
import pytest, os
from appium import webdriver

@pytest.fixture(scope="session")
def driver():
    opts = UiAutomator2Options()
    opts.platform_name    = "Android"
    opts.platform_version = "14"
    opts.device_name      = "emulator-5554"
    opts.app_package      = "com.destinyai.astrology"
    opts.app_activity     = ".MainActivity"
    opts.automation_name  = "UiAutomator2"
    opts.no_reset         = False
    opts.app              = os.environ.get("APK_PATH", "app/build/outputs/apk/debug/app-debug.apk")

    # Inject test session — Android equivalent of iOS UI_TEST_MODE
    opts.intent_args = "--es E2E_USER_EMAIL prabhukushwaha@gmail.com " \
                       "--es E2E_DOB 1980-07-01 " \
                       "--es E2E_TIME 06:32 " \
                       "--es E2E_LATITUDE 21.2138 " \
                       "--es E2E_LONGITUDE 81.3943 " \
                       "--es E2E_CITY Bhilai " \
                       "--ez UI_TEST_MODE true"

    drv = webdriver.Remote("http://127.0.0.1:4723", options=opts)
    drv.implicitly_wait(15)
    yield drv
    drv.quit()
```

### Android UI_TEST_MODE Implementation

In `MainActivity.kt`, detect the Intent extra and inject the test session:

```kotlin
// MainActivity.kt — DEBUG only, stripped from release builds
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (BuildConfig.DEBUG && intent.getBooleanExtra("UI_TEST_MODE", false)) {
        injectE2ESession(intent)
    }
}

private fun injectE2ESession(intent: Intent) {
    // Write directly to DataStore/SharedPrefs — same keys as production
    val prefs = getSharedPreferences("destiny_prefs", MODE_PRIVATE)
    prefs.edit()
        .putString("userEmail", intent.getStringExtra("E2E_USER_EMAIL"))
        .putBoolean("isGuest", false)
        .putBoolean("hasBirthData", true)
        .putString("userDOB", intent.getStringExtra("E2E_DOB"))
        .putString("userTime", intent.getStringExtra("E2E_TIME"))
        .putFloat("userLatitude", intent.getStringExtra("E2E_LATITUDE")!!.toFloat())
        .putFloat("userLongitude", intent.getStringExtra("E2E_LONGITUDE")!!.toFloat())
        .putString("userCity", intent.getStringExtra("E2E_CITY"))
        .apply()
    // Navigate directly to HomeScreen — skip onboarding and auth
    navController.navigate(Screen.Home)
}
```

### Required Accessibility IDs

Every interactive element in the Android app needs a `testTag` (Compose) or
`contentDescription` (XML) that matches the iOS `accessibilityIdentifier`.
This is what allows the same `screens.py` to work for both platforms.

**Complete required list — add to EVERY element before running E2E:**

```
Navigation:
  home_screen          tab_home             tab_chat
  tab_match            tab_history          tab_profile

Chat:
  chat_input           send_button          streaming_indicator
  ai_message           user_message         copy_button
  new_chat_button      chat_history_button  back_button

Compatibility:
  compat_screen        compat_analyze_button   compat_result_score
  partner_chip_0       partner_chip_1          add_partner_button
  compat_history_tab   kuta_grid               dosha_sheet_button

Profile & Partners:
  profile_screen       birth_details_button    partner_manager_button
  partner_add_button   partner_form_name       partner_form_dob
  partner_form_time    partner_form_city       partner_save_button
  partner_row_0        partner_delete_button

Settings:
  settings_sheet       language_picker         chart_style_picker
  ayanamsa_picker      house_system_picker

Notifications:
  notification_inbox   unread_badge            mark_all_read_button
  notification_row_0

Subscription:
  subscription_screen  plan_card_core          plan_card_plus
  subscribe_button     restore_button          sheet_close_button

History:
  history_screen       history_thread_row      delete_thread_button
```

**In Jetpack Compose:**
```kotlin
Text(
    text = greeting,
    modifier = Modifier.semantics { testTag = "home_screen" }
)
```

### E2E Test Files — Port Status

All 30 test files from `ios_app/e2e/` are ported to `android_app/e2e/`.
The test logic is identical. Only the driver fixture is swapped.

| File | Flows Tested | Port Effort |
|------|-------------|-------------|
| `test_01_onboarding.py` | Language select, birth data entry, submit | Medium — different keyboard handling |
| `test_02_home.py` | Home screen cards, tab navigation, quota | Low |
| `test_03_chat.py` (12 tests) | Send, stream, copy, new chat, charts, history | High — SSE timing may differ |
| `test_04_compatibility.py` | Partner select, analyze, result, dosha sheets | Medium |
| `test_05_charts.py` | Chart tabs, planet count | Low |
| `test_06_history.py` | Thread list, open, delete | Low |
| `test_07_profile.py` | Birth data sheet, language picker | Low |
| `test_08_settings.py` | Chart style, notification prefs | Low |
| `test_09_partners.py` | Add, list, delete with protection | Medium |
| `test_10_subscription.py` | Plan cards visible, close | Low |
| `test_11_notifications.py` | Inbox load, unread, mark read | Low |
| `test_12_style_finance.py` | Finance guardrails | Low — same assertions |
| `test_13_style_health.py` | Health guardrails | Low |
| `test_14_style_travel.py` | Travel guardrails | Low |
| `test_15_style_education.py` | Education guardrails | Low |
| `test_17_style_self.py` | Self/identity guardrails | Low |
| `test_18_style_spiritual.py` | Spiritual guardrails | Low |
| `test_19_style_family.py` | Family guardrails | Low |
| `test_20_style_property.py` | Property guardrails | Low |
| `test_21_style_legal.py` | Legal guardrails | Low |
| `test_22_style_muhurta.py` | Muhurta guardrails | Low |
| `test_23_style_general.py` | General guardrails | Low |
| `test_24_home_card_queries.py` | Home card → chat navigation | Low |
| `test_25_chat_visual_layout.py` | Message alignment, timestamps | Medium — layout differs |
| `test_26_progress_indicator.py` | Streaming progress animation | Medium |
| `test_27_compat_ask_destiny.py` | Compatibility + follow-up question | Medium |
| `test_28_optimizer_scenarios.py` | Query optimizer edge cases | Low — backend-side |
| `test_29_notifications.py` | Notification deep-link routing | High — Android back stack |

### Guardrail Assertions (shared, zero changes)

`assertions.py` is pure Python — works identically for both platforms.

```python
# All these run unchanged against Android LLM responses
assert_no_disease_names(text)      # "cancer diagnosis", "tumor", "malignant"
assert_no_fatalistic(text)         # "will die", "death is near", "fatal outcome"
assert_no_guarantees(text)         # "guaranteed", "you will definitely", "100% certain"
assert_no_bankruptcy(text)         # "bankruptcy"
assert_has_timing_window(text)     # requires a year reference (2025, 2026, etc.)
assert_has_recovery_path(text)     # requires "recovery", "rebuild", "protective"
assert_min_words(text, n=50)       # minimum response length
assert_no_em_dashes(text)          # no — or – characters
assert_no_education_fail_verdict(text)
```

---

## 6. Android-Specific Tests

These have no iOS equivalent and must be written from scratch.

### 6.1 FCM Token Lifecycle

```kotlin
class FCMTokenTest {
    fun `token registered with backend on first launch`()
    fun `token updated when FCM refreshes token`()
    fun `token deactivated on logout`()
    fun `token uses platform android not ios`()
    fun `token registered even for guest users`()
}
```

### 6.2 Google Play Billing

```kotlin
class GoogleBillingTest {
    // These use BillingClient test fixtures
    fun `queryProductDetails returns core and plus products`()
    fun `launchBillingFlow opens Google Play sheet`()
    fun `purchaseAcknowledged sends token to backend verify endpoint`()
    fun `verify endpoint called with platform=google`()
    fun `restorePurchases queries activePurchases`()
    fun `subscriptionExpired updates UI to free plan`()
}
```

### 6.3 Android Back Button Navigation

```kotlin
class BackButtonTest {
    fun `back from chat returns to home tab`()
    fun `back from compatibility result returns to partner form`()
    fun `back from profile returns to home tab`()
    fun `back from subscription sheet dismisses sheet`()
    fun `back from notification deep link returns to home`()
    fun `double back tap exits app`()
}
```

### 6.4 Configuration Changes

```kotlin
class ConfigurationChangeTest {
    fun `screen rotation preserves chat messages`()
    fun `screen rotation preserves streaming state`()
    fun `screen rotation preserves compatibility result`()
    fun `app resume from background refreshes subscription state`()
    fun `app resume from background does not restart stream`()
}
```

### 6.5 Deep Link / Notification Routing

```kotlin
class NotificationDeepLinkTest {
    fun `transit notification navigates to charts screen`()
    fun `yoga notification navigates to home yoga card`()
    fun `daily insight notification navigates to chat`()
    fun `compatibility notification navigates to compat tab`()
    fun `unknown notification type navigates to home`()
}
```

### 6.6 Locale / Language (38 languages)

```kotlin
class LocaleTest {
    fun `runtime language change reloads string resources`()
    fun `language preference persists across app restarts`()
    fun `hindi locale uses hi string resources`()
    fun `kannada locale uses kn string resources`()
    fun `arabic locale uses RTL layout direction`()
    fun `API calls include Accept-Language header matching selected language`()
}
```

---

## 7. CI/CD Integration

### GitHub Actions Workflow

```yaml
# .github/workflows/android-tests.yml
name: Android Tests

on:
  push:
    branches: [test, main]
    paths: ["android_app/**"]
  pull_request:
    branches: [test]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17' }
      - run: cd android_app && ./gradlew test --tests "com.destinyai.astrology.*"
      - uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: unit-test-results
          path: android_app/app/build/reports/tests/

  contract-tests:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env: { POSTGRES_DB: astro_test, POSTGRES_PASSWORD: test }
    steps:
      - uses: actions/checkout@v4
      - run: |
          cd astrology_api/astroapi-v2 && source venv/bin/activate
          pytest tests/contract/ -v --tb=short

  e2e-tests:
    runs-on: macos-14          # macOS required for Android emulator
    strategy:
      matrix:
        test-group: [core, guardrails]
    steps:
      - uses: actions/checkout@v4
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          target: google_apis
          arch: x86_64
          script: |
            cd android_app/e2e
            pytest ${{ matrix.test-group == 'core' && 'test_01* test_02* test_03* test_04* test_05* test_06* test_07* test_08* test_09* test_10* test_11*' || 'test_12* test_13* test_14* test_15* test_17* test_18* test_19* test_20* test_21* test_22* test_23*' }} -v
```

### Quality Gates (PR cannot merge without)

| Gate | Tool | Threshold |
|------|------|-----------|
| Unit tests pass | JUnit 5 | 100% pass rate |
| Code coverage | JaCoCo | ≥ 80% line coverage on ViewModels |
| Contract tests pass | pytest | 100% pass rate |
| Lint clean | Android Lint | 0 errors, 0 high-severity warnings |
| Static analysis | Detekt | 0 issues |

---

## 8. Feature Parity Matrix

This is the final acceptance checklist. A feature is **done** only when all three
layers are green. Updated as implementation progresses.

| Feature | API Contract | Unit Test | E2E Test | Status |
|---------|:---:|:---:|:---:|:---:|
| Guest signup + email generation | `test_auth_contracts` | `AuthViewModelTest` | `test_01_onboarding` | ⬜ |
| Birth data entry + validation | `test_auth_contracts` | `BirthDataViewModelTest` | `test_01_onboarding` | ⬜ |
| Google Sign-In | `test_auth_contracts` | `AuthViewModelTest` | `test_01_onboarding` | ⬜ |
| Guest → registered upgrade | `test_auth_contracts` | `AuthViewModelTest` | — | ⬜ |
| Session restore on cold start | — | `AuthViewModelTest` | `test_02_home` | ⬜ |
| Account delete (confirmation) | `test_auth_contracts` | `AuthViewModelTest` | `test_07_profile` | ⬜ |
| Home screen + daily insight | — | `HomeViewModelTest` | `test_02_home` | ⬜ |
| Quota display (used / limit) | `test_quota_contracts` | `HomeViewModelTest` | `test_02_home` | ⬜ |
| Time-based greeting | — | `HomeViewModelTest` | — | ⬜ |
| Chat send message | — | `ChatViewModelTest` | `test_03_chat` | ⬜ |
| Chat SSE streaming | — | `ChatViewModelTest` | `test_03_chat` | ⬜ |
| Streaming progress steps | — | `ChatViewModelTest` | `test_26_progress_indicator` | ⬜ |
| Background interruption recovery | — | `ChatViewModelTest` | `test_03_chat` | ⬜ |
| Chat history persist + reload | — | `DataManagerTest` | `test_03_chat` | ⬜ |
| Copy AI message | — | — | `test_03_chat` | ⬜ |
| Compatibility partner form | `test_partner_contracts` | `CompatibilityViewModelTest` | `test_04_compatibility` | ⬜ |
| Compatibility SSE streaming | — | `CompatibilityViewModelTest` | `test_04_compatibility` | ⬜ |
| Ashtakoot 8-kuta grid | — | `KutaTextBuilderTest` | `test_04_compatibility` | ⬜ |
| Kuta tooltip with effectiveScore | — | `KutaTextBuilderTest` | `test_04_compatibility` | ⬜ |
| Dosha sheets (Mangal, KalaSarpa) | — | — | `test_04_compatibility` | ⬜ |
| Affirmation tier text | — | `AffirmationBuilderTest` | `test_04_compatibility` | ⬜ |
| Chart tabs (N/S Indian) | — | `ChartViewModelTest` | `test_05_charts` | ⬜ |
| Dasha timeline | — | `ChartViewModelTest` | `test_05_charts` | ⬜ |
| Transit positions | — | `ChartViewModelTest` | `test_05_charts` | ⬜ |
| History list + delete thread | — | `HistoryViewModelTest` | `test_06_history` | ⬜ |
| Partner CRUD | `test_partner_contracts` | `PartnerViewModelTest` | `test_09_partners` | ⬜ |
| Partner protection rules (403) | `test_partner_contracts` | `PartnerViewModelTest` | `test_09_partners` | ⬜ |
| Profile switch + quota | `test_quota_contracts` | `PartnerViewModelTest` | `test_07_profile` | ⬜ |
| FCM token registration | `test_notification_contracts` | `FCMTokenTest` | `test_11_notifications` | ⬜ |
| Notification inbox (paginated) | `test_notification_contracts` | `NotificationVMTest` | `test_11_notifications` | ⬜ |
| Mark read / mark all read | `test_notification_contracts` | `NotificationVMTest` | `test_11_notifications` | ⬜ |
| Notification deep links | — | `NotificationDeepLinkTest` | `test_29_notifications` | ⬜ |
| Notification preferences | `test_notification_contracts` | `NotificationVMTest` | `test_08_settings` | ⬜ |
| Subscription plan list | `test_subscription_contracts` | `SubscriptionVMTest` | `test_10_subscription` | ⬜ |
| Google Play Billing purchase | `test_subscription_contracts` | `GoogleBillingTest` | `test_10_subscription` | ⬜ |
| Subscription receipt verify | `test_subscription_contracts` | `SubscriptionVMTest` | — | ⬜ |
| Quota enforcement (free limits) | `test_quota_contracts` | `QuotaManagerTest` | `test_03_chat` | ⬜ |
| Paywall on quota exceeded | `test_quota_contracts` | `QuotaManagerTest` | `test_03_chat` | ⬜ |
| Finance guardrails | — | — | `test_12_style_finance` | ⬜ |
| Health guardrails | — | — | `test_13_style_health` | ⬜ |
| Travel guardrails | — | — | `test_14_style_travel` | ⬜ |
| Education guardrails | — | — | `test_15_style_education` | ⬜ |
| Self/identity guardrails | — | — | `test_17_style_self` | ⬜ |
| Spiritual guardrails | — | — | `test_18_style_spiritual` | ⬜ |
| Family guardrails | — | — | `test_19_style_family` | ⬜ |
| Property guardrails | — | — | `test_20_style_property` | ⬜ |
| Legal guardrails | — | — | `test_21_style_legal` | ⬜ |
| Muhurta guardrails | — | — | `test_22_style_muhurta` | ⬜ |
| General guardrails | — | — | `test_23_style_general` | ⬜ |
| 38-language runtime switch | — | `LocaleTest` | `test_07_profile` | ⬜ |
| Language persists across restart | — | `LocaleTest` | — | ⬜ |
| RTL layout (Arabic, Hebrew, Farsi) | — | `LocaleTest` | — | ⬜ |
| Screen rotation preserves state | — | `ConfigurationChangeTest` | — | ⬜ |
| App resume refreshes subscription | — | `SubscriptionVMTest` | — | ⬜ |
| Back button navigation | — | `BackButtonTest` | — | ⬜ |
| 403 account_deleted → force logout | `test_auth_contracts` | `NetworkClientTest` | — | ⬜ |
| 409 birth conflict → merge dialog | `test_auth_contracts` | `BirthDataViewModelTest` | — | ⬜ |

**Legend:** ⬜ Not started · 🟡 In progress · ✅ Complete

---

## 9. Development Sequence

Write tests first. Implement to make them pass.

```
Week 1:   Contract tests (Layer 1) — runs against existing backend today
          All 6 contract test files written and passing

Week 2:   Android unit test shells written (all fail — no impl yet)
          AuthViewModelTest, NetworkClientTest, BirthDataModelTest

Week 3-4: Implement Auth + Network layer
          Goal: AuthViewModelTest + NetworkClientTest green

Week 5:   Implement Home screen
          Goal: HomeViewModelTest green, test_02_home E2E passes

Week 6-7: Implement Chat + SSE streaming (highest complexity)
          Goal: ChatViewModelTest green, test_03_chat E2E passes

Week 8:   Implement Compatibility
          Goal: CompatibilityViewModelTest + KutaTextBuilderTest green
          Goal: test_04_compatibility E2E passes

Week 9:   Implement Charts, History, Profile, Partners
          Goal: all unit tests green, test_05 through test_09 E2E pass

Week 10:  Run full guardrail suite (test_12 through test_23)
          Fix any guardrail failures (backend-side, no Android code changes needed)

Week 11:  Notifications + Billing + Settings
          Goal: FCMTokenTest, GoogleBillingTest, LocaleTest green

Week 12:  Android-specific tests
          Goal: BackButtonTest, ConfigurationChangeTest, DeepLinkTest green
          Goal: full parity matrix green
```

---

## 10. File Locations

```
android_app/
├── ANDROID_TEST_STRATEGY.md           ← this document
├── app/src/test/java/com/destinyai/astrology/
│   ├── viewmodels/
│   │   ├── AuthViewModelTest.kt
│   │   ├── HomeViewModelTest.kt
│   │   ├── ChatViewModelTest.kt
│   │   ├── CompatibilityViewModelTest.kt
│   │   ├── BirthDataViewModelTest.kt
│   │   ├── HistoryViewModelTest.kt
│   │   ├── PartnerViewModelTest.kt
│   │   ├── NotificationViewModelTest.kt
│   │   └── SubscriptionViewModelTest.kt
│   ├── services/
│   │   ├── NetworkClientTest.kt
│   │   ├── FCMTokenTest.kt
│   │   ├── QuotaManagerTest.kt
│   │   ├── GoogleBillingTest.kt
│   │   └── LocaleTest.kt
│   ├── models/
│   │   ├── BirthDataModelTest.kt
│   │   ├── KutaTextBuilderTest.kt
│   │   └── AffirmationBuilderTest.kt
│   └── android/
│       ├── BackButtonTest.kt
│       ├── ConfigurationChangeTest.kt
│       └── NotificationDeepLinkTest.kt
├── app/src/androidTest/java/com/destinyai/astrology/
│   └── DataManagerTest.kt             ← Room requires instrumented test
└── e2e/
    ├── conftest_android.py            ← Android driver config
    ├── helpers/
    │   ├── screens_android.py         ← UiAutomator2 adapter
    │   └── assertions.py              ← Shared with iOS, no changes
    └── test_01_onboarding.py          ← All 30 files ported from ios_app/e2e/
        test_02_home.py
        ... (all 30 files)

astrology_api/astroapi-v2/tests/contract/   ← New — shared between iOS + Android
    ├── test_auth_contracts.py
    ├── test_partner_contracts.py
    ├── test_quota_contracts.py
    ├── test_notification_contracts.py
    ├── test_subscription_contracts.py
    └── test_compatibility_contracts.py
```

---

*Document version: 1.0 — 2026-05-17*  
*Next review: when Layer 1 contract tests are written*
