package com.destinyai.astrology.data.repository.impl

import com.destinyai.astrology.data.local.prefs.SecureStorage
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.*
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.ui.auth.AccountDeletedException
import com.destinyai.astrology.ui.auth.ConflictException
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AstroApiService,
    private val secure: SecureStorage,
    private val prefs: UserPreferences,
) : AuthRepository {

    override suspend fun getSavedUser(): User? {
        val email = secure.getEmail() ?: return null
        return try {
            api.getStatus(email).toUser()
        } catch (e: HttpException) {
            when (e.code()) {
                // 404: user not found in DB (deleted or never existed)
                404 -> throw AccountDeletedException()
                // 403: backend returns this for account_deleted on register/upgrade, not getStatus.
                // Mapped conservatively to AccountDeletedException rather than ArchivedGuestError.
                403 -> throw AccountDeletedException()
                else -> null
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> = runCatching {
        val resp = api.signInWithGoogle(GoogleSignInRequest(idToken = idToken))
        secure.saveEmail(resp.userEmail)
        prefs.setUserEmail(resp.userEmail)
        resp.name?.let { prefs.setUserName(it) }
        prefs.setSubscription(resp.isPremium, resp.planId)
        prefs.setAccessState(resp.accessState)
        resp.toUser()
    }

    override suspend fun registerGuest(): Result<User> = runCatching {
        val guestEmail = "guest_${UUID.randomUUID().toString().replace("-", "").take(12)}@destinyai.app"
        val resp = api.register(RegisterRequest(email = guestEmail, isGeneratedEmail = true))
        secure.saveEmail(resp.userEmail)
        prefs.setUserEmail(resp.userEmail)
        prefs.setSubscription(resp.isPremium, resp.planId)
        prefs.setAccessState(resp.accessState)
        resp.toUser()
    }

    override suspend fun upgradeGuest(guestEmail: String, newEmail: String): Result<User> = runCatching {
        try {
            val resp = api.upgradeGuest(UpgradeRequest(oldEmail = guestEmail, newEmail = newEmail))
            secure.saveEmail(resp.userEmail)
            prefs.setUserEmail(resp.userEmail)
            prefs.setSubscription(resp.isPremium, resp.planId)
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
        secure.clearAll()
        prefs.clearAll()
    }

    private fun RegisterResponse.toUser() = User(
        email = userEmail,
        isGuestEmail = isGeneratedEmail,
        name = name,
        googleId = googleId,
        isPremium = isPremium,
        planId = planId,
        accessState = accessState,
    )

    private fun StatusResponse.toUser() = User(
        email = userEmail,
        isGuestEmail = isGeneratedEmail,
        name = name,
        isPremium = isPremium,
        planId = planId,
        dailyQuota = dailyQuota,
        dailyUsed = dailyUsed,
        accessState = accessState,
    )
}
