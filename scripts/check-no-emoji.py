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

# Emoji and pictograph blocks, dingbats/symbols used as emoji, flags, variation selector 16 and ZWJ.
EMOJI = re.compile(
    "["
    "\U0001F000-\U0001FAFF"  # pictographs, emoticons, transport, symbols & pictographs extended
    "\U0001F1E6-\U0001F1FF"  # regional indicators (flags)
    "☀-➿"          # misc symbols + dingbats (check marks, crosses, stars, ...)
    "⬀-⯿"          # misc symbols and arrows used as emoji (stars, circles)
    "⌚⌛⏩-⏺"  # watch, hourglass, media buttons
    "️‍"           # emoji presentation selector, zero-width joiner
    "]"
)

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
