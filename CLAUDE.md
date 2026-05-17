# Android App — Kotlin (Early Stage)

## Current State: Scaffolded — MainActivity + 2 Fragments only
## Package: com.destinyai.astrology
## Full feature development not yet started — mirror iOS architecture when building

## Structure
```
app/src/main/java/com/destinyai/astrology/
├── MainActivity.kt   → entry point
├── FirstFragment.kt  → placeholder screen 1
└── SecondFragment.kt → placeholder screen 2

app/src/main/res/     → layouts, strings, drawables
```

## Commands
- Build: ./gradlew build
- Test: ./gradlew test
- Install: ./gradlew installDebug
- Lint: ./gradlew lint
- Connected tests: ./gradlew connectedAndroidTest (needs emulator)

## Patterns to follow (when developing)
- Coroutines for ALL async (no RxJava, no callbacks)
- MVVM architecture (ViewModel + StateFlow)
- Package naming: com.destinyai.astrology.*
- String resources in res/values/strings.xml (never hardcode)
- FCM for push notifications — tokens must refresh on startup

## FCM Debug
- Check FIREBASE_CREDENTIALS_PATH on server
- Verify google-services.json in app/
- FCM tokens expire — clients must re-register
