package com.destinyai.astrology.ui.partners

import app.cash.turbine.test
import com.destinyai.astrology.data.local.db.PartnerDao
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.location.LocationSearchService
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.data.remote.PartnerDto
import com.destinyai.astrology.services.QuotaManager
import io.mockk.coEvery
import io.mockk.coVerify
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
class PartnersViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var api: AstroApiService
    private lateinit var prefs: UserPreferences
    private lateinit var quotaManager: QuotaManager
    private lateinit var locationSearchService: LocationSearchService
    private lateinit var partnerDao: PartnerDao
    private lateinit var vm: PartnersViewModel

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
        quotaManager = mockk(relaxed = true)
        locationSearchService = mockk(relaxed = true)
        partnerDao = mockk(relaxed = true)
        val profileContextManager: com.destinyai.astrology.services.ProfileContextManager = mockk(relaxed = true)
        coEvery { prefs.getUserEmail() } returns "u@x.com"
        vm = PartnersViewModel(api, prefs, quotaManager, locationSearchService, partnerDao, profileContextManager)
    }

    @Test
    fun `initial state has empty partners`() = runTest {
        vm.uiState.test {
            val s = awaitItem()
            assertTrue(s.partners.isEmpty())
            assertFalse(s.isLoading)
            assertFalse(s.showAddForm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isFormValid false when name empty`() {
        setValidForm()
        vm.setFormName("")
        assertFalse(vm.uiState.value.isFormValid)
    }

    @Test
    fun `isFormValid false when dob empty`() {
        setValidForm()
        vm.setFormDob("")
        assertFalse(vm.uiState.value.isFormValid)
    }

    @Test
    fun `isFormValid false when city empty`() {
        setValidForm()
        vm.setFormLocation("", 0.0, 0.0)
        assertFalse(vm.uiState.value.isFormValid)
    }

    @Test
    fun `isFormValid true with all required fields`() {
        setValidForm()
        assertTrue(vm.uiState.value.isFormValid)
    }

    @Test
    fun `loadPartners populates partners`() = runTest {
        coEvery { api.listPartners("u@x.com") } returns listOf(fakePartner("p1", "Priya"))

        vm.loadPartners()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(1, s.partners.size)
            assertEquals("Priya", s.partners[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadPartners sets error on failure`() = runTest {
        coEvery { api.listPartners(any()) } throws RuntimeException("error")

        vm.loadPartners()

        vm.uiState.test {
            assertNotNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addPartner appends partner and resets form`() = runTest {
        setValidForm()
        coEvery { api.addPartner(any()) } returns fakePartner("p1", "Priya")

        vm.addPartner()

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(1, s.partners.size)
            assertFalse(s.showAddForm)
            assertEquals("", s.formName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addPartner does nothing when form is invalid`() = runTest {
        vm.addPartner()

        coVerify(exactly = 0) { api.addPartner(any()) }
    }

    @Test
    fun `addPartner sets error on api failure`() = runTest {
        setValidForm()
        coEvery { api.addPartner(any()) } throws RuntimeException("error")

        vm.addPartner()

        vm.uiState.test {
            assertNotNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deletePartner removes partner from state`() = runTest {
        coEvery { api.listPartners("u@x.com") } returns listOf(
            fakePartner("p1", "Priya"),
            fakePartner("p2", "Anita"),
        )
        vm.loadPartners()

        vm.deletePartner("p1")

        vm.uiState.test {
            val s = awaitItem()
            assertEquals(1, s.partners.size)
            assertEquals("p2", s.partners[0].id)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { api.deletePartner("p1", "u@x.com") }
    }

    @Test
    fun `toggleAddForm flips showAddForm`() = runTest {
        vm.toggleAddForm()
        vm.uiState.test {
            assertTrue(awaitItem().showAddForm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun setValidForm() {
        vm.setFormName("Priya")
        vm.setFormGender("female")
        vm.setFormDob("1985-03-15")
        vm.setFormTime("08:00")
        vm.setFormLocation("Mumbai", 19.076, 72.877)
    }

    private fun fakePartner(id: String, name: String) = PartnerDto(
        id = id,
        name = name,
        dateOfBirth = "1985-03-15",
        timeOfBirth = "08:00",
        cityOfBirth = "Mumbai",
        latitude = 19.076,
        longitude = 72.877,
    )
}
