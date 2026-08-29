# android_app/e2e/helpers/screens.py
"""
Page objects for Android E2E tests.
Mirrors ios_app/e2e/helpers/screens.py.

Android elements are located by Compose testTag (exposed as resource-id via
`testTagsAsResourceId = true` at the root composable). Because UiAutomator2 may
report that id either bare (`home_screen`) or package-qualified, and a few
legacy nodes are still only reachable by content-description, `find`/`finds`
try each strategy in order — see `_locator_strategies`.
"""

import os
from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

_APP_PKG = "com.destinyai.astrology"


def _locator_strategies(aid: str):
    """Ordered (by, value) strategies for a Compose testTag / legacy locator.

    Compose `testTagsAsResourceId = true` exposes the testTag as the node's
    resource-id. Depending on the UiAutomator2 / Appium version this is reported
    either as the BARE tag (`home_screen`) or package-qualified
    (`com.destinyai.astrology:id/home_screen`). A few legacy locators may still
    only be reachable via content-description (ACCESSIBILITY_ID). We try each in
    order and use the first that resolves, so the suite is robust to all three.
    """
    return (
        (AppiumBy.ID, aid),
        (AppiumBy.ID, f"{_APP_PKG}:id/{aid}"),
        (AppiumBy.ACCESSIBILITY_ID, aid),
    )


class _Base:
    def __init__(self, driver):
        self.d = driver
        self._wait = WebDriverWait(driver, 20)

    def find(self, aid):
        last = None
        for by, value in _locator_strategies(aid):
            els = self.d.find_elements(by, value)
            if els:
                return els[0]
            last = (by, value)
        # Nothing matched — raise the canonical NoSuchElement via find_element.
        return self.d.find_element(*last)

    def finds(self, aid):
        for by, value in _locator_strategies(aid):
            els = self.d.find_elements(by, value)
            if els:
                return els
        return []

    def tap(self, aid):
        """Tap an element by accessibility id.

        Compose exposes a contentDescription/testTag node that is often distinct
        from the node carrying the click action, so Appium reports the located
        element as clickable=false and element.click() becomes a no-op. To be
        robust we always tap the element's on-screen center via a pointer
        gesture, which dispatches to whatever clickable Compose node sits there.
        """
        el = self.find(aid)
        if el.get_attribute("clickable") == "true":
            el.click()
            return
        rect = el.rect
        cx = int(rect["x"] + rect["width"] / 2)
        cy = int(rect["y"] + rect["height"] / 2)
        self.d.execute_script("mobile: clickGesture", {"x": cx, "y": cy})

    def present(self, aid) -> bool:
        return len(self.finds(aid)) > 0

    def save_screenshot(self, name: str):
        os.makedirs("android_app/e2e/screenshots", exist_ok=True)
        self.d.save_screenshot(f"android_app/e2e/screenshots/{name}.png")

    def wait_for(self, aid, timeout=20):
        WebDriverWait(self.d, timeout).until(lambda _: self.present(aid))

    def wait_gone(self, aid, timeout=90):
        WebDriverWait(self.d, timeout).until_not(lambda _: self.present(aid))


