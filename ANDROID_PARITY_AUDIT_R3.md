# Android iOS Parity — Round 3 Deep Audit
**Generated: 2026-05-30**
**Total findings: 255 (29 CRITICAL · 105 HIGH · 93 MEDIUM · 28 LOW)**
**Source: Workflow wsx4m0dts — 318 parallel agents, 16 functional areas, 3-vote adversarial verification**

---

## Executive Summary

The Android app has substantial parity gaps with iOS across 21 functional areas, with 29 CRITICAL findings that block core flows including subscription purchase, FCM token registration, notification interactions, and Charts/Home API integrations. The most concentrated risk areas are Home (21 findings), Subscription (21), and Profile (19), where missing features and business-logic mismatches create dead-ends in primary user journeys. Immediate focus should be on the 12 must-fix items below to restore feature parity before the next TestFlight cut.

## By Area

| Area | Critical | High | Medium | Low | Total |
|------|----------|------|--------|-----|-------|
| Home | 2 | 10 | 8 | 1 | 21 |
| Subscription | 2 | 8 | 9 | 2 | 21 |
| Profile | 3 | 8 | 6 | 2 | 19 |
| Chat | 1 | 5 | 9 | 2 | 17 |
| Services - Subscription | 1 | 6 | 8 | 1 | 16 |
| Compatibility | 3 | 8 | 4 | 0 | 15 |
| Partners | 0 | 9 | 3 | 2 | 14 |
| Auth | 2 | 4 | 4 | 3 | 13 |
| Notifications | 3 | 6 | 3 | 1 | 13 |
| Services - Networking | 2 | 7 | 4 | 0 | 13 |
| History | 0 | 6 | 4 | 2 | 12 |
| Components | 0 | 5 | 4 | 2 | 11 |
| Services - Push | 2 | 4 | 3 | 2 | 11 |
| Services - Auth | 2 | 6 | 2 | 0 | 10 |
| Onboarding | 3 | 3 | 2 | 1 | 9 |
| Services - Sound | 0 | 3 | 4 | 2 | 9 |
| Settings | 1 | 2 | 3 | 1 | 7 |
| Splash | 0 | 2 | 5 | 0 | 7 |
| Charts | 2 | 2 | 1 | 1 | 6 |
| Waitlist | 0 | 1 | 4 | 1 | 6 |
| Services - Haptics | 0 | 0 | 3 | 2 | 5 |

## By Category

| Category | Count |
|----------|-------|
| missing_feature | 55 |
| business_logic | 44 |
| ui_visual | 38 |
| api | 30 |
| localization | 23 |
| state | 17 |
| data | 17 |
| auth | 10 |
| navigation | 10 |
| runtime_crash | 7 |
| security | 4 |

## Top 10 Must-Fix Items

