# android_app/e2e/conftest.py
"""
Android E2E session setup — mirrors ios_app/e2e/conftest.py.
Uses UiAutomator2 instead of XCUITest.

Prerequisites:
    appium --version   # 2.x
    appium driver list # uiautomator2 must be installed

Run:
    appium --port 4723 &
    cd android_app/e2e && source ../../astrology_api/astroapi-v2/venv/bin/activate
    pytest . -v --html=screenshots/report.html
"""

import os
import pytest
from appium import webdriver
from appium.options.android import UiAutomator2Options
from .helpers.screens import Screens

TEST_ENV = os.environ.get("TEST_ENV", "local")
BASE_URLS = {
    "local":   "http://10.0.2.2:8000",   # emulator → host localhost
    "staging": "https://astroapi-test-dsqvza5jza-ul.a.run.app",
}

E2E_EMAIL = "prabhukushwaha@gmail.com"

BIRTH = {
    "dob":       "1980-07-01",
    "time":      "06:32",
    "latitude":  "21.2138",
    "longitude": "81.3943",
    "city":      "Bhilai",
}

PARTNER = {
    "name":      "Smita",
    "dob":       "1980-11-13",
    "time":      "09:30",
    "city":      "Belgaum, Karnataka",
    "latitude":  "15.8497",
    "longitude": "74.4977",
}

APP_PACKAGE = "com.destinyai.astrology"
APP_ACTIVITY = ".ui.MainActivity"


@pytest.fixture(scope="session")
def driver():
    opts = UiAutomator2Options()
    opts.platform_name = "Android"
    opts.automation_name = "UiAutomator2"
    opts.app_package = APP_PACKAGE
    opts.app_activity = APP_ACTIVITY
    opts.no_reset = False

    # Inject E2E mode via intent extras — MainActivity reads these in debug builds
    opts.intent_action = "android.intent.action.MAIN"
    opts.intent_extras = {
        "UI_TEST_MODE": True,
        "E2E_USER_EMAIL": E2E_EMAIL,
        "API_BASE_URL": BASE_URLS[TEST_ENV],
        "E2E_DOB": BIRTH["dob"],
        "E2E_TIME": BIRTH["time"],
        "E2E_LATITUDE": BIRTH["latitude"],
        "E2E_LONGITUDE": BIRTH["longitude"],
        "E2E_CITY": BIRTH["city"],
        "E2E_PARTNER_NAME": PARTNER["name"],
        "E2E_PARTNER_DOB": PARTNER["dob"],
        "E2E_PARTNER_TIME": PARTNER["time"],
        "E2E_PARTNER_CITY": PARTNER["city"],
        "E2E_PARTNER_LAT": PARTNER["latitude"],
        "E2E_PARTNER_LON": PARTNER["longitude"],
    }

    drv = webdriver.Remote("http://127.0.0.1:4723", options=opts)
    drv.implicitly_wait(15)
    yield drv
    drv.quit()


@pytest.fixture(scope="session")
def screens(driver):
    return Screens(driver)
