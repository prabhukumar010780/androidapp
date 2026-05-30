# Android iOS Parity — Gap Tracker
**Generated: 2026-05-30**
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
- **What's missing**: `AuthScreen.kt` has empty `onClick = { /* Google sign-in */ }` stubs. No Google One Tap / Credential Manager integration.
- **iOS reference**: `Auth/AuthView.swift` → `AuthViewModel.signInWithGoogle(idToken:)`
- **Android files to touch**: `ui/auth/AuthScreen.kt`, `ui/auth/AuthViewModel.kt`, `di/AppModule.kt`
- **Backend endpoint**: `POST /subscription/register` with `GoogleSignInRequest`
- **Steps**:
  - [ ] Add `credentials-play-services-auth` dependency
  - [ ] Implement `GoogleSignInHelper` (Credential Manager One Tap)
  - [ ] Wire `onClick` in AuthScreen → ViewModel → `AuthRepository.signInWithGoogle()`
  - [ ] Write `AuthViewModelTest` cases for success + failure
- **Status**: [ ]

---

### P0-2 · 5-tab navigation bar (History + Profile as tabs)
- **What's missing**: `MainScreen.kt` has only 3 tabs (Home / Chat / Compatibility). iOS has 5 tabs — History and Profile are tab-level destinations. On Android they are buried push routes.
- **iOS reference**: `MainTabView.swift` — custom `PremiumTabBar` with 5 items
- **Android files to touch**: `ui/main/MainScreen.kt`, `ui/nav/AppNav.kt`, `ui/nav/Routes.kt`
- **Steps**:
  - [ ] Add History and Profile as tab items in `MainScreen`
  - [ ] Update `DestinyTabBar` composable to render 5 tabs
  - [ ] Move History/Profile from push routes to tab root destinations in `AppNav`
  - [ ] Verify back-stack behaviour (tab roots should clear back stack on re-select)
  - [ ] Guest sign-in gate: show `GuestSignInPromptScreen` if guest taps History/Profile tabs
- **Status**: [ ]

---

### P0-3 · Home screen — transit / yoga / dosha / life-area cards
- **What's missing**: `HomeScreen.kt` shows only quota + suggestions + daily insight. iOS `HomeView` shows transit alerts, dasha insight card, yoga highlight cards, dosha status chips (Mangal / Kalsarpa), and life-area question sheets.
- **iOS reference**: `Home/HomeView.swift` + `Home/Components/` (TransitAlertCard, DashaInsightCard, YogaHighlightCard, DoshaStatusSection, TransitInfluenceCard)
- **Backend endpoint**: `POST /vedic/api/astrodata/full` (already used by `ChartsViewModel`)
- **Android files to touch**: `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`
- **Sub-tasks**:
  - [ ] **HomeViewModel**: add `loadRichHomeData()` calling `/vedic/api/astrodata/full`, parse `life_areas`, `yogas`, `doshas`, `transits` fields into new state fields
  - [ ] **TransitAlertCard composable**: planet icon + timing text + influence indicator
  - [ ] **DashaInsightCard composable**: current mahadasha/antardasha period + lord
  - [ ] **YogaHighlightCard composable** + `YogaDetailPopup` bottom sheet
  - [ ] **DoshaStatusSection composable**: Mangal + Kalsarpa severity chips
  - [ ] **LifeAreaQuestionsSheet**: bottom sheet showing domain-specific suggested questions when a life-area orb is tapped
  - [ ] **Profile switcher** button in home header (see P1-5 for full ProfileSwitcher)
  - [ ] Add `HomeViewModelTest` cases for each new data source
- **Status**: [ ]

---

### P0-4 · History tab — combine chat + compatibility history
- **What's missing**: `HistoryScreen.kt` shows ONLY chat threads. iOS `HistoryView` shows both chat and compatibility history in one screen with a segmented control.
- **iOS reference**: `History/HistoryView.swift`
- **Android files to touch**: `ui/history/HistoryScreen.kt`, `ui/history/HistoryViewModel.kt`
- **Steps**:
  - [ ] Add `selectedTab` (Chat / Compatibility) segmented control to `HistoryScreen`
  - [ ] `HistoryViewModel`: add `loadCompatibilityHistory()` reading from Room `CompatibilityHistoryDao`
  - [ ] Render compatibility thread cards (score badge, partner name, date)
  - [ ] Tapping a compatibility thread navigates to `CompatibilityResultScreen` with saved data
  - [ ] Delete compatibility thread (Room delete + API if applicable)
  - [ ] Add `HistoryViewModelTest` cases for combined history
