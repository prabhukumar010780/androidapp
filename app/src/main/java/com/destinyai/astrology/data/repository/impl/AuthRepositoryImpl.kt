package com.destinyai.astrology.data.repository.impl

import com.destinyai.astrology.data.local.prefs.SecureStorage
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.*
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.services.QuotaManager
import com.destinyai.astrology.ui.auth.AccountDeletedException
import com.destinyai.astrology.ui.auth.ConflictException
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AstroApiService,
    private val secure: SecureStorage,
    private val prefs: UserPreferences,
    private val sessionStore: com.destinyai.astrology.data.local.prefs.SessionTokenStore,
    private val exchangeClient: com.destinyai.astrology.data.remote.AuthExchangeClient,
    // Provider to break the potential cycle (QuotaManager → AstroApiService → ...)
    // and to keep the manager optional during clearSession (iOS parity:
    // QuotaManager.resetForSignOut is called from signOut to wipe in-memory caches).
    private val quotaManager: Provider<QuotaManager>,
    // Provider to reset the @Singleton BillingManager's per-session cross-account state
    // on sign-out (C4). Provider (not direct) mirrors quotaManager + avoids constructing
    // BillingManager on an unauthenticated launch.
    private val billingManager: Provider<com.destinyai.astrology.data.billing.BillingManager>,
    // iOS parity (AuthViewModel.signOut wipes AstroDataCache + CompatibilityHistory for
    // the departing account): Room caches are owner_email-scoped, so same-account rows
    // otherwise persist as bloat and server-deleted compat matches can reappear (F6).
    private val astroDataCacheDao: com.destinyai.astrology.data.local.db.AstroDataCacheDao,
    private val compatibilityHistoryDao: com.destinyai.astrology.data.local.db.CompatibilityHistoryDao,
) : AuthRepository {

    override suspend fun getSavedUser(): User? {
        val email = secure.getEmail() ?: return null
        return try {
            api.getStatus(email).toUser()
        } catch (e: HttpException) {
            when (e.code()) {
                // 404: user not found in DB (deleted or never existed)
                404 -> throw AccountDeletedException()
                // iOS parity: 403 on /subscription/status is treated as transient
                // (rate-limit, expired token, etc.) — keep the session intact.
                // Account-deletion is signaled by the backend on register/upgrade only.
                else -> null
            }
        }
    }

    override suspend fun signInWithGoogle(
        email: String,
        googleId: String,
        name: String?,
        idToken: String?,
    ): Result<User> = runCatching {
        val resp = try {
            api.signInWithGoogle(
                GoogleSignInRequest(
                    email = email,
                    isGeneratedEmail = false,
                    googleId = googleId,
                    name = name,
                    idToken = idToken,
                )
            )
        } catch (e: HttpException) {
            // iOS parity (ProfileService.swift:189-198): on /subscription/register
            // 403 with detail.error == "account_deleted", soft-deleted accounts
            // must be blocked from re-registering. Surface a typed exception so
            // the ViewModel shows the localized account_deleted_error string.
            if (e.code() == 403 && parseAccountDeletedError(e)) {
                throw AccountDeletedException()
            }
            throw e
        }
        secure.saveEmail(resp.userEmail)
        prefs.setUserEmail(resp.userEmail)
        // iOS parity (AuthViewModel.handleAuthSuccess sets isAuthenticated=true,
        // swift:649): persist the flag so a COLD LAUNCH routes to Main instead of
        // bouncing to the sign-in screen. Without it the warm-start gate
        // (SplashViewModel: !isAuthenticated → Auth) fired on every relaunch (G1).
        // Waitlist gating still holds — SplashViewModel re-checks lastAccessState after.
        prefs.setAuthenticated(true)
        // iOS parity (BirthDataView.swift:614-616): persist google_id so subsequent
        // profile saves can include it for backend user lookup. setGoogleUserId
        // also clears any previously-stored appleUserId — the two are mutually
        // exclusive (mirrors AuthViewModel.swift:592-599).
        resp.googleId?.let { prefs.setGoogleUserId(it) }
        resp.name?.let { prefs.setUserName(it) }
        prefs.setSubscription(resp.isPremium, resp.planId ?: "")
        prefs.setAccessState(resp.accessState)
        // iOS parity (AuthViewModel.swift:264 / 549): mirror access_state into
        // lastAccessState so SplashViewModel/AppNav can route to WaitlistPending
        // on next launch when applicable.
        prefs.setLastAccessState(resp.accessState)
        // W7 parity + SECURITY: clear the ACTIVE POINTER before minting so that, in the
        // window between register and a successful mint (or if mint fails), the interceptor
        // falls back to the API key and can never attach a previous user's JWT to this
        // user's calls. We deliberately DO NOT wipe per-email JWT entries here — a guest's
        // JWT must survive so a guest→registered upgrade can authenticate /subscription/upgrade
        // as the guest (M2). The guest entry is cleared in upgradeGuest only after a 200.
        sessionStore.clearActivePointer()
        if (!idToken.isNullOrBlank()) {
            runCatching { exchangeClient.signInWithGoogle(idToken, nonce = null) }
                .onFailure { android.util.Log.w("AuthRepository", "google session mint failed: ${it.message}") }
        }
        resp.toUser()
    }

    // Reserved: iOS-cross-platform sign-in. No Android UI path. May be wired via
    // deep-link in a future build — backend integration preserved for parity.
    //
    // Mirrors iOS AuthViewModel.signInWithApple → ProfileService.registerUser flow.
    // Apple's email/name are only provided on FIRST sign-in; on subsequent logins
    // they may be null and the backend resolves the existing user via apple_id.
    // A placeholder email is used in that case (matches iOS "lookup-by-id@placeholder.local").
    override suspend fun signInWithApple(
        appleId: String,
        email: String?,
        name: String?,
        idToken: String?,
    ): Result<User> = runCatching {
        // iOS parity (AppleAuthService.swift:108-128): dual-store recovery cache.
        // FIRST sign-in: persist email+name into both SecureStorage (primary,
        // encrypted) and UserPreferences (DataStore fallback) keyed by appleId.
        // SUBSEQUENT sign-ins: when Apple omits email/name, recover from
        // SecureStorage first, then UserPreferences as fallback.
        if (!email.isNullOrBlank()) {
            secure.saveAppleEmail(appleId, email)
            prefs.setAppleEmailFallback(appleId, email)
        }
        if (!name.isNullOrBlank()) {
            secure.saveAppleName(appleId, name)
            prefs.setAppleNameFallback(appleId, name)
        }
        val recoveredEmail = email
            ?: secure.getAppleEmail(appleId)
            ?: prefs.getAppleEmailFallback(appleId)
        val recoveredName = name
            ?: secure.getAppleName(appleId)
            ?: prefs.getAppleNameFallback(appleId)

        val resp = try {
            api.signInWithApple(
                AppleSignInRequest(
                    email = recoveredEmail ?: "lookup-by-id@placeholder.local",
                    isGeneratedEmail = false,
                    appleId = appleId,
                    name = recoveredName,
                )
            )
        } catch (e: HttpException) {
            // iOS parity (ProfileService.swift:189-198): same 403 detail.error ==
            // "account_deleted" path — soft-deleted accounts cannot be re-used
            // even via Apple Sign-In with the same apple_id.
            if (e.code() == 403 && parseAccountDeletedError(e)) {
                throw AccountDeletedException()
            }
            throw e
        }
        secure.saveEmail(resp.userEmail)
        prefs.setUserEmail(resp.userEmail)
        // iOS parity (handleAuthSuccess isAuthenticated=true): survive cold launch (G1).
        prefs.setAuthenticated(true)
        // iOS parity (BirthDataView.swift:611-613): persist apple_id so subsequent
        // profile saves can include it for backend user lookup (handles Hide-My-Email).
        // setAppleUserId also clears any previously-stored googleUserId — the two
        // are mutually exclusive (mirrors AuthViewModel.swift:595-599).
        prefs.setAppleUserId(appleId)
        (resp.name ?: recoveredName)?.let { prefs.setUserName(it) }
        prefs.setSubscription(resp.isPremium, resp.planId ?: "")
        prefs.setAccessState(resp.accessState)
        // iOS parity (AuthViewModel.swift:264 / 549): mirror access_state into
        // lastAccessState so SplashViewModel/AppNav can route to WaitlistPending
        // on next launch when applicable.
        prefs.setLastAccessState(resp.accessState)
        // W7 parity + SECURITY: clear any prior active session before minting so a
        // stale/failed mint can never leave another user's JWT active for this user.
        sessionStore.clearActiveSession()
        // Mint a session JWT when the Apple id_token is available.
        if (!idToken.isNullOrBlank()) {
            runCatching { exchangeClient.signInWithApple(idToken, nonce = null) }
                .onFailure { android.util.Log.w("AuthRepository", "apple session mint failed: ${it.message}") }
        }
        resp.toUser()
    }

    /** iOS BirthDataViewModel.swift:277-303 parity — mint a guest session JWT
     *  with 3 attempts + exponential backoff so a transient failure at sign-in
     *  time doesn't leave the guest without a JWT (needed for later
     *  guest→registered /auth/upgrade). Best-effort: never blocks guest mode. */
    private suspend fun mintGuestSession(email: String) {
        var delayMs = 400L
        repeat(3) { attempt ->
            val ok = runCatching { exchangeClient.signInAsGuest(email, isGeneratedEmail = true) }
                .onFailure {
                    android.util.Log.w("AuthRepository", "guest mint attempt ${attempt + 1} failed: ${it.message}")
                }.isSuccess
            if (ok) return
            if (attempt < 2) {
                kotlinx.coroutines.delay(delayMs)
                delayMs *= 2
            }
        }
    }

    override suspend fun registerGuest(): Result<User> = runCatching {
        // iOS parity (AppleAuthService.signInAsGuest): generate guest email
        // locally and persist immediately. NO network call — guest mode must
        // work offline. Backend `register` is deferred to first authenticated
        // request (best-effort fire-and-forget).
        val guestEmail = "guest_${UUID.randomUUID().toString().replace("-", "").take(12)}@destinyai.app"
        secure.saveEmail(guestEmail)
        prefs.setUserEmail(guestEmail)
        prefs.setGuestUser(true)
        // iOS parity (guest sessions are authenticated sessions): persist the flag so a
        // cold launch keeps the guest in the app instead of routing to sign-in (G1).
        prefs.setAuthenticated(true)
        prefs.setSubscription(isPremium = false, planId = "free")
        prefs.setAccessState("granted")
        // iOS parity (AuthViewModel.swift:600-603): guest sessions clear any
        // stale appleUserID/googleUserID so a previous registered account's
        // provider IDs cannot leak into the guest's profile-sync calls.
        prefs.clearProviderIds()
        prefs.setLastAccessState("granted")
        // SECURITY: drop any prior user's active session before the guest mint so a
        // previous account's JWT can't be attached to this guest's requests.
        sessionStore.clearActiveSession()

        // Best-effort backend register; ignore failures so offline guests still proceed.
        try {
            val resp = api.register(RegisterRequest(email = guestEmail, isGeneratedEmail = true))
            // Reconcile with backend canonical values when reachable.
            secure.saveEmail(resp.userEmail)
            prefs.setUserEmail(resp.userEmail)
            prefs.setSubscription(resp.isPremium, resp.planId ?: "")
            prefs.setAccessState(resp.accessState)
            prefs.setLastAccessState(resp.accessState)
            // W7 parity: mint a guest session JWT so a later guest→registered
            // /auth/upgrade can authorize with the guest bearer.
            mintGuestSession(resp.userEmail)
            resp.toUser()
        } catch (e: Exception) {
            android.util.Log.w(
                "AuthRepository",
                "Guest backend register deferred (offline?): ${e.message}",
            )
            mintGuestSession(guestEmail)
            User(
                email = guestEmail,
                isGuestEmail = true,
                name = null,
                isPremium = false,
                planId = "free",
                accessState = "granted",
            )
        }
    }

    override suspend fun upgradeGuest(guestEmail: String, newEmail: String): Result<User> = runCatching {
        try {
            // iOS parity (ProfileService upgrade uses the GUEST's session JWT so the backend
            // ownership check identity.user_email == old_email passes). Resolve the guest
            // bearer; lazy-mint it if the per-email entry is missing (e.g. offline register
            // earlier). Without an explicit guest bearer the interceptor would attach the
            // new user's JWT → 403 body_email_mismatch → migration silently fails (M1).
            var guestJwt = sessionStore.sessionJwt(forEmail = guestEmail)
            if (guestJwt.isNullOrBlank()) {
                mintGuestSession(guestEmail)
                guestJwt = sessionStore.sessionJwt(forEmail = guestEmail)
            }
            if (guestJwt.isNullOrBlank()) {
                // Can't authenticate as the guest → the server migration would 403 and
                // orphan the guest's history. Surface loudly rather than swallow.
                throw IllegalStateException("guest_jwt_unavailable_for_upgrade")
            }
            val resp = api.upgradeGuest("Bearer $guestJwt", UpgradeRequest(oldEmail = guestEmail, newEmail = newEmail))
            secure.saveEmail(resp.userEmail)
            prefs.setUserEmail(resp.userEmail)
            // iOS parity (handleAuthSuccess): the upgraded user is authenticated — persist
            // the flag so a cold launch keeps them in the app (G1).
            prefs.setAuthenticated(true)
            prefs.setSubscription(resp.isPremium, resp.planId ?: "")
            // iOS parity (ProfileService.swift:303 clears guest session only AFTER a 200):
            // now that the migration succeeded, drop the guest's per-email JWT entry and
            // make the new user the active session.
            sessionStore.clearSession(forEmail = guestEmail)
            resp.toUser()
        } catch (e: HttpException) {
            // 409: email already registered (ConflictException)
            // 403: target account was soft-deleted (AccountDeletedException)
            when (e.code()) {
                409 -> throw ConflictException("email_conflict")
                403 -> throw AccountDeletedException()
                else -> throw e
            }
        }
    }

    override suspend fun clearSession() {
        // iOS parity (AuthViewModel.signOut): clear secure storage + prefs PLUS
        // explicitly reset cross-account state that lives in singletons or that
        // must trigger re-registration on next sign-in:
        //  - subscription/quota fields (defensive — prefs.clearAll() should cover, but
        //    in-memory caches in singletons may not be backed by DataStore).
        //  - FCM_TOKEN_REGISTERED flag so a new account re-registers its token.
        //  - access state / guest flag.
        //  - QuotaManager in-memory + persisted caches (mirrors iOS
        //    QuotaManager.resetForSignOut + SubscriptionManager teardown). Hooked
        //    via Provider so signOut still works if the manager hasn't been
        //    constructed yet (e.g. unauthenticated launch).
        // ProfileChangeBus is not reset here (it's an event bus, not state).
        // ProfileContextManager parity is achieved via prefs.clearAll() removing
        // ACTIVE_PROFILE_ID/EMAIL — Android has no separate singleton today.
        prefs.setSubscription(isPremium = false, planId = "free")
        prefs.setAccessState("granted")
        prefs.setLastAccessState("unknown")
        prefs.setGuestUser(false)
        prefs.setFcmTokenRegistered(false)
        // iOS parity (SubscriptionManager.swift:403-407): wipe server-mirrored
        // subscription metadata + provider IDs so account A's state cannot
        // bleed into account B on next sign-in.
        prefs.clearSubscriptionMeta()
        prefs.clearProviderIds()
        runCatching { quotaManager.get().resetForSignOut() }
        runCatching { billingManager.get().resetForSignOut() }
        // iOS parity (AuthViewModel.signOut:174-191): wipe the departing account's
        // owner_email-scoped Room caches BEFORE clearing prefs (which erases the email).
        // Without this, same-account rows persist as unbounded bloat and a server-deleted
        // compat match can reappear on next sign-in via upsert-only sync (F6).
        val departingEmail = runCatching { secure.getEmail() }.getOrNull()
        if (!departingEmail.isNullOrBlank()) {
            runCatching {
                astroDataCacheDao.deleteForUser(departingEmail)
                compatibilityHistoryDao.deleteAllForUser(departingEmail)
            }
        }
        sessionStore.clearActiveSession()
        secure.clearAll()
        prefs.clearAll()
    }

    override suspend fun signOutPreserveBirthData() {
        // iOS parity (ChatView.swift signOutAndReauth + Components/QuotaExhaustedView.swift onSignIn):
        // a softer sign-out used when a guest hits the quota wall and taps Sign In.
        // Clears just enough state for AuthScreen to route to login UI (not bounce
        // back to Main) without nuking the guest's birth profile. The new
        // registered account flow reads the preserved birth data and carries it forward.
        secure.clearAll()
        // Reset auth flag + guest flag so AuthScreen's loadSession() returns null user.
        prefs.setAuthenticated(false)
        prefs.setGuestUser(false)
        prefs.setUserEmail("")
        prefs.setUserName("")
        // Subscription / quota — match iOS resetForSignOut so account-A's plan
        // cannot leak into account-B's first sync.
        prefs.setSubscription(isPremium = false, planId = "free")
        prefs.setAccessState("granted")
        prefs.setLastAccessState("unknown")
        prefs.setFcmTokenRegistered(false)
        prefs.clearSubscriptionMeta()
        prefs.clearProviderIds()
        runCatching { quotaManager.get().resetForSignOut() }
        runCatching { billingManager.get().resetForSignOut() }
        sessionStore.clearActiveSession()
        // NOTE: do NOT call prefs.clearAll() — that wipes BIRTH_DOB/lat/lng/etc.
        // The whole point of this method is to preserve birth data so the user
        // doesn't have to re-enter it after re-auth.
    }

    /**
     * Fetch the server-side profile for [email]. Returns null on 404 or any
     * network/HTTP error so callers can fall through to the local-pref birth
     * profile path. Mirrors iOS [fetchAndRestoreProfile] returning false on error.
     *
     * iOS parity (ProfileService.swift fetchProfile + AuthViewModel.fetchAndRestoreProfile):
     * mirror analytics_consent and access_state from the server response onto local
     * prefs so launch-time UI (gate routing, consent toggle default) reflects the
     * canonical server state instead of stale on-device defaults.
     */
    override suspend fun fetchProfile(email: String): ProfileResponse? = try {
        val resp = api.getProfile(email)
        resp.analyticsConsent?.let { prefs.setAnalyticsConsent(it) }
        resp.accessState?.let { prefs.setAccessState(it) }
        resp
    } catch (e: HttpException) {
        // 404 = no profile saved yet for this user. Other HTTP errors are
        // treated as transient — never block sign-in on a profile read.
        null
    } catch (e: Exception) {
        android.util.Log.w("AuthRepository", "fetchProfile failed: ${e.message}", e)
        null
    }

    private fun RegisterResponse.toUser() = User(
        email = userEmail,
        isGuestEmail = isGeneratedEmail,
        name = name,
        googleId = googleId,
        isPremium = isPremium,
        planId = planId ?: "free_guest",
        accessState = accessState,
    )

    private fun StatusResponse.toUser() = User(
        email = userEmail,
        isGuestEmail = isGeneratedEmail,
        name = name,
        isPremium = isPremium,
        planId = planId ?: "free_guest",
        // Backend returns null when no quota gates the plan; coerce to 0 so the
        // domain model stays non-nullable. UI guards on dailyQuota > 0 already.
        dailyQuota = dailyQuota ?: 0,
        dailyUsed = dailyUsed ?: 0,
        accessState = accessState,
    )

    /**
     * Mirrors iOS ProfileService:189-198 — parse 403 response body and return true
     * when detail.error == "account_deleted". Used to convert backend's soft-delete
     * signal into a typed AccountDeletedException at the register/signIn boundary.
     */
    private fun parseAccountDeletedError(e: HttpException): Boolean = runCatching {
        val raw = e.response()?.errorBody()?.string().orEmpty()
        if (raw.isBlank()) return@runCatching false
        val root = com.google.gson.JsonParser.parseString(raw)
        if (!root.isJsonObject) return@runCatching false
        val detail = root.asJsonObject.get("detail") ?: return@runCatching false
        val errorField = when {
            detail.isJsonObject -> detail.asJsonObject.get("error")
                ?.takeIf { it.isJsonPrimitive }?.asString
            detail.isJsonPrimitive -> detail.asString
            else -> null
        }
        errorField == "account_deleted"
    }.getOrDefault(false)
}
