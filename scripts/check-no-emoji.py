#!/usr/bin/env python3
"""Repository rule: no emojis in code, docs, commit messages, PRs or issues.

Usage:
  python scripts/check-no-emoji.py                 # scan every tracked text file
  python scripts/check-no-emoji.py --text "..."    # scan a piece of text (PR title/body, commit message)
  git log --format=%B base..head | python scripts/check-no-emoji.py --stdin

Exits with status 1 and lists every offending location when an emoji is found.
"""

import argparse
import re
import subprocess
import sys

# Built from code points so this file itself stays plain ASCII.
_RANGES = [
    (0x1F000, 0x1FAFF),  # pictographs, emoticons, transport, symbols and pictographs extended
    (0x1F1E6, 0x1F1FF),  # regional indicators (flags)
    (0x2600, 0x27BF),    # misc symbols + dingbats (check marks, crosses, stars, ...)
    (0x2B00, 0x2BFF),    # misc symbols and arrows used as emoji (stars, circles)
    (0x231A, 0x231B),    # watch, hourglass
    (0x23E9, 0x23FA),    # media buttons
    (0xFE0F, 0xFE0F),    # emoji presentation selector
    (0x200D, 0x200D),    # zero-width joiner
]
EMOJI = re.compile("[" + "".join(f"{chr(a)}-{chr(b)}" for a, b in _RANGES) + "]")

BINARY_EXTENSIONS = (".png", ".jpg", ".jpeg", ".gif", ".ico", ".pdf", ".jar", ".woff", ".woff2", ".ttf")


def scan_text(label: str, text: str) -> list[str]:
    problems = []
    for number, line in enumerate(text.splitlines(), start=1):
        for match in EMOJI.finditer(line):
            problems.append(f"{label}:{number}: emoji U+{ord(match.group()):04X} -> {line.strip()[:100]}")
    return problems


def tracked_files() -> list[str]:
    output = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True).stdout
    return [f for f in output.splitlines() if f and not f.lower().endswith(BINARY_EXTENSIONS)]


def main() -> int:
    # Report offending characters even on consoles that are not UTF-8 (e.g. Windows cp1252).
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--text", action="append", default=[], help="text to check (repeatable)")
    parser.add_argument("--stdin", action="store_true", help="check text read from standard input")
    args = parser.parse_args()

    problems: list[str] = []
    if args.text or args.stdin:
        for index, text in enumerate(args.text, start=1):
            problems += scan_text(f"text#{index}", text)
        if args.stdin:
            problems += scan_text("stdin", sys.stdin.read())
    else:
        for path in tracked_files():
            try:
                with open(path, encoding="utf-8") as handle:
                    problems += scan_text(path, handle.read())
            except (UnicodeDecodeError, FileNotFoundError):
                continue

    if problems:
        print("Emojis are not allowed in this repository (see CONTRIBUTING.md):")
        print("\n".join(problems))
        return 1
    print("No emojis found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
