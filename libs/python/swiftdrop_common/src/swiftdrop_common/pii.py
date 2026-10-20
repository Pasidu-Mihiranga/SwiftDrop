"""Masks personal data and credentials. Same rules as the Java PiiMasker and the Node maskPii."""

import re
from collections.abc import Callable

_Rule = tuple[re.Pattern[str], Callable[[re.Match[str]], str]]


def _mask_digits(phone: str) -> str:
    """Keeps the first two and last two digits: +94771234567 -> +94*******67."""
    total = sum(ch.isdigit() for ch in phone)
    seen = 0
    out = []
    for ch in phone:
        if ch.isdigit():
            seen += 1
            out.append("*" if 2 < seen <= total - 2 else ch)
        else:
            out.append(ch)
    return "".join(out)


_RULES: list[_Rule] = [
    (
        re.compile(
            r'("?(?:password|passwd|secret|client_secret|api[_-]?key|access_token|refresh_token)"?\s*[:=]\s*"?)([^"\s,&}]+)',
            re.IGNORECASE,
        ),
        lambda m: f"{m.group(1)}***",
    ),
    (re.compile(r"(bearer\s+)[A-Za-z0-9._~+/=-]+", re.IGNORECASE), lambda m: f"{m.group(1)}***"),
    (
        re.compile(r"([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\.[A-Za-z]{2,})"),
        lambda m: f"{m.group(1)}***@{m.group(2)}",
    ),
    (
        re.compile(r"(?<![\w+])(?:\+\d{1,3}[ -]?\d{2,4}[ -]?\d{3,4}[ -]?\d{3,4}|0\d{9})(?!\w)"),
        lambda m: _mask_digits(m.group(0)),
    ),
]


def mask_pii(text: str | None) -> str | None:
    """Returns ``text`` with emails, phone numbers, passwords and bearer tokens masked."""
    if not text:
        return text
    for pattern, replace in _RULES:
        text = pattern.sub(replace, text)
    return text
