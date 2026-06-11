# Android iOS Replica — Conversion Plan
**Last updated: 2026-05-28**
**Status: Phases 1–8 (Compatibility E2E) COMPLETE. Phase 9 (Play Billing) is the active blocker.**

---

## Completion Overview

| Area | iOS | Android | Gap |
|------|-----|---------|-----|
| Auth (Google, Guest, Upgrade) | ✅ | ✅ Full | None |
| Onboarding (13 languages, response style) | ✅ | ✅ Full | None |
| Home (quota, daily insight, suggested Q) | ✅ | ✅ Full | None |
| Chat (SSE streaming, thread history, Room) | ✅ | ✅ Full | conversation_id passed ✅ |
| Compatibility (SSE, multi-partner, Room history) | ✅ | ✅ Full | All 8 phases complete |
| Charts (North/South, Dasha, Transits) | ✅ | ✅ Full | None |
| History (chat + compat, search, pin) | ✅ | ✅ Full | None |
| Partners (CRUD, picker) | ✅ | ✅ Full | None |
| Profile / Birth Details | ✅ | ✅ Full | BirthDetailsViewModel stub (minor) |
| Settings (chart style, language, response style) | ✅ | ✅ Full | AstrologySettingsViewModel stub (minor) |
| Notifications (inbox, preferences, FCM) | ✅ | 80% | FCM MessagingService missing |
| Location Search (birth city autocomplete) | ✅ | 20% | No geocoding API wired |
| **Subscription / Play Billing** | ✅ StoreKit 2 | **5%** | **CRITICAL — no purchase flow** |
| Multi-Profile Switching | ✅ | 40% | UI only, no logic |
| Test suite (526 unit tests) | ✅ | ✅ 526/526 | All passing |

---

## PHASE 9 — Google Play Billing (HIGHEST PRIORITY)

**This is the only gap that blocks deployment to production.**

### iOS → Android translation

| iOS (StoreKit 2) | Android (Play Billing 7.x) |
|-----------------|---------------------------|
| `Product.products(for: ids)` | `BillingClient.queryProductDetailsAsync()` |
| `product.purchase()` | `BillingClient.launchBillingFlow()` |
| `Transaction.updates` listener | `PurchasesUpdatedListener` |
| `transaction.jwsRepresentation` | `purchase.purchaseToken` (sent to backend) |
| `AppStore.sync()` (restore) | `BillingClient.queryPurchasesAsync()` |
| `transaction.currentEntitlements` | `BillingClient.queryPurchasesAsync(SUBS)` |
| `/subscription/verify` with `signed_transaction` | `/subscription/verify` with `purchase_token` + `platform: "android"` |

### Product IDs (must match Play Console exactly)
```
com.daa.core.monthly    (Core monthly)
com.daa.core.yearly     (Core yearly)
com.daa.plus.monthly    (Plus monthly)
com.daa.plus.yearly     (Plus yearly)
```
These are the SAME product ID root format as iOS (`com.daa.*`) — confirm in Play Console.

### Files to create / modify

#### New: `data/billing/BillingManager.kt`
Singleton Hilt-injectable. Mirrors `SubscriptionManager.swift` 1:1:
```kotlin
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AstroApiService,
    private val prefs: UserPreferences,
) {
    // BillingClient with PurchasesUpdatedListener
    // queryProductDetails() — with 3-retry + exponential backoff
    // launchBillingFlow(activity, productDetails, offerToken)
    // verifyWithBackend(purchaseToken, productId, email)
    // reconcileEntitlements() — query active subs + push each to backend
    // resetForAccountSwitch()

    val products: StateFlow<List<ProductDetails>>
    val purchasedProductIds: StateFlow<Set<String>>
    val isLoading: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>
    val subscriptionConflict: StateFlow<SubscriptionConflict?>
}
```

Key differences from iOS:
- BillingClient requires an `Activity` reference for `launchBillingFlow` (pass via `startConnection`)
- No equivalent of `jwsRepresentation` — send `purchaseToken` (String) to backend
- Backend endpoint `/subscription/verify` already exists; it needs to accept `platform: "android"` and `purchase_token` instead of `signed_transaction`
- Pending purchases: `purchase.purchaseState == PurchaseState.PENDING` (same semantics as iOS `.pending`)
- Acknowledge purchases: `client.acknowledgePurchase()` must be called within 3 days or Google auto-refunds (equivalent of `transaction.finish()`)

#### Modify: `ui/subscription/SubscriptionViewModel.kt`
Add:
```kotlin
fun purchase(productDetails: ProductDetails, activity: Activity)
fun restorePurchases()
fun hasActiveSubscription: StateFlow<Boolean>
val activePlanId: StateFlow<String?>
val pendingUpgradePlanId: StateFlow<String?>
val subscriptionConflict: StateFlow<SubscriptionConflict?>
```

