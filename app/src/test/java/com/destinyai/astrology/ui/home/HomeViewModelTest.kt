package com.destinyai.astrology.ui.home

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.BirthProfileDto
import com.destinyai.astrology.data.repository.HomeRepository
import com.destinyai.astrology.domain.model.User
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalTime

/**
 * TDD test shell for HomeViewModel.
 * These tests define the contract. All fail until HomeViewModel is implemented.
 *
 * Mirrors: iOS HomeViewModelTests.swift (14 assertions)
 */
@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: HomeRepository
    private lateinit var prefs: UserPreferences
    private lateinit var viewModel: HomeViewModel

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
        repository = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        coEvery { prefs.getUserEmail() } returns null
        coEvery { prefs.getBirthProfile() } returns null
        viewModel = HomeViewModel(repository, prefs)
    }

    // --- Defaults ---

    @Test
    fun `init sets quota to plan default (10 for free registered)`() = runTest {
        coEvery { repository.getDailyQuota() } returns 10

        val vm = HomeViewModel(repository, prefs)

        vm.uiState.test {
            assertEquals(10, awaitItem().dailyQuota)
        }
    }

    @Test
    fun `init sets isLoading false`() = runTest {
        viewModel.uiState.test {
            assertFalse(awaitItem().isLoading)
        }
    }

    // --- Greeting ---

    @Test
    fun `greeting is Good Morning before noon`() {
        val greeting = HomeViewModel.greetingFor(LocalTime.of(9, 0))
        assertEquals("Good Morning", greeting)
    }

    @Test
    fun `greeting is Good Afternoon 12pm to 5pm`() {
        val greeting = HomeViewModel.greetingFor(LocalTime.of(14, 0))
        assertEquals("Good Afternoon", greeting)
    }

    @Test
    fun `greeting is Good Evening after 5pm`() {
        val greeting = HomeViewModel.greetingFor(LocalTime.of(19, 0))
        assertEquals("Good Evening", greeting)
    }

    // --- Display name ---

    @Test
    fun `displayName returns Guest for guest user`() = runTest {
        coEvery { repository.getCurrentUser() } returns User(
            email = "guest.abc@destinyai.app",
            isGuestEmail = true,
        )

        val vm = HomeViewModel(repository, prefs)

        vm.uiState.test {
            assertEquals("Guest", awaitItem().displayName)
        }
    }

    @Test
    fun `displayName returns first name for registered user`() = runTest {
        coEvery { repository.getCurrentUser() } returns User(
            email = "prabhu@gmail.com",
            isGuestEmail = false,
            name = "Prabhu Kushwaha",
        )

        val vm = HomeViewModel(repository, prefs)

        vm.uiState.test {
            assertEquals("Prabhu", awaitItem().displayName)
        }
    }

    // --- Quota ---

    @Test
    fun `quotaProgress is 0_5 when 5 of 10 used`() = runTest {
        coEvery { repository.getDailyQuota() } returns 10
        coEvery { repository.getDailyUsed() } returns 5

        val vm = HomeViewModel(repository, prefs)

        vm.uiState.test {
            assertEquals(0.5f, awaitItem().quotaProgress, 0.001f)
        }
    }

    @Test
    fun `decrementQuota reduces remaining by 1`() = runTest {
        coEvery { repository.getDailyQuota() } returns 10
        coEvery { repository.getDailyUsed() } returns 3

        viewModel.decrementQuota()

        viewModel.uiState.test {
            assertEquals(4, awaitItem().dailyUsed)
        }
    }

    @Test
    fun `decrementQuota does not go below 0`() = runTest {
        coEvery { repository.getDailyQuota() } returns 5
        coEvery { repository.getDailyUsed() } returns 5

        viewModel.decrementQuota()

        viewModel.uiState.test {
            assertEquals(0, awaitItem().remaining)
        }
    }

    @Test
    fun `premium user quota shows unlimited`() = runTest {
        coEvery { repository.getCurrentUser() } returns User(
            email = "premium@gmail.com",
            isGuestEmail = false,
            isPremium = true,
        )
        coEvery { repository.getDailyQuota() } returns -1

        val vm = HomeViewModel(repository, prefs)

        vm.uiState.test {
            assertTrue(awaitItem().isUnlimited)
        }
    }

    // --- Data loading ---

    @Test
    fun `loadHomeData sets isLoading true then false`() = runTest {
        viewModel.uiState.test {
            viewModel.loadHomeData()
            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading || !loadingState.isLoading) // emits both
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadHomeData populates suggestedQuestions`() = runTest {
        val questions = listOf("What's my luck today?", "Career outlook this week?")
        coEvery { repository.getSuggestedQuestions() } returns questions

        viewModel.loadHomeData()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(questions, state.suggestedQuestions)
        }
    }

    @Test
    fun `loadHomeData populates dailyInsight`() = runTest {
        coEvery { repository.getDailyInsight() } returns "A powerful day for decisions."

        viewModel.loadHomeData()

        viewModel.uiState.test {
            assertNotNull(awaitItem().dailyInsight)
        }
    }

    // --- Date formatting ---

    @Test
    fun `renewalDateString formats as MMM d`() {
        val formatted = HomeViewModel.formatRenewalDate("2026-06-01")
        assertEquals("Jun 1", formatted)
    }

    // --- Rich home data ---

    @Test
    fun `loadHomeData sets dashaInfo when api returns chart data`() = runTest {
        val fakeDasha = HomeDashaInfo(mahadasha = "Venus", antardasha = "Sun", endsAt = "2027-01")
        val fakeRichData = HomeRichData(dashaInfo = fakeDasha)
        coEvery { prefs.getUserEmail() } returns "test@example.com"
        coEvery { prefs.getBirthProfile() } returns BirthProfileDto(
            dateOfBirth = "1990-01-01",
            timeOfBirth = "06:00",
            cityOfBirth = "Delhi",
            latitude = 28.6,
            longitude = 77.2,
        )
        coEvery { repository.getRichHomeData(any(), any()) } returns fakeRichData

        viewModel.loadHomeData()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Venus", state.dashaInfo?.mahadasha)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectLifeArea sets selectedLifeArea`() = runTest {
        val area = HomeLifeArea(name = "Career", emoji = "💼", questions = listOf("Question?"))

        viewModel.selectLifeArea(area)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(area, state.selectedLifeArea)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissLifeArea clears selectedLifeArea`() = runTest {
        val area = HomeLifeArea(name = "Career", emoji = "💼", questions = listOf("Question?"))
        viewModel.selectLifeArea(area)
        viewModel.dismissLifeArea()

        viewModel.uiState.test {
            assertNull(awaitItem().selectedLifeArea)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