- **Status**: [ ]

---

## P1 — Significant Feature Gaps

### P1-1 · BirthData screen — real location search (wire LocationSearchService)
- **What's missing**: `BirthDataScreen.kt` uses a static list of 18 hardcoded cities. `LocationSearchService` was created in Phase 11 but is NOT wired into `BirthDataViewModel`.
- **iOS reference**: `Auth/BirthDataView.swift` + `Services/LocationSearchService.swift`
- **Android files to touch**: `ui/auth/BirthDataViewModel.kt`, `ui/auth/BirthDataScreen.kt`, `data/location/LocationSearchService.kt`
- **Steps**:
  - [ ] Inject `LocationSearchService` into `BirthDataViewModel`
  - [ ] Replace hardcoded city filter with `searchLocation(query)` coroutine call
  - [ ] Debounce input (300ms) before calling API
  - [ ] Show loading indicator during search
  - [ ] Replace `LocationResult` display to use `displayName` field
  - [ ] Check backend has `/api/v2/location/search` endpoint — if not, implement it
  - [ ] Add `BirthDataViewModelTest` for location search success + empty + error
- **Status**: [ ]

---

### P1-2 · Profile screen — settings rows (language, style, chart, notifs, history, analytics, consent)
- **What's missing**: Android `ProfileScreen.kt` only has a single "Settings" button. iOS `ProfileView` has individual rows for: Language, Response Style, Chart Style, Notification Preferences, History opt-in toggle, Analytics consent toggle.
- **iOS reference**: `Profile/ProfileView.swift` (8 individual settings rows)
- **Android files to touch**: `ui/profile/ProfileScreen.kt`, `ui/profile/ProfileViewModel.kt`
- **Steps**:
  - [ ] Add **Language row** → navigates to `LanguageSelectionScreen` (or inline sheet)
  - [ ] Add **Response Style row** → navigates to `ResponseStyleOnboardingScreen`
  - [ ] Add **Chart Style row** → inline `ChartStylePickerSheet` bottom sheet
  - [ ] Add **Notification Preferences row** → navigates to `NotificationPreferencesScreen`
  - [ ] Add **History enabled toggle** → calls `prefs.setHistoryEnabled()`
  - [ ] Add **Analytics consent toggle** → calls backend consent endpoint
  - [ ] Verify guest state shows upgrade CTA instead of premium rows
  - [ ] Add `ProfileViewModelTest` cases for each toggle
- **Status**: [ ]

---

### P1-3 · Compatibility screen — saved partner picker (PartnerPickerSheet)
- **What's missing**: No UI to select a previously saved partner into the compatibility form. iOS has `PartnerPickerSheet.swift`.
- **iOS reference**: `Compatibility/CompatibilityView.swift` → `PartnerPickerSheet` modal
- **Android files to touch**: `ui/compatibility/CompatibilityScreen.kt`, `ui/compatibility/CompatibilityViewModel.kt`
- **Steps**:
  - [ ] Create `PartnerPickerSheet` composable listing saved partners from `PartnersViewModel`/API
  - [ ] Add "Load saved partner" button/icon to the partner form section
  - [ ] On selection, populate all partner fields (name, DOB, time, city, lat, lng, gender)
  - [ ] Add `CompatibilityViewModelTest` for `loadSavedPartner(partnerId)`
- **Status**: [ ]

---

### P1-4 · Astrology settings — link from Settings screen (nav gap)
- **What's missing**: `AstrologySettingsScreen.kt` exists and is now wired to DataStore, but there is no nav action leading to it from `SettingsScreen.kt`. It is unreachable.
- **iOS reference**: `Profile/ProfileView.swift` → `AstrologySettingsSheet`
- **Android files to touch**: `ui/settings/SettingsScreen.kt`, `ui/nav/AppNav.kt`, `ui/nav/Routes.kt`
- **Steps**:
  - [ ] Add "Astrology Settings" row to `SettingsScreen`
  - [ ] Register `Routes.ASTROLOGY_SETTINGS` in `AppNav`
  - [ ] Pass `onNavigateToAstrologySettings` callback through nav graph
  - [ ] Verify `AstrologySettingsScreen` load/save cycle works end-to-end
- **Status**: [ ]

---

