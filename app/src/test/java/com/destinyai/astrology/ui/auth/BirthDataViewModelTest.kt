package com.destinyai.astrology.ui.auth

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.location.LocationSearchService
import com.destinyai.astrology.data.location.LocationSearchResult
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.remote.LocationResult
import com.destinyai.astrology.data.remote.ProfileResponse
import com.destinyai.astrology.data.remote.RegisterResponse
import com.destinyai.astrology.services.SoundManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import retrofit2.HttpException
import retrofit2.Response

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BirthDataViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var locationSearchService: LocationSearchService
    private lateinit var soundManager: SoundManager
    private lateinit var vm: BirthDataViewModel

    @BeforeAll
    fun setMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterAll
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @BeforeEach
    fun setUp() {
        api = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        locationSearchService = mockk(relaxed = true)
        soundManager = mockk(relaxed = true)
        coEvery { prefs.getUserEmail() } returns "test@example.com"
        coEvery { prefs.isGuestUser() } returns false
        coEvery { prefs.getResponseStyle() } returns "detailed" // not "balanced" → no response style sheet
        // BirthDataViewModel error paths route through context.getString(R.string.X) — relaxed
        // mocks return "" for String return types, so stub the strings the tests assert on.
        val context: android.content.Context = mockk(relaxed = true)
        every { context.getString(com.destinyai.astrology.R.string.account_deleted_error) } returns "Account archived"
        every { context.getString(com.destinyai.astrology.R.string.birth_data_save_failed) } returns "Failed to save birth data"
        every { context.getString(com.destinyai.astrology.R.string.birth_data_please_select_city) } returns "Please select a city"
        every { context.getString(com.destinyai.astrology.R.string.birth_data_please_select_valid_city) } returns "Please select a valid city"
        vm = BirthDataViewModel(
            api,
            prefs,
            locationSearchService,
            soundManager,
            mockk(relaxed = true), // ChatRepository
            context,
        )
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty form fields`() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("", state.userName)
            assertEquals("", state.cityOfBirth)
            assertEquals("", state.gender)
            assertFalse(state.isDateSelected)
            assertFalse(state.isTimeSelected)
            assertFalse(state.isLoading)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── isValid ────────────────────────────────────────────────────────────────

    @Test
    fun `isValid returns false when userName is empty`() {
        setValidState()
        vm.setUserName("")
        assertFalse(vm.isValid)
    }

    @Test
    fun `isValid returns false when cityOfBirth is empty`() {
        setValidState()
        vm.setLocation("", 0.0, 0.0)
        assertFalse(vm.isValid)
    }

    @Test
    fun `isValid returns false when coordinates are zero`() {
        setValidState()
        vm.setLocation("Bhilai", 0.0, 0.0)
        assertFalse(vm.isValid)
    }

    @Test
    fun `isValid returns false when date not selected`() {
        setValidState()
        // Re-apply date without triggering isDateSelected
        vm.uiState  // ensure state read
        vm.setDateOfBirth("1980-07-01")
        vm.clearDateSelected()
        assertFalse(vm.isValid)
    }

    @Test
    fun `isValid returns false when time not selected and timeUnknown false`() {
        setValidState()
        vm.setTimeUnknown(false)
        vm.clearTimeSelected()
        assertFalse(vm.isValid)
    }

    @Test
    fun `isValid returns true when timeUnknown is true without time selection`() {
        setValidState()
        vm.setTimeUnknown(true)
        vm.clearTimeSelected()
        assertTrue(vm.isValid)
    }

    @Test
    fun `isValid returns false when gender is empty`() {
        setValidState()
        vm.setGender("")
        assertFalse(vm.isValid)
    }

    @Test
    fun `isValid returns true with all valid fields`() {
        setValidState()
        assertTrue(vm.isValid)
    }

    @Test
    fun `isValid returns false for user under 13`() {
        setValidState()
        vm.setDateOfBirth("2015-01-01")  // 11 years old in 2026
        assertFalse(vm.isValid)
    }

    // ── setters ────────────────────────────────────────────────────────────────

    @Test
    fun `setUserName updates state`() = runTest {
        vm.setUserName("Prabhu Kumar")
        vm.uiState.test {
            assertEquals("Prabhu Kumar", awaitItem().userName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDateOfBirth sets isDateSelected true`() = runTest {
        vm.setDateOfBirth("1980-07-01")
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("1980-07-01", state.dateOfBirth)
            assertTrue(state.isDateSelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setTimeOfBirth sets isTimeSelected true`() = runTest {
        vm.setTimeOfBirth("06:32")
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("06:32", state.timeOfBirth)
            assertTrue(state.isTimeSelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setGender updates state`() = runTest {
        vm.setGender("male")
        vm.uiState.test {
            assertEquals("male", awaitItem().gender)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setTimeUnknown updates state`() = runTest {
        vm.setTimeUnknown(true)
        vm.uiState.test {
            assertTrue(awaitItem().timeUnknown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setLocation updates city latitude longitude`() = runTest {
        vm.setLocation("Bhilai", 21.2138, 81.3943)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("Bhilai", state.cityOfBirth)
            assertEquals(21.2138, state.latitude, 0.0001)
            assertEquals(81.3943, state.longitude, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── save ───────────────────────────────────────────────────────────────────

    @Test
    fun `save calls api saveProfile with correct fields`() = runTest {
        setValidState()
        coEvery { api.saveProfile(any()) } returns fakeProfileResponse()

        vm.save()

        coVerify {
            api.saveProfile(match { req ->
                req.email == "test@example.com" &&
                    req.birthProfile.dateOfBirth == "1980-07-01" &&
                    req.birthProfile.cityOfBirth == "Bhilai" &&
                    req.birthProfile.latitude == 21.2138
            })
        }
    }

    @Test
    fun `save stores birth profile in prefs`() = runTest {
        setValidState()
        coEvery { api.saveProfile(any()) } returns fakeProfileResponse()

        vm.save()

        coVerify { prefs.setBirthProfile(any()) }
    }

    @Test
    fun `save sets hasBirthData to true`() = runTest {
        setValidState()
        coEvery { api.saveProfile(any()) } returns fakeProfileResponse()

        vm.save()

        coVerify { prefs.setHasBirthData(true) }
    }

    @Test
    fun `save stores userName in prefs`() = runTest {
        setValidState()
        coEvery { api.saveProfile(any()) } returns fakeProfileResponse()

        vm.save()

        coVerify { prefs.setUserName("Prabhu Kumar") }
    }

    @Test
    fun `save sets isSaved true on success`() = runTest {
        setValidState()
        coEvery { api.saveProfile(any()) } returns fakeProfileResponse()

        vm.save()

        // iOS parity (BirthDataView.swift:474): showResponseStylePicker is unconditionally
        // raised after every successful save — isSaved is only flipped to true downstream
        // by confirmResponseStyle() once the user finishes the response-style sheet.
        // The post-save success signal observable to the screen is showResponseStyleSheet.
        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.showResponseStyleSheet)
            assertFalse(s.isLoading)
            assertNull(s.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save sets error when city is blank`() = runTest {
        setValidState()
        vm.setLocation("", 21.21, 81.39)

        vm.save()

        vm.uiState.test {
            assertNotNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save sets error when coordinates are zero`() = runTest {
        setValidState()
        vm.setLocation("Bhilai", 0.0, 0.0)

        vm.save()

        vm.uiState.test {
            assertNotNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save handles 409 conflict by setting birthDataTakenEmail`() = runTest {
        setValidState()
        val errorJson = """{"detail":{"existing_email":"conflict@example.com","provider":"google"}}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        coEvery { api.saveProfile(any()) } throws HttpException(Response.error<Any>(409, errorBody))

        vm.save()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals("conflict@example.com", state.birthDataTakenEmail)
            assertEquals("google", state.birthDataTakenProvider)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save sets error on network failure`() = runTest {
        setValidState()
        coEvery { api.saveProfile(any()) } throws Exception("Network error")

        vm.save()

        vm.uiState.test {
            assertNotNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save raises RegisteredUserConflictError on 409`() = runTest {
        setValidState()
        val errorJson = """{"detail":{"existing_email":"conflict@example.com","provider":"google"}}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        coEvery { api.saveProfile(any()) } throws HttpException(Response.error<Any>(409, errorBody))

        vm.save()

        // RegisteredUserConflictError path: state reflects conflict email
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("conflict@example.com", state.birthDataTakenEmail)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save raises AccountDeletedError on 403 archived`() = runTest {
        setValidState()
        val errorBody = "{}".toResponseBody("application/json".toMediaType())
        coEvery { api.saveProfile(any()) } throws HttpException(Response.error<Any>(403, errorBody))

        vm.save()

        // AccountDeletedError path: error is set
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("Account archived", state.error)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── searchLocation ─────────────────────────────────────────────────────────

    @Test
    fun `searchLocation calls LocationSearchService and updates results`() = runTest {
        val results = listOf(
            LocationResult(city = "Mumbai", latitude = 19.07, longitude = 72.87, displayName = "Mumbai, India"),
            LocationResult(city = "Mysore", latitude = 12.29, longitude = 76.63, displayName = "Mysore, India"),
        )
        coEvery { locationSearchService.search("mum") } returns LocationSearchResult.Success(results)

        vm.searchLocation("mum")
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.locationResults.size)
            assertEquals("Mumbai, India", state.locationResults[0].displayName)
            assertFalse(state.isSearchingLocation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchLocation with short query returns empty list`() = runTest {
        vm.searchLocation("m")

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.locationResults.isEmpty())
            assertFalse(state.isSearchingLocation)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { locationSearchService.search(any()) }
    }

    // ── loadSaved ──────────────────────────────────────────────────────────────
    @Test
    fun `loadSaved populates form fields from prefs`() = runTest {
        coEvery { prefs.getBirthProfile() } returns BirthProfileDto(
            dateOfBirth = "1980-07-01",
            timeOfBirth = "06:32",
            cityOfBirth = "Bhilai",
            latitude = 21.2138,
            longitude = 81.3943,
            gender = "male",
            birthTimeUnknown = false,
        )
        coEvery { prefs.getUserName() } returns "Prabhu Kumar"

        vm.loadSaved()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals("1980-07-01", state.dateOfBirth)
            assertEquals("06:32", state.timeOfBirth)
            assertEquals("Bhilai", state.cityOfBirth)
            assertEquals("male", state.gender)
            assertEquals("Prabhu Kumar", state.userName)
            assertTrue(state.isDateSelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadSaved does nothing when no birth profile in prefs`() = runTest {
        coEvery { prefs.getBirthProfile() } returns null

        vm.loadSaved()

        vm.uiState.test {
            assertEquals("", awaitItem().cityOfBirth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun setValidState() {
        vm.setUserName("Prabhu Kumar")
        vm.setDateOfBirth("1980-07-01")
        vm.setTimeOfBirth("06:32")
        vm.setLocation("Bhilai", 21.2138, 81.3943)
        vm.setGender("male")
    }

    private fun fakeRegisterResponse() = RegisterResponse(
        userEmail = "test@example.com",
        planId = "free_registered",
        isGeneratedEmail = false,
        isPremium = false,
        accessState = "granted",
    )

    private fun fakeProfileResponse() = ProfileResponse(
        userEmail = "test@example.com",
        planId = "free_registered",
        isGeneratedEmail = false,
        isPremium = false,
    )
}
