# iOS Auth Flow Reference (Guest + Google) — Source of Truth for Android Parity

This is the canonical reference of how the iOS app drives the Guest and Google auth flows end‑to‑end. Use it as the contract when building the Android equivalent. Every row points back to a Swift file:line so the iOS behavior can be re‑verified.

> Files referenced are absolute paths under `/Users/i074917/Documents/destiny_ai_astrology/ios_app/ios_app/`.

---

## 1. Sequence Diagrams

### 1.1 Guest path (ASCII)

```
User            AuthView         AuthVM           AppleAuthService     UserDefaults/Keychain     AppRootView
 |                |                |                    |                      |                     |
 | tap "Continue  |                |                    |                      |                     |
 | as Guest"      |                |                    |                      |                     |
 |--------------->|                |                    |                      |                     |
 |                | continueAsGuest()                   |                      |                     |
 |                |--------------->|                    |                      |                     |
 |                |                | isLoading=true     |                      |                     |
 |                |                | remove("birthDataRefreshedOnServer")     |                       |
 |                |                |------------------------------------------>|                     |
 |                |                | signInAsGuest()    |                      |                     |
 |                |                |------------------->|                      |                     |
 |                |                |                    | guestId = "guest_<uuid8>"                  |
 |                |                |   User(id, nil, nil, "guest")             |                     |
 |                |                |<-------------------|                      |                     |
 |                |                | handleAuthSuccess(user, isGuest=true)     |                     |
 |                |                | Keychain[userId]=guestId                  |                     |
 |                |                | UD: isAuthenticated=true,                 |                     |
 |                |                |     lastAccessState="granted",            |                     |
 |                |                |     isGuest=true                          |                     |
 |                |                | UD remove appleUserID, googleUserID       |                     |
 |                |                | (APNs registration SKIPPED — email==nil)  |                     |
 |                |                |------------------------------------------>|                     |
 |                |                | isLoading=false                                                 |
 |                |                |                                                                 |
 |                |                | isAuthenticated=true ===========================================>|
 |                |                |                                                                 |
 |                |                |                              hasBirthData==false (fresh guest)  |
 |                |                |                              -> route to BirthDataView          |
 |                |                |                                                                 |
 | fill birth form ---------------> BirthDataView.submit -> BirthDataViewModel.save                  |
 |                |                |   POST /subscription/register (is_generated_email=true)         |
 |                |                |   POST /subscription/profile (birth_profile)                    |
 |                |                |   on success: hasBirthData=true                                  |
 |                |                |   show ResponseStyleOnboardingView -> ProfileSetupLoadingView    |
 |                |                |                                                                 |
 |                |                |                              hasBirthData==true                  |
 |                |                |                              -> route to MainTabView             |
```

### 1.2 Google path (ASCII)