#### Modify: `ui/subscription/SubscriptionScreen.kt`
Add purchase button handlers, trial badge, restore button, conflict banner.
Currently shows plan cards with no purchase action.

#### Modify: `di/AppModule.kt`
Provide `BillingManager` as singleton.

#### Backend change required
Check `/subscription/verify` endpoint — it must accept:
```json
{
  "purchase_token": "...",
  "product_id": "com.daa.core.monthly",
  "user_email": "...",
  "platform": "android",
  "environment": "Production"
}
```
Check `astrology_api/astroapi-v2/app/core/api/subscription_router.py` verify endpoint.

#### New: `data/billing/SubscriptionConflict.kt`
```kotlin
data class SubscriptionConflict(val productId: String)
```

### TDD order for Phase 9
1. RED: `BillingManagerTest` — mock BillingClient, verify product loading, purchase flow, backend verify call
2. GREEN: Implement `BillingManager`
3. RED: `SubscriptionViewModelTest` — purchase/restore/conflict state transitions
4. GREEN: Wire ViewModel to BillingManager
5. Manual smoke test against Play Billing sandbox

---

## PHASE 10 — FCM Push Notifications

**Missing: `FirebaseMessagingService` subclass.**

### What exists
- `google-services.json` placeholder (real file downloaded from Firebase Console)
- `NotificationsViewModel`, `NotificationsScreen` — both complete
- FCM token stored in `UserPreferences` as `fcm_token`
- `registerDeviceToken` API endpoint called from `AuthViewModel` on sign-in

### What's missing
Create `services/DestinyFirebaseMessagingService.kt`:
```kotlin
@AndroidEntryPoint
class DestinyFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var prefs: UserPreferences
    @Inject lateinit var api: AstroApiService

    override fun onNewToken(token: String) {
        // Save token + re-register with backend
        lifecycleScope.launch {
            prefs.saveFcmToken(token)
            val email = prefs.getUserEmail() ?: return@launch
            api.registerDeviceToken(DeviceTokenRequest(email, token, "android", BuildConfig.VERSION_NAME))
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Show notification using NotificationManager
        // Deep-link routing: match iOS NotificationRouter notification types
        // Types: daily_prediction, transit_alert, life_alert, compatibility_ready, subscription_expiring
    }
}
```
Register in `AndroidManifest.xml`.

---

## PHASE 11 — Location Search (Birth City Autocomplete)

**Currently: UI state exists but no geocoding call.**

### iOS approach
iOS uses `LocationSearchService.swift` with Google Places SDK + Apple MapKit.

### Android approach (recommended: Google Places SDK for Android or Geocoder)

Option A (recommended): **Google Places Autocomplete API** (same as iOS)
- Add dependency: `com.google.android.libraries.places:places:3.3.0`
- Initialize in `Application` class with `Places.initialize(context, apiKey)`
- `PlacesClient.findAutocompletePredictions()` for city search
- Return `(city: String, lat: Double, lng: Double)`

Option B: **Backend-proxied geocoding** — call a backend endpoint that wraps Places API (no SDK key in app)

#### Files to create/modify
- New: `data/location/LocationSearchService.kt`
- Modify: `ui/birthdata/BirthDataViewModel.kt` — wire `searchLocation(query)` to service
- `UserPreferences.saveBirthLocation(city, lat, lng)` — already exists

---

## PHASE 12 — Multi-Profile Switching

**Currently: API endpoints defined, UI component in CompatibilityScreen, no switching logic.**

### iOS behavior
- `ProfileContextManager` tracks `activeProfileId`
- Each ViewModel reads `activeProfileId` to scope data
- Switch Profile sheet lists all profiles (own + partners marked `isSelf = true`)
- On switch: Home reloads, Chat threads reload scoped to new profileId

### Android gaps
- No `ProfileSwitcherScreen` or `ProfileSwitcherViewModel`
- `switchProfile` API not called anywhere
- `active_profile_email` preference exists but not used for scoping

### Files to create/modify
- New: `ui/profile/ProfileSwitcherViewModel.kt`
- New: `ui/profile/ProfileSwitcherScreen.kt`  
- Modify: `HomeViewModel` — reload on profile switch
- Modify: `ChatViewModel` — scope threads to active profileId
- `UserPreferences.getActiveProfileEmail()` / `saveActiveProfileEmail()` — add if missing

---

## PHASE 13 — AstrologySettings Persistence (Minor)

**Currently: `AstrologySettingsViewModel` has TODO comments. State is never saved.**

