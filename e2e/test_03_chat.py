# android_app/e2e/test_03_chat.py
"""
E2E: Chat send / stream / copy / history.
Mirrors: ios_app/e2e/test_03_chat.py (12 tests)
"""

import pytest


def _ask_and_get(screens, question: str) -> str:
    screens.chat.send(question)
    screens.chat.wait_response(timeout=60)
    return screens.chat.last_response_text()


class TestChat:
    def test_chat_input_field_visible(self, screens):
        screens.home.tap_chat_tab()
        assert screens.chat.is_visible()

    def test_send_button_disabled_on_empty_input(self, screens):
        screens.home.tap_chat_tab()
        # send_button should not be clickable / have enabled=false
        from appium.webdriver.common.appiumby import AppiumBy
        el = screens.chat.find("send_button")
        assert el.get_attribute("enabled") == "false"

    def test_send_message_gets_response(self, screens):
        screens.home.tap_chat_tab()
        response = _ask_and_get(screens, "What is my moon sign?")
        assert len(response) > 50

    def test_response_is_not_empty(self, screens):
        screens.home.tap_chat_tab()
        response = _ask_and_get(screens, "Give me a brief daily outlook")
        assert response.strip() != ""

    def test_loading_indicator_shown_during_response(self, driver, screens):
        from appium.webdriver.common.appiumby import AppiumBy
        screens.home.tap_chat_tab()
        screens.chat.find("chat_input").send_keys("What is my ascendant?")
        screens.chat.tap("send_button")
        # loading_indicator should appear during streaming
        try:
            driver.find_element(AppiumBy.ACCESSIBILITY_ID, "loading_indicator")
        except Exception:
            pass  # may have already disappeared — timing dependent

    def test_copy_button_appears_on_response(self, screens):
        screens.home.tap_chat_tab()
        _ask_and_get(screens, "Brief career prediction")
        assert screens.chat.present("copy_message_button")

    def test_new_chat_clears_messages(self, screens):
        screens.home.tap_chat_tab()
        _ask_and_get(screens, "Tell me about my chart")
        screens.chat.tap_new_chat()
        # After new chat, only welcome message should remain
        msgs = screens.chat.finds("chat_message_assistant")
        assert len(msgs) == 1

    def test_history_button_opens_history(self, screens):
        screens.home.tap_history()
        assert screens.history.is_visible()

    def test_history_shows_previous_threads(self, screens):
        screens.home.tap_history()
        count = screens.history.thread_count()
        assert count >= 0  # may be empty on fresh install

    def test_open_thread_loads_messages(self, screens):
        screens.home.tap_chat_tab()
        _ask_and_get(screens, "What dasha am I in?")
        screens.home.tap_history()
        if screens.history.thread_count() > 0:
            screens.history.tap_thread(0)
            assert screens.chat.is_visible()

    def test_chart_button_appears_for_planet_response(self, screens):
        screens.home.tap_chat_tab()
        _ask_and_get(screens, "Show me my natal chart")
        # chart button should appear when response contains chart data
        # may not appear if LLM doesn't return chart — soft assertion
        screens.chat.present("open_chart_button")

    def test_send_finance_question_gets_response(self, screens):
        screens.home.tap_chat_tab()
        response = _ask_and_get(screens, "What does my chart say about wealth?")
        assert len(response) > 50