```
User      AuthView       AuthVM            AppleAuthService       GIDSignIn      ProfileService            Backend            AppRootView
 |          |              |                      |                   |               |                       |                  |
 | tap     |              |                      |                   |               |                       |                  |
 | Google  |              |                      |                   |               |                       |                  |
 |-------->|              |                      |                   |               |                       |                  |
 |         | signInWithGoogle()                  |                   |               |                       |                  |
 |         |------------->|                      |                   |               |                       |                  |
 |         |              | performSignIn { ... }                    |               |                       |                  |
 |         |              | isLoading=true; errorMessage=nil         |               |                       |                  |
 |         |              | capture: wasGuest, guestEmail,           |               |                       |                  |
 |         |              |          guestBirthData, guestUserName,  |               |                       |                  |
 |         |              |          guestGender (user-scoped)       |               |                       |                  |
 |         |              | if wasGuest && hasBirthData:             |               |                       |                  |
 |         |              |   DataManager.deleteAllThreads(guestEmail)               |                       |                  |
 |         |              | authService.signInWithGoogle()           |               |                       |                  |
 |         |              |--------------------->|                   |               |                       |                  |
 |         |              |                      | rootVC -> GIDSignIn.signIn(withPresenting:)               |                  |
 |         |              |                      |------------------>|               |                       |                  |
 |         |              |                      |                   | OAuth UI      |                       |                  |
 |         |              |                      |                   | (system browser)                      |                  |
 |         |              |                      |   user, error     |               |                       |                  |
 |         |              |                      |<------------------|               |                       |                  |
 |         |              |                      | User(id=userID, email, name, provider="google")           |                  |
 |         |              |  user                |                                                            |                  |
 |         |              |<---------------------|                                                            |                  |
 |         |              | branch on user.email != nil                                                        |                  |
 |         |              | -> registerUser(email, isGeneratedEmail=false, googleId=user.id)                  |                  |
 |         |              |--------------------------------------------->| POST /subscription/register ----->| {accessState,    |
 |         |              |                                              |                                  |  userEmail, ...} |
 |         |              | accountDeleted -> throw, errorMessage="account_deleted_error"                                       |
 |         |              | other failures: registerResponse=nil (non-fatal)                                                    |
 |         |              | accessState=="waitlist_pending" -> set userEmail+lastAccessState+isAuthenticated, return early       |
 |         |              | actualEmail = registerResponse?.userEmail ?? email                                                  |
 |         |              | fetchAndRestoreProfile(actualEmail, skipSync=isGuestUpgrade)                                         |
 |         |              |                                              | GET /subscription/profile -------->| ProfileResponse  |
 |         |              | if !profileHasBirthData && guestBirthData:                                                          |
 |         |              |   ProfileService.upgradeGuestToRegistered(oldEmail, actualEmail)                                    |
 |         |              |   saveGuestBirthDataForRegisteredUser(...)                                                          |
 |         |              |   profileHasBirthData=true                                                                          |
 |         |              |   LoginSyncCoordinator.syncAll(actualEmail) (post-migration)                                        |
 |         |              | catch ProfileError.birthDataTaken -> throw BirthDataTakenError(existingEmail, provider)              |
 |         |              | UD.hasBirthData = profileHasBirthData                                                               |
 |         |              | handleAuthSuccess(user, isGuest=false, skipHydration=!profileHasBirthData)                          |
 |         |              |   Keychain[userId]=user.id                                                                          |
 |         |              |   UD.googleUserID=user.id; remove appleUserID                                                       |
 |         |              |   if email -> UIApplication.registerForRemoteNotifications()                                        |
 |         |              | UD.synchronize()                                                                                    |
 |         |              | isLoading=false                                                                                     |
 |         |              | isAuthenticated=true ============================================================================>  |
 |         |              |                                                                                                     |
 |         |              | hasBirthData==true   -> route to MainTabView                                                        |
 |         |              | hasBirthData==false  -> route to BirthDataView                                                      |
 |         |              | accessState=="waitlist_pending" -> route to WaitlistPendingView                                     |
```

---

## 2. Step Tables

### 2.1 Guest — Step / File:Line / Action / API / State / UI

