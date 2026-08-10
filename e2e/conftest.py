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
import shlex
import time
import pytest
from appium import webdriver
from appium.options.android import UiAutomator2Options
from .helpers.screens import Screens

_shell_quote = shlex.quote

TEST_ENV = os.environ.get("TEST_ENV", "local")
BASE_URLS = {
    "local":   "http://10.0.2.2:8000",   # emulator → host localhost
    "staging": "https://astroapi-test-dsqvza5jza-ul.a.run.app",
}

E2E_EMAIL = "prabhukushwaha@gmail.com"

# The guest identity the backend mints is DETERMINISTIC in the birth data
# (dob/time/city/lat/lng → guest email `YYYYMMDD_HHMM_city_latInt_lngInt@daa.com`,
# iOS-parity). The local backend persists across runs and matches birth data with a
# 150km / ±5min fuzzy radius, so fixed birth data makes the 2nd+ run 409-Conflict on
# /subscription/profile and the app shows the "profile taken" prompt instead of Home.
# Vary the DOB (a whole distinct day) per run so a fresh guest is minted each time;
# the human-visible city stays "Bhilai". Env override lets CI pin a value.
import datetime as _dt
_RUN_SALT = int(os.environ.get("E2E_RUN_SALT") or int(time.time()))
_dob = (_dt.date(1970, 1, 1) + _dt.timedelta(days=_RUN_SALT % 12000)).isoformat()

BIRTH = {
    "name":      "E2E Tester",
    "gender":    "other",
    "dob":       _dob,
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
APP_ACTIVITY = ".MainActivity"


@pytest.fixture(scope="session")
def driver():
    opts = UiAutomator2Options()
    opts.platform_name = "Android"
    opts.automation_name = "UiAutomator2"
    opts.app_package = APP_PACKAGE
    opts.app_activity = APP_ACTIVITY
    opts.no_reset = False

    # Inject E2E mode via real `am start` intent extras. MainActivity reads these
    # in debug builds (E2EBirthDataOverrides / E2EPartnerOverrides). Must use
    # optionalIntentArguments (raw `--ez`/`--es` args) — the older intent_extras
    # capability is NOT honored by UiAutomator2 and silently drops the extras.
    opts.intent_action = "android.intent.action.MAIN"
    string_extras = {
        "E2E_USER_EMAIL": E2E_EMAIL,
        "E2E_USER_NAME": BIRTH["name"],
        "E2E_GENDER": BIRTH["gender"],
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
    args = ["--ez", "UI_TEST_MODE", "true"]
    for k, v in string_extras.items():
        args += ["--es", k, str(v)]
    opts.optional_intent_arguments = " ".join(_shell_quote(a) for a in args)

    drv = webdriver.Remote("http://127.0.0.1:4723", options=opts)
    drv.implicitly_wait(15)
    yield drv
    drv.quit()


@pytest.fixture(scope="session")
def screens(driver):
    return Screens(driver)


@pytest.fixture(scope="session")
def at_home(screens):
    """
    Session-scoped navigation: drive the real first-launch flow once so screens
    that live behind onboarding + auth start at Home. Idempotent — if already at
    Home (a prior test navigated there) it returns immediately.

    Flow: splash → language(English) → onboarding(Skip) → auth(guest) →
    birth-data(auto-filled via E2EBirthDataOverrides) → profile-setup → Home.
    """
    screens.onboarding.reach_home()
    return screens

