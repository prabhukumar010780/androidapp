package com.destinyai.astrology.ui.compatibility

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.remote.CompatibilityResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompatibilityViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var vm: CompatibilityViewModel

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
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { prefs.getUserName() } returns "Prabhu"
        coEvery { prefs.getBirthProfile() } returns fakeBirthProfile()
        vm = CompatibilityViewModel(api, prefs)
    }

    @Test
    fun `initial state has no person A loaded`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertFalse(s.personALoaded)
            assertEquals("", s.personAName)
            assertFalse(s.canAnalyze)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadUserData sets personALoaded and personAName`() = runTest {
        vm.loadUserData()

        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.personALoaded)
            assertEquals("Prabhu", s.personAName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadUserData does nothing when no email`() = runTest {
        coEvery { prefs.getUserEmail() } returns null

        vm.loadUserData()

        vm.uiState.test {
            assertFalse(awaitItem().personALoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canAnalyze false when personA not loaded`() {
        setValidPartner()
        assertFalse(vm.uiState.value.canAnalyze)
    }

    @Test
    fun `canAnalyze false when partner name empty`() = runTest {
        vm.loadUserData()
        setValidPartner()
        vm.setPartnerName("")
        assertFalse(vm.uiState.value.canAnalyze)
    }

    @Test
    fun `canAnalyze false when partner dob empty`() = runTest {
        vm.loadUserData()
        setValidPartner()
        vm.setPartnerDob("")
        assertFalse(vm.uiState.value.canAnalyze)
    }

    @Test
    fun `canAnalyze true with all required fields`() = runTest {
        vm.loadUserData()
        setValidPartner()
        assertTrue(vm.uiState.value.canAnalyze)
    }

    @Test
    fun `setPartnerName updates state`() = runTest {
        vm.setPartnerName("Priya")
        vm.uiState.test {
            assertEquals("Priya", awaitItem().partnerName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `analyze calls api and sets result`() = runTest {
        vm.loadUserData()
        setValidPartner()
        coEvery { api.analyzeCompatibility(any()) } returns
            CompatibilityResponse(score = 82, content = "Excellent match")

        vm.analyze()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals("Excellent match", s.result)
            assertEquals(82, s.score)
            assertFalse(s.isAnalyzing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `analyze does nothing when canAnalyze false`() = runTest {
        vm.analyze()

        vm.uiState.test {
            assertFalse(awaitItem().isAnalyzing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `analyze sets error on failure`() = runTest {
        vm.loadUserData()
        setValidPartner()
        coEvery { api.analyzeCompatibility(any()) } throws RuntimeException("api error")

        vm.analyze()

        vm.uiState.test {
            val s = awaitItem()
            assertNotNull(s.error)
            assertFalse(s.isAnalyzing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearResult resets result and score`() = runTest {
        vm.loadUserData()
        setValidPartner()
        coEvery { api.analyzeCompatibility(any()) } returns
            CompatibilityResponse(score = 75, content = "Good match")
        vm.analyze()

        vm.clearResult()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals("", s.result)
            assertNull(s.score)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun setValidPartner() {
        vm.setPartnerName("Priya")
        vm.setPartnerDob("1985-03-15")
        vm.setPartnerTime("08:00")
        vm.setPartnerLocation("Mumbai", 19.076, 72.877)
    }

    private fun fakeBirthProfile() = BirthProfileDto(
        dateOfBirth = "1980-07-01",
        timeOfBirth = "06:32",
        cityOfBirth = "Bhilai",
        latitude = 21.2138,
        longitude = 81.3943,
    )
}