| # | File:Line | Action | API | State writes | UI |
|---|-----------|--------|-----|--------------|----|
| 1 | `Views/Auth/AuthView.swift:268-272` | User taps "Continue as Guest" (button only rendered if `appStartup.allowGuest`) | — | `HapticManager.playButtonTap`, `SoundManager.playButtonTap` | AuthView, guest button under divider |
| 2 | `Views/Auth/AuthView.swift:271` | Button action calls `viewModel.continueAsGuest()` | — | — | unchanged |
| 3 | `ViewModels/AuthViewModel.swift:68-72` | `continueAsGuest()` spawns `Task { continueAsGuestAsync() }` | — | — | unchanged |
| 4 | `ViewModels/AuthViewModel.swift:75-79` | Set `isLoading=true`, remove `birthDataRefreshedOnServer` UD key | — | `isLoading=true`, UD remove `birthDataRefreshedOnServer` | loadingOverlay appears |
| 5 | `ViewModels/AuthViewModel.swift:81` | Call `authService.signInAsGuest()` | — | — | loading overlay |
| 6 | `Services/AppleAuthService.swift:57-65` | Generate `guestId = "guest_<uuid8>"`, return `User(id=guestId, email=nil, name=nil, provider="guest")` — NO network call | — | — | loading overlay |
| 7 | `ViewModels/AuthViewModel.swift:83` | `handleAuthSuccess(user, isGuest=true)` | — | — | loading overlay |
| 8 | `ViewModels/AuthViewModel.swift:538-604` | Persist guest session; APNs register SKIPPED because email is nil | — | `isAuthenticated=true`, `isGuest=true`, `userEmail=nil`, `userName=nil`, Keychain `userId=guestId`, UD `isAuthenticated=true`, `lastAccessState="granted"`, `isGuest=true`, remove `appleUserID`, `googleUserID` | loading overlay |
| 9 | `ViewModels/AuthViewModel.swift:86-89` | Debug log; `isLoading=false` | — | `isLoading=false` | overlay disappears |
| 10 | `AppRootView.swift` (router) | `isAuthenticated && isGuest && !hasBirthData` -> route to `BirthDataView` | — | — | BirthDataView shown |
| 11 | `Views/Onboarding/BirthDataView.swift` -> `ViewModels/BirthDataViewModel.swift` | User submits form; `viewModel.save()` calls register + profile | `POST /subscription/register` (is_generated_email=true), `POST /subscription/profile` | UD `userBirthData`, `hasBirthData=true`, user-scoped birth keys | ResponseStyleOnboardingView -> ProfileSetupLoadingView |
| 12 | `AppRootView.swift` | `isAuthenticated && hasBirthData` -> `MainTabView` | — | — | MainTabView |

### 2.2 Google — Step / File:Line / Action / API / State / UI

