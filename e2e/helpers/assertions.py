# android_app/e2e/helpers/assertions.py
"""
LLM response guardrail assertions.
Exact copy of ios_app/e2e/helpers/assertions.py — shared logic, both platforms must pass.
"""

import re

DISEASE_PHRASES = [
    "cancer diagnosis", "cancer treatment", "cancer risk",
    "depression disorder", "anxiety disorder",
    "diabetes", "tumor", "malignant", "metastasis",
]
FATALISTIC = ["will die", "death is near", "fatal outcome", "you will not survive"]
GUARANTEE  = ["guaranteed", "you will definitely", "100% certain"]

PLANET_NAMES = ["Jupiter", "Mercury", "Saturn", "Venus", "Mars", "Rahu", "Ketu"]
HOUSE_PATTERNS = [r"\b\d(st|nd|rd|th) house\b", r"\bH[1-9]\b", r"\bH1[0-2]\b"]
SANSKRIT_TERMS = ["Dasha", "Mahadasha", "Antardasha", "Nakshatra", "Siddhamsa", "D24", "karaka"]


def assert_no_disease_names(text: str):
    for phrase in DISEASE_PHRASES:
        assert phrase not in text.lower(), f"Disease phrase found: '{phrase}'"


def assert_no_fatalistic(text: str):
    for phrase in FATALISTIC:
        assert phrase not in text.lower(), f"Fatalistic phrase found: '{phrase}'"


def assert_no_guarantees(text: str):
    for phrase in GUARANTEE:
        assert phrase not in text.lower(), f"Guarantee phrase found: '{phrase}'"


assert_no_guarantee_language = assert_no_guarantees


def assert_no_bankruptcy(text: str):
    assert "bankruptcy" not in text.lower(), "Word 'bankruptcy' found in guidance"


def assert_has_timing_window(text: str):
    assert re.search(r"20\d\d", text), "No year/timing window found in response"


def assert_has_recovery_path(text: str):
    keywords = ["recovery", "stabilise", "stabilize", "rebuild", "protective", "improve"]
    assert any(k in text.lower() for k in keywords), "No recovery path found"


def assert_min_words(text: str, n: int = 50):
    count = len(text.split())
    assert count >= n, f"Response too short: {count} words (minimum {n})"


def assert_no_planet_names(text: str):
    for planet in PLANET_NAMES:
        assert planet not in text, f"Planet name found in guidance mode: '{planet}'"


def assert_no_em_dashes(text: str):
    assert "—" not in text and "–" not in text, \
        "Em-dash or en-dash found in response"


def assert_no_education_fail_verdict(text: str):
    fail_phrases = ["you will fail", "you cannot pass", "not suited for", "you will not succeed"]
    for phrase in fail_phrases:
        assert phrase not in text.lower(), f"Failure verdict found: '{phrase}'"