### P1-5 · Profile switcher (switch between saved birth charts)
- **What's missing**: iOS has `ProfileSwitcherSheet` accessible from both `HomeView` header and `ProfileView`. Android has no profile switching UI.
- **iOS reference**: `Profile/ProfileSwitcherSheet.swift`, `ProfileContextManager`
- **Android files to touch**: `ui/profile/ProfileSwitcherViewModel.kt` (new), `ui/profile/ProfileSwitcherScreen.kt` (new), `ui/home/HomeScreen.kt`, `ui/profile/ProfileScreen.kt`
- **Steps**:
  - [ ] Create `ProfileSwitcherViewModel` — calls `api.getActiveProfile()` + `api.switchProfile()`
  - [ ] Create `ProfileSwitcherSheet` bottom sheet composable
  - [ ] Add profile switcher button to home screen header
  - [ ] Add "Switch Profile" row to ProfileScreen
  - [ ] On switch: reload `HomeViewModel` + `ChatViewModel` scoped to new profile
  - [ ] Persist `active_profile_email` in `UserPreferences` (key already exists)
  - [ ] Add `ProfileSwitcherViewModelTest`
- **Status**: [ ]

---

### P1-6 · Waitlist screen — form link button
- **What's missing**: `WaitlistPendingScreen.kt` has a "Fill out this form" button with empty `onClick`.
- **iOS reference**: `Waitlist/WaitlistPendingView.swift` opens a Tally form URL in SafariView
- **Android files to touch**: `ui/auth/WaitlistPendingScreen.kt`
- **Steps**:
  - [ ] Add Tally form URL constant (match iOS URL)
  - [ ] Wire `onClick` → `Intent(Intent.ACTION_VIEW, Uri.parse(tallyUrl))`
  - [ ] Fallback: show snackbar if no browser found
- **Status**: [ ]

---

## P2 — Polish & Completeness

### P2-1 · Chat — interrupted question recovery card
- **What's missing**: iOS `ChatView` shows a recovery card when the last question was interrupted mid-stream (network drop). Android `interruptedQuestion` state exists in `ChatViewModel` but the recovery card UI is not confirmed in `ChatScreen.kt`.
- **iOS reference**: `Chat/ChatView.swift` — recovery card with retry button
- **Android files to touch**: `ui/chat/ChatScreen.kt`
- **Steps**:
  - [ ] Read `ChatScreen.kt` fully to confirm if recovery card is rendered
  - [ ] If missing: add `RecoveryCard` composable — shows last question + "Try again" button
  - [ ] Wire to `viewModel.retryInterruptedQuestion()`
- **Status**: [ ]

---

### P2-2 · Chat / Compatibility — paywall sheet on quota exceeded
- **What's missing**: `showPaywall` flag exists in `ChatViewModel` and `CompatibilityViewModel` but it's not confirmed whether tapping triggers a full `SubscriptionScreen` bottom sheet modal.
- **iOS reference**: `Chat/ChatView.swift` → `SubscriptionView` sheet presentation
- **Android files to touch**: `ui/chat/ChatScreen.kt`, `ui/compatibility/CompatibilityScreen.kt`
- **Steps**:
  - [ ] Confirm `showPaywall` in `ChatScreen` triggers a `ModalBottomSheet { SubscriptionScreen() }`
  - [ ] Same confirmation for `CompatibilityScreen`
  - [ ] If missing: add bottom sheet trigger on `showPaywall = true`
- **Status**: [ ]

---

### P2-3 · Compatibility — share / export report
- **What's missing**: `ShareCardView.kt` composable exists but iOS `ReportShareService` generates a shareable image/PDF. No share action wired on Android.
- **iOS reference**: `Compatibility/Report/ShareCardView.swift` + `Services/ReportShareService.swift`
- **Android files to touch**: `ui/compatibility/ShareCardView.kt`, `ui/compatibility/CompatibilityResultScreen.kt`
- **Steps**:
  - [ ] Add Share icon button to `CompatibilityResultScreen` toolbar
  - [ ] Render `ShareCardView` to a `Bitmap` using `ComposeView.drawToBitmap()`
  - [ ] Share via `Intent.ACTION_SEND` with image MIME type
  - [ ] Add to `CompatibilityViewModelTest`
- **Status**: [ ]

---

### P2-4 · Charts — chart comparison sheet trigger
- **What's missing**: `ChartComparisonSheet.kt` exists but there is no confirmed nav action or button that opens it.
- **iOS reference**: `Charts/ChartComparisonSheet.swift` — opened from charts toolbar
- **Android files to touch**: `ui/charts/ChartsScreen.kt`
- **Steps**:
  - [ ] Add "Compare" icon button to `ChartsScreen` toolbar
  - [ ] Wire to a `showComparisonSheet` boolean state → `ModalBottomSheet { ChartComparisonSheet() }`
- **Status**: [ ]

---

