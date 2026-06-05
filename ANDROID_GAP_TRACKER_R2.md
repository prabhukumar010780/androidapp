# Android iOS Parity — Round 2 Gap Tracker
**Generated: 2026-05-30 (after first 19-gap pass closed)**
**Source: Deep file-by-file audit of `ios_app/Views/` subfolders against `android_app/app/src/main/java/com/destinyai/astrology/ui/`**
**Status key: [ ] = open · [x] = done · [~] = in-progress**

---

## Scope

This document captures the **second-pass** gaps found by reading every iOS view file (73 files across 14 subfolders) and comparing to the Android implementation. All Round-1 P0/P1/P2 items from `ANDROID_GAP_TRACKER.md` are confirmed done. These are the remaining polish/feature gaps not surfaced in the first audit.

**Files covered**: every file under `ios_app/ios_app/Views/` including `Auth/`, `Home/`, `Home/Components/`, `Charts/`, `Chat/`, `Compatibility/`, `Compatibility/Components/`, `Compatibility/Sheets/`, `Compatibility/Report/`, `History/`, `Notifications/`, `Onboarding/`, `Partners/`, `Profile/`, `Settings/`, `Splash/`, `Subscription/`, `Waitlist/`, `Components/`, plus `AppRootView.swift` and `MainTabView.swift`.

---

## Round-2 Gaps — by iOS View

### Home subfolder

#### `Home/HomeView.swift`
- [x] **R2-H1** Animated entrance (contentOpacity + headerOffset) — Android shows content immediately
- [x] **R2-H2** Profile-switcher button in home header (avatar with initials, gold circle) — only plain greeting today
- [x] **R2-H3** Notification bell badge with unread count (`99+` cap) — bell shows but no count overlay
- [x] **R2-H4** Pull-to-refresh with "syncing cosmic data" feedback
- [x] **R2-H5** Offline banner overlay when network down
- [x] **R2-H6** Quick-question grid (2x2 premium card layout) — currently flat list
- [x] **R2-H7** Story-orb horizontal scroll with "cropped hint" peek of next item

#### `Home/Components/StoryOrbView.swift`
- [x] **R2-H8** Pulsing glow on outer ring (animated radial gradient)
- [x] **R2-H9** Specular highlight (glass refraction) at fixed 0.30/0.28 position
- [x] **R2-H10** Status dot with dark border overlay (2.5dp)
- [x] **R2-H11** Press-feedback spring (scale 0.88) — currently no press anim

#### `Home/Components/DashaProgressWidget.swift`
- [x] **R2-H12** "Next Up" section showing upcoming antardasha after current
- [x] **R2-H13** Progress ring with percentage overlay (date-span calc)
- [x] **R2-H14** "Until <date>" formatted end label
- [x] **R2-H15** Dasha card shadow/elevation styling

#### `Home/Components/TransitInfluenceCard.swift`
- [x] **R2-H16** Planet glow icon (44x44 radial gradient circle) — currently simple dot
- [x] **R2-H17** Dedicated `TransitOrbView` for horizontal scroll display
- [x] **R2-H18** Premium planet image assets (planet_<name>) — Android uses system icons

#### `Home/Components/DashaInsightCard.swift`
- [x] **R2-H19** Quality-badge color logic (good=green / steady=gold / caution=orange)
- [x] **R2-H20** Localized planet-name splitting in period names (e.g. "Saturn-Mercury")
- [x] **R2-H21** Theme label with theatremasks icon

#### `Home/Components/TransitAlertCard.swift`
- [x] **R2-H22** Filter to show only major-planet transits (Saturn, Jupiter, Rahu, Ketu, Mars)
- [x] **R2-H23** "Cosmic shifts" header with exclamation icon

#### `Home/Components/YogaHighlightCard.swift`
- [x] **R2-H24** Filter tabs with right-fade gradient hint (chevron.right)
- [x] **R2-H25** Status badge color (yoga=green, dosha=red) — currently neutral
- [x] **R2-H26** Per-card accessibilityIdentifier

#### `Home/Components/DoshaStatusSection.swift`
- [x] **R2-H27** Premium dosha card styling with gradient backgrounds + colored borders

#### `Home/LifeAreaBriefPopup.swift`
- [x] **R2-H28** Brief popup variant before full questions sheet — currently goes straight to sheet

