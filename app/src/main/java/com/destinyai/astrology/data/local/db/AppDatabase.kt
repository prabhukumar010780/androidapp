package com.destinyai.astrology.data.local.db

import androidx.room.*

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

// ── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities = [
        LocalChatThreadEntity::class,
        LocalChatMessageEntity::class,
        PartnerProfileEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun partnerDao(): PartnerDao
}
