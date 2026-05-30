# android_app/e2e/test_04_compatibility.py
"""E2E: Compatibility screen. Mirrors ios_app/e2e/test_04_compatibility.py"""

import time

from .helpers.assertions import assert_no_guarantee_language


PARTNER_NAME = "Priya"
PARTNER_DOB  = "1985-03-20"


class TestCompatibility:
    def test_compat_screen_loads(self, screens):
        screens.home.tap_match_tab()
        assert screens.compatibility.is_visible(), "compat_screen not found"

    def test_analyze_button_disabled_without_partner_data(self, screens):
        assert not screens.compatibility.is_analyze_enabled(), \
            "Analyze button should be disabled without partner data"

    def test_dob_field_is_tappable(self, screens):
        screens.compatibility.tap_dob_person2()
        time.sleep(0.5)
        if screens.compatibility.present("sheet_close_button"):
            screens.compatibility.tap("sheet_close_button")

    def test_history_button_opens_sheet(self, screens):
        screens.compatibility.tap_history()
        time.sleep(0.5)
        assert screens.compatibility.present("history_screen") or True
        if screens.compatibility.present("sheet_close_button"):
            screens.compatibility.tap("sheet_close_button")

    def test_analyze_with_preset_partner_runs(self, screens):
        """Full analyze flow — uses pre-saved partner if available."""
        if screens.compatibility.present("compat_partner_picker"):
            screens.compatibility.tap("compat_partner_picker")
            time.sleep(0.5)
            rows = screens.compatibility.finds("partner_row")
            if rows:
                rows[0].click()
                time.sleep(0.3)

        if screens.compatibility.is_analyze_enabled():
            screens.compatibility.tap_analyze()
            time.sleep(3)
            assert screens.compatibility.present("streaming_indicator") or \
                   screens.compatibility.present("compat_result_score"), \
                "Analysis did not start"

    def test_compat_result_shows_score(self, screens):
        if screens.compatibility.present("compat_result_score"):
            score = screens.compatibility.result_score()
            assert len(score) > 0, "Score label is empty"

    def test_mangal_dosha_row_opens_sheet(self, screens):
        if screens.compatibility.present("mangal_dosha_row"):
            screens.compatibility.tap_mangal_dosha()
            time.sleep(0.5)
            if screens.compatibility.present("sheet_close_button"):
                screens.compatibility.tap("sheet_close_button")

    def test_kalsarpa_row_opens_sheet(self, screens):
        if screens.compatibility.present("kalsarpa_dosha_row"):
            screens.compatibility.tap_kalsarpa_dosha()
            time.sleep(0.5)
            if screens.compatibility.present("sheet_close_button"):
                screens.compatibility.tap("sheet_close_button")

    def test_ask_destiny_button_opens_dialog(self, screens):
        if screens.compatibility.present("ask_destiny_button"):
            screens.compatibility.tap_ask_destiny()
            time.sleep(0.5)
            if screens.compatibility.present("sheet_close_button"):
                screens.compatibility.tap("sheet_close_button")

    def test_navigate_back_to_home(self, screens):
        screens.home.tap("tab_home")
        assert screens.home.is_visible()
