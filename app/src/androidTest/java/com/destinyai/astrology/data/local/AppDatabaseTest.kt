package com.destinyai.astrology.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.destinyai.astrology.data.local.db.AppDatabase
import com.destinyai.astrology.data.local.db.LocalChatMessageEntity
import com.destinyai.astrology.data.local.db.LocalChatThreadEntity
import com.destinyai.astrology.data.local.db.PartnerProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented Room database tests.
 * Uses an in-memory database — no real storage needed.
 * Run: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    // ── Chat Thread DAO ────────────────────────────────────────────────────────

    @Test
    fun insertAndGetThread() = runTest {
        val thread = LocalChatThreadEntity(
            id = "thread-001",
            ownerEmail = "user@test.com",
            title = "Test Thread",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )
        db.chatThreadDao().insert(thread)

        val threads = db.chatThreadDao().getThreadsForUser("user@test.com")
        assertEquals(1, threads.size)
        assertEquals("thread-001", threads[0].id)
    }

    @Test
    fun insertMultipleThreadsReturnsAllForOwner() = runTest {
        db.chatThreadDao().insert(LocalChatThreadEntity("t1", "u@t.com", "A", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"))
        db.chatThreadDao().insert(LocalChatThreadEntity("t2", "u@t.com", "B", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"))
        db.chatThreadDao().insert(LocalChatThreadEntity("t3", "other@t.com", "C", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"))

        val threads = db.chatThreadDao().getThreadsForUser("u@t.com")
        assertEquals(2, threads.size)
    }

    @Test
    fun deleteThreadRemovesIt() = runTest {
        val thread = LocalChatThreadEntity("del-t1", "u@t.com", "Delete Me", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z")
        db.chatThreadDao().insert(thread)
        db.chatThreadDao().delete("del-t1")

        val threads = db.chatThreadDao().getThreadsForUser("u@t.com")
        assertTrue(threads.none { it.id == "del-t1" })
    }

    @Test
    fun deleteAllThreadsForUserClearsOnlyThatUser() = runTest {
        db.chatThreadDao().insert(LocalChatThreadEntity("x1", "clear@t.com", "X1", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"))
        db.chatThreadDao().insert(LocalChatThreadEntity("x2", "keep@t.com", "X2", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"))

        db.chatThreadDao().deleteAllForUser("clear@t.com")

        assertEquals(0, db.chatThreadDao().getThreadsForUser("clear@t.com").size)
        assertEquals(1, db.chatThreadDao().getThreadsForUser("keep@t.com").size)
    }

    // ── Chat Message DAO ───────────────────────────────────────────────────────

    @Test
    fun insertAndGetMessagesForThread() = runTest {
        db.chatMessageDao().insert(LocalChatMessageEntity(
            id = "msg-001",
            threadId = "thread-abc",
            role = "user",
            content = "Hello",
            createdAt = "2026-01-01T00:00:00Z",
        ))
        db.chatMessageDao().insert(LocalChatMessageEntity(
            id = "msg-002",
            threadId = "thread-abc",
            role = "assistant",
            content = "Namaste!",
            createdAt = "2026-01-01T00:00:01Z",
        ))

        val msgs = db.chatMessageDao().getMessagesForThread("thread-abc")
        assertEquals(2, msgs.size)
    }

    @Test
    fun messagesForDifferentThreadAreIsolated() = runTest {
        db.chatMessageDao().insert(LocalChatMessageEntity("m1", "thread-A", "user", "Hi A", "2026-01-01T00:00:00Z"))
        db.chatMessageDao().insert(LocalChatMessageEntity("m2", "thread-B", "user", "Hi B", "2026-01-01T00:00:00Z"))

        assertEquals(1, db.chatMessageDao().getMessagesForThread("thread-A").size)
        assertEquals(1, db.chatMessageDao().getMessagesForThread("thread-B").size)
    }

    @Test
    fun deleteMessagesForThreadClearsThread() = runTest {
        db.chatMessageDao().insert(LocalChatMessageEntity("m3", "thread-del", "user", "Bye", "2026-01-01T00:00:00Z"))
        db.chatMessageDao().deleteForThread("thread-del")

        assertEquals(0, db.chatMessageDao().getMessagesForThread("thread-del").size)
    }

    // ── Partner Profile DAO ────────────────────────────────────────────────────

    @Test
    fun insertAndGetPartners() = runTest {
        val partner = PartnerProfileEntity(
            id = "partner-001",
            ownerEmail = "me@test.com",
            name = "Smita",
            dateOfBirth = "1980-11-13",
            timeOfBirth = "09:30",
            cityOfBirth = "Belgaum",
            latitude = 15.84,
            longitude = 74.49,
        )
        db.partnerDao().insert(partner)

        val partners = db.partnerDao().getPartnersForUser("me@test.com")
        assertEquals(1, partners.size)
        assertEquals("Smita", partners[0].name)
    }

    @Test
    fun deletePartnerRemovesById() = runTest {
        db.partnerDao().insert(PartnerProfileEntity("p-del", "me@t.com", "Del", "1990-01-01", "08:00", "Mumbai", 19.07, 72.87))
        db.partnerDao().delete("p-del")

        assertTrue(db.partnerDao().getPartnersForUser("me@t.com").none { it.id == "p-del" })
    }

    @Test
    fun updatePartnerReplacesExisting() = runTest {
        val original = PartnerProfileEntity("p-upd", "me@t.com", "Old Name", "1990-01-01", "08:00", "Delhi", 28.61, 77.20)
        db.partnerDao().insert(original)

        val updated = original.copy(name = "New Name")
        db.partnerDao().insertOrReplace(updated)

        val result = db.partnerDao().getPartnersForUser("me@t.com").first { it.id == "p-upd" }
        assertEquals("New Name", result.name)
    }

    @Test
    fun multiplePartnersForSameOwner() = runTest {
        db.partnerDao().insert(PartnerProfileEntity("pp1", "owner@t.com", "P1", "1985-01-01", "07:00", "Chennai", 13.08, 80.27))
        db.partnerDao().insert(PartnerProfileEntity("pp2", "owner@t.com", "P2", "1987-06-15", "12:00", "Hyderabad", 17.38, 78.48))

        assertEquals(2, db.partnerDao().getPartnersForUser("owner@t.com").size)
    }
}
