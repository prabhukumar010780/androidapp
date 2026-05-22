# android_app/e2e/test_04_compatibility.py
"""E2E: Compatibility screen. Mirrors ios_app/e2e/test_04_compatibility.py"""

from .helpers.assertions import assert_no_guarantee_language


class TestCompatibility:
    def test_compatibility_screen_visible(self, screens):
        screens.home.tap_match_tab()
        assert screens.compatibility.is_visible()

    def test_analyze_button_exists(self, screens):
        screens.home.tap_match_tab()
        assert screens.compatibility.present("analyze_button")

    def test_compatibility_result_has_no_guarantees(self, screens):
        screens.home.tap_match_tab()
        screens.compatibility.tap_analyze()
        screens.compatibility.wait_result()
        result = screens.compatibility.result_text()
        assert_no_guarantee_language(result)