#### `Home/LifeAreaQuestionsSheet.swift`
- [x] **R2-H29** Staggered question-card cascade animation (`.delay(index * 0.08)`)
- [x] **R2-H30** Custom "Ask any question" input field with arrow.up.circle.fill submit

#### `Home/YogaDetailPopup.swift`
- [x] **R2-H31** Status icons (checkmark.seal.fill / minus.circle / xmark.circle) per yoga state
- [x] **R2-H32** Cosmic radial-gradient background glow inside popup
- [x] **R2-H33** Localized "exception keys" rendering for cancellation reasons

---

### Auth subfolder

#### `Auth/AuthView.swift`
- [x] **R2-A1** Sound-toggle button in top-right (gated by `AppTheme.Features.showSoundToggle`)
- [x] **R2-A2** 3D tilt effect on logo (`.tilt3D(intensity: 10)`)
- [x] **R2-A3** Custom press scale spring (ScaleButtonStyle 0.96)

#### `Auth/BirthDataView.swift`
- [x] **R2-A4** Sound-toggle button (top right)
- [x] **R2-A5** Analytics-consent checkbox (US default off, non-US default on)
- [x] **R2-A6** "Backend data refreshed" banner when prefs flag set
- [x] **R2-A7** Backend conflict handling: `ArchivedGuestError`, `RegisteredUserConflictError`, `AccountDeletedError` — Android catches generic exception, no typed errors
- [x] **R2-A8** ShimmerButton animation for submit (currently static gold button)

#### `Auth/GuestSignInPromptView.swift`
- [x] **R2-A9** Suppress error toast when error is user-cancelled (`isCancellationError()` check)
- [x] **R2-A10** Haptic feedback on success — Android has no haptics anywhere
- [x] **R2-A11** Provider-specific button filtering (`provider == "apple"` etc.)

---

### Profile subfolder

#### `Profile/ProfileView.swift`
- [x] **R2-P1** Avatar with premium gradient fill (70x70 circle) — currently solid color initials
- [x] **R2-P2** Active-chart row with "Viewing: <birth chart>" formatted display
- [x] **R2-P3** Plan-name badge for free users (`quotaManager.planDisplayName`)
- [x] **R2-P4** Pull-to-refresh that calls `subscriptionManager.reconcileEntitlementsWithBackend()` + `quotaManager.syncStatus(force=true)`
- [x] **R2-P5** Pending-upgrade card (e.g. "Upgrading to Plus on Feb 15, 2026") for paid users
- [x] **R2-P6** "Manage subscription" button → opens `play.google.com/store/account/subscriptions` (Android equivalent of iOS App Store deep link)
- [x] **R2-P7** History section: clear-history button with deleted-thread count toast
- [x] **R2-P8** FAQ Help screen with 14 expandable Q&A items
- [x] **R2-P9** Notification permission flow (`requestNotificationPermission()`, settings deep link)
- [x] **R2-P10** Analytics-consent toggle backed by `ProfileService.updateAnalyticsConsent()` — Android currently only flips a local flag
- [x] **R2-P11** Support section: contact-us mailto, privacy-policy + terms-of-service web links
- [x] **R2-P12** Partner-manager link (currently buried)

#### `Profile/BirthDetailsView.swift`
- [x] **R2-P13** Read-only-section header "BIRTH DATA" + lock icon per row
- [x] **R2-P14** "Editable" section header for name/gender block
- [x] **R2-P15** Save button enabled only when `hasChanges` — currently always enabled
- [x] **R2-P16** Support-info block ("Need to update birth data? Contact support")
- [x] **R2-P17** Pre-filled email template with current birth details

#### `Profile/ProfileSwitcherSheet.swift`
- [x] **R2-P18** Dedicated "active profile" card at top with large 60x60 gradient avatar
- [x] **R2-P19** "you_label" badge on self profile
- [x] **R2-P20** Empty state if no other profiles
- [x] **R2-P21** "Manage Birth Charts" link at bottom → PartnersScreen
- [x] **R2-P22** Quota-check before switching (`canAddProfile()` upgrade prompt)

#### `Profile/DeleteAccountSheet.swift`
- [x] **R2-P23** Standalone delete-account sheet with TYPE "DELETE" confirmation field — Android uses simple AlertDialog
- [x] **R2-P24** 3 warning bullets with icons
- [x] **R2-P25** Active-subscription warning badge before delete

---

### Settings subfolder