class OnboardingScreen(_Base):
    """First-launch flow driver: language → onboarding slides → auth → guest →
    birth-data (auto-filled via E2EBirthDataOverrides) → profile-setup → Home."""

    def reach_home(self, timeout=60):
        from appium.webdriver.common.appiumby import AppiumBy
        from selenium.common.exceptions import TimeoutException, WebDriverException

        # Already at Home? (a prior test in the session navigated there)
        if self.present("home_screen"):
            return

        # 1. Language selection — tap English card, then continue.
        try:
            self.wait_for("language_card_en", timeout=20)
            self.tap("language_card_en")
            self.tap("language_continue_button")
        except (TimeoutException, WebDriverException):
            pass  # language screen may be skipped if a language was already chosen

        # 2. Onboarding — page through the slides via the primary Continue CTA
        #    (the last slide's Continue navigates to auth). The leading "Skip"
        #    sits inside the status-bar padding zone and isn't reliably tappable,
        #    so we advance with Continue, which also exercises the real slide flow.
        try:
            self.wait_for("onboarding_screen", timeout=15)
            import time
            for _ in range(6):
                if self.present("auth_screen"):
                    break
                if not self.present("onboarding_continue"):
                    break
                self.tap("onboarding_continue")
                time.sleep(1.2)
        except (TimeoutException, WebDriverException):
            pass

        # 3. Auth — continue as guest (mints a guest session against the backend).
        self.wait_for("continue_as_guest_button", timeout=20)
        self.tap("continue_as_guest_button")

        # 4. Birth-data — E2EBirthDataOverrides auto-fills the form so isValid is
        #    true; just tap Continue. Wait for the button (guest mint is async).
        self.wait_for("birth_data_continue", timeout=30)
        self.tap("birth_data_continue")

        # 5. Response-style sheet — a successful save presents the response-style
        #    picker; its Continue triggers onSaved() → profile-setup. Wait for it
        #    (the save round-trips the backend) then continue with the default style.
        try:
            self.wait_for("response_style_continue", timeout=30)
            self.tap("response_style_continue")
        except (TimeoutException, WebDriverException):
            pass  # some flows may skip straight to profile-setup

        # 6. Profile-setup loader prefetches chart + today's prediction (an LLM
        #    call, ~20s) then lands on Home. On first reach of Home the OS
        #    notification-permission dialog appears and covers the screen — grant
        #    it so home_screen becomes visible. Poll generously for the prefetch.
        deadline_polls = max(1, timeout // 3)
        for _ in range(deadline_polls):
            self._grant_notification_permission_if_present()
            if self.present("home_screen"):
                return
            import time
            time.sleep(3)
        # Final explicit wait so the failure message points here if still not Home.
        self._grant_notification_permission_if_present()
        self.wait_for("home_screen", timeout=15)

    def _grant_notification_permission_if_present(self):
        """Grant the Android 13+ POST_NOTIFICATIONS system dialog if it is up.
        It renders in com.android.permissioncontroller (not the app), so match by
        resource-id / text rather than an app accessibility id."""
        from appium.webdriver.common.appiumby import AppiumBy
        for by, sel in [
            (AppiumBy.ID, "com.android.permissioncontroller:id/permission_allow_button"),
            (AppiumBy.XPATH, "//*[@text='Allow' or @text='ALLOW']"),
        ]:
            try:
                els = self.d.find_elements(by, sel)
                if els:
                    els[0].click()
                    import time
                    time.sleep(1)
                    return
            except Exception:
                pass


class HomeScreen(_Base):
    def is_visible(self): return self.present("home_screen")
    def tap_chat_tab(self):         self.tap("tab_chat")
    def tap_match_tab(self):        self.tap("tab_match")
    def tap_profile(self):          self.tap("home_profile_button")
    def tap_history(self):          self.tap("home_history_button")
    def tap_notifications(self):    self.tap("home_notifications_button")
    def tap_life_area(self, area):  self.tap(f"life_area_{area}")
    def tap_yoga_card(self):        self.tap("yoga_highlight_card")
    def tap_dasha_card(self):       self.tap("dasha_insight_card")
    def tap_transit_alert(self):    self.tap("transit_alert_card")
    def dasha_card_text(self):      return self.find("dasha_insight_card").get_attribute("content-desc")


class ChatScreen(_Base):
    def is_visible(self): return self.present("chat_input")

    def send(self, text: str):
        field = self.find("chat_input")
        field.clear()
        field.send_keys(text)
        self.tap("send_button")

    def wait_response(self, timeout=60):
        self.wait_gone("loading_indicator", timeout)

    def last_response_text(self) -> str:
        msgs = self.finds("chat_message_assistant")
        return msgs[-1].get_attribute("content-desc") if msgs else ""

    def tap_copy(self):     self.tap("copy_message_button")
    def tap_new_chat(self): self.tap("new_chat_button")


class CompatibilityScreen(_Base):
    def is_visible(self):                 return self.present("compat_screen")
    def tap_analyze(self):                self.tap("compat_analyze_button")
    def tap_history(self):                self.tap("compat_history_button")
    def is_analyze_enabled(self) -> bool: return self.find("compat_analyze_button").is_enabled()
    def tap_dob_person2(self):            self.tap("compat_person2_dob")
    def result_score(self) -> str:        return self.find("compat_result_score").get_attribute("content-desc")
    def tap_mangal_dosha(self):           self.tap("mangal_dosha_row")
    def tap_kalsarpa_dosha(self):         self.tap("kalsarpa_dosha_row")
    def tap_ask_destiny(self):            self.tap("ask_destiny_button")
    def wait_for_result(self, timeout=60):self.wait_for("compat_result_score", timeout)


class ChartsScreen(_Base):
    # Single vertical-scroll layout (no Dasha/Transits/Planets tabs — those are
    # intentionally omitted to match iOS PlanetaryPositionsSheet.swift).
    def is_visible(self):           return self.present("charts_screen")
    def tap_close(self):            self.tap("charts_close_button")


class HistoryScreen(_Base):
    def is_visible(self):           return self.present("history_screen")
    def thread_count(self) -> int:  return len(self.finds("history_thread_item"))
    def tap_thread(self, index=0):  self.finds("history_thread_item")[index].click()


class ProfileScreen(_Base):
    def is_visible(self):           return self.present("profile_screen")
    def tap_birth_settings(self):   self.tap("profile_birth_settings")
    def tap_language(self):         self.tap("profile_language_settings")
    def tap_chart_style(self):      self.tap("profile_chart_style_settings")


class PartnersScreen(_Base):
    def is_visible(self):           return self.present("partners_screen")
    def tap_add_partner(self):      self.tap("add_partner_button")
    def partner_count(self) -> int: return len(self.finds("partner_list_item"))


class NotificationsScreen(_Base):
    def is_visible(self):           return self.present("notifications_screen")
    def notification_count(self) -> int: return len(self.finds("notification_item"))
    def tap_mark_all_read(self):    self.tap("mark_all_read_button")


class SubscriptionScreen(_Base):
    def is_visible(self):           return self.present("subscription_screen")
    def plan_card_count(self) -> int: return len(self.finds("subscription_plan_card"))


class Screens:
    """Aggregates all screen page objects — injected into every test via fixture."""

    def __init__(self, driver):
        self.onboarding = OnboardingScreen(driver)
        self.home = HomeScreen(driver)
        self.chat = ChatScreen(driver)
        self.compatibility = CompatibilityScreen(driver)
        self.charts = ChartsScreen(driver)
        self.history = HistoryScreen(driver)
        self.profile = ProfileScreen(driver)
        self.partners = PartnersScreen(driver)
        self.notifications = NotificationsScreen(driver)
        self.subscription = SubscriptionScreen(driver)
