package com.destinyai.astrology.data.repository.impl

import android.util.Log
import com.destinyai.astrology.data.local.prefs.SecureStorage
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AnalyticsConsentRequest
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.remote.CreatePartnerRequest
import com.destinyai.astrology.data.remote.DeleteAccountRequest
import com.destinyai.astrology.data.remote.PartnerRequest
import com.destinyai.astrology.data.remote.ProfileRequest
import com.destinyai.astrology.data.remote.ProfileResponse
import com.destinyai.astrology.data.remote.StatusResponse
import com.destinyai.astrology.data.remote.SuccessResponse
import com.destinyai.astrology.data.remote.UpgradeRequest
import com.destinyai.astrology.data.repository.AccountDeletionBlockedException
import com.destinyai.astrology.data.repository.BirthDataTakenException
import com.destinyai.astrology.data.repository.ProfileRepository
import com.destinyai.astrology.ui.auth.AccountDeletedError
import com.destinyai.astrology.ui.auth.ArchivedGuestError
import com.destinyai.astrology.ui.auth.RegisteredUserConflictError
import com.google.gson.JsonParser
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProfileRepository"

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val api: AstroApiService,
    private val secure: SecureStorage,
    private val prefs: UserPreferences,
) : ProfileRepository {

    override suspend fun fetchProfile(email: String): ProfileResponse? = try {
        api.getProfile(email)
    } catch (e: HttpException) {
        // 404 = no profile saved yet; other HTTP errors treated as transient.
        null
    } catch (e: Exception) {
        Log.w(TAG, "fetchProfile failed: ${e.message}", e)
        null
    }

    override suspend fun saveProfile(
        email: String,
        userName: String?,
        userType: String,
        isGeneratedEmail: Boolean,
        birthProfile: BirthProfileDto,
        appleId: String?,
        googleId: String?,
    ): ProfileResponse {
        return try {
            api.saveProfile(
                ProfileRequest(
                    email = email,
                    userName = userName?.ifBlank { null },
                    userType = userType,
                    isGeneratedEmail = isGeneratedEmail,
                    birthProfile = birthProfile,
                    appleId = appleId?.takeIf { it.isNotBlank() },
                    googleId = googleId?.takeIf { it.isNotBlank() },
                )
            )
        } catch (e: HttpException) {
            if (e.code() == 409) {
                throw parseBirthDataConflict(e)
            }
            throw e
        }
    }

    override suspend fun saveProfile(
        email: String,
        userName: String,
        dateOfBirth: String,
        timeOfBirth: String,
        cityOfBirth: String,
        latitude: Double,
        longitude: Double,
        isGuest: Boolean,
        gender: String,
        birthTimeUnknown: Boolean,
        placeId: String?,
        appleId: String?,
        googleId: String?,
    ): ProfileResponse {
        val dto = BirthProfileDto(
            dateOfBirth = dateOfBirth,
            timeOfBirth = timeOfBirth,
            cityOfBirth = cityOfBirth,
            latitude = latitude,
            longitude = longitude,
            gender = gender.ifBlank { null },
            birthTimeUnknown = birthTimeUnknown,
            placeId = placeId?.takeIf { it.isNotBlank() },
        )
        return saveProfile(
            email = email,
            userName = userName,
            userType = if (isGuest) "guest" else "registered",
            isGeneratedEmail = isGuest,
            birthProfile = dto,
            appleId = appleId,
            googleId = googleId,
        )
    }

    /**
     * iOS parity (ProfileService.restoreProfileLocally): mirror server fields
     * onto DataStore + SecureStorage, then bootstrap the self partner profile.
     */
    override suspend fun restoreProfileLocally(profile: ProfileResponse): Boolean {
        val email = profile.userEmail
        if (email.isBlank()) return false

        secure.saveEmail(email)
        prefs.setUserEmail(email)
        profile.userName?.takeIf { it.isNotBlank() }?.let { prefs.setUserName(it) }
        prefs.setSubscription(profile.isPremium, profile.planId ?: "free")

        val birth = profile.birthProfile ?: return false
        try {
            prefs.setBirthProfile(birth)
            prefs.setHasBirthData(true)
        } catch (e: Exception) {
            Log.w(TAG, "restoreProfileLocally — setBirthProfile failed: ${e.message}", e)
        }

        // iOS parity: bootstrap the self partner row so Switch Profile works.
        runCatching {
            createSelfPartnerProfile(
                email = email,
                userName = profile.userName ?: "Me",
                birthProfile = birth,
            )
        }

        return true
    }

    override suspend fun deleteAccount(email: String): SuccessResponse {
        return try {
            api.deleteAccount(DeleteAccountRequest(userEmail = email))
        } catch (e: HttpException) {
            if (e.code() == 403) {
                // iOS parity (ProfileService.swift:537-544): parse detail string;
                // fall back to the generic active-subscription guard message.
                val detail = runCatching {
                    val raw = e.response()?.errorBody()?.string().orEmpty()
                    if (raw.isBlank()) null
                    else JsonParser.parseString(raw)
                        .asJsonObject.get("detail")?.asString
                }.getOrNull()
                throw AccountDeletionBlockedException(
                    detail ?: "Please cancel your subscription before deleting your account."
                )
            }
            throw e
        }
    }

    override suspend fun getUserStatus(email: String): StatusResponse {
        return api.getStatus(email)
    }

    override suspend fun updateAnalyticsConsent(email: String, consent: Boolean): SuccessResponse {
        return api.updateAnalyticsConsent(
            AnalyticsConsentRequest(email = email, consent = consent)
        )
    }

    override suspend fun createSelfPartnerProfile(
        email: String,
        userName: String,
        birthProfile: BirthProfileDto,
    ) {
        if (email.isBlank()) return
        try {
            // Idempotency check — skip when a self profile already exists.
            val existing = runCatching { api.listPartners(email) }.getOrNull()
            if (existing != null && existing.any { it.isSelf }) {
                Log.d(TAG, "createSelfPartnerProfile: self profile already exists")
                return
            }
            val firstName = userName.trim().split(" ").firstOrNull()?.ifBlank { null } ?: userName
            api.addPartner(
                CreatePartnerRequest(
                    userEmail = email,
                    profile = PartnerRequest(
                        name = firstName.ifBlank { "Me" },
                        gender = birthProfile.gender ?: "",
                        dateOfBirth = birthProfile.dateOfBirth,
                        timeOfBirth = birthProfile.timeOfBirth,
                        cityOfBirth = birthProfile.cityOfBirth,
                        latitude = birthProfile.latitude,
                        longitude = birthProfile.longitude,
                        birthTimeUnknown = birthProfile.birthTimeUnknown,
                        isSelf = true,
                    ),
                    consentGiven = true,
                )
            )
        } catch (e: Exception) {
            // iOS parity: createSelfPartnerProfile is fire-and-forget — log only.
            Log.w(TAG, "createSelfPartnerProfile failed: ${e.message}", e)
        }
    }

    override suspend fun upgradeGuestToRegistered(oldEmail: String, newEmail: String): String? {
        if (oldEmail == newEmail) {
            Log.d(TAG, "upgradeGuestToRegistered: old and new email same, skipping")
            return newEmail
        }
        return try {
            val resp = api.upgradeGuest(UpgradeRequest(oldEmail = oldEmail, newEmail = newEmail))
            resp.userEmail
        } catch (e: HttpException) {
            // iOS parity (ProfileService.swift:189-198 + AuthErrors.kt):
            //   403 detail.error == "account_deleted" → AccountDeletedError
            //   409 detail.error == "archived_guest"  → ArchivedGuestError
            //   409 detail.error == "registered_user_conflict" → RegisteredUserConflictError
            // Anything else: log and return null (best-effort upgrade).
            when (e.code()) {
                403 -> if (parseDetailErrorIs(e, "account_deleted")) {
                    val msg = parseDetailField(e.response()?.errorBody()?.string().orEmpty(), "message")
                    throw AccountDeletedError(serverMessage = msg)
                } else {
                    Log.w(TAG, "upgrade 403 (non-deleted): ${e.message}")
                    null
                }
                409 -> {
                    val raw = e.response()?.errorBody()?.string().orEmpty()
                    val errorType = parseDetailField(raw, "error")
                    val provider = parseDetailField(raw, "provider")
                    when (errorType) {
                        "archived_guest" -> {
                            val upgradedTo = parseDetailField(raw, "upgraded_to_email")
                            val msg = parseDetailField(raw, "message")
                                ?: "This guest session has already been migrated."
                            throw ArchivedGuestError(
                                upgradedToEmail = upgradedTo,
                                provider = provider,
                                msg = msg,
                            )
                        }
                        "registered_user_conflict" -> {
                            val masked = parseDetailField(raw, "masked_email")
                                ?: parseDetailField(raw, "existing_email")
                                ?: parseDetailField(raw, "email")
                            throw RegisteredUserConflictError(
                                maskedEmail = masked,
                                provider = provider,
                            )
                        }
                        else -> {
                            // Unknown 409 detail.error — surface as generic conflict.
                            val masked = parseDetailField(raw, "masked_email")
                                ?: parseDetailField(raw, "existing_email")
                            throw RegisteredUserConflictError(
                                maskedEmail = masked,
                                provider = provider,
                            )
                        }
                    }
                }
                else -> {
                    Log.w(TAG, "upgrade failed status=${e.code()}: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "upgrade non-HTTP failure: ${e.message}", e)
            null
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun parseBirthDataConflict(e: HttpException): BirthDataTakenException {
        val raw = e.response()?.errorBody()?.string().orEmpty()
        val existingEmail = parseDetailField(raw, "existing_email")
        val provider = parseDetailField(raw, "provider")
        return BirthDataTakenException(existingEmail = existingEmail, provider = provider)
    }

    private fun parseDetailErrorIs(e: HttpException, expected: String): Boolean = runCatching {
        val raw = e.response()?.errorBody()?.string().orEmpty()
        parseDetailField(raw, "error") == expected
    }.getOrDefault(false)

    private fun parseDetailField(raw: String, field: String): String? = runCatching {
        if (raw.isBlank()) return@runCatching null
        val root = JsonParser.parseString(raw)
        if (!root.isJsonObject) return@runCatching null
        val detail = root.asJsonObject.get("detail") ?: return@runCatching null
        when {
            detail.isJsonObject -> detail.asJsonObject.get(field)
                ?.takeIf { it.isJsonPrimitive }?.asString
            detail.isJsonPrimitive && field == "error" -> detail.asString
            else -> null
        }
    }.getOrNull()
}
