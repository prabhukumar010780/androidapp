package com.destinyai.astrology.data.local.prefs

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionTokenStoreTest {

    // In-memory fake for SecureStorage's raw string map, so we test real store
    // logic (namespacing, atomicity, freshness) not EncryptedSharedPrefs.
    private lateinit var backing: MutableMap<String, String>
    private lateinit var secure: SecureStorage
    private lateinit var store: SessionTokenStore

    @BeforeEach
    fun setup() {
        backing = mutableMapOf()
        secure = mockk(relaxed = true)
        every { secure.putRaw(any(), any()) } answers {
            backing[firstArg()] = secondArg(); Unit
        }
        every { secure.getRaw(any()) } answers { backing[firstArg()] }
        every { secure.removeRaw(any()) } answers { backing.remove(firstArg()); Unit }
        store = SessionTokenStore(secure)
    }

    @Test
    fun `setActiveSession persists and reads back scoped to email`() {
        val exp = System.currentTimeMillis() + 3_600_000
        val ok = store.setActiveSession("A@Example.com", "jwt-1", exp, "ref-1", exp)
        assertTrue(ok)
        assertEquals("a@example.com", store.activeEmail())
        assertEquals("jwt-1", store.currentSessionJwt())
        assertEquals("ref-1", store.currentRefreshToken())
        assertEquals("jwt-1", store.sessionJwt("a@example.com"))
    }

    @Test
    fun `sessionIsFresh false within 60s of expiry`() {
        val now = 1_000_000L
        store.setActiveSession("a@x.com", "jwt", now + 59_000, "ref", now + 100_000)
        assertFalse(store.sessionIsFresh(now))
        store.setActiveSession("a@x.com", "jwt", now + 61_000, "ref", now + 100_000)
        assertTrue(store.sessionIsFresh(now))
    }

    @Test
    fun `updateSession rotates tokens for active email`() {
        val exp = System.currentTimeMillis() + 3_600_000
        store.setActiveSession("a@x.com", "jwt-1", exp, "ref-1", exp)
        val ok = store.updateSession("jwt-2", exp, "ref-2", exp)
        assertTrue(ok)
        assertEquals("jwt-2", store.currentSessionJwt())
        assertEquals("ref-2", store.currentRefreshToken())
    }

    @Test
    fun `clearSession for active email wipes active pointer`() {
        val exp = System.currentTimeMillis() + 3_600_000
        store.setActiveSession("a@x.com", "jwt", exp, "ref", exp)
        store.clearSession("a@x.com")
        assertNull(store.activeEmail())
        assertNull(store.currentSessionJwt())
    }

    @Test
    fun `clearSession for other email keeps active session`() {
        val exp = System.currentTimeMillis() + 3_600_000
        store.setActiveSession("a@x.com", "jwt-a", exp, "ref-a", exp)
        store.setActiveSession("b@x.com", "jwt-b", exp, "ref-b", exp) // b now active
        store.clearSession("a@x.com")
        assertEquals("b@x.com", store.activeEmail())
        assertNull(store.sessionJwt("a@x.com"))
        assertEquals("jwt-b", store.currentSessionJwt())
    }
}