### Fix
- `AstrologySettingsViewModel.saveAyanamsa(ayanamsa: String)` → `prefs.saveAyanamsa()`
- `AstrologySettingsViewModel.saveHouseSystem(system: String)` → `prefs.saveHouseSystem()`
- Add `ayanamsa` and `house_system` keys to `UserPreferences` DataStore
- Pass ayanamsa + house_system in `PredictBirthDataDto` when making API calls (already has fields)

---

## PHASE 14 — BirthDetails Edit Screen (Minor)

**Currently: `BirthDetailsViewModel` is a stub with TODO comments.**

### Fix
- Load birth data from `prefs.getBirthProfile()` on init
- Save edited birth data via `api.saveProfile()` (endpoint already exists)
- Reload Home after save (birth data change invalidates all cached chart data)

---

## PHASE 15 — Home Screen Enhancements (iOS parity)

**Currently: HomeScreen shows quota + daily insight + suggested questions. Missing:**

### Missing iOS home features
1. **Life Areas section** — career/marriage/finance/health/spiritual cards with status (Good/Steady/Caution)
   - iOS calls `/vedic/api/astrodata/full` which includes life_areas in response
   - Android `ChartsViewModel` already calls `/vedic/api/chart-data/` — check if response includes life areas
2. **Yoga highlights** — Raja yoga, Dhana yoga cards
3. **Dasha widget** — current mahadasha + antardasha period on home
4. **Transit influences** — top 2-3 transits with brief description
5. **Dosha status** — Mangal/Kala Sarpa status chips

These all come from `UserChartService` in iOS which calls `/vedic/api/astrodata/full`. The Android `ChartsViewModel` fetches chart data — check if HomeViewModel can reuse it.

---

## Current Test State

```
Test run: ./gradlew :app:testReleaseUnitTest
Result: 526/526 passing, 0 failures
```

All existing tests pass. New phases must follow TDD: write failing test first, then implement.

---

## Deployment Readiness Checklist

- [x] API endpoints: all 27 defined and wired
- [x] SSE streaming: chat + compatibility both working
- [x] Room DB: all 4 entities, version 2, fallbackToDestructiveMigration
- [x] Hilt DI: all modules complete
- [x] BuildConfig: API key per flavor, no hardcoded URLs
- [x] Auth: Google + Guest + Upgrade flows
- [x] Compatibility: full E2E including history persistence
- [x] Chat: conversation_id threading, Gson-only JSON parsing
- [ ] **Play Billing: BLOCKING deployment** — no purchase flow
- [ ] FCM service: push notifications don't work yet
- [ ] Location search: birth city entry degraded (no autocomplete)
- [ ] google-services.json: real file needed (gitignored placeholder exists)

---

## Priority Order for Next Sessions

1. **Phase 9 — Play Billing** (UNBLOCKS production deployment)
2. **Phase 10 — FCM service** (push notifications)
3. **Phase 11 — Location search** (user experience)
4. **Phase 13+14 — Settings/BirthDetails stubs** (minor, 1-2 hours each)
5. **Phase 12 — Multi-Profile** (future feature, not blocking MVP)
6. **Phase 15 — Home enhancements** (nice to have for parity)

---

## Resume Instructions

To resume any phase:
1. Check `ANDROID_REPLICA_PLAN.md` for phase status
2. Read the relevant files listed under that phase
3. Follow TDD: write failing test in `app/src/test/`, run `./gradlew :app:testReleaseUnitTest`, confirm RED
4. Implement, confirm GREEN
5. Run full suite to confirm no regressions: `JAVA_HOME=/Users/i074917/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew :app:testReleaseUnitTest`

---

## Key File Paths Quick Reference

| Purpose | Path |
|---------|------|
| API service | `data/remote/AstroApiService.kt` |
| SSE chat impl | `data/repository/impl/ChatRepositoryImpl.kt` |
| SSE compat impl | `data/repository/impl/CompatibilityRepositoryImpl.kt` |
| Compatibility mapper | `data/remote/CompatibilityMapper.kt` |
| Room DB (all entities+DAOs) | `data/local/db/AppDatabase.kt` |
| Hilt modules | `di/AppModule.kt` |
| User preferences (DataStore) | `data/local/prefs/UserPreferences.kt` |
| Subscription ViewModel | `ui/subscription/SubscriptionViewModel.kt` |
| Subscription Screen | `ui/subscription/SubscriptionScreen.kt` |
| Build config (product IDs) | `app/build.gradle.kts` |
| Test runner command | `JAVA_HOME=.../temurin-21.jdk/Contents/Home ./gradlew :app:testReleaseUnitTest` |
| iOS SubscriptionManager ref | `ios_app/ios_app/Services/SubscriptionManager.swift` |
| iOS QuotaManager ref | `ios_app/ios_app/Services/QuotaManager.swift` |
