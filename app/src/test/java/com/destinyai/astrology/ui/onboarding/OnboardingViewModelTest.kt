package com.destinyai.astrology.ui.onboarding

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.services.HapticManager
import com.destinyai.astrology.services.SoundManager
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@ExperimentalCoroutinesApi
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OnboardingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var prefs: UserPreferences
    private lateinit var haptic: HapticManager
    private lateinit var sound: SoundManager
    private lateinit var vm: OnboardingViewModel

    @BeforeAll
    fun setMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @BeforeEach
    fun setUp() {
        prefs = mockk(relaxed = true)
        haptic = mockk(relaxed = true)
        sound = mockk(relaxed = true)
        vm = OnboardingViewModel(prefs, haptic, sound)
    }

    @Test
    fun `initial slide index is 0`() = runTest {
        vm.currentSlide.test {
            assertEquals(0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `slides list has 4 items`() {
        assertEquals(4, OnboardingSlide.slides.size)
    }

    @Test
    fun `nextSlide increments slide index`() = runTest {
        vm.currentSlide.test {
            assertEquals(0, awaitItem())
            vm.nextSlide {}
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nextSlide increments through all slides`() = runTest {
        vm.currentSlide.test {
            awaitItem() // 0
            vm.nextSlide {}
            assertEquals(1, awaitItem())
            vm.nextSlide {}
            assertEquals(2, awaitItem())
            vm.nextSlide {}
            assertEquals(3, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nextSlide on last slide calls onComplete callback`() = runTest {
        // Advance to last slide
        repeat(3) { vm.nextSlide {} }

        var completed = false
        vm.nextSlide { completed = true }

        assertTrue(completed)
    }

    @Test
    fun `complete saves hasSeenOnboarding to prefs`() = runTest {
        vm.complete()

        coVerify { prefs.setSeenOnboarding(true) }
    }

    @Test
    fun `nextSlide on last slide saves hasSeenOnboarding`() = runTest {
        repeat(3) { vm.nextSlide {} }
        vm.nextSlide {}

        coVerify { prefs.setSeenOnboarding(true) }
    }

    @Test
    fun `isLastSlide returns false for slide 0`() {
        assertFalse(vm.isLastSlide(0))
    }

    @Test
    fun `isLastSlide returns true for last slide`() {
        val lastIndex = OnboardingSlide.slides.size - 1
        assertTrue(vm.isLastSlide(lastIndex))
    }
}
