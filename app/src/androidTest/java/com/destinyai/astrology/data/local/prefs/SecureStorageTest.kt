package com.destinyai.astrology.data.local.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for SecureStorage — exercises the real
 * EncryptedSharedPreferences pipeline that backs auth/session restoration.
 *
 * Verifies that an email/auth token written on one "launch" is recoverable on
 * the next, which is the core contract behind AuthViewModel restoring a
 * session from disk on cold start.
 *
 * Run: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SecureStorageTest {

    private lateinit var storage: SecureStorage
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = SecureStorage(context)
        storage.clearAll()
    }

    @After
    fun tearDown() {
        storage.clearAll()
    }

    @Test
    fun saveEmail_then_getEmail_returnsSameValue() {
        storage.saveEmail("session@destinyai.app")
        assertEquals("session@destinyai.app", storage.getEmail())
    }

    @Test
    fun getEmail_returnsNull_whenNothingStored() {
        assertNull(storage.getEmail())
    }

    @Test
    fun authTokenRoundTrip_returnsSameValue() {
        storage.saveAuthToken("Bearer abc.def.ghi")
        assertEquals("Bearer abc.def.ghi", storage.getAuthToken())
    }

    @Test
    fun appleEmailIsKeyedByUserId() {
        storage.saveAppleEmail("apple-uid-1", "first@apple.com")
        storage.saveAppleEmail("apple-uid-2", "second@apple.com")

        assertEquals("first@apple.com", storage.getAppleEmail("apple-uid-1"))
        assertEquals("second@apple.com", storage.getAppleEmail("apple-uid-2"))
        assertNull(storage.getAppleEmail("apple-uid-missing"))
    }

    @Test
    fun clearAll_removesAllStoredValues() {
        storage.saveEmail("e@x.com")
        storage.saveAuthToken("tok")
        storage.saveUserId("uid-1")
        storage.saveAppleEmail("apple-uid", "ae@x.com")

        storage.clearAll()

        assertNull(storage.getEmail())
        assertNull(storage.getAuthToken())
        assertNull(storage.getUserId())
        assertNull(storage.getAppleEmail("apple-uid"))
    }

    /**
     * Cold-start restoration analogue: write values via one SecureStorage
     * instance, read them via a freshly-constructed instance. Mirrors the
     * AuthViewModel.init() path that loads a saved email after process death.
     */
    @Test
    fun newInstance_seesValuesPersistedByPriorInstance() {
        storage.saveEmail("persist@x.com")
        storage.saveUserId("user-id-99")

        val freshInstance = SecureStorage(context)
        assertEquals("persist@x.com", freshInstance.getEmail())
        assertEquals("user-id-99", freshInstance.getUserId())
    }
}
