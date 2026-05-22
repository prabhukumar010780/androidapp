# android_app/e2e/test_05_charts.py
"""E2E: Chart sheet, dasha/transits/planets tabs. Mirrors ios_app/e2e/test_05_charts.py"""


class TestCharts:
    def test_charts_open_from_chat(self, screens):
        screens.home.tap_chat_tab()
        screens.chat.send("Show me my natal chart planets")
        screens.chat.wait_response()
        if screens.chat.present("open_chart_button"):
            screens.chat.tap("open_chart_button")
            assert screens.charts.is_visible()

    def test_dasha_tab_visible(self, screens):
        if screens.charts.is_visible():
            screens.charts.tap_dasha_tab()
            assert screens.charts.present("charts_tab_dasha")

    def test_transits_tab_visible(self, screens):
        if screens.charts.is_visible():
            screens.charts.tap_transits_tab()
            assert screens.charts.present("charts_tab_transits")

    def test_planets_tab_visible(self, screens):
        if screens.charts.is_visible():
            screens.charts.tap_planets_tab()
            assert screens.charts.present("charts_tab_planets")

    def test_close_chart_returns_to_chat(self, screens):
        if screens.charts.is_visible():
            screens.charts.tap_close()
            assert screens.chat.is_visible()