| # | File:Line | Action | API | State writes | UI |
|---|-----------|--------|-----|--------------|----|
| 1 | `Views/Auth/AuthView.swift:199-207` (also `Views/Auth/GuestSignInPromptView.swift:169-179`) | Tap Google AuthButton -> `Task { await viewModel.signInWithGoogle() }` | — | `isLoading=true`, `errorMessage=nil` | loadingOverlay with `signing_in` label |
| 2 | `ViewModels/AuthViewModel.swift:61-65` | `signInWithGoogle()` -> `performSignIn { authService.signInWithGoogle() }` | — | — | loading overlay |
| 3 | `ViewModels/AuthViewModel.swift:175-211` | Capture pre-sign-in guest state for upgrade: `wasGuest`, `guestHadBirthData`, `guestEmail`, decoded `BirthData`, `userName`, gender (user-scoped). If guest had birth data -> `DataManager.deleteAllThreads(guestEmail)` | — | local DataManager threads cleared; in-memory `guestBirthData/guestUserName/guestGender` captured | loading overlay |
| 4 | `Services/AppleAuthService.swift:30-53` | Resolve rootViewController; `GIDSignIn.sharedInstance.signIn(withPresenting:)`; on success build `User(id=user.userID, email=user.profile.email, name=user.profile.name, provider="google")`. NOTE: idToken is NOT exchanged with backend, only the userID. | `GIDSignIn.signIn(withPresenting:)` | — | Google account chooser sheet |
| 5 | `ViewModels/AuthViewModel.swift:213-232` | Branch on `user.email != nil`. Set `googleId=user.id`, `appleId=nil` | — | — | loading overlay |
| 6 | `ViewModels/AuthViewModel.swift:242-270` | `ProfileService.shared.registerUser(email, isGeneratedEmail=false, appleId=nil, googleId=user.id)`. `accountDeleted` re-throws (fatal). Other errors -> `registerResponse=nil` (non-fatal). `accessState=="waitlist_pending"` -> set UD `userEmail`, `lastAccessState="waitlist_pending"`, `isAuthenticated=true`, return early. | `POST /subscription/register` body `{email, is_generated_email:false, apple_id:nil, google_id:user.id}` | UD on waitlist branch | loading overlay |
| 7 | `ViewModels/AuthViewModel.swift:273-280` | `actualEmail = registerResponse?.userEmail ?? email` (server-authoritative) | — | — | loading overlay |
| 8 | `ViewModels/AuthViewModel.swift:278-279` | `fetchAndRestoreProfile(actualEmail, skipSync=isGuestUpgrade)` | `GET /subscription/profile?email=actualEmail` | If profile present: `ProfileService.restoreProfileLocally(profile)`, UD `quotaUsed`. If profile lacks birth data and prior local birth data existed -> `birthDataRefreshedOnServer=true`. | loading overlay |
| 9 | `ViewModels/AuthViewModel.swift:284-333` | If !profileHasBirthData and guest had birth data: `upgradeGuestToRegistered(guestEmail, actualEmail)`, then `saveGuestBirthDataForRegisteredUser(...)`. Catch `ProfileError.birthDataTaken` -> throw `BirthDataTakenError`. Then `LoginSyncCoordinator.syncAll(actualEmail)`. | `POST /subscription/upgrade-guest`, `POST /subscription/profile`, history sync endpoints | `profileHasBirthData=true` (in‑memory) | loading overlay |
| 10 | `ViewModels/AuthViewModel.swift:337-356` | On MainActor: set UD `hasBirthData=profileHasBirthData`, clear stale `userBirthData` if no birth data, call `handleAuthSuccess(user, isGuest=false, skipHydration=!profileHasBirthData)`, `UserDefaults.synchronize()` | — | UD `hasBirthData`, possibly remove `userBirthData` | loading overlay |
| 11 | `ViewModels/AuthViewModel.swift:538-604` | `handleAuthSuccess`: persist `userEmail`, `userName`, hydrate user-scoped cache (only if `!skipHydration`), set `googleUserID=user.id`, remove `appleUserID`, `UIApplication.registerForRemoteNotifications()` (because email != nil) | `registerForRemoteNotifications` (APNs) | Keychain `userId`, UD `isAuthenticated=true`, `lastAccessState="granted"`, `isGuest=false`, `userEmail`, `userName`, `googleUserID`; remove `appleUserID` | loading overlay |
| 12 | `ViewModels/AuthViewModel.swift:357-515` | If sign-in returned no email (rare for Google — Apple Hide-My-Email path): call `registerUser(placeholder, googleId=user.id)`, recover stored email, repeat profile fetch + carry-forward | `POST /subscription/register`, `GET /subscription/profile` | same as steps 7-11 with recovered email | loading overlay |
| 13 | `ViewModels/AuthViewModel.swift:533-535` | `isLoading=false` | — | — | overlay disappears |
| 14 | `AppRootView.swift` | `isAuthenticated && hasBirthData` -> MainTabView; `isAuthenticated && !hasBirthData` -> BirthDataView; `lastAccessState=="waitlist_pending"` -> WaitlistPendingView | — | — | route resolved |

---

## 3. Decision-Point Matrix

