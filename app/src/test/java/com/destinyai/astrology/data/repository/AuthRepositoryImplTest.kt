package com.destinyai.astrology.data.repository

import com.destinyai.astrology.data.local.prefs.SecureStorage
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.RegisterRequest
import com.destinyai.astrology.data.remote.RegisterResponse
import com.destinyai.astrology.data.remote.UpgradeRequest
import com.destinyai.astrology.data.repository.impl.AuthRepositoryImpl
import com.destinyai.astrology.domain.model.User
import com.destinyai.astrology.services.QuotaManager
import com.destinyai.astrology.ui.auth.AccountDeletedException
import com.destinyai.astrology.ui.auth.ConflictException
import io.mockk.*
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthRepositoryImplTest {

    private lateinit var api: AstroApiService
    private lateinit var secure: SecureStorage
    private lateinit var prefs: UserPreferences
    private lateinit var quotaManager: QuotaManager
    private lateinit var repo: AuthRepositoryImpl

    @BeforeEach
    fun setUp() {
        api = mockk(relaxed = true)
        secure = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        quotaManager = mockk(relaxed = true)
        repo = AuthRepositoryImpl(api, secure, prefs, Provider { quotaManager })
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

        // iOS parity (AppleAuthService.signInAsGuest): guest registration is
        // offline-first — the local guest email/state is persisted unconditionally
        // and the backend /register call is best-effort. Network errors are logged
        // and ignored so guests can use the app without connectivity. registerGuest
        // therefore always returns Result.success regardless of api outcome.
        assertTrue(result.isSuccess)
        // The local guest email must still be persisted so chat/compat work offline.
        verify { secure.saveEmail(any()) }
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

        val result = repo.signInWithGoogle(
            email = "google@user.com",
            googleId = "google-sub-123",
            name = "Jane Doe",
            idToken = "test-id-token",
        )

        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals("google@user.com", user.email)
        assertFalse(user.isGuestEmail)
        verify { secure.saveEmail("google@user.com") }
        // Verify backend payload matches Pydantic RegisterRequest schema
        coVerify {
            api.signInWithGoogle(
                match {
                    it.email == "google@user.com" &&
                        it.googleId == "google-sub-123" &&
                        it.name == "Jane Doe" &&
                        !it.isGeneratedEmail
                }
            )
        }
    }

    @Test
    fun `signInWithGoogle returns failure on api error`() = runTest {
        coEvery { api.signInWithGoogle(any()) } throws RuntimeException("auth failed")

        val result = repo.signInWithGoogle(
            email = "x@y.z",
            googleId = "g-1",
            name = null,
            idToken = "bad-token",
        )
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

    @Test
    fun `clearSession resets QuotaManager in-memory caches`() = runTest {
        // iOS parity: signOut wipes StoreKit/QuotaManager/SubscriptionManager so
        // account A's Plus state can't bleed into account B on the same device.
        repo.clearSession()

        verify { quotaManager.resetForSignOut() }
    }

    // ── signInWithApple dual-store recovery ───────────────────────────────────

    @Test
    fun `signInWithApple persists email and name to dual-store on first sign-in`() = runTest {
        coEvery { api.signInWithApple(any()) } returns RegisterResponse(
            userEmail = "apple@user.com",
            planId = "free_registered",
            isGeneratedEmail = false,
            isPremium = false,
            accessState = "granted",
            dailyQuota = 5,
            dailyUsed = 0,
        )

        repo.signInWithApple(
            appleId = "apple-uid-123",
            email = "apple@user.com",
            name = "Jane",
        )

        // Primary: SecureStorage (encrypted, persists across reinstall on iOS).
        verify { secure.saveAppleEmail("apple-uid-123", "apple@user.com") }
        verify { secure.saveAppleName("apple-uid-123", "Jane") }
        // Fallback: DataStore mirror (matches iOS UserDefaults dual-store).
        coVerify { prefs.setAppleEmailFallback("apple-uid-123", "apple@user.com") }
        coVerify { prefs.setAppleNameFallback("apple-uid-123", "Jane") }
    }

    @Test
    fun `signInWithApple recovers email and name from SecureStorage on subsequent sign-in`() = runTest {
        every { secure.getAppleEmail("apple-uid-123") } returns "apple@user.com"
        every { secure.getAppleName("apple-uid-123") } returns "Jane"
        coEvery { api.signInWithApple(any()) } returns RegisterResponse(
            userEmail = "apple@user.com",
            planId = "free_registered",
            isGeneratedEmail = false,
            isPremium = false,
            accessState = "granted",
            dailyQuota = 5,
            dailyUsed = 0,
        )

        // Apple omits email/name on subsequent sign-ins.
        repo.signInWithApple(appleId = "apple-uid-123", email = null, name = null)

        // Backend was called with the recovered email, NOT the placeholder.
        coVerify {
            api.signInWithApple(match { it.email == "apple@user.com" && it.name == "Jane" })
        }
    }
}