### P2-5 · Auth — ResponseStyleOnboarding sheet in BirthData flow
- **What's missing**: iOS triggers `ResponseStyleOnboardingView` as a sheet from `BirthDataView` after birth data is saved. Android `ResponseStyleOnboardingScreen` exists but is not triggered from the BirthData flow.
- **iOS reference**: `Auth/BirthDataView.swift` — `showResponseStyleOnboarding` sheet trigger post-save
- **Android files to touch**: `ui/auth/BirthDataScreen.kt`, `ui/auth/BirthDataViewModel.kt`
- **Steps**:
  - [ ] Add `showResponseStyleOnboarding: Boolean` to `BirthDataUiState`
  - [ ] Set to `true` after successful `save()` call (first-time onboarding only)
  - [ ] Trigger `ResponseStyleOnboardingScreen` as `ModalBottomSheet` in `BirthDataScreen`
- **Status**: [ ]

---

### P2-6 · FCM — replace google-services.json placeholder
- **What's missing**: `app/google-services.json` is a gitignored placeholder. FCM will not work until the real file from Firebase Console is in place.
- **Firebase project**: `destiny-ai-astrology-4f52a`
- **Steps**:
  - [ ] Download real `google-services.json` from Firebase Console → Project `destiny-ai-astrology-4f52a` → Project Settings → Android app
  - [ ] Place at `app/google-services.json` (gitignored — do not commit)
  - [ ] Verify FCM token generation on device
  - [ ] Confirm `DestinyFirebaseMessagingService.onNewToken()` registers with backend
- **Status**: [ ]

---

### P2-7 · Subscription — free trial eligibility banner
- **What's missing**: iOS `SubscriptionView` checks `SubscriptionManager.isPlusTrialEligible` and shows a "Free Trial Available" banner on the Plus plan card. Android shows "Best Value" but does not check Play Store offer tokens for trial eligibility.
- **iOS reference**: `Subscription/SubscriptionView.swift` — `isPlusTrialEligible` banner
- **Android files to touch**: `ui/subscription/SubscriptionScreen.kt`, `data/billing/BillingManager.kt`
- **Steps**:
  - [ ] In `BillingManager.queryProductDetails()`, check for offer tokens with `offerId = "free-trial"`
  - [ ] Expose `isPlusTrialEligible: StateFlow<Boolean>` from `BillingManager`
  - [ ] In `SubscriptionScreen`, show "Free Trial Available" banner on Plus monthly card when eligible
- **Status**: [ ]

---

### P2-8 · History — pin thread UI in HistoryScreen
- **What's missing**: `ChatViewModel.pinThread()` is implemented but the pin button/swipe action in `HistoryScreen.kt` is not confirmed.
- **iOS reference**: `History/HistoryView.swift` — swipe actions with pin
- **Android files to touch**: `ui/history/HistoryScreen.kt`
- **Steps**:
  - [ ] Confirm whether pin icon is rendered in `HistoryScreen` thread rows
  - [ ] If missing: add pin `IconButton` to each thread item row
  - [ ] Wire to `historyViewModel.pinThread(threadId)`
  - [ ] Show pinned indicator (star/pin icon) on pinned threads
- **Status**: [ ]

---

### P2-9 · Haptics and sound effects
- **What's missing**: iOS uses `UIImpactFeedbackGenerator` and custom sound playback on send, receive, auth, and transitions. No equivalent in Android.
- **iOS reference**: `Services/SoundManager.swift` (if exists), haptic calls throughout views
- **Android files to touch**: New `services/HapticManager.kt`
- **Steps**:
  - [ ] Create `HapticManager` using `VibrationEffect` (API 26+)
  - [ ] Add light tap on: button presses, message send, tab switch
  - [ ] Add medium haptic on: sign-in success, purchase complete
  - [ ] (Optional) Add subtle sound effects matching iOS
- **Status**: [ ]

---

## Completion Summary

| Priority | Total | Done | Remaining |
|---|---|---|---|
| P0 (critical) | 4 | 0 | 4 |
| P1 (significant) | 6 | 0 | 6 |
| P2 (polish) | 9 | 0 | 9 |
| **Total** | **19** | **0** | **19** |

---

## Quick-start for next session

```
1. Open this file
2. Pick the lowest-numbered open [ ] item
3. Read the iOS reference file listed
4. Read the Android files listed
5. Follow TDD: write failing test → implement → confirm green
6. Mark [x] and update the summary table
7. Run: JAVA_HOME=.../temurin-21.jdk/Contents/Home ./gradlew :app:testProductionReleaseUnitTest
```
