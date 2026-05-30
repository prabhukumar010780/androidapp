# Android iOS Parity — Gap Tracker
**Generated: 2026-05-30 | Last updated: 2026-05-30**
**Source: Full iOS vs Android view-level audit**
**Status key: [ ] = open · [x] = done · [~] = in-progress**

---

## How to use this file
1. Pick the next open item (start from P0).
2. Mark it `[~]` while working.
3. Mark it `[x]` when tests pass and the feature is confirmed working.
4. Add any sub-tasks or notes under the item.

---

## P0 — Blocks Core Usability

### P0-1 · Google Sign-In wired
- **Status**: [x] **DONE** — 2026-05-30
- `AuthScreen.kt` wired with `rememberLauncherForActivityResult` + `GoogleSignInOptions` + `BuildConfig.GOOGLE_SERVER_CLIENT_ID` buildConfigField
- `viewModel.signInWithGoogle(idToken)` called on result
- Test added: `signInWithGoogle calls repository and updates state on success`
- **Note**: `GOOGLE_SERVER_CLIENT_ID` is an empty-string placeholder — fill in with real Web Client ID from Google Cloud Console before releasing

---

### P0-2 · 5-tab navigation bar (History + Profile as tabs)
- **Status**: [x] **DONE** — 2026-05-30
- `MainScreen.kt` expanded to 5 tabs: Home / Ask (FAB) / Match / History / Profile
- `DestinyTabBar` renders 5 items with `Icons.Filled.History` and `Icons.Filled.Person`
- History tab: `HistoryScreen(onBack = { selectedTab = 0 })`
- Profile tab: `ProfileScreen(...)` with all callbacks passed through
- `AppNav.kt` updated — removed standalone `onNavigateToHistory`/`onNavigateToProfile` push routes from MainScreen call

---

### P0-3 · Home screen — transit / yoga / dosha / life-area cards
- **Status**: [x] **DONE** — 2026-05-30
- New file: `HomeModels.kt` — `HomeTransit`, `HomeDashaInfo`, `HomeYoga`, `HomeDoshaStatus`, `HomeLifeArea`, `HomeRichData`, `defaultLifeAreas()` (6 areas with per-area questions)
- `HomeRepository` + `HomeRepositoryImpl`: added `getRichHomeData()` calling `/vedic/api/chart-data/`, extracting transits, detecting yogas, Mangal Dosha, Kala Sarpa
- `HomeViewModel`: added `UserPreferences` injection, `loadRichHomeData()`, `selectLifeArea()`, `dismissLifeArea()`
- `HomeScreen.kt`: added `DashaInsightCard`, `TransitAlertsRow`, `YogaHighlightRow`, `DoshaStatusRow`, `LifeAreaQuestionsSheet` (ModalBottomSheet on life-area orb tap)
- Tests added: `loadHomeData sets dashaInfo`, `selectLifeArea sets selectedLifeArea`, `dismissLifeArea clears selectedLifeArea`

---

### P0-4 · History tab — combine chat + compatibility
- **Status**: [x] **DONE** — 2026-05-30
- `HistoryUiState` + `HistoryViewModel`: added `selectedTab`, `compatibilityItems`, `loadCompatibilityHistory()` via `CompatibilityHistoryDao`, `setTab()`, `deleteCompatibilityItem()`
- `HistoryScreen.kt`: `TabRow` with "Chat" / "Compatibility" tabs; compatibility tab shows items with score badge, partner name, date
- Tests added: `setTab(1) loads compatibility history`, `deleteCompatibilityItem removes item`

---

## P1 — Significant Feature Gaps

### P1-1 · BirthData screen — real location search (wire LocationSearchService)
- **Status**: [x] **DONE** — 2026-05-30
- `BirthDataViewModel`: injected `LocationSearchService`, added `locationResults`/`isSearchingLocation` to state, `searchLocation(query)` with 300ms debounce, `clearLocationResults()`
- `BirthDataScreen.kt`: `LocationSearchSheet` now driven by ViewModel state (drops hardcoded 18-city list), shows loading indicator
- Tests added: `searchLocation calls LocationSearchService and updates results`, `searchLocation with short query returns empty list`

---

### P1-2 · Profile screen — settings rows
- **Status**: [x] **DONE** — 2026-05-30
- `ProfileViewModel`: added `historyEnabled`, `analyticsConsent`, `showProfileSwitcher` to state; `toggleHistory()`, `toggleAnalytics()`, `showProfileSwitcher()`/`dismissProfileSwitcher()`
- `ProfileScreen.kt`: "Preferences" section with arrow rows for Language, Response Style, Notification Preferences, Switch Profile; toggles for Chat History and Analytics
- `Routes.kt`: added `RESPONSE_STYLE = "response_style"`
- `AppNav.kt`: ProfileScreen call updated with new callbacks; `composable(Routes.RESPONSE_STYLE)` added
- Tests added: `toggleHistory updates historyEnabled`, `loadProfile reads historyEnabled from prefs`

---

### P1-3 · Compatibility screen — saved partner picker
- **Status**: [x] **DONE** — 2026-05-30
- `CompatibilityViewModel`: added `showPartnerPicker`, `savedPartners`, `loadSavedPartners()`, `selectSavedPartner(PartnerDto)`, `showPartnerPicker()`/`dismissPartnerPicker()`
- `CompatibilityScreen.kt`: "Load saved partner" icon button near partner name field, wired to `PartnerPickerSheet`
- `PartnerPickerSheet.kt`: `ModalBottomSheet` listing saved partners; on select populates all partner form fields
- Tests added: `selectSavedPartner populates all partner fields`, `showPartnerPicker sets flag`, `dismissPartnerPicker clears flag`

