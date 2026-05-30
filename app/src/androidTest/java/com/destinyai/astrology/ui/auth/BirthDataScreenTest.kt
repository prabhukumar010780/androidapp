package com.destinyai.astrology.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.destinyai.astrology.data.local.prefs.UserPreferences
import com.destinyai.astrology.data.remote.AstroApiService
import com.destinyai.astrology.ui.theme.DestinyTheme
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TDD spec for the premium BirthDataScreen UI.
 *
 * iOS reference: ios_app/Views/Auth/BirthDataView.swift
 *
 * Pins semantic structure: header title, field row tap targets,
 * "I don't know" toggle, and submit button gating.
 *
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class BirthDataScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun renderScreen(block: BirthDataViewModel.() -> Unit = {}) {
        val api = mockk<AstroApiService>(relaxed = true)
        val prefs = mockk<UserPreferences>(relaxed = true)
        coEvery { prefs.getUserEmail() } returns "test@example.com"
        coEvery { prefs.isGuestUser() } returns false
        coEvery { prefs.getBirthProfile() } returns null
        val viewModel = BirthDataViewModel(api, prefs).also(block)
        composeTestRule.setContent {
            DestinyTheme {
                BirthDataScreen(
                    onSaved = {},
                    onBack = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    @Test
    fun header_title_create_birth_chart_is_displayed() {
        renderScreen()
        composeTestRule.onNodeWithText("Create your birth chart").assertIsDisplayed()
    }

    @Test
    fun header_subtitle_is_displayed() {
        renderScreen()
        // Contains "birth details" anywhere in the visible text
        composeTestRule.onNodeWithText("birth details", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    // ── Name field ────────────────────────────────────────────────────────────

    @Test
    fun name_field_label_is_displayed() {
        renderScreen()
        composeTestRule.onNodeWithText("Your Name").assertIsDisplayed()
    }

    @Test
    fun name_field_is_editable() {
        renderScreen()
        composeTestRule.onNodeWithContentDescription("Your Name", substring = true)
            .assertHasClickAction()
    }

    // ── Date row ─────────────────────────────────────────────────────────────

    @Test
    fun date_of_birth_row_shows_select_date_placeholder() {
        renderScreen()
        composeTestRule.onNodeWithText("Select Date").assertIsDisplayed()
    }

    @Test
    fun date_of_birth_row_is_tappable() {
        renderScreen()
        composeTestRule
            .onNodeWithContentDescription("Date of birth", substring = true, ignoreCase = true)
            .assertHasClickAction()
    }

    // ── Time row ─────────────────────────────────────────────────────────────

    @Test
    fun time_of_birth_row_shows_select_time_placeholder() {
        renderScreen()
        composeTestRule.onNodeWithText("Select Time").assertIsDisplayed()
    }

    @Test
    fun time_of_birth_row_is_tappable_when_time_known() {
        renderScreen()
        composeTestRule
            .onNodeWithContentDescription("Time of birth", substring = true, ignoreCase = true)
            .assertHasClickAction()
    }

    // ── Time unknown toggle ───────────────────────────────────────────────────

    @Test
    fun time_unknown_toggle_label_is_displayed() {
        renderScreen()
        composeTestRule.onNodeWithText("I don't know my birth time").assertIsDisplayed()
    }

    @Test
    fun time_unknown_toggle_is_tappable() {
        renderScreen()
        composeTestRule
            .onNodeWithText("I don't know my birth time")
            .assertHasClickAction()
    }

    @Test
    fun time_warning_shown_after_toggling_time_unknown() {
        renderScreen()
        composeTestRule.onNodeWithText("I don't know my birth time").performClick()
        composeTestRule
            .onNodeWithText("birth time", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    // ── Location row ─────────────────────────────────────────────────────────

    @Test
    fun place_of_birth_row_shows_placeholder() {
        renderScreen()
        composeTestRule.onNodeWithText("Select Birth City").assertIsDisplayed()
    }

    @Test
    fun place_of_birth_row_is_tappable() {
        renderScreen()
        composeTestRule
            .onNodeWithContentDescription("Place of birth", substring = true, ignoreCase = true)
            .assertHasClickAction()
    }

    // ── Gender row ────────────────────────────────────────────────────────────

    @Test
    fun gender_row_shows_placeholder() {
        renderScreen()
        composeTestRule.onNodeWithText("Select Gender").assertIsDisplayed()
    }

    @Test
    fun gender_row_is_tappable() {
        renderScreen()
        composeTestRule
            .onNodeWithContentDescription("Gender identity", substring = true, ignoreCase = true)
            .assertHasClickAction()
    }

    // ── Submit button ─────────────────────────────────────────────────────────

    @Test
    fun continue_button_is_disabled_when_form_is_empty() {
        renderScreen()
        composeTestRule
            .onNodeWithContentDescription("Continue", substring = true, ignoreCase = true)
            .assertIsNotEnabled()
    }
}
