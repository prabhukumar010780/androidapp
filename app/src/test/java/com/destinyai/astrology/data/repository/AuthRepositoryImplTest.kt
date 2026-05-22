package com.destinyai.astrology.data.repository

import com.destinyai.astrology.data.local.prefs.SecureStorage
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.RegisterRequest
import com.destinyai.astrology.data.remote.RegisterResponse
import com.destinyai.astrology.data.remote.UpgradeRequest
import com.destinyai.astrology.data.repository.impl.AuthRepositoryImpl
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.ui.auth.AccountDeletedException
import com.destinyai.astrology.ui.auth.ConflictException
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthRepositoryImplTest {

    private lateinit var api: AstroApiService
    private lateinit var secure: SecureStorage
    private lateinit var prefs: UserPreferences
    private lateinit var repo: AuthRepositoryImpl

    @BeforeEach
    fun setUp() {
        api = mockk(relaxed = true)
        secure = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        repo = AuthRepositoryImpl(api, secure, prefs)
    }

    // ── getSavedUser ──────────────────────────────────────────────────────────

    @Test
    fun `getSavedUser returns null when no email stored`() = runTest {
        every { secure.getEmail() } returns null
        assertNull(repo.getSavedUser())
    }

    @Test
    fun `getSavedUser fetches status from api when email stored`() = runTest {
        every { secure.getEmail() } returns "u@x.com"
        coEvery { api.getStatus("u@x.com") } returns mockk {
            every { userEmail } returns "u@x.com"
            every { isGeneratedEmail } returns false
            every { isPremium } returns false
            every { planId } returns "free_registered"
            every { dailyQuota } returns 5
            every { dailyUsed } returns 1
            every { accessState } returns "granted"
            every { name } returns null
        }
        val user = repo.getSavedUser()
        assertNotNull(user)
        assertEquals("u@x.com", user!!.email)
        assertFalse(user.isGuestEmail)
    }

    @Test
    fun `getSavedUser throws AccountDeletedException when api returns 404`() = runTest {
        every { secure.getEmail() } returns "deleted@x.com"
        coEvery { api.getStatus("deleted@x.com") } throws retrofit2.HttpException(
            okhttp3.ResponseBody.create(null, "").let {
                retrofit2.Response.error<Any>(404, it)
            }
        )
        assertThrows<AccountDeletedException> { repo.getSavedUser() }
    }

    // ── registerGuest ─────────────────────────────────────────────────────────

    @Test
    fun `registerGuest generates guest email and saves to secure storage`() = runTest {
        coEvery { api.register(any()) } returns RegisterResponse(
            userEmail = "guest_abc@destinyai.app",
            planId = "free_guest",
            isGeneratedEmail = true,
            isPremium = false,
            accessState = "granted",
            dailyQuota = 3,
            dailyUsed = 0,
        )

        val result = repo.registerGuest()

        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertTrue(user.isGuestEmail)
        verify { secure.saveEmail(any()) }
    }

    @Test
    fun `registerGuest returns failure on api error`() = runTest {
        coEvery { api.register(any()) } throws RuntimeException("network error")

        val result = repo.registerGuest()

        assertTrue(result.isFailure)
    }

    // ── signInWithGoogle ──────────────────────────────────────────────────────

    @Test
    fun `signInWithGoogle exchanges idToken with backend`() = runTest {
        coEvery { api.signInWithGoogle(any()) } returns RegisterResponse(
            userEmail = "google@user.com",
            planId = "free_registered",
            isGeneratedEmail = false,
            isPremium = false,
            accessState = "granted",
            dailyQuota = 5,
            dailyUsed = 0,
        )

        val result = repo.signInWithGoogle("test-id-token")

        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals("google@user.com", user.email)
        assertFalse(user.isGuestEmail)
        verify { secure.saveEmail("google@user.com") }
    }

    @Test
    fun `signInWithGoogle returns failure on api error`() = runTest {
        coEvery { api.signInWithGoogle(any()) } throws RuntimeException("auth failed")

        val result = repo.signInWithGoogle("bad-token")
        assertTrue(result.isFailure)
    }

    // ── upgradeGuest ──────────────────────────────────────────────────────────

    @Test
    fun `upgradeGuest migrates email in secure storage on success`() = runTest {
        every { secure.getEmail() } returns "guest_old@destinyai.app"
        coEvery { api.upgradeGuest(any()) } returns RegisterResponse(
            userEmail = "real@user.com",
            planId = "free_registered",
            isGeneratedEmail = false,
            isPremium = false,
            accessState = "granted",
            dailyQuota = 5,
            dailyUsed = 0,
        )

        val result = repo.upgradeGuest("guest_old@destinyai.app", "real@user.com")

        assertTrue(result.isSuccess)
        verify { secure.saveEmail("real@user.com") }
    }

    @Test
    fun `upgradeGuest returns ConflictException on 409`() = runTest {
        every { secure.getEmail() } returns "guest@destinyai.app"
        coEvery { api.upgradeGuest(any()) } throws retrofit2.HttpException(
            okhttp3.ResponseBody.create(null, "").let {
                retrofit2.Response.error<Any>(409, it)
            }
        )

        val result = repo.upgradeGuest("guest@destinyai.app", "existing@user.com")

        assertTrue(result.isFailure)
        assertInstanceOf(ConflictException::class.java, result.exceptionOrNull())
    }

    // ── clearSession ──────────────────────────────────────────────────────────

    @Test
    fun `clearSession clears secure storage`() = runTest {
        repo.clearSession()

        verify { secure.clearAll() }
    }

    @Test
    fun `clearSession clears user preferences`() = runTest {
        repo.clearSession()

        coVerify { prefs.clearAll() }
    }
}