| Condition | True branch | False branch |
|-----------|-------------|--------------|
| `appStartup.allowGuest` | Show "Continue as Guest" button (`AuthView.swift:268-272`) | Hide guest section entirely |
| `user.email != nil` after sign-in (`AuthViewModel.swift:223`) | Path A: register by email + provider id | Path B: ID-based lookup with placeholder email (`AuthViewModel.swift:357-515`) |
| `user.provider == "google"` (`AuthViewModel.swift:230-231`) | `googleId=user.id`, `appleId=nil` | `appleId=user.id`, `googleId=nil` |
| `ProfileError.isAccountDeleted` thrown (`AuthViewModel.swift:250-251`, `516-520`) | Re-throw, set `errorMessage="account_deleted_error"`, abort | Set `registerResponse=nil`, continue (non-fatal) |
| `registerResponse?.accessState == "waitlist_pending"` (`AuthViewModel.swift:261-270`, `395-405`) | Set UD `userEmail`, `lastAccessState="waitlist_pending"`, `isAuthenticated=true`, return early -> AppRootView shows `WaitlistPendingView` | Continue to profile fetch |
| `wasGuest && guestHadBirthData` (`AuthViewModel.swift:190-211`) | Capture guest data, clear local guest threads, perform carry-forward after register | Skip carry-forward |
| `!profileHasBirthData && guestBirthData != nil` (`AuthViewModel.swift:284`) | Run `upgradeGuestToRegistered` + `saveGuestBirthDataForRegisteredUser` + post-migration `syncAll` | No carry-forward; user lands on BirthDataView |
| `ProfileError.birthDataTaken` (`AuthViewModel.swift:319-323`) | Throw `BirthDataTakenError(existingEmail, provider)`, surface as `errorMessage` | Continue |
| `profileHasBirthData` (`AuthViewModel.swift:338-346`) | UD `hasBirthData=true`, hydrate from local cache | UD `hasBirthData=false`, remove stale `userBirthData`, force BirthDataView |
| `skipHydration` (`AuthViewModel.swift:564`) | Skip user-scoped local cache copy into session keys | Hydrate session keys from user-scoped cache |
| `user.email != nil` in `handleAuthSuccess` (`AuthViewModel.swift:609-614`) | `UIApplication.registerForRemoteNotifications()` | Skip APNs (guest case) |
| `storedEmail != "lookup-by-id@placeholder.local"` in ID-lookup branch (`AuthViewModel.swift:407`) | Existing user found -> fetch + restore profile | Treat as new user with placeholder email (Apple Hide-My-Email reinstall) |
| `isAuthenticated && isGuest && !hasBirthData` (AppRootView) | Route to BirthDataView | — |
| `isAuthenticated && hasBirthData` (AppRootView) | Route to MainTabView | — |
| `lastAccessState == "waitlist_pending"` (AppRootView) | Route to WaitlistPendingView | — |

---

## 4. Error Code Map

| Error / Source | Detection (file:line) | UI behavior |
|----------------|------------------------|-------------|
| `ProfileError.accountDeleted` | `AuthViewModel.swift:250, 384, 516` | Block sign-in, `errorMessage = "account_deleted_error".localized`, isLoading=false; user remains on AuthView |
| `BirthDataTakenError(existingEmail, provider)` | `AuthViewModel.swift:319-323, 481-483, 521-525` | Block sign-in; `errorMessage = error.localizedDescription` (mentions taken email + provider) |
| Generic register/profile failure (non `accountDeleted`) | `AuthViewModel.swift:252-255, 386-389` | Logged as warning; `registerResponse=nil`; flow continues — user enters birth-data form manually |
| Guest thread migration failure | `AuthViewModel.swift:294-296, 457-459` | Logged as warning; non-fatal; sign-in continues |
| Save-guest-birth-data failure (non-conflict) | `AuthViewModel.swift:329-332, 487-489` | Logged; user lands on BirthDataView and re-enters birth data |
| Generic catch-all sign-in error | `AuthViewModel.swift:526-531` | `errorMessage = "sign_in_failed".localized + " (\(error.localizedDescription))"` |
| `accessState == "waitlist_pending"` | `AuthViewModel.swift:261-270, 395-405` | Soft success: `isAuthenticated=true`, `lastAccessState="waitlist_pending"`, route to `WaitlistPendingView` |
| GIDSignIn user cancel | (Google SDK throws) caught by generic handler | `errorMessage = "sign_in_failed".localized + ...` (iOS does not specifically suppress cancel) |
| Network unavailable on `loadSession` | (Android only — iOS keeps session if Keychain has userId) | n/a on iOS |

---

## 5. Comparison: iOS Guest vs iOS Google

