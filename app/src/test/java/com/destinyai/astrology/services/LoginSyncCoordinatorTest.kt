package com.destinyai.astrology.services

import com.destinyai.astrology.data.local.db.ChatThreadDao
import com.destinyai.astrology.data.local.db.PartnerDao
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.ChatThreadDto
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.data.repository.ChatRepository
import com.destinyai.astrology.data.repository.HomeRepository
import com.destinyai.astrology.data.local.prefs.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginSyncCoordinatorTest {

    private lateinit var api: AstroApiService
    private lateinit var coordinator: LoginSyncCoordinator

    @BeforeEach
    fun setUp() {
        api = mockk(relaxed = true)
        coordinator = LoginSyncCoordinator(
            api = api,
            chatRepository = mockk(relaxed = true),
            authRepository = mockk(relaxed = true),
            homeRepository = mockk(relaxed = true),
            quotaManager = mockk(relaxed = true),
            chatThreadDao = mockk(relaxed = true),
            partnerDao = mockk(relaxed = true),
            prefs = mockk(relaxed = true),
        )
    }

    @Test
    fun `syncAfterLogin calls listChatThreads exactly once`() = runTest {
        coEvery { api.listChatThreads(any()) } returns emptyList()

        coordinator.syncAfterLogin("u@x.com")

        coVerify(exactly = 1) { api.listChatThreads("u@x.com") }
    }

    @Test
    fun `syncAfterLogin completes even when API throws`() = runTest {
        coEvery { api.listChatThreads(any()) } throws RuntimeException("network error")

        // Should not rethrow — best-effort sync
        val result = coordinator.syncAfterLogin("u@x.com")
        assertEquals(emptyList<ChatThreadDto>(), result)
    }

    @Test
    fun `syncAfterLogin returns thread list`() = runTest {
        val threads = listOf(
            ChatThreadDto(
                threadId = "t1",
                title = "Thread 1",
                createdAt = "2024-01-01",
                updatedAt = "2024-01-01",
                isPinned = false,
            ),
        )
        coEvery { api.listChatThreads(any()) } returns threads

        val result = coordinator.syncAfterLogin("u@x.com")

        assertEquals(1, result.size)
        assertEquals("t1", result.first().threadId)
    }
}
