package com.destinyai.astrology.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entities ──────────────────────────────────────────────────────────────────

@Entity(tableName = "chat_threads")
data class LocalChatThreadEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_email") val ownerEmail: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "is_pinned", defaultValue = "0") val isPinned: Boolean = false,
    // iOS parity (LocalChatThread.profileId): scope threads to the active profile so
    // Switch Profile isolates history per profile. Null = legacy/self (owner) thread.
    @ColumnInfo(name = "profile_id") val profileId: String? = null,
    // iOS parity (LocalChatThread.primaryArea): drives the per-row life-area icon.
    @ColumnInfo(name = "primary_area") val primaryArea: String? = null,
)

@Entity(tableName = "chat_messages")
data class LocalChatMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    // iOS parity (LocalChatMessage) — assistant metadata persisted so a reopened
    // thread keeps its depth layers, tool/source chips, exec pill, rating, and
    // follow-up pills instead of degrading to plain text.
    @ColumnInfo(name = "follow_ups") val followUps: String? = null, // JSON array of strings
    @ColumnInfo(name = "advice") val advice: String? = null,
    @ColumnInfo(name = "timing") val timing: String? = null,
    @ColumnInfo(name = "tool_calls") val toolCalls: String? = null, // JSON array of strings
    @ColumnInfo(name = "sources") val sources: String? = null, // JSON array of strings
    @ColumnInfo(name = "execution_time_ms") val executionTimeMs: Double? = null,
    @ColumnInfo(name = "trace_id") val traceId: String? = null,
    @ColumnInfo(name = "area") val area: String? = null,
    @ColumnInfo(name = "rating") val rating: Int? = null,
)

@Entity(tableName = "partner_profiles")
data class PartnerProfileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_email") val ownerEmail: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: String,
    @ColumnInfo(name = "time_of_birth") val timeOfBirth: String,
    @ColumnInfo(name = "city_of_birth") val cityOfBirth: String,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    // iOS parity (PartnerProfile.swift @Model persists the FULL record): keep the cache
    // lossless so the offline fallback shows protection badges, gender, and compat flags.
    @ColumnInfo(name = "gender") val gender: String = "",
    @ColumnInfo(name = "birth_time_unknown") val birthTimeUnknown: Boolean = false,
    @ColumnInfo(name = "for_compatibility") val forCompatibility: Boolean = false,
    @ColumnInfo(name = "guardian_consent_given") val guardianConsentGiven: Boolean = false,
    @ColumnInfo(name = "is_self") val isSelf: Boolean = false,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    @ColumnInfo(name = "first_switched_at") val firstSwitchedAt: String? = null,
    @ColumnInfo(name = "timezone") val timezone: Double? = null,
)

@Entity(tableName = "compatibility_history")
data class CompatibilityHistoryEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "owner_email") val ownerEmail: String,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    @ColumnInfo(name = "boy_name") val boyName: String,
    @ColumnInfo(name = "boy_dob") val boyDob: String,
    @ColumnInfo(name = "boy_city") val boyCity: String,
    @ColumnInfo(name = "boy_time") val boyTime: String,
    @ColumnInfo(name = "girl_name") val girlName: String,
    @ColumnInfo(name = "girl_dob") val girlDob: String,
    @ColumnInfo(name = "girl_city") val girlCity: String,
    @ColumnInfo(name = "girl_time") val girlTime: String,
    @ColumnInfo(name = "total_score") val totalScore: Int,
    @ColumnInfo(name = "max_score") val maxScore: Int,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "comparison_group_id") val comparisonGroupId: String? = null,
    @ColumnInfo(name = "partner_index") val partnerIndex: Int? = null,
    @ColumnInfo(name = "result_json") val resultJson: String = "",
)

