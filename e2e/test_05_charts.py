# android_app/e2e/test_05_charts.py
"""
E2E: Chart sheet.

The Android Charts screen is a single vertical-scroll layout (birth info +
North/South chart + planetary grid + badge legend) — it intentionally OMITS the
Dasha/Transits tabs to match iOS PlanetaryPositionsSheet.swift (surfaced on Home
instead). So there are no tab assertions here.

Charts opens from a chat response that contains chart data (open_chart_button).
That button only appears when the LLM returns chart-shaped output, so the entry
is soft-guarded — the suite must not fail because a live model reply varied.
"""

import pytest


class TestCharts:
    def test_charts_open_from_chat(self, at_home):
        screens = at_home
        screens.home.tap_chat_tab()
        screens.chat.send("Show me my natal chart planets")
        screens.chat.wait_response()
        if not screens.chat.present("open_chart_button"):
            pytest.skip("LLM reply did not include a chart button this run")
        screens.chat.tap("open_chart_button")
        assert screens.charts.is_visible()

    def test_charts_screen_is_scrollable_layout(self, at_home):
        screens = at_home
        if not screens.charts.is_visible():
            pytest.skip("charts sheet not open (no chart button in prior reply)")
        # No tabs — assert the screen root + close affordance exist.
        assert screens.charts.present("charts_screen")
        assert screens.charts.present("charts_close_button")

    def test_close_chart_returns_to_chat(self, at_home):
        screens = at_home
        if not screens.charts.is_visible():
            pytest.skip("charts sheet not open")
        screens.charts.tap_close()
        assert screens.chat.is_visible()
