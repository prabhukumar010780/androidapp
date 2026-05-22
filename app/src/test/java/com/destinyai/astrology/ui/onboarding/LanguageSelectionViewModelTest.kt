package com.destinyai.astrology.ui.onboarding

import app.cash.turbine.test
import com.destinyai.astrology.data.local.prefs.UserPreferences
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
class LanguageSelectionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var prefs: UserPreferences
    private lateinit var vm: LanguageSelectionViewModel

    @BeforeAll
    fun setMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @BeforeEach
    fun setUp() {
        prefs = mockk(relaxed = true)
        vm = LanguageSelectionViewModel(prefs)
    }

    @Test
    fun `languages list has 13 items`() {
        assertEquals(13, vm.languages.size)
    }

    @Test
    fun `all 13 language codes are present`() {
        val codes = vm.languages.map { it.code }
        assertTrue(codes.contains("en"))
        assertTrue(codes.contains("hi"))
        assertTrue(codes.contains("ta"))
        assertTrue(codes.contains("te"))
        assertTrue(codes.contains("kn"))
        assertTrue(codes.contains("ml"))
        assertTrue(codes.contains("es"))
        assertTrue(codes.contains("pt"))
        assertTrue(codes.contains("de"))
        assertTrue(codes.contains("fr"))
        assertTrue(codes.contains("zh-Hans"))
        assertTrue(codes.contains("ja"))
        assertTrue(codes.contains("ru"))
    }

    @Test
    fun `initial selected language is null`() = runTest {
        vm.selectedCode.test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectLanguage updates selectedCode`() = runTest {
        vm.selectedCode.test {
            awaitItem() // null
            vm.selectLanguage("hi")
            assertEquals("hi", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmSelection saves language to prefs`() = runTest {
        vm.selectLanguage("hi")
        vm.confirmSelection()

        coVerify { prefs.setSelectedLanguage("hi") }
        coVerify { prefs.setLanguageSelectionComplete(true) }
    }

    @Test
    fun `confirmSelection with no selection defaults to en`() = runTest {
        vm.confirmSelection()

        coVerify { prefs.setSelectedLanguage("en") }
        coVerify { prefs.setLanguageSelectionComplete(true) }
    }

    @Test
    fun `each language has non-empty name and nativeName`() {
        vm.languages.forEach { lang ->
            assertTrue(lang.name.isNotBlank(), "Empty name for code: ${lang.code}")
            assertTrue(lang.nativeName.isNotBlank(), "Empty nativeName for code: ${lang.code}")
        }
    }
}
