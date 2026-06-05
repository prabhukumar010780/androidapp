# android_app/e2e/helpers/screens.py
"""
Page objects for Android E2E tests.
Mirrors ios_app/e2e/helpers/screens.py.

Android elements use content-description (accessibility label) — same IDs as iOS
to keep the test layer platform-agnostic where possible.
"""

import os
from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC


class _Base:
    def __init__(self, driver):
        self.d = driver
        self._wait = WebDriverWait(driver, 20)

    def find(self, aid):
        return self.d.find_element(AppiumBy.ACCESSIBILITY_ID, aid)

    def finds(self, aid):
        return self.d.find_elements(AppiumBy.ACCESSIBILITY_ID, aid)

    def tap(self, aid):
        self.find(aid).click()

    def present(self, aid) -> bool:
        return len(self.finds(aid)) > 0

    def save_screenshot(self, name: str):
        os.makedirs("android_app/e2e/screenshots", exist_ok=True)
        self.d.save_screenshot(f"android_app/e2e/screenshots/{name}.png")

    def wait_for(self, aid, timeout=20):
        self._wait.until(EC.presence_of_element_located((AppiumBy.ACCESSIBILITY_ID, aid)))

    def wait_gone(self, aid, timeout=90):
        WebDriverWait(self.d, timeout).until_not(
            EC.presence_of_element_located((AppiumBy.ACCESSIBILITY_ID, aid))
        )


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
    def is_visible(self):           return self.present("charts_screen")
    def tap_dasha_tab(self):        self.tap("charts_tab_dasha")
    def tap_transits_tab(self):     self.tap("charts_tab_transits")
    def tap_planets_tab(self):      self.tap("charts_tab_planets")
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
        self.home = HomeScreen(driver)
        self.chat = ChatScreen(driver)
        self.compatibility = CompatibilityScreen(driver)
        self.charts = ChartsScreen(driver)
        self.history = HistoryScreen(driver)
        self.profile = ProfileScreen(driver)
        self.partners = PartnersScreen(driver)
        self.notifications = NotificationsScreen(driver)
        self.subscription = SubscriptionScreen(driver)
