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

// ── DAOs ──────────────────────────────────────────────────────────────────────

@Dao
interface ChatThreadDao {
    @Query("SELECT * FROM chat_threads WHERE owner_email = :ownerEmail ORDER BY updated_at DESC")
    suspend fun getThreadsForUser(ownerEmail: String): List<LocalChatThreadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thread: LocalChatThreadEntity)

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
}

@Dao
interface CompatibilityHistoryDao {
    @Query("SELECT * FROM compatibility_history WHERE owner_email = :ownerEmail ORDER BY is_pinned DESC, timestamp_ms DESC")
    fun observeAll(ownerEmail: String): Flow<List<CompatibilityHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CompatibilityHistoryEntity)

    @Query("UPDATE compatibility_history SET is_pinned = :pinned WHERE session_id = :sessionId")
    suspend fun setPin(sessionId: String, pinned: Boolean)

    @Query("DELETE FROM compatibility_history WHERE session_id = :sessionId")
    suspend fun delete(sessionId: String)
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities = [
        LocalChatThreadEntity::class,
        LocalChatMessageEntity::class,
        PartnerProfileEntity::class,
        CompatibilityHistoryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun partnerDao(): PartnerDao
    abstract fun compatibilityHistoryDao(): CompatibilityHistoryDao
}