1. **[CRITICAL]** *Services - Networking* — **SSE read timeout (60s) far below iOS — Opus streams killed mid-response**
   - iOS: [`ios_app/ios_app/Services/StreamingPredictionService.swift:64-67`](ios_app/ios_app/Services/StreamingPredictionService.swift#L64-L67) — config.timeoutIntervalForRequest=300; timeoutIntervalForResource=600; waitsForConnectivity=true
   - Android: [`android_app/app/src/main/java/com/destinyai/astrology/di/AppModule.kt:59-61`](android_app/app/src/main/java/com/destinyai/astrology/di/AppModule.kt#L59-L61) — single OkHttpClient with .readTimeout(60, SECONDS) shared by all calls including streaming
   - **Impact**: Every streaming prediction and compatibility analysis fails on Android — user sees timeout error instead of receiving the LLM response. Total feature break.
   - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/di/AppModule.kt:59-61`](android_app/app/src/main/java/com/destinyai/astrology/di/AppModule.kt#L59-L61) — provide a separate @Named("streaming") OkHttpClient with .readTimeout(10, MINUTES) (or 0 = no timeout) and inject it into StreamingPredictionService and CompatibilityRepository.

2. **[CRITICAL]** *Home* — **Wrong API endpoint — Android calls /vedic/api/predict/ instead of /vedic/api/todays-prediction**
   - iOS: [`ios_app/ios_app/Services/PredictionService.swift:22-28`](ios_app/ios_app/Services/PredictionService.swift#L22-L28) calls APIConfig.todaysPrediction = '/vedic/api/todays-prediction' and decodes into TodaysPredictionResponse with current_dasha, life areas, transit influences, suggested questions
   - Android: [`android_app/app/src/main/java/com/destinyai/astrology/data/repository/impl/HomeRepositoryImpl.kt:54-73`](android_app/app/src/main/java/com/destinyai/astrology/data/repository/impl/HomeRepositoryImpl.kt#L54-L73) — getDailyInsight() calls api.predict() with hardcoded query 'Give me a brief daily insight'
   - **Impact**: Core Home content broken: per-area life-area statuses, AI suggested questions, dasha insight card, transit influence cards never load. Android shows hardcoded fallbacks instead of personalized prediction.
   - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/data/remote/AstroApiService.kt`](android_app/app/src/main/java/com/destinyai/astrology/data/remote/AstroApiService.kt) — add `@POST("vedic/api/todays-prediction") suspend fun getTodaysPrediction(@Body req: UserAstroDataRequest): TodaysPredictionResponse`; HomeRepositoryImpl.kt:54-73 — replace api.predict() call with api.getTodaysPrediction().

3. **[CRITICAL]** *Home* — **Tapping life area orb does nothing — VM sets briefLifeArea but UI observes selectedLifeArea**
   - iOS: [`ios_app/ios_app/Views/Home/HomeView.swift:763-774`](ios_app/ios_app/Views/Home/HomeView.swift#L763-L774) onTap sets selectedLifeArea; HomeView.swift:237-254 renders LifeAreaBriefPopup when selectedLifeArea != nil
   - Android: [`android_app/app/src/main/java/com/destinyai/astrology/ui/home/HomeViewModel.kt:150-153`](android_app/app/src/main/java/com/destinyai/astrology/ui/home/HomeViewModel.kt#L150-L153) selectLifeArea() sets briefLifeArea and explicitly clears selectedLifeArea; HomeScreen.kt:56-61 only observes selectedLifeArea
   - **Impact**: Tapping a life area orb produces no popup, no sheet, no navigation. Core Home interaction is dead on Android.
   - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/ui/home/HomeScreen.kt:56-61`](android_app/app/src/main/java/com/destinyai/astrology/ui/home/HomeScreen.kt#L56-L61) — render a LifeAreaBriefPopup composable bound to state.briefLifeArea with onConfirm calling viewModel.confirmLifeAreaBrief(); OR change HomeViewModel.kt:150-153 selectLifeArea() to set selectedLifeArea directly.

4. **[CRITICAL]** *Charts* — **Android Charts API endpoint and payload differ from iOS — 404/422 on every Charts request**
   - iOS: [`ios_app/ios_app/Services/UserChartService.swift:21`](ios_app/ios_app/Services/UserChartService.swift#L21) POSTs to /vedic/api/astrodata/full with UserAstroDataRequest containing nested birth_data { dob, time, latitude, longitude, ayanamsa, house_system, city_of_birth, birth_time_unknown }
   - Android: [`android_app/app/src/main/java/com/destinyai/astrology/data/remote/AstroApiService.kt:530-531`](android_app/app/src/main/java/com/destinyai/astrology/data/remote/AstroApiService.kt#L530-L531) POSTs to vedic/api/chart-data/; ui/charts/ChartModels.kt:104-109 ChartDataRequest is flat {dob, time, latitude, longitude} missing ayanamsa/house_system/city_of_birth/wrapper
   - **Impact**: Charts screen permanently shows error/loading state. Even if /chart-data/ alias exists, missing ayanamsa silently ignores user's sidereal selection.
   - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/data/remote/AstroApiService.kt:530-531`](android_app/app/src/main/java/com/destinyai/astrology/data/remote/AstroApiService.kt#L530-L531) — change to @POST("vedic/api/astrodata/full"); ChartModels.kt:104-109 — wrap fields in birth_data object and add ayanamsa, house_system, city_of_birth, birth_time_unknown.

5. **[CRITICAL]** *Chat* — **Quota / paywall pre-check missing in Chat — paywall only on HTTP 402/429**
   - iOS: [`ios_app/ios_app/ViewModels/ChatViewModel.swift:316-351`](ios_app/ios_app/ViewModels/ChatViewModel.swift#L316-L351) sendMessage() calls QuotaManager.shared.canAccessFeature(.aiQuestions) BEFORE network, branches on daily_limit_reached / overall_limit_reached / upgrade_required
   - Android: [`android_app/app/src/main/java/com/destinyai/astrology/ui/chat/ChatViewModel.kt:67-127`](android_app/app/src/main/java/com/destinyai/astrology/ui/chat/ChatViewModel.kt#L67-L127) sendMessage() never consults a quota manager, never reads canAskQuestion, sends request unconditionally
   - **Impact**: Free users on Android can spam the API; backend rejects with generic paywall regardless of reason. iOS's 'Daily limit reached. Resets at HH:mm' / 'Sign in to continue' UX absent.
   - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/ui/chat/ChatViewModel.kt:67-127`](android_app/app/src/main/java/com/destinyai/astrology/ui/chat/ChatViewModel.kt#L67-L127) — port iOS QuotaManager (add /subscription/can-access GET to AstroApiService.kt), call canAccessFeature before streamingPredictionService.send(), surface reason codes into ChatUiState, gate send button via canAskQuestion.

6. **[CRITICAL]** *Compatibility* — **LocationSearchDialog hardcodes lat=0.0/lon=0.0 — partner coordinates always zero**
   - iOS: [`ios_app/ios_app/Views/Compatibility/CompatibilityView.swift:104-110`](ios_app/ios_app/Views/Compatibility/CompatibilityView.swift#L104-L110) uses LocationSearchView that geocodes selectedCity → latitude/longitude bindings populated from places API
   - Android: [`android_app/app/src/main/java/com/destinyai/astrology/ui/compatibility/CompatibilityScreen.kt:899-905`](android_app/app/src/main/java/com/destinyai/astrology/ui/compatibility/CompatibilityScreen.kt#L899-L905) — `if (cityInput.isNotBlank()) { onLocationSelected(cityInput.trim(), 0.0, 0.0) }` passes 0.0/0.0 for every search
   - **Impact**: 100% of Android users typing a city via the dialog get an astrologically meaningless reading — calculation runs against (0,0) Atlantic Ocean instead of partner's birthplace.
   - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/ui/compatibility/CompatibilityScreen.kt:899-905`](android_app/app/src/main/java/com/destinyai/astrology/ui/compatibility/CompatibilityScreen.kt#L899-L905) — call existing /api/v2/location/search at AstroApiService.kt:526 to geocode cityInput, pass returned lat/lon; add validation block rejecting submission when lat==0 && lon==0.

7. **[CRITICAL]** *Subscription* — **Subscription Purchase button non-functional — empty onClick callback**
   - iOS: [`ios_app/ios_app/Views/Subscription/SubscriptionView.swift:334-338`](ios_app/ios_app/Views/Subscription/SubscriptionView.swift#L334-L338) calls purchaseSubscription(planId:) which invokes subscriptionManager.purchase(product) and dismisses on success
   - Android: [`android_app/app/src/main/java/com/destinyai/astrology/ui/subscription/SubscriptionScreen.kt:240-247`](android_app/app/src/main/java/com/destinyai/astrology/ui/subscription/SubscriptionScreen.kt#L240-L247) — Button onClick is empty `{ }` with comment 'Full wiring happens in a future slice'
   - **Impact**: Users cannot purchase a subscription on Android. Tapping Subscribe has no effect. Core monetization flow is completely broken.
   - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/ui/subscription/SubscriptionScreen.kt:240-247`](android_app/app/src/main/java/com/destinyai/astrology/ui/subscription/SubscriptionScreen.kt#L240-L247) — pass billingManager.products (List<ProductDetails>) into the screen, match each PlanDto to a ProductDetails by productId, replace empty onClick with `viewModel.purchase(productDetails, activity, offerToken)`.

8. **[CRITICAL]** *Notifications* — **Notifications API mismatch — wrong path and query param ('email' vs 'user_email')**
   - iOS: [`ios_app/ios_app/Services/NotificationInboxService.swift:50`](ios_app/ios_app/Services/NotificationInboxService.swift#L50) GET /notifications?user_email=...&page=...&page_size=...; line 101 /notifications/unread-count?user_email=...
   - Android: [`android_app/app/src/main/java/com/destinyai/astrology/data/remote/AstroApiService.kt:475`](android_app/app/src/main/java/com/destinyai/astrology/data/remote/AstroApiService.kt#L475) @GET("notifications/list") with @Query("email"); :478 notifications/unread-count w/ email; :481 wrong path
   - **Impact**: Every Android list/unread/markRead/markAllRead call hits wrong endpoint or unrecognized query param — inbox never loads, badge stays at 0, mark-as-read silently fails.
   - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/data/remote/AstroApiService.kt:475-481`](android_app/app/src/main/java/com/destinyai/astrology/data/remote/AstroApiService.kt#L475-L481) — change paths to /notifications, /notifications/unread-count, /notifications/{id}/read, /notifications/read-all and rename @Query("email") to @Query("user_email").

9. **[CRITICAL]** *Notifications* — **NotificationDetailSheet missing — tapping notifications does nothing**
   - iOS: [`ios_app/ios_app/Views/Notifications/NotificationInboxView.swift:46-51`](ios_app/ios_app/Views/Notifications/NotificationInboxView.swift#L46-L51) .sheet(item: $selectedNotification); :167-173 onTapGesture marks read AND opens NotificationDetailSheet; :326-468 detail sheet with deep-link routing
   - Android: [`android_app/app/src/main/java/com/destinyai/astrology/ui/notifications/NotificationsScreen.kt:143-199`](android_app/app/src/main/java/com/destinyai/astrology/ui/notifications/NotificationsScreen.kt#L143-L199) LazyColumn item Row has NO .clickable modifier; no detail sheet; no NotificationRouter equivalent
   - **Impact**: Users cannot read full notification bodies, cannot mark individual notifications read by tapping, cannot deep-link to chat/compatibility/subscription. Core feature unusable.
   - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/ui/notifications/NotificationsScreen.kt:143-199`](android_app/app/src/main/java/com/destinyai/astrology/ui/notifications/NotificationsScreen.kt#L143-L199) — add `.clickable { vm.markRead(notif.id); selected = notif }` to row; build NotificationDetailBottomSheet composable; port iOS NotificationRouter to map type → destination (chat with prefill, match, settings).

10. **[CRITICAL]** *Profile* — **Sign Out button completely missing on Android Profile**
    - iOS: [`ios_app/ios_app/Views/Profile/ProfileView.swift:792-819`](ios_app/ios_app/Views/Profile/ProfileView.swift#L792-L819) — full Sign Out section with destructive button + confirmation alert calling authViewModel.signOutAsync()
    - Android: [`android_app/app/src/main/java/com/destinyai/astrology/ui/profile/ProfileScreen.kt`](android_app/app/src/main/java/com/destinyai/astrology/ui/profile/ProfileScreen.kt) — has no Sign Out button anywhere; only Delete Account exists at line 450-455
    - **Impact**: Users cannot sign out of the Android app. Switching accounts requires uninstalling or deleting the account — major navigation/auth dead-end.
    - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/ui/profile/ProfileScreen.kt`](android_app/app/src/main/java/com/destinyai/astrology/ui/profile/ProfileScreen.kt) — add a Sign Out section above Delete Account (around line 448) with a destructive ShimmerButton triggering an AlertDialog that calls AuthViewModel.signOut() on confirm.

11. **[CRITICAL]** *Services - Push* — **FcmTokenManager orphan code — app never registers FCM token at launch**
    - iOS: [`ios_app/ios_app/Services/PushNotificationService.swift:37-49`](ios_app/ios_app/Services/PushNotificationService.swift#L37-L49) — handleDeviceToken() called from AppDelegate on every launch (didRegisterForRemoteNotificationsWithDeviceToken); registerToken() POSTs to backend
    - Android: [`android_app/app/src/main/java/com/destinyai/astrology/services/FcmTokenManager.kt:15`](android_app/app/src/main/java/com/destinyai/astrology/services/FcmTokenManager.kt#L15) — registerToken() exists but grep shows zero call sites; only the class definition exists
    - **Impact**: After install/login or token expiry, the backend never learns the FCM token unless Firebase happens to rotate it. Users receive no push notifications at all.
    - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/MainActivity.kt`](android_app/app/src/main/java/com/destinyai/astrology/MainActivity.kt) (or DestinyApp.onCreate) — inject FcmTokenManager and call `FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmTokenManager.registerToken(it, BuildConfig.VERSION_NAME) }` on every launch.

12. **[CRITICAL]** *Subscription* — **Backend conflict detection broken — wrong heuristic flags multi-product as conflict, misses real cross-account conflicts**
    - iOS: [`ios_app/ios_app/Services/SubscriptionManager.swift:644-664`](ios_app/ios_app/Services/SubscriptionManager.swift#L644-L664) — parses VerifyResponseBody { success, error } and triggers conflict only when errorCode == 'transaction_belongs_to_different_user'
    - Android: [`android_app/app/src/main/java/com/destinyai/astrology/data/billing/BillingManager.kt:187-192`](android_app/app/src/main/java/com/destinyai/astrology/data/billing/BillingManager.kt#L187-L192) — triggers conflict when purchases.size > 1 (multiple active subs on device); ignores backend error code entirely
    - **Impact**: Legitimate multi-product purchases falsely flagged as conflict. Real cross-account conflicts (Apple/Google sub on different email) NEVER detected — user stuck on free plan with no UI explanation.
    - **Fix**: [`android_app/app/src/main/java/com/destinyai/astrology/data/billing/BillingManager.kt:187-192`](android_app/app/src/main/java/com/destinyai/astrology/data/billing/BillingManager.kt#L187-L192) and 236-256 — add `error: String?` field to VerifyResponse, in verifyWithBackend check `if (!response.success && response.error == 'transaction_belongs_to_different_user') setConflict(...)`; remove the purchases.size > 1 heuristic.

## Full Findings

For the full 255 findings, run:
```bash
cat /private/tmp/claude-501/-Users-i074917-Documents-destiny-ai-astrology/c366bcc6-17f8-4d7d-acf3-15375283b918/tasks/wsx4m0dts.output | python3 -m json.tool
```

## Next Steps

1. Fix all CRITICAL items (29) before next TestFlight
2. Triage HIGH items (105) — group by category for batch fixes
3. Schedule MEDIUM polish (93) for follow-up sprint
4. LOW items (28) → backlog
