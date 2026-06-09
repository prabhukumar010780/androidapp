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
)

@Entity(tableName = "chat_messages")
data class LocalChatMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
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

    // Paginated query mirroring iOS dataManager.fetchChatThreadsPaginated (ChatView.swift:512-644).
    // Use offset/limit for incremental load-more from the history sheet.
    @Query("SELECT * FROM chat_threads WHERE owner_email = :ownerEmail ORDER BY is_pinned DESC, updated_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getThreadsForUserPaginated(ownerEmail: String, limit: Int, offset: Int): List<LocalChatThreadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thread: LocalChatThreadEntity)

    @Query("UPDATE chat_threads SET is_pinned = :pinned WHERE id = :threadId")
    suspend fun setPin(threadId: String, pinned: Boolean)

    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun delete(threadId: String)

    @Query("DELETE FROM chat_threads WHERE owner_email = :ownerEmail")
    suspend fun deleteAllForUser(ownerEmail: String)
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
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun partnerDao(): PartnerDao
    abstract fun compatibilityHistoryDao(): CompatibilityHistoryDao
    abstract fun astroDataCacheDao(): AstroDataCacheDao
}