| Aspect | iOS Guest | iOS Google |
|--------|-----------|------------|
| Entry button | `AuthView.swift:268-272` (only if `allowGuest`) | `AuthView.swift:199-207` and `GuestSignInPromptView.swift:169-179` |
| ViewModel entrypoint | `AuthViewModel.continueAsGuest()` (`:68-72`) | `AuthViewModel.signInWithGoogle()` (`:61-65`) |
| Path used | `continueAsGuestAsync()` (`:75-90`) — does NOT call `performSignIn` | `performSignIn { authService.signInWithGoogle() }` (`:175-536`) |
| Sign-in service | `AppleAuthService.signInAsGuest()` (`:57-65`) | `AppleAuthService.signInWithGoogle()` (`:30-53`) |
| Network call during sign-in | NONE (local UUID guest id) | `GIDSignIn.signIn(withPresenting:)` (Google OAuth, system browser) |
| Backend register call | None at sign-in. Happens later in BirthDataView with `is_generated_email=true` | `POST /subscription/register` with `email`, `is_generated_email=false`, `google_id=user.id` |
| Profile fetch | Not performed (no email) | `fetchAndRestoreProfile(actualEmail)` -> `GET /subscription/profile` |
| Guest-data carry-forward | n/a (this IS the guest) | Captured pre-sign-in (`:181-211`); migrated via `upgradeGuestToRegistered` + `saveGuestBirthDataForRegisteredUser` if registered user has no birth data |
| Local guest history clear | n/a | `DataManager.deleteAllThreads(guestEmail)` BEFORE sign-in (`:208-209`) |
| Post-migration sync | n/a | `LoginSyncCoordinator.shared.syncAll(actualEmail)` after migration (`:316`) |
| Waitlist gate | n/a | `accessState=="waitlist_pending"` -> set UD + early return -> `WaitlistPendingView` |
| Account-deleted handling | n/a | `ProfileError.accountDeleted` re-thrown -> `errorMessage="account_deleted_error"` |
| Birth-data taken handling | n/a | `BirthDataTakenError` -> `errorMessage` with existingEmail+provider |
| Keychain `userId` | `guest_<uuid8>` | Google `user.userID` |
| UD `isGuest` | `true` | `false` |
| UD `userEmail` | `nil` (not set) | server-returned `actualEmail` (or recovered storedEmail in ID-lookup branch) |
| UD `userName` | `nil` | `user.profile.name` if present |
| UD `googleUserID` | removed | set to `user.id` |
| UD `appleUserID` | removed | removed |
| UD `lastAccessState` | `"granted"` | `"granted"` (or `"waitlist_pending"` on waitlist) |
| Local cache hydration | none (no email) | If `!skipHydration` and user-scoped cache present, copy to session keys |
| APNs registration in `handleAuthSuccess` | SKIPPED (email==nil) | `UIApplication.registerForRemoteNotifications()` |
| Loading overlay | yes (`isLoading`) | yes (`isLoading`) |
| Post-auth route (no birth data) | BirthDataView -> ResponseStyleOnboardingView -> ProfileSetupLoadingView -> MainTabView | BirthDataView (only if profile has none AND guest had none) |
| Post-auth route (with birth data) | n/a (fresh guest never has it) | MainTabView |
| Special route | n/a | WaitlistPendingView (waitlist), AccountDeleted toast, BirthDataTaken toast |
| Error UX | `errorMessage` set, no overlay, AuthView remains | same surface (`errorMessage`), but more error classes possible |

---

## 6. Notes for Android implementers

- iOS uses `UserDefaults` as the session source-of-truth and `Keychain` only for `userId`. Android equivalent: `DataStore`/`SharedPreferences` for flags, `EncryptedSharedPreferences` for `userId`.
- iOS performs APNs registration directly inside `handleAuthSuccess` whenever `email != nil`. Android must mirror this with FCM token registration on every sign-in (including silent re-registration) — register only when `user.email != null`.
- Provider ID hygiene: iOS clears `appleUserID` on Google sign-in and vice versa (`AuthViewModel.swift:591-604`). Android must do the same in `UserPreferences`.
- The Google idToken is NOT sent to the backend — only the Google `userID` (`AppleAuthService.swift:48`). Android current implementation passes `idToken`; backend contract should accept either, but for parity Android can also send only the user id.
- Guest carry-forward sequence is order-sensitive: capture guest state -> delete local threads -> sign-in -> register -> fetch profile -> upgradeGuest -> saveGuestBirthData -> syncAll. Skipping any step causes duplicate threads or stale local data.