#### `Settings/AstrologySettingsSheet.swift`
- [x] **R2-S1** Add Morinus, Alcabitus, Porphyrius house systems (currently 6, iOS has 9)
- [x] **R2-S2** Section footer descriptions for each setting

#### `Settings/ChartStylePickerSheet.swift`
- [x] **R2-S3** Dedicated chart-style picker sheet (Android currently uses inline FilterChip in SettingsScreen)
- [x] **R2-S4** Per-option subtitle text ("north_indian_desc" / "south_indian_desc")

#### `Settings/LanguageSettingsSheet.swift`
- [x] **R2-S5** Dedicated language sheet from Profile/Settings (Android navigates back to onboarding LanguageSelectionScreen instead of presenting a sheet)
- [x] **R2-S6** Language change posts in-app notification triggering live re-localisation — Android requires app restart

#### `Settings/NotificationPreferencesSheet.swift`
- [x] **R2-S7** 3 channel toggles: push / email / in-app — Android currently has only 3 category toggles (daily / transit / compatibility)
- [x] **R2-S8** Permission-status check + "Enable notifications" deep link to system settings when denied
- [x] **R2-S9** **User-defined custom alert items** (max 5): text + frequency picker (daily/weekly/monthly) — entire sub-feature missing
- [x] **R2-S10** Add/Edit alert sheet with character counter (200 max)
- [x] **R2-S11** Built-in suggestion list (4 pre-written suggestions, filtered against existing)
- [x] **R2-S12** Delete confirmation alert with item-text preview
- [x] **R2-S13** "Saved" success toast

---

### Charts subfolder

#### `Charts/PlanetaryPositionsSheet.swift`
- [x] **R2-C1** Standalone "Planetary positions" sheet (currently inline in `ChartsScreen`)
- [x] **R2-C2** Chart-style toggle inside that sheet

#### `Charts/ChartComparisonSheet.swift`
- [x] **R2-C3** D1 / D9 tab selector inside the sheet
- [x] **R2-C4** 3x3 planet-detail grid below the chart
- [x] **R2-C5** Per-person chart wrapper showing Ascendant sign

#### `Charts/DashaView.swift`
- [x] **R2-C6** Localised planet-name lookup in dasha rows (currently raw codes)

#### `Charts/TransitsView.swift`
- [x] **R2-C7** Monospace date column (Menlo)
- [x] **R2-C8** Sign localisation with "sign_<name>" keys
- [x] **R2-C9** Collapsible per-planet sections with nested events

#### `Charts/PlanetDetailCard.swift`
- [x] **R2-C10** Per-planet color mapping (Moon silver, Sun gold, etc.) — currently uniform tint

---

### Compatibility subfolder

#### `Compatibility/CompatibilityView.swift`
- [x] **R2-CM1** Match-input header with explicit History + Reset buttons
- [x] **R2-CM2** Partner tab strip with checkmark indicator per completed partner
- [x] **R2-CM3** "+" button to add up to 3 partners with crown badge for non-Plus
- [x] **R2-CM4** Birth-time-unknown checkbox + warning text in compatibility form
- [x] **R2-CM5** "Save birth chart" checkbox (hidden for free plan, disabled when partner already from saved charts)
- [x] **R2-CM6** Duplicate-birth-chart alert (suppressed when partner from saved)
- [x] **R2-CM7** Shimmer button for "Compare all" with dynamic count label

#### `Compatibility/CompatibilityResultView.swift`
- [x] **R2-CM8** OrbitTooltipView floating tooltips above scroll for kuta details
- [x] **R2-CM9** "Ask Destiny" follow-up sheet with `initialPrompt` + `initialFollowUpSuggestions`
- [x] **R2-CM10** RecommendationBanner with affirmation text
- [x] **R2-CM11** Kuta tooltip overlay rendered at zIndex 200

#### `Compatibility/Report/ShareCardView.swift`
- [x] **R2-CM12** Render rich shareable image (logo + score circle + corner ornaments) — Android currently shares text-only
- [x] **R2-CM13** `drawToBitmap()` of compose view → `Intent.ACTION_SEND` with `image/png`

#### `Compatibility/CompatibilityHistorySheet.swift`
- [x] **R2-CM14** Group history rows for multi-partner comparisons (person.3.fill icon + count overlay)
- [x] **R2-CM15** Best-score star indicator per group
- [x] **R2-CM16** Swipe / context-menu pin-unpin actions on compatibility items
- [x] **R2-CM17** User-question-count bubble icon per item
- [x] **R2-CM18** Search bar in compatibility tab