/**
 * Mirrors iOS AstroDataCache + TodaysPredictionCache (Services/AstroDataCache.swift,
 * TodaysPredictionCache.swift). Stores serialized JSON keyed by (kind, profile_id,
 * birth_hash, year, month) so charts/dasha/transits/today are not re-fetched on every
 * tab switch. `kind` discriminates the four shapes; nullable year/month let chart use
 * a forever cache, dasha use per-year, transits + today use per-year+month.
 */
@Entity(tableName = "astro_data_cache", primaryKeys = ["kind", "profile_id", "birth_hash", "year", "month"])
data class AstroDataCacheEntity(
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "birth_hash") val birthHash: String,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "month") val month: Int,
    @ColumnInfo(name = "owner_email") val ownerEmail: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "saved_at_ms") val savedAtMs: Long,
)

// ── DAOs ──────────────────────────────────────────────────────────────────────

@Dao
interface ChatThreadDao {
    @Query("SELECT * FROM chat_threads WHERE owner_email = :ownerEmail ORDER BY is_pinned DESC, updated_at DESC")
    suspend fun getThreadsForUser(ownerEmail: String): List<LocalChatThreadEntity>

    // iOS parity (ChatViewModel.loadHistory filtered by activeProfileId): scope the
    // feed to the active profile. profile_id IS NULL rows are legacy/self threads and
    // surface only when the self profile (profileId == null or == owner) is active.
    @Query(
        "SELECT * FROM chat_threads WHERE owner_email = :ownerEmail AND " +
            "(profile_id = :profileId OR (:profileId IS NULL AND profile_id IS NULL)) " +
            "ORDER BY is_pinned DESC, updated_at DESC",
    )
    suspend fun getThreadsForProfile(ownerEmail: String, profileId: String?): List<LocalChatThreadEntity>

