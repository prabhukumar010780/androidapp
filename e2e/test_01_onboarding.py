# android_app/e2e/test_01_onboarding.py
"""
E2E: Onboarding flow.
Mirrors: ios_app/e2e/test_01_onboarding.py

Note: UI_TEST_MODE is NOT set here — tests cover the real first-launch flow.
"""

import pytest


class TestOnboarding:
    def test_splash_screen_shows_app_name(self, driver):
        from appium.webdriver.common.appiumby import AppiumBy
        from selenium.webdriver.support.ui import WebDriverWait
        from selenium.webdriver.support import expected_conditions as EC

        wait = WebDriverWait(driver, 15)
        # Splash screen should show the app name or logo
        wait.until(EC.presence_of_element_located(
            (AppiumBy.ACCESSIBILITY_ID, "splash_screen")
        ))

    def test_auth_screen_appears_after_splash(self, driver):
        from appium.webdriver.common.appiumby import AppiumBy
        from selenium.webdriver.support.ui import WebDriverWait
        from selenium.webdriver.support import expected_conditions as EC

        wait = WebDriverWait(driver, 20)
        wait.until(EC.presence_of_element_located(
            (AppiumBy.ACCESSIBILITY_ID, "auth_screen")
        ))

    def test_continue_as_guest_button_exists(self, screens):
        screens.home.wait_for("continue_as_guest_button")

    def test_google_sign_in_button_exists(self, screens):
        screens.home.wait_for("google_sign_in_button")