---

### Components / theme parity

#### `Components/CelestialOrbView.swift`
- [x] **R2-X1** Reusable celestial-orb composable for splash + onboarding parity

#### `Components/CosmicBackgroundView.swift`
- [x] **R2-X2** Animated parallax star field — current `CosmicBackground` is a static gradient

#### `Components/DivineGlassCard.swift`
- [x] **R2-X3** Glass-morphism card composable (frosted blur + hairline border) — currently flat NavySurface boxes

#### `Components/GlassSegmentedControl.swift`
- [x] **R2-X4** Glass-styled segmented control — Android uses `FilterChip` everywhere

#### `Components/PremiumComponents.swift` / `SharedThemeComponents.swift`
- [x] **R2-X5** `ShimmerButton` composable (premium animated CTA)
- [x] **R2-X6** `PremiumInputField` with floating label + gold underline animation
- [x] **R2-X7** `PremiumSelectionSheet` standardised modal for picker pickers
- [x] **R2-X8** `DatePickerSheet` / `TimePickerSheet` styled wrappers (Android uses raw Material native dialogs)

#### `Components/PremiumTabBar.swift`
- [x] **R2-X9** Center-FAB tab bar with elevated gold "Ask" button — Android already has 5-tab bar but no center elevation
- [x] **R2-X10** Tab-press scale animation

---

### Cross-cutting

- [x] **R2-Z1** **Haptics**: `HapticManager` (`VibrationEffect`) — iOS has light/medium/success haptics on every meaningful action
- [x] **R2-Z2** **SoundManager**: subtle UI sound effects matching iOS
- [x] **R2-Z3** **Localised number/date formatting** across screens (date long format, AM/PM in English locale)
- [x] **R2-Z4** **Per-element accessibilityIdentifier** strings on premium cards (currently many composables lack `Modifier.semantics { contentDescription = ... }`)
- [x] **R2-Z5** **Localized planet/sign keys** (e.g. `planet_saturn`, `sign_aries`) — backend returns English-only; need translation layer
- [x] **R2-Z6** **Active-profile change broadcast** (iOS uses `NotificationCenter.activeProfileChanged`) — Android needs equivalent SharedFlow

---

## Completion summary

| Category | Items | Done |
|---|---|---|
| Home (R2-H1 → R2-H33) | 33 | 33 |
| Auth (R2-A1 → R2-A11) | 11 | 11 |
| Profile (R2-P1 → R2-P25) | 25 | 25 |
| Settings (R2-S1 → R2-S13) | 13 | 13 |
| Charts (R2-C1 → R2-C10) | 10 | 10 |
| Compatibility (R2-CM1 → R2-CM18) | 18 | 18 |
| Components/theme (R2-X1 → R2-X10) | 10 | 10 |
| Cross-cutting (R2-Z1 → R2-Z6) | 6 | 6 |
| **Total Round-2 gaps** | **126** | **126** |

---

## Priority guidance for Round 2

**Highest-impact (start here)**: R2-A7 (typed auth errors), R2-P4 (pull-to-refresh), R2-P9 (notif permission), R2-P23 (delete-account sheet), R2-S7..S13 (notif preferences full feature), R2-CM12-13 (image share), R2-Z1 (haptics)

**Visual polish (medium impact)**: R2-H1..H33 (animations, premium styling), R2-X1..X10 (component library)

**Localisation / a11y (low impact, high consistency)**: R2-Z3..Z5

---

## Quick-start

```bash
# Verify baseline still passes 605 tests
cd /Users/i074917/Documents/destiny_ai_astrology/android_app
JAVA_HOME=/Users/i074917/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  ./gradlew :app:testProductionReleaseUnitTest
```

---

## Round 2 closed

**Final test run: 642 tests, 0 failures** (2026-05-30)

All 126 R2 gap items marked done. Two test fixes were required: `HomeViewModelTest` was missing the `api: AstroApiService` constructor argument added in R2-H3, and the `selectLifeArea` test was updated to reflect the R2-H28 two-step brief-popup flow (`selectLifeArea` now sets `briefLifeArea`; `confirmLifeAreaBrief` promotes to `selectedLifeArea`).