    // Paginated query mirroring iOS dataManager.fetchChatThreadsPaginated (ChatView.swift:512-644).
    // Use offset/limit for incremental load-more from the history sheet.
    @Query("SELECT * FROM chat_threads WHERE owner_email = :ownerEmail ORDER BY is_pinned DESC, updated_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getThreadsForUserPaginated(ownerEmail: String, limit: Int, offset: Int): List<LocalChatThreadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thread: LocalChatThreadEntity)

    /**
     * iOS parity (a thread's title/createdAt/pin are set ONCE at creation, never
     * rewritten by later messages — ChatModels.swift:121-137): insert only if the
     * row is new. Combined with [touch] on every send this preserves pin, the
     * first-message title, createdAt, and primaryArea across the conversation
     * instead of the blind REPLACE that wiped them (D2).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(thread: LocalChatThreadEntity)

    /** Bump only updated_at so an existing thread re-sorts to the top on each send. */
    @Query("UPDATE chat_threads SET updated_at = :updatedAt WHERE id = :threadId")
    suspend fun touch(threadId: String, updatedAt: String)

    @Query("UPDATE chat_threads SET is_pinned = :pinned WHERE id = :threadId")
    suspend fun setPin(threadId: String, pinned: Boolean)

    // iOS parity (LocalChatThread.primaryArea): tag the thread's dominant life area
    // so the History row shows the matching icon.
    @Query("UPDATE chat_threads SET primary_area = :area WHERE id = :threadId")
    suspend fun setPrimaryArea(threadId: String, area: String)

    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun delete(threadId: String)

    @Query("DELETE FROM chat_threads WHERE owner_email = :ownerEmail")
    suspend fun deleteAllForUser(ownerEmail: String)

    /**
     * iOS parity (ChatHistorySyncService.syncFromServer clears local then repopulates
     * from the server set): prune local threads for an owner whose id is NOT in the
     * authoritative server list, so a thread deleted on another device / whose local
     * delete's server call failed doesn't linger and resurface (D8). Scoped delete
     * (not a full wipe) so surviving threads keep their messages.
     */
    @Query("DELETE FROM chat_threads WHERE owner_email = :ownerEmail AND id NOT IN (:keepIds)")
    suspend fun pruneNotIn(ownerEmail: String, keepIds: List<String>)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE thread_id = :threadId ORDER BY created_at ASC")
    suspend fun getMessagesForThread(threadId: String): List<LocalChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: LocalChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<LocalChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE thread_id = :threadId")
    suspend fun deleteForThread(threadId: String)

    /**
     * iOS parity (DataManager.deleteAllThreads called from
     * HistorySettingsManager.clearAllHistory): wipe every chat_messages row for
     * threads owned by the given account so Clear History flushes both halves
     * of the local store. Without this, the row count / preview helpers above
     * would still be served by stale rows after a clear.
     */
    @Query(
        "DELETE FROM chat_messages WHERE thread_id IN " +
            "(SELECT id FROM chat_threads WHERE owner_email = :ownerEmail)",
    )
    suspend fun deleteAllForUser(ownerEmail: String)

    /**
     * iOS parity (LocalChatThread.messageCount + .preview): the History sheet shows
     * a per-thread message count badge and a one-line subtitle preview drawn from
     * the most recent message. Single-row queries keep the cost negligible per
     * thread (paginated to 20 at a time).
     */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE thread_id = :threadId")
    suspend fun countMessagesForThread(threadId: String): Int

    @Query("SELECT content FROM chat_messages WHERE thread_id = :threadId ORDER BY created_at DESC LIMIT 1")
    suspend fun latestMessageContent(threadId: String): String?

    // iOS parity (ChatViewModel.loadThread rehydrates follow-up pills from the last
    // assistant message): fetch its persisted follow_ups JSON.
    @Query("SELECT follow_ups FROM chat_messages WHERE thread_id = :threadId AND role = 'assistant' ORDER BY created_at DESC LIMIT 1")
    suspend fun latestAssistantFollowUps(threadId: String): String?

    // iOS parity (ChatViewModel.submitRating persists rating locally): update the
    // stored rating so filled stars survive a thread reopen.
    @Query("UPDATE chat_messages SET rating = :rating WHERE id = :messageId")
    suspend fun updateRating(messageId: String, rating: Int)
}

@Dao
interface PartnerDao {
    @Query("SELECT * FROM partner_profiles WHERE owner_email = :ownerEmail")
    suspend fun getPartnersForUser(ownerEmail: String): List<PartnerProfileEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(partner: PartnerProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(partner: PartnerProfileEntity)

    @Query("DELETE FROM partner_profiles WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * iOS parity (DataManager.shared.deleteAllPartners(for: guestEmail) called by
     * AuthViewModel during guest→registered upgrade): purge a former owner's
     * partner-profile rows so the server-migrated rows can be re-pulled without
     * shadowing duplicates. Used by [com.destinyai.astrology.services.LoginSyncCoordinator].
     */
    @Query("DELETE FROM partner_profiles WHERE owner_email = :ownerEmail")
    suspend fun deleteForOwner(ownerEmail: String)
}

@Dao
interface CompatibilityHistoryDao {
    @Query("SELECT * FROM compatibility_history WHERE owner_email = :ownerEmail ORDER BY is_pinned DESC, timestamp_ms DESC")
    fun observeAll(ownerEmail: String): Flow<List<CompatibilityHistoryEntity>>

    /**
     * Synchronous snapshot of all saved matches for cache-reuse lookup at analyze time.
     * Mirrors iOS CompatibilityHistoryService.loadAll() (called inside findExistingMatch):
     * a deterministic read that doesn't depend on the observeAll Flow having emitted yet
     * (which can still be emptyList() on fresh screen entry / cold start → silent miss).
     */
    @Query("SELECT * FROM compatibility_history WHERE owner_email = :ownerEmail ORDER BY is_pinned DESC, timestamp_ms DESC")
    suspend fun getAllForUser(ownerEmail: String): List<CompatibilityHistoryEntity>