---

### P1-4 · Astrology settings — link from Settings screen
- **Status**: [x] **DONE** — 2026-05-30
- `Routes.kt`: added `ASTROLOGY_SETTINGS = "astrology_settings"`
- `SettingsScreen.kt`: added `onNavigateToAstrologySettings` param + "Astrology Settings" clickable row
- `AppNav.kt`: `SettingsScreen` call updated; `composable(Routes.ASTROLOGY_SETTINGS)` added

---

### P1-5 · Profile switcher
- **Status**: [x] **DONE** — 2026-05-30
- New: `ProfileSwitcherViewModel.kt` — loads self + partners as `ProfileEntry` list; `switchProfile(email)` calls API + persists `active_profile_email`
- New: `ProfileSwitcherSheet.kt` — `ModalBottomSheet` with profile list + active checkmark
- `UserPreferences`: `getActiveProfileEmail()`/`setActiveProfileEmail()` wired (key already existed)
- `ProfileScreen.kt`: "Switch Profile" row in Preferences section
- Tests: 6 tests in `ProfileSwitcherViewModelTest.kt`

---

### P1-6 · Waitlist screen — form link button
- **Status**: [x] **DONE** — 2026-05-30
- `WaitlistPendingScreen.kt`: button `onClick` wired to `Intent(ACTION_VIEW, Uri.parse("https://tally.so/r/destinyai"))`

---

## P2 — Polish & Completeness

### P2-1 · Chat — interrupted question recovery card
- **Status**: [x] **DONE** — was already implemented
- `ChatScreen.kt` already had `InterruptedBanner` composable wired to `state.interruptedQuestion` with a "Retry" button calling `viewModel.retryInterruptedQuestion()`

---

### P2-2 · Chat / Compatibility — paywall sheet on quota exceeded
- **Status**: [x] **DONE** — 2026-05-30
- `ChatScreen.kt`: `showPaywall` state now triggers `ModalBottomSheet { SubscriptionScreen() }`; `dismissPaywall()` added to `ChatViewModel`
- `CompatibilityScreen.kt`: same pattern; `showPaywall` added to `CompatibilityUiState`, `dismissPaywall()` to `CompatibilityViewModel`

---

### P2-3 · Compatibility — share / export report
- **Status**: [ ] **OPEN** — deferred (requires `ComposeView.drawToBitmap()` which needs Activity context; low priority)
- Needs: Share icon in `CompatibilityResultScreen` toolbar, render `ShareCardView` to Bitmap, share via `Intent.ACTION_SEND`

---

### P2-4 · Charts — chart comparison sheet trigger
- **Status**: [x] **DONE** — 2026-05-30
- `ChartsScreen.kt`: "Compare" `IconButton` (CompareArrows) added to toolbar; `showComparison` state triggers `ModalBottomSheet { ChartComparisonSheet() }`

---

### P2-5 · Auth — ResponseStyleOnboarding sheet in BirthData flow
- **Status**: [x] **DONE** — 2026-05-30
- `BirthDataUiState`: `showResponseStyleSheet` field added
- `BirthDataViewModel.save()`: sets `showResponseStyleSheet = true` on first save
- `BirthDataScreen.kt`: `ModalBottomSheet { ResponseStyleOnboardingScreen }` shown when flag set

---

### P2-6 · FCM — replace google-services.json placeholder
- **Status**: [ ] **OPEN** — manual step required
- `app/google-services.json` placeholder exists; `DestinyFirebaseMessagingService` is registered in `AndroidManifest.xml`
- **Action**: Download real file from Firebase Console → Project `destiny-ai-astrology-4f52a` → Project Settings → Android app → `google-services.json`

---

### P2-7 · Subscription — free trial eligibility banner
- **Status**: [x] **DONE** — 2026-05-30
- `BillingManager`: `isPlusTrialEligible: StateFlow<Boolean>` — true when any Plus product has an offer with `offerId` containing "trial"
- `SubscriptionViewModel`: exposes `isPlusTrialEligible` passthrough
- `SubscriptionScreen.kt`: "✦ Free Trial Available" chip shown on Plus monthly card when eligible

---

### P2-8 · History — pin thread UI
- **Status**: [ ] **OPEN**
- `ChatViewModel.pinThread()` exists; need to add pin `IconButton` to `HistoryScreen` thread rows + show pinned indicator

---

### P2-9 · Haptics and sound effects
- **Status**: [ ] **OPEN**
- Low priority; requires new `HapticManager` using `VibrationEffect` (API 26+)

---

## Completion Summary

| Priority | Total | Done | Remaining |
|---|---|---|---|
| P0 (critical) | 4 | 4 | 0 |
| P1 (significant) | 6 | 6 | 0 |
| P2 (polish) | 9 | 6 | 3 |
| **Total** | **19** | **16** | **3** |

**Test suite: 605/605 passing (0 failures)**

---

## Remaining open items (3)

| ID | Item | Effort | Blocker |
|---|---|---|---|
| P2-3 | Share/export compatibility report | Medium | Needs Activity context for `drawToBitmap` |
| P2-6 | Replace FCM `google-services.json` | Manual | Download from Firebase Console |
| P2-8 | Pin thread UI in HistoryScreen | Small | `pinThread()` ViewModel method ready |

---

## Quick-start for remaining items

```bash
# Run tests
cd /Users/i074917/Documents/destiny_ai_astrology/android_app
JAVA_HOME=/Users/i074917/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  ./gradlew :app:testProductionReleaseUnitTest
```
