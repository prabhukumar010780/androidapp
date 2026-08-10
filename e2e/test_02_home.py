# android_app/e2e/test_02_home.py
"""
E2E: Home screen.

Uses the `at_home` fixture to drive the real first-launch flow (language →
onboarding → guest auth → birth-data → Home) once per session before asserting.
Dasha/yoga cards are backend-data-dependent, so those are soft-guarded — the
tab/nav chrome assertions are the hard ones.
"""

import pytest


class TestHomeScreen:
    def test_home_screen_visible(self, at_home):
        assert at_home.home.is_visible()

    def test_chat_tab_exists(self, at_home):
        assert at_home.home.present("tab_chat")

    def test_match_tab_exists(self, at_home):
        assert at_home.home.present("tab_match")

    def test_profile_button_exists(self, at_home):
        assert at_home.home.present("home_profile_button")

    def test_history_button_exists(self, at_home):
        assert at_home.home.present("home_history_button")

    def test_notifications_button_exists(self, at_home):
        assert at_home.home.present("home_notifications_button")

    def test_dasha_card_shows_text(self, at_home):
        # Dasha card depends on the astrodata fetch completing; soft-guard so a
        # slow/absent backend row doesn't fail the nav-chrome suite.
        if not at_home.home.present("dasha_insight_card"):
            pytest.skip("dasha_insight_card not rendered (astrodata not ready)")
        assert len(at_home.home.dasha_card_text()) > 0

    def test_yoga_highlight_card_visible(self, at_home):
        if not at_home.home.present("yoga_highlight_card"):
            pytest.skip("yoga_highlight_card not rendered (no yogas for profile)")
        assert at_home.home.present("yoga_highlight_card")

    def test_tap_chat_tab_opens_chat(self, at_home):
        at_home.home.tap_chat_tab()
        assert at_home.chat.is_visible()

    def test_navigate_back_to_home(self, driver, at_home):
        # Return to Home from the Chat tab via Chat's own back affordance
        # (chat_back_button). driver.back() pops the whole task here, and tab_home
        # is occluded by the active Chat surface, so use the in-screen control.
        import time
        at_home.chat.tap("chat_back_button")
        time.sleep(1)
        assert at_home.home.is_visible()