    /**
     * Lookup a single saved match by sessionId. Mirrors iOS
     * dataManager.fetchCompatibilityHistoryItem(by:) — used for deep-link
     * navigation from Home/History into the Match tab.
     */
    @Query("SELECT * FROM compatibility_history WHERE session_id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): CompatibilityHistoryEntity?

    /**
     * Lookup all saved matches that share a comparisonGroupId. Mirrors iOS
     * dataManager.fetchComparisonGroup(by:) — used for deep-link navigation
     * into a multi-partner group on the Match tab.
     */
    @Query("SELECT * FROM compatibility_history WHERE comparison_group_id = :groupId ORDER BY partner_index ASC")
    suspend fun getByGroupId(groupId: String): List<CompatibilityHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CompatibilityHistoryEntity)

    /**
     * iOS parity (CompatibilityHistoryService caps stored matches at 50,
     * `CompatibilityHistoryService.swift:13,201-204`): delete the oldest rows for
     * an owner beyond the newest [keep], so the local table can't grow unbounded.
     * Pinned rows are kept regardless (they float to the top by timestamp anyway;
     * this only trims the unpinned tail once total exceeds the cap).
     */
    @Query(
        "DELETE FROM compatibility_history WHERE owner_email = :ownerEmail AND session_id NOT IN (" +
            "SELECT session_id FROM compatibility_history WHERE owner_email = :ownerEmail " +
            "ORDER BY is_pinned DESC, timestamp_ms DESC LIMIT :keep)",
    )
    suspend fun trimToNewest(ownerEmail: String, keep: Int)

    @Query("UPDATE compatibility_history SET is_pinned = :pinned WHERE session_id = :sessionId")
    suspend fun setPin(sessionId: String, pinned: Boolean)

    @Query("DELETE FROM compatibility_history WHERE session_id = :sessionId")
    suspend fun delete(sessionId: String)

    /**
     * iOS parity (HistorySettingsManager.clearAllHistory step 3,
     * `HistorySettingsManager.swift:122`): wipe every saved match for the
     * owner so Clear History also flushes the local Match list.
     */
    @Query("DELETE FROM compatibility_history WHERE owner_email = :ownerEmail")
    suspend fun deleteAllForUser(ownerEmail: String)
}

/**
 * Mirrors iOS AstroDataCache + TodaysPredictionCache lookup/invalidate semantics.
 * Use kind="chart" with year=0,month=0 for the forever-cached full chart,
 * kind="dasha" with year=YYYY,month=0, kind="transits" with year=YYYY,month=MM,
 * kind="today" with year=YYYY,month=MM,day-encoded-into-month-key by callers.
 */
@Dao
interface AstroDataCacheDao {
    @Query(
        "SELECT * FROM astro_data_cache WHERE kind = :kind AND profile_id = :profileId AND " +
            "birth_hash = :birthHash AND year = :year AND month = :month LIMIT 1"
    )
    suspend fun get(
        kind: String,
        profileId: String,
        birthHash: String,
        year: Int,
        month: Int,
    ): AstroDataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AstroDataCacheEntity)

    /** Invalidate all entries for a profile on profile-switch (parity with iOS). */
    @Query("DELETE FROM astro_data_cache WHERE profile_id = :profileId")
    suspend fun deleteForProfile(profileId: String)

    /** Invalidate all entries for a user on logout. */
    @Query("DELETE FROM astro_data_cache WHERE owner_email = :ownerEmail")
    suspend fun deleteForUser(ownerEmail: String)

    /** Drop entries where the saved birth_hash no longer matches the current profile. */
    @Query(
        "DELETE FROM astro_data_cache WHERE profile_id = :profileId AND birth_hash != :currentBirthHash"
    )
    suspend fun deleteStaleForProfile(profileId: String, currentBirthHash: String)
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities = [
        LocalChatThreadEntity::class,
        LocalChatMessageEntity::class,
        PartnerProfileEntity::class,
        CompatibilityHistoryEntity::class,
        AstroDataCacheEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun partnerDao(): PartnerDao
    abstract fun compatibilityHistoryDao(): CompatibilityHistoryDao
    abstract fun astroDataCacheDao(): AstroDataCacheDao
}
