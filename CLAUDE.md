# Android App — Kotlin

## Current State: TDD scaffold — MVVM structure + test shells in place, ViewModels not yet implemented
## Package: com.destinyai.astrology

## Structure
```
app/src/main/java/com/destinyai/astrology/
├── domain/model/     → User, ChatMessage, ChatThread
├── data/repository/  → AuthRepository, HomeRepository, ChatRepository (interfaces)
├── data/remote/      → Retrofit API (TODO)
├── data/local/       → Room DB + DataStore (TODO)
├── di/               → Hilt modules (TODO)
├── ui/auth/          → AuthViewModel (stub — TDD red)
├── ui/home/          → HomeViewModel (stub — TDD red)
├── ui/chat/          → ChatViewModel (stub — TDD red)
├── ui/compatibility/ → TODO
├── ui/charts/        → TODO
├── ui/history/       → TODO
├── ui/profile/       → TODO
├── ui/partners/      → TODO
├── ui/notifications/ → TODO
├── ui/subscription/  → TODO
└── ui/settings/      → TODO
```

## TDD Rules
- Write test first, implementation second
- Tests live in `app/src/test/java/com/destinyai/astrology/`
- Unit tests: JUnit5 + MockK + Turbine (StateFlow testing)
- Run: `./gradlew :app:testProductionReleaseUnitTest`
- 44 tests currently compiled, 33 failing (expected — TDD red phase)

## E2E Tests
- Location: `android_app/e2e/`
- Mirrors: `ios_app/e2e/` test suite (same test IDs, same accessibility IDs)
- Driver: Appium + UiAutomator2
- Run: `appium --port 4723 & pytest android_app/e2e/ -v`

## API Contract Tests
- Location: `astrology_api/astroapi-v2/tests/contract/`
- Run: `pytest tests/contract/ -v` (requires server at localhost:8000)
- 20 tests, all passing

## Commands
- Build: `./gradlew assembleStagingDebug`
- Unit tests: `./gradlew :app:testProductionReleaseUnitTest`
- Install: `./gradlew installStagingDebug`
- Lint: `./gradlew lintProductionRelease`
- Connected tests: `./gradlew connectedAndroidTest` (needs emulator)

## Key Config
- API URLs in BuildConfig (production/staging flavors, debug override for emulator)
- FCM: requires `app/google-services.json` (gitignored — decode from `GOOGLE_SERVICES_JSON` GitHub secret)
- Keystore: decoded from `ANDROID_KEYSTORE_BASE64` secret at build time

## google-services.json
- NOT committed to git (gitignored)
- Firebase project: `destiny-ai-astrology-4f52a` (NOT circular-genius-481518-v0 — that's the GCP/Cloud Run project)
- Local dev: download from Firebase Console → project `destiny-ai-astrology-4f52a` → Project Settings → Your apps → Android
- CI: decoded from `GOOGLE_SERVICES_JSON` secret in `prabhukumar010780/androidapp`
- Placeholder exists at `app/google-services.json` — REPLACE with real file before FCM testing

## FCM Debug
- Check `FIREBASE_CREDENTIALS_PATH` on server side
- Firebase project: `destiny-ai-astrology-4f52a`
- GCP project (`circular-genius-481518-v0`) is only for Cloud Run + Play Store WIF — unrelated to FCM
- FCM tokens expire — clients must re-register on each app launch

## Patterns
- Coroutines for ALL async — no RxJava, no callbacks
- MVVM: ViewModel + StateFlow (never LiveData in new code)
- Hilt for DI — all ViewModels injected
- Room for local DB, DataStore for preferences
- EncryptedSharedPreferences (security-crypto) for auth tokens
- String resources in `res/values/strings.xml` — never hardcode user-visible strings
