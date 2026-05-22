#!/usr/bin/env python3
"""
Convert iOS .strings files to Android strings.xml format.
Run from the android_app/ directory.
"""

import os
import re
import html

IOS_BASE = "../ios_app/ios_app"
ANDROID_RES = "app/src/main/res"

# iOS keys that are reserved Java keywords → rename for Android
RESERVED_KEY_RENAMES = {
    "continue": "action_continue",
    "new": "action_new",
    "default": "default_label",
    "class": "class_label",
    "return": "action_return",
    "switch": "action_switch",
    "do": "action_do",
    "for": "label_for",
    "if": "label_if",
    "else": "label_else",
    "try": "action_try",
    "final": "label_final",
    "import": "action_import",
    "package": "label_package",
    "this": "label_this",
    "null": "label_null",
    "true": "label_true",
    "false": "label_false",
    "void": "label_void",
    "int": "label_int",
    "long": "label_long",
    "float": "label_float",
    "double": "label_double",
    "byte": "label_byte",
    "char": "label_char",
    "short": "label_short",
    "boolean": "label_boolean",
    "static": "label_static",
    "private": "label_private",
    "public": "label_public",
    "protected": "label_protected",
    "abstract": "label_abstract",
    "interface": "label_interface",
    "enum": "label_enum",
    "extends": "label_extends",
    "implements": "label_implements",
    "super": "label_super",
    "throw": "action_throw",
    "throws": "action_throws",
    "catch": "action_catch",
    "finally": "label_finally",
    "while": "label_while",
    "break": "action_break",
    "case": "label_case",
    "goto": "action_goto",
    "native": "label_native",
    "instanceof": "label_instanceof",
    "synchronized": "label_synchronized",
    "volatile": "label_volatile",
    "transient": "label_transient",
    "assert": "action_assert",
    "strictfp": "label_strictfp",
    "const": "label_const",
}

# iOS .lproj dir → Android values dir suffix
LANG_MAP = {
    "en": "",
    "de": "-de",
    "es": "-es",
    "fr": "-fr",
    "hi": "-hi",
    "ja": "-ja",
    "kn": "-kn",
    "ml": "-ml",
    "pt": "-pt",
    "ru": "-ru",
    "ta": "-ta",
    "te": "-te",
    "zh-Hans": "-zh-rCN",
}

# iOS format: "key" = "value";  (possibly multi-line with \n)
_ENTRY_RE = re.compile(r'^"([^"]+)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;', re.MULTILINE)


def ios_value_to_android(value: str) -> str:
    """Convert iOS string value to Android XML-safe string."""
    # Unescape iOS escape sequences first
    value = value.replace('\\"', '"')
    value = value.replace("\\n", "\n")
    value = value.replace("\\t", "\t")
    value = value.replace("\\\\", "\\")

    # Android XML escaping
    value = value.replace("&", "&amp;")
    value = value.replace("<", "&lt;")
    value = value.replace(">", "&gt;")
    # Apostrophes must be escaped in Android string resources
    value = value.replace("'", "\\'")
    # Quotes inside string values don't need escaping in XML (using > / < / &)
    # but double quotes used standalone need backslash escape in Android
    value = value.replace('"', '\\"')

    # Collapse literal newlines to space (keep \n as actual newlines for multi-line)
    return value


def _sanitize_key(key: str) -> str:
    """Make iOS key safe for Android resource names (no spaces, no Java keywords)."""
    # Replace spaces with underscores and lowercase (e.g. "Sudden Events" → "sudden_events")
    sanitized = key.strip().replace(" ", "_").lower()
    # Strip any remaining characters not valid in Android resource names
    sanitized = re.sub(r"[^a-zA-Z0-9_]", "_", sanitized)
    return RESERVED_KEY_RENAMES.get(sanitized, sanitized)


def parse_strings_file(path: str) -> list[tuple[str, str]]:
    """Return ordered list of (key, value) pairs from an iOS .strings file."""
    with open(path, encoding="utf-8") as f:
        content = f.read()
    pairs = _ENTRY_RE.findall(content)
    return [(_sanitize_key(k), v) for k, v in pairs]


def write_android_xml(pairs: list[tuple[str, str]], out_path: str) -> None:
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    # Deduplicate: last value for each key wins (same as iOS behavior for duplicate keys)
    seen: dict[str, str] = {}
    for key, value in pairs:
        seen[key] = value
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for key, raw_value in seen.items():
        value = ios_value_to_android(raw_value)
        lines.append(f'    <string name="{key}">{value}</string>')
    lines.append("</resources>")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"  Written {len(seen)} strings → {out_path}")


def main() -> None:
    script_dir = os.path.dirname(os.path.abspath(__file__))
    android_root = os.path.dirname(script_dir)
    ios_base = os.path.normpath(os.path.join(android_root, IOS_BASE))
    android_res = os.path.join(android_root, ANDROID_RES)

    for lang, suffix in LANG_MAP.items():
        lproj = os.path.join(ios_base, f"{lang}.lproj", "Localizable.strings")
        if not os.path.exists(lproj):
            print(f"  SKIP {lang} — {lproj} not found")
            continue

        values_dir = f"values{suffix}"
        out_path = os.path.join(android_res, values_dir, "strings.xml")

        pairs = parse_strings_file(lproj)
        write_android_xml(pairs, out_path)

    print("Done.")


if __name__ == "__main__":
    main()
