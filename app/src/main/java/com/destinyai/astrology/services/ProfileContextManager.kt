package com.destinyai.astrology.services

import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android equivalent of iOS ProfileContextManager (Services/ProfileContextManager.swift).
 *
 * Resolves the currently active profile's birth data with the same priority chain
 * iOS uses for the Switch Profile feature:
 *
 *   1. Active partner profile (active_profile_id != self) — fetched via listPartners
 *      and matched on the stored UUID. Partner must have valid coordinates.
 *   2. Self birth profile from prefs.getBirthProfile().
 *   3. Legacy `userBirthData_<email>` JSON blob (iOS UserDefaults parity, used by
 *      older app versions before the structured BIRTH_* keys existed).
 *
 * Used by feature ViewModels (e.g. ChartsViewModel) so a sheet/screen can load the
 * correct profile's chart even when the user is viewing a partner.
 */
@Singleton
class ProfileContextManager @Inject constructor(
    private val prefs: UserPreferences,
    private val api: AstroApiService,
) {

    /**
     * In-memory cache of (profileId → display name) for partners fetched via
     * [activeBirthData] / [activeProfileName]. Avoids round-tripping listPartners
     * twice for the same switch (Home + Notifications + any other consumer).
     * Cleared by [invalidate] after a successful add/edit/delete partner so the
     * cache can't go stale.
     */
    private val partnerNameCache = mutableMapOf<String, String>()

    /**
     * In-memory cache of (profileId → isSelf) for partners. Mirrors iOS
     * `PartnerProfile.isSelf` (`ProfileContextManager.swift:41-43`). The
     * primary user's profile is stored as a partner row with `isSelf = true`
     * but its `id` is a UUID — NOT the owner email — so naive
     * `activeId == email` checks misclassify it as a partner. This cache lets
     * [isUsingSelfProfile] answer correctly without an extra round-trip.
     */
    private val partnerIsSelfCache = mutableMapOf<String, Boolean>()

    fun invalidate() {
        partnerNameCache.clear()
        partnerIsSelfCache.clear()
    }

    /**
     * True when the active profile is the account owner's own profile. Mirrors
     * iOS `ProfileContextManager.isUsingSelf` (`ProfileContextManager.swift:41-43`)
     * which reads `activeProfile.isSelf`. We resolve via the cached partner row
     * (populated by [activeBirthData] / [activeProfileName]) and fall back to
     * a comparison against the owner email when nothing has been resolved yet.
     */
    suspend fun isUsingSelfProfile(): Boolean {
        val email = prefs.getUserEmail().orEmpty()
        val activeId = prefs.getActiveProfileId()
        if (activeId.isNullOrBlank() || activeId == email) return true
        partnerIsSelfCache[activeId]?.let { return it }
        val isSelf = runCatching {
            api.listPartners(email).firstOrNull { it.id == activeId }?.isSelf
        }.getOrNull()
        if (isSelf != null) {
            partnerIsSelfCache[activeId] = isSelf
            return isSelf
        }
        // Couldn't resolve (network failure with no cached row). Default to
        // "not self" only when an explicit non-email activeId is set —
        // matches iOS conservative fallback at ProfileContextManager.swift:42.
        return false
    }

    /**
     * Resolve the active profile's display name. Mirrors iOS
     * `ProfileContextManager.activeProfileName` — partner name when a partner is
     * active, the user's own name otherwise. Falls back to the email prefix when
     * everything else is blank, never returns null (Home greeting + avatar
     * always need *something* to render).
     */
    suspend fun activeProfileName(): String {
        val email = prefs.getUserEmail().orEmpty()
        val activeId = prefs.getActiveProfileId()
        if (activeId != null && activeId.isNotBlank() && activeId != email) {
            partnerNameCache[activeId]?.let { return it }
            val partner = runCatching {
                api.listPartners(email).firstOrNull { it.id == activeId }
            }.getOrNull()
            if (partner != null) {
                partnerIsSelfCache[activeId] = partner.isSelf
                if (partner.name.isNotBlank()) {
                    partnerNameCache[activeId] = partner.name
                    return partner.name
                }
            }
        }
        val selfName = prefs.getUserName()
        if (!selfName.isNullOrBlank()) return selfName
        return email.substringBefore('@').ifBlank { email }
    }

    /**
     * Resolve the active profile's birth data.
     *
     * @return BirthProfileDto for the active profile, or null when no profile (and
     *         no legacy fallback) can be located. Network failures fetching the
     *         partner list are non-fatal and degrade to the self profile.
     */
    suspend fun activeBirthData(): BirthProfileDto? {
        val email = prefs.getUserEmail()
        val activeId = prefs.getActiveProfileId()

        // Active profile is a partner (not self) — try to resolve from server list.
        if (!email.isNullOrBlank() && !activeId.isNullOrBlank() && activeId != email) {
            val partnerProfile = runCatching {
                val partners = api.listPartners(email)
                partners.firstOrNull { it.id == activeId }
            }.getOrNull()

            if (partnerProfile != null) {
                partnerIsSelfCache[activeId] = partnerProfile.isSelf
                if (partnerProfile.name.isNotBlank()) {
                    partnerNameCache[activeId] = partnerProfile.name
                }
                val lat = partnerProfile.latitude
                val lon = partnerProfile.longitude
                val dob = partnerProfile.dateOfBirth
                // Partner must have full coordinates to render a chart — fall through
                // to self when birth data is incomplete (iOS hasValidBirthData check).
                if (lat != null && lon != null && !dob.isNullOrBlank()) {
                    return BirthProfileDto(
                        dateOfBirth = dob,
                        timeOfBirth = partnerProfile.timeOfBirth ?: "12:00",
                        cityOfBirth = partnerProfile.cityOfBirth.orEmpty(),
                        latitude = lat,
                        longitude = lon,
                        gender = partnerProfile.gender.takeIf { it.isNotBlank() },
                        birthTimeUnknown = partnerProfile.birthTimeUnknown,
                        placeId = null,
                    )
                }
            }
        }

        // Self profile from structured prefs.
        val selfProfile = prefs.getBirthProfile()
        if (selfProfile != null) return selfProfile

        // Legacy fallback: iOS UserDefaults `userBirthData_<email>` JSON blob.
        val legacyJson = prefs.getUserBirthDataJson() ?: return null
        return decodeLegacyBirthData(legacyJson)
    }

    /**
     * Decode the legacy iOS-style `userBirthData_<email>` JSON blob into a BirthProfileDto.
     * Mirrors iOS BirthData.init(from:) — accepts the v1 schema with snake_case keys
     * and tolerates missing optional fields. Returns null on parse failure.
     */
    private fun decodeLegacyBirthData(json: String): BirthProfileDto? = try {
        val parsed = Gson().fromJson(json, LegacyBirthData::class.java) ?: return null
        if (parsed.dob.isNullOrBlank() || parsed.latitude == null || parsed.longitude == null) {
            null
        } else {
            BirthProfileDto(
                dateOfBirth = parsed.dob,
                timeOfBirth = parsed.time ?: "12:00",
                cityOfBirth = parsed.cityOfBirth.orEmpty(),
                latitude = parsed.latitude,
                longitude = parsed.longitude,
                gender = parsed.gender,
                birthTimeUnknown = parsed.birthTimeUnknown ?: false,
                placeId = parsed.placeId,
            )
        }
    } catch (_: JsonSyntaxException) {
        null
    } catch (_: Exception) {
        null
    }

    /**
     * Wire shape for the legacy iOS `userBirthData` JSON blob. Field names mirror
     * iOS BirthData.CodingKeys (Models/BirthData.swift) so a payload written by an
     * older iOS install can be read transparently when the user reinstalls into the
     * Android app on a synced account.
     */
    private data class LegacyBirthData(
        @SerializedName("dob") val dob: String? = null,
        @SerializedName("time") val time: String? = null,
        @SerializedName("latitude") val latitude: Double? = null,
        @SerializedName("longitude") val longitude: Double? = null,
        @SerializedName("city_of_birth") val cityOfBirth: String? = null,
        @SerializedName("gender") val gender: String? = null,
        @SerializedName("birth_time_unknown") val birthTimeUnknown: Boolean? = null,
        @SerializedName("place_id") val placeId: String? = null,
    )
}
