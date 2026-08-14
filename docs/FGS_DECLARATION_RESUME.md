# RESUME — Foreground Service Declaration blocks alpha deploy

**Created:** 2026-08-14
**Repo:** `prabhukumar010780/androidapp` (android_app is its own git repo), branch `test`
**Status:** 🔴 BLOCKED on one manual Play Console action (below). All code is done + pushed.

---

## TL;DR — the one thing that must happen

Every CI deploy to the **Closed testing (alpha)** track fails at the final
`Committing the Edit` step with:

> `You must let us know whether your app uses any Foreground Service permissions.`

This is a **Play Console declaration**, not a code/CI bug. Until it's completed
**once through the Play Console UI**, no new build reaches alpha.

**Fix = create/promote a release via the UI (not the API), fill the FGS
declaration inline, roll out.** Once committed via UI, it's stored app-wide and
CI deploys stop failing.

---

## Why it's blocked (root cause)

- App targets `targetSdk = 36` (Android 16) and declares a foreground service:
  - `app/src/main/AndroidManifest.xml:10` → `android.permission.FOREGROUND_SERVICE_DATA_SYNC`
  - `app/src/main/AndroidManifest.xml:65-68` → `.services.ChatStreamingForegroundService`, `android:foregroundServiceType="dataSync"`
  - Added in commit `03e8fc2` (DES-162, Aug 10 findings).
- Google requires a `dataSync` FGS type declaration for Android 14+ apps.
- **Chicken-and-egg:** the declaration card only appears on the *App content*
  page **after** a bundle with FGS is committed — but the API commit fails
  *without* the declaration. So `Monitor and improve → App content →
  Need attention` is **empty** and there's no card there.
- The current **live alpha release** (1.10-staging, ~19,147 devices) is an
  **older build predating the FGS permission** — that's why it committed fine.
  Testers CAN still install it; they are not blocked. Only *new* builds are.
- Internal testing accepted build 111 *with* FGS (internal track is lenient);
  Closed testing / Production enforce the declaration.

---

## MANUAL STEPS (do in Play Console — I cannot do these)

Path: play.google.com/console → app **Destiny AI Astrology** (`com.destinyai.astrology`)

1. `Test and release → Testing → Closed testing` → the **Alpha** track.
2. **Create new release** (top-right).
3. **App bundles → Add from library** → pick latest build (**111**, already in
   internal testing, has the FGS permission).
4. Continue to the **review** step. An **Errors** section will show the
   **foreground service permission** declaration with an inline **Declare** link.
   (This is the form that never appears under App content.)
5. Fill it (answers below), **Save**, then **Roll out**.

### Declaration answers to paste
- **Foreground service type:** Data sync
- **Description:** Streams long-running AI astrology predictions (LLM responses
  take 1–3 min). The foreground service keeps the streaming connection alive if
  the user briefly backgrounds the app so the in-progress reading isn't dropped.
- **Why not WorkManager / alternative API:** It's a continuous, user-initiated
  network stream that must not be deferred or batched; deferrable background work
  would break the live stream mid-response.
- **Video:** ≤30s screen recording of a prediction streaming while backgrounding
  and returning; upload unlisted to YouTube/Drive, paste the link.

> ⚠️ If Google **rejects** the `dataSync` justification (they're tightening
> `dataSync` for streaming), the fallback is a code change: switch the service
> type or remove the FGS entirely for alpha. See "Fallback" below — ping Claude
> to prep it.

---

## AFTER the declaration is saved — verify CI is unblocked

```bash
cd /Users/i074917/Documents/destiny_ai_astrology/android_app
# re-run the last failed deploy, OR push a trivial commit to `test`
gh run rerun 31803526227 --repo prabhukumar010780/androidapp
gh run list --limit 4        # confirm "Android Deploy" → success
gh run view <deploy_run_id> --log-failed | grep -i "Committing\|Successfully\|foreground"
# success looks like: "Validating tracks: 'alpha'" → "Committing the Edit" → (no error)
```

Expected: `Android Deploy` goes green; the 6 parity fixes (commit `2e95912`)
land on alpha.

---

## What's already DONE (no action needed)

- ✅ 6 Android↔iOS parity fixes committed + pushed → commit `2e95912` on `test`:
  localized home questions ordering + fallback, chart sign localization (5 sites),
  optional partner city, 600s non-streaming timeout, java.time microsecond expiry
  parse, guest-conflict merge-dialog routing. 808 tests pass.
- ✅ `Android CI` (build/test) run `31803526365` → **success**.
- ✅ CI test→alpha routing works (bundle uploads fine; only the commit is blocked).
- ✅ Keystore + LLM/APNs secrets configured.

---

## Secondary pending items (not blocking alpha builds)

1. **12 testers for production** — dashboard shows "1 tester currently opted in".
   Need ≥12 opted-in testers running the closed test ≥14 days before you can
   `Apply for production`. Add testers: `Closed testing → Alpha → Testers` tab.
2. **`GOOGLE_SERVICES_JSON` GitHub secret** (repo `prabhukumar010780/androidapp`)
   — reportedly still pending; without it FCM push won't work on the built app.
   (Could not verify from CLI — 403 on `gh secret list`. Confirm in repo settings.)

---

## Fallback (only if Google rejects the dataSync declaration)

Client-side code change to unblock alpha without the FGS (Android-only, no
backend impact):
- Remove `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` from
  `AndroidManifest.xml:9-10` and the `<service>` block at `65-68`.
- Remove the `ChatStreamingForegroundService` start call (search
  `startForegroundService`/`ChatStreamingForegroundService` in
  `app/src/main/java/com/destinyai/astrology/`).
- Note: this drops "keep chat streaming alive in background" — a real vs-iOS
  parity regression, acceptable for alpha, revisit before production.

---

## Key facts / IDs
- Play Console account ID: `9033182797613751210` (personal account)
- Package: `com.destinyai.astrology` (single applicationId across build types)
- Failed deploy run to reference: `31803526227` (13:20:20Z, FGS commit error)
- Live alpha release: `1.10-staging`, released 14 Aug, 100% rollout (older build)
- Build variants: `test` branch → `bundleStaging` → alpha (Closed testing)
