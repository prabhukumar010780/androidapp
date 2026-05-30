package com.destinyai.astrology.ui.compatibility

import app.cash.turbine.test
import com.destinyai.astrology.data.local.db.CompatibilityHistoryDao
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.repository.CompatibilityRepository
import com.destinyai.astrology.data.repository.SseEvent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

private val FAKE_FINAL_JSON = """
{
  "session_id": "sess_123",
  "status": "success",
  "llm_analysis": "Excellent match",
  "hard_no_flags": {"is_recommended": true, "rejection_reasons": []},
  "adjusted_total_score": 28.0,
  "adjusted_category": "Good",
  "analysis_data": {
    "joint": {
      "ashtakoot_matching": {
        "total_score": 28,
        "guna_scores": {
          "varna":   {"score": 1, "description": ""},
          "vashya":  {"score": 2, "description": ""},
          "tara":    {"score": 3, "description": ""},
          "yoni":    {"score": 3, "description": ""},
          "maitri":  {"score": 5, "description": ""},
          "gana":    {"score": 5, "description": ""},
          "bhakoot": {"score": 5, "description": ""},
          "nadi":    {"score": 4, "description": ""}
        }
      }
    }
  }
}
""".trimIndent()

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompatibilityViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var compatibilityRepo: CompatibilityRepository
    private lateinit var historyDao: CompatibilityHistoryDao
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
        compatibilityRepo = mockk(relaxed = true)
        historyDao = mockk(relaxed = true)
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        coEvery { prefs.getUserName() } returns "Prabhu"
        coEvery { prefs.getBirthProfile() } returns fakeBirthProfile()
        every { prefs.isHistoryEnabledFlow } returns flowOf(true)
        every { compatibilityRepo.streamAnalysis(any()) } returns flowOf(SseEvent.FinalJson(FAKE_FINAL_JSON))
        every { historyDao.observeAll(any()) } returns flowOf(emptyList())
        vm = CompatibilityViewModel(api, prefs, compatibilityRepo, historyDao)
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
    fun `canAnalyze true when city provided but lat and lon are zero`() = runTest {
        vm.loadUserData()
        vm.setPartnerName("Priya")
        vm.setPartnerDob("1985-03-15")
        vm.setPartnerTime("08:00")
        vm.setPartnerLocation("Mumbai", 0.0, 0.0)
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
    fun `analyze calls repo and sets result from final_json`() = runTest(testDispatcher) {
        vm.loadUserData()
        assertTrue(vm.uiState.value.personALoaded, "personA must be loaded")

        setValidPartner()
        vm.analyze()

        val s = vm.uiState.value
        assertTrue(s.result.isNotBlank())
        assertFalse(s.isAnalyzing)
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
    fun `analyze sets error when repo emits SseEvent Error`() = runTest {
        every { compatibilityRepo.streamAnalysis(any()) } returns flowOf(SseEvent.Error("server error"))
        vm.loadUserData()
        setValidPartner()

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

    // ── Multi-partner tests ────────────────────────────────────────────────────

    @Test
    fun `initial partners list has one empty partner`() {
        assertEquals(1, vm.partners.value.size)
        assertEquals(0, vm.activePartnerIndex.value)
    }

    @Test
    fun `addPartner appends new partner and makes it active`() {
        vm.addPartner()
        assertEquals(2, vm.partners.value.size)
        assertEquals(1, vm.activePartnerIndex.value)
    }

    @Test
    fun `addPartner respects max 3 partners`() {
        vm.addPartner()
        vm.addPartner()
        vm.addPartner() // 4th attempt should be silently ignored
        assertEquals(3, vm.partners.value.size)
    }

    @Test
    fun `removePartner removes partner at index and adjusts active`() {
        vm.addPartner() // now 2 partners
        vm.removePartner(0)
        assertEquals(1, vm.partners.value.size)
        assertEquals(0, vm.activePartnerIndex.value)
    }

    @Test
    fun `removePartner does nothing when only 1 partner`() {
        vm.removePartner(0)
        assertEquals(1, vm.partners.value.size)
    }

    @Test
    fun `selectPartner changes activePartnerIndex`() {
        vm.addPartner()
        vm.selectPartner(0)
        assertEquals(0, vm.activePartnerIndex.value)
    }

    @Test
    fun `hasFailedPartners false when no failures`() {
        assertFalse(vm.hasFailedPartners.value)
    }

    @Test
    fun `retryFailedPartners is callable`() {
        vm.retryFailedPartners() // must not throw
    }

    // ── New picker/overlay state tests ────────────────────────────────────────

    @Test
    fun `initial partnerTimeUnknown is false`() {
        assertFalse(vm.uiState.value.partnerTimeUnknown)
    }

    @Test
    fun `setPartnerTimeUnknown updates state`() {
        vm.setPartnerTimeUnknown(true)
        assertTrue(vm.uiState.value.partnerTimeUnknown)
    }

    @Test
    fun `initial partnerGender is empty`() {
        assertEquals("", vm.uiState.value.partnerGender)
    }

    @Test
    fun `setPartnerGender updates state`() {
        vm.setPartnerGender("female")
        assertEquals("female", vm.uiState.value.partnerGender)
    }

    @Test
    fun `initial showDatePicker is false`() {
        assertFalse(vm.uiState.value.showDatePicker)
    }

    @Test
    fun `setShowDatePicker updates state`() {
        vm.setShowDatePicker(true)
        assertTrue(vm.uiState.value.showDatePicker)
    }

    @Test
    fun `initial showTimePicker is false`() {
        assertFalse(vm.uiState.value.showTimePicker)
    }

    @Test
    fun `setShowTimePicker updates state`() {
        vm.setShowTimePicker(true)
        assertTrue(vm.uiState.value.showTimePicker)
    }

    @Test
    fun `initial showLocationSearch is false`() {
        assertFalse(vm.uiState.value.showLocationSearch)
    }

    @Test
    fun `setShowLocationSearch updates state`() {
        vm.setShowLocationSearch(true)
        assertTrue(vm.uiState.value.showLocationSearch)
    }

    @Test
    fun `initial showStreamingView is false`() {
        assertFalse(vm.uiState.value.showStreamingView)
    }

    @Test
    fun `initial showComparisonOverview is false`() {
        assertFalse(vm.uiState.value.showComparisonOverview)
    }

    @Test
    fun `setShowComparisonOverview updates state`() {
        vm.setShowComparisonOverview(true)
        assertTrue(vm.uiState.value.showComparisonOverview)
    }

    @Test
    fun `canAnalyze true when partnerTimeUnknown and time is empty`() = runTest {
        vm.loadUserData()
        vm.setPartnerName("Priya")
        vm.setPartnerDob("1985-03-15")
        vm.setPartnerTimeUnknown(true)
        vm.setPartnerLocation("Mumbai", 0.0, 0.0)
        assertTrue(vm.uiState.value.canAnalyze)
    }

    @Test
    fun `analyzeButtonTitle returns compare label when multiple partners complete`() {
        vm.addPartner()
        vm.setPartnerName("Priya")
        vm.setPartnerDob("1985-03-15")
        vm.setPartnerTime("08:00")
        vm.setPartnerLocation("Mumbai", 0.0, 0.0)
        val title = analyzeButtonTitle(completedCount = 2, isAnalyzing = false)
        assertTrue(title.contains("2") || title.contains("Compare"))
    }

    @Test
    fun `analyzeButtonTitle returns single label when one partner`() {
        val title = analyzeButtonTitle(completedCount = 1, isAnalyzing = false)
        assertFalse(title.contains("2"))
    }

    @Test
    fun `selectSavedPartner populates all partner fields`() = runTest {
        val partner = com.destinyai.astrology.data.remote.PartnerDto(
            id = "p1",
            name = "Priya Sharma",
            dateOfBirth = "1988-06-15",
            timeOfBirth = "10:30",
            cityOfBirth = "Bangalore",
            latitude = 12.97,
            longitude = 77.59,
        )

        vm.selectSavedPartner(partner)

        vm.uiState.test {
            val s = awaitItem()
            assertEquals("Priya Sharma", s.partnerName)
            assertEquals("1988-06-15", s.partnerDob)
            assertEquals("10:30", s.partnerTime)
            assertEquals("Bangalore", s.partnerCity)
            assertEquals(12.97, s.partnerLatitude, 0.001)
            assertEquals(77.59, s.partnerLongitude, 0.001)
            assertFalse(s.showPartnerPicker)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showPartnerPicker sets showPartnerPicker true`() {
        vm.showPartnerPicker()
        assertTrue(vm.uiState.value.showPartnerPicker)
    }

    @Test
    fun `dismissPartnerPicker clears showPartnerPicker`() {
        vm.showPartnerPicker()
        vm.dismissPartnerPicker()
        assertFalse(vm.uiState.value.showPartnerPicker)
    }

    private fun fakeBirthProfile() = BirthProfileDto(
        dateOfBirth = "1980-07-01",
        timeOfBirth = "06:32",
        cityOfBirth = "Bhilai",
        latitude = 21.2138,
        longitude = 81.3943,
    )
}
