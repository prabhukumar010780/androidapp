package com.destinyai.astrology.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.destinyai.astrology.data.repository.AuthRepository
import com.destinyai.astrology.ui.theme.DestinyTheme
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TDD spec for AuthScreen semantic structure (Compose UI tests).
 *
 * Each test pins one piece of behaviour the iOS `AuthView` provides
 * that the Android `AuthScreen` must match. Tests describe the
 * EXTERNAL behaviour (what a user / E2E test would see), not internal
 * markers — so the production code stays clean.
 *
 * iOS reference: ios_app/Views/Auth/AuthView.swift
 *
 * NOTE: Instrumented tests — require an emulator or device.
 * Run via `./gradlew connectedAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class AuthScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun renderScreen(allowGuest: Boolean = true) {
        val repository = mockk<AuthRepository>(relaxed = true)
        coEvery { repository.getSavedUser() } returns null
        val viewModel = AuthViewModel(repository)
        composeTestRule.setContent {
            DestinyTheme {
                AuthScreen(
                    onNavigateToMain = {},
                    onNavigateToBirthData = {},
                    onNavigateToWaitlist = {},
                    viewModel = viewModel,
                    allowGuest = allowGuest,
                )
            }
        }
    }

    @Test
    fun welcome_title_is_displayed() {
        renderScreen()
        composeTestRule.onNodeWithText("Welcome to Destiny").assertIsDisplayed()
    }

    @Test
    fun subtitle_is_displayed() {
        renderScreen()
        composeTestRule
            .onNodeWithText("Sign in to save your birth chart and chats")
            .assertIsDisplayed()
    }

    @Test
    fun continue_with_apple_button_is_displayed_and_clickable() {
        renderScreen()
        composeTestRule
            .onNodeWithText("Continue with Apple", substring = true)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun continue_with_google_button_is_displayed_and_clickable() {
        renderScreen()
        composeTestRule
            .onNodeWithText("Continue with Google")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun continue_as_guest_is_shown_when_guest_signup_is_allowed() {
        renderScreen(allowGuest = true)
        composeTestRule
            .onNodeWithText("Continue as Guest")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun continue_as_guest_is_hidden_when_waitlist_gating_is_on() {
        renderScreen(allowGuest = false)
        composeTestRule.onNodeWithText("Continue as Guest").assertDoesNotExist()
    }

    @Test
    fun localized_or_divider_text_is_displayed() {
        renderScreen()
        composeTestRule
            .onNodeWithText("or", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun terms_of_service_link_is_displayed_and_clickable() {
        renderScreen()
        composeTestRule
            .onNodeWithText("Terms of Service")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun privacy_policy_link_is_displayed_and_clickable() {
        renderScreen()
        composeTestRule
            .onNodeWithText("Privacy Policy")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
