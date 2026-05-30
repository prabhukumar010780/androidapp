package com.destinyai.astrology.data.repository

import com.destinyai.astrology.data.local.db.ChatMessageDao
import com.destinyai.astrology.data.local.db.ChatThreadDao
import com.destinyai.astrology.data.local.db.LocalChatMessageEntity
import com.destinyai.astrology.data.local.db.LocalChatThreadEntity
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.repository.impl.ChatRepositoryImpl
import com.destinyai.astrology.domain.model.ChatMessage
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatRepositoryImplTest {

    private lateinit var api: AstroApiService
    private lateinit var threadDao: ChatThreadDao
    private lateinit var messageDao: ChatMessageDao
    private lateinit var prefs: UserPreferences
    private lateinit var repo: ChatRepositoryImpl

    @BeforeEach
    fun setUp() {
        api = mockk(relaxed = true)
        threadDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        repo = ChatRepositoryImpl(api, threadDao, messageDao, prefs)
    }

    // ── loadHistory ───────────────────────────────────────────────────────────

    @Test
    fun `loadHistory returns empty list when no threads in db`() = runTest {
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { threadDao.getThreadsForUser("u@x.com") } returns emptyList()

        val history = repo.loadHistory()
        assertTrue(history.isEmpty())
    }

    @Test
    fun `loadHistory maps db entities to domain ChatThread`() = runTest {
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { threadDao.getThreadsForUser("u@x.com") } returns listOf(
            LocalChatThreadEntity("t1", "u@x.com", "Thread 1", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z"),
            LocalChatThreadEntity("t2", "u@x.com", "Thread 2", "2026-01-02T00:00:00Z", "2026-01-02T00:00:00Z"),
        )

        val history = repo.loadHistory()
        assertEquals(2, history.size)
        assertEquals("t1", history[0].id)
        assertEquals("Thread 1", history[0].title)
    }

    // ── loadThread ────────────────────────────────────────────────────────────

    @Test
    fun `loadThread maps db messages to domain ChatMessage with correct roles`() = runTest {
        coEvery { messageDao.getMessagesForThread("thread-123") } returns listOf(
            LocalChatMessageEntity("m1", "thread-123", "user", "Hello", "2026-01-01T00:00:00Z"),
            LocalChatMessageEntity("m2", "thread-123", "assistant", "Namaste!", "2026-01-01T00:00:01Z"),
        )

        val messages = repo.loadThread("thread-123")

        assertEquals(2, messages.size)
        assertEquals(ChatMessage.Role.USER, messages[0].role)
        assertEquals(ChatMessage.Role.ASSISTANT, messages[1].role)
        assertEquals("Hello", messages[0].content)
    }

    @Test
    fun `loadThread returns empty list for unknown thread`() = runTest {
        coEvery { messageDao.getMessagesForThread("unknown") } returns emptyList()
        assertTrue(repo.loadThread("unknown").isEmpty())
    }

    // ── sendMessage ───────────────────────────────────────────────────────────

    @Test
    fun `sendMessage emits chunks from streaming api`() = runTest {
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { prefs.getBirthProfile() } returns BirthProfileDto(
            dateOfBirth = "1980-07-01",
            timeOfBirth = "06:32",
            cityOfBirth = "Bhilai",
            latitude = 21.21,
            longitude = 81.39,
        )
        coEvery { api.streamPredict(any()) } returns
            "event: answer\ndata: {\"answer\":\"Hello World\"}\n\nevent: done\ndata: {}\n\n"
                .toByteArray()
                .toResponseBody("text/event-stream".toMediaType())

        val results = repo.sendMessage("session-1", "What is my sun sign?").toList()
        val successChunks = results.filter { it.isSuccess }.map { it.getOrThrow() }
        assertTrue(successChunks.isNotEmpty())
    }

    @Test
    fun `sendMessage emits failure on network error`() = runTest {
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { prefs.getBirthProfile() } returns mockk(relaxed = true)
        coEvery { api.streamPredict(any()) } throws RuntimeException("network error")

        val results = repo.sendMessage("session-x", "test").toList()
        assertTrue(results.any { it.isFailure })
    }

    // ── deleteThread ──────────────────────────────────────────────────────────

    @Test
    fun `deleteThread calls api deleteChatThread and clears local db`() = runTest {
        coEvery { prefs.getUserEmail() } returns "u@x.com"

        repo.deleteThread("thread-abc")

        coVerify { api.deleteChatThread("u@x.com", "thread-abc") }
        coVerify { threadDao.delete("thread-abc") }
        coVerify { messageDao.deleteForThread("thread-abc") }
    }

    @Test
    fun `deleteThread still clears local db when api call fails`() = runTest {
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { api.deleteChatThread(any(), any()) } throws RuntimeException("api error")

        repo.deleteThread("thread-abc")

        coVerify { threadDao.delete("thread-abc") }
        coVerify { messageDao.deleteForThread("thread-abc") }
    }

    @Test
    fun `deleteThread does nothing when no user email`() = runTest {
        coEvery { prefs.getUserEmail() } returns null

        repo.deleteThread("thread-abc")

        coVerify(exactly = 0) { api.deleteChatThread(any(), any()) }
        coVerify(exactly = 0) { threadDao.delete(any()) }
    }
}
