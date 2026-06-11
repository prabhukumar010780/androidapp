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
- Build: `./gradlew assembleDebug` (local emulator, hits 10.0.2.2:8000)
- Build staging: `./gradlew assembleStaging -PAPI_KEY_STAGING=<key> -PGOOGLE_SERVER_CLIENT_ID=<id>`
- Build release: `./gradlew assembleRelease -PAPI_KEY_PRODUCTION=<key> -PGOOGLE_SERVER_CLIENT_ID=<id>`
- Unit tests: `./gradlew :app:testStagingUnitTest`
- Install: `./gradlew installDebug` (or `installStaging` / `installRelease`)
- Lint: `./gradlew lintRelease`
- Connected tests: `./gradlew connectedAndroidTest` (needs emulator)

## Build types (single applicationId, iOS parity)
| Build type | applicationId | API base URL | versionNameSuffix |
|---|---|---|---|
| `debug` | `com.destinyai.astrology` | http://10.0.2.2:8000 (emulator host) | `-local` |
| `staging` | `com.destinyai.astrology` | https://astroapi-test-...run.app | `-staging` |
| `release` | `com.destinyai.astrology` | https://astroapi-prod-...run.app | (none) |

Single Play Console app for both staging and release. Branch routing:
- `test` branch → CI runs `bundleStaging` → uploads to internal track (auto-completed)
- `main` branch → CI runs `bundleRelease` → uploads to internal track (draft, manual promote)

The `[STAGING]` / `[LOCAL]` label in the Settings screen footer (driven by
`BuildConfig.ENV`) tells testers which environment a given install is on.

## Key Config
- Single `applicationId = com.destinyai.astrology` across all build types (parity with iOS bundle id)
- API URLs / API keys / ENV in BuildConfig (per-build-type — see `app/build.gradle.kts`)
- FCM: `app/google-services.json` carries the production package only (gitignored — decode from `GOOGLE_SERVICES_JSON` GitHub secret)
- Keystore: decoded from `ANDROID_KEYSTORE_BASE64` secret at build time

## google-services.json
- NOT committed to git (gitignored)
- Firebase project: `destiny-ai-astrology-4f52a` (NOT circular-genius-481518-v0 — that's the GCP/Cloud Run project)
- Single client entry: `com.destinyai.astrology` (no `.staging` / `.local` clients — single applicationId)
- Local dev: download from Firebase Console → project `destiny-ai-astrology-4f52a` → Project Settings → Your apps → Android (com.destinyai.astrology)
- CI: decoded from `GOOGLE_SERVICES_JSON` secret in `prabhukumar010780/androidapp`

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
