# android_app/e2e/test_01_onboarding.py
"""
E2E: First-launch flow — splash → language → onboarding → auth.

Mirrors the REAL Android navigation (SplashViewModel → LANGUAGE_SELECTION →
ONBOARDING → AUTH). Runs first (test_01) so it exercises the pre-auth screens
before the `at_home` fixture drives past them for the later suites.
"""

from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


def _wait(driver, aid, timeout=20):
    WebDriverWait(driver, timeout).until(
        EC.presence_of_element_located((AppiumBy.ACCESSIBILITY_ID, aid))
    )


class TestOnboarding:
    def test_start_of_flow_is_splash_or_language(self, driver, screens):
        # Splash auto-advances after ~2.5s, so depending on timing the first
        # observable surface is either the splash overlay or the language screen
        # it hands off to. Either proves we launched into the real first-run flow.
        assert screens.onboarding.present("splash_screen") or \
            screens.onboarding.present("language_card_en"), \
            "app did not launch into splash/language first-run flow"

    def test_language_selection_appears(self, driver):
        _wait(driver, "language_card_en", timeout=20)

    def test_select_language_advances_to_onboarding(self, driver, screens):
        screens.onboarding.tap("language_card_en")
        screens.onboarding.tap("language_continue_button")
        _wait(driver, "onboarding_screen", timeout=20)

    def test_onboarding_advances_to_auth(self, driver, screens):
        # Page through the onboarding slides via the primary Continue CTA; the
        # last slide's Continue navigates to the auth screen.
        import time
        for _ in range(6):
            if screens.onboarding.present("auth_screen"):
                break
            if not screens.onboarding.present("onboarding_continue"):
                break
            screens.onboarding.tap("onboarding_continue")
            time.sleep(1.2)
        _wait(driver, "auth_screen", timeout=20)

    def test_auth_shows_guest_and_google_buttons(self, screens):
        assert screens.onboarding.present("continue_as_guest_button")
        assert screens.onboarding.present("google_sign_in_button")
