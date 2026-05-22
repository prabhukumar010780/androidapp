# android_app/e2e/test_02_home.py
"""
E2E: Home screen.
Mirrors: ios_app/e2e/test_02_home.py
"""

import pytest


class TestHomeScreen:
    def test_home_screen_visible(self, screens):
        assert screens.home.is_visible()

    def test_chat_tab_exists(self, screens):
        assert screens.home.present("tab_chat")

    def test_match_tab_exists(self, screens):
        assert screens.home.present("tab_match")

    def test_profile_button_exists(self, screens):
        assert screens.home.present("home_profile_button")

    def test_history_button_exists(self, screens):
        assert screens.home.present("home_history_button")

    def test_notifications_button_exists(self, screens):
        assert screens.home.present("home_notifications_button")

    def test_dasha_card_shows_text(self, screens):
        screens.home.wait_for("dasha_insight_card")
        text = screens.home.dasha_card_text()
        assert len(text) > 0

    def test_yoga_highlight_card_visible(self, screens):
        assert screens.home.present("yoga_highlight_card")

    def test_tap_chat_tab_opens_chat(self, screens):
        screens.home.tap_chat_tab()
        assert screens.chat.is_visible()

    def test_navigate_back_to_home(self, driver, screens):
        driver.back()
        assert screens.home.is_visible()
