"""Finds personal data, in free text (patterns) and in API responses (by field name)."""

import re
from collections.abc import Sequence
from dataclasses import dataclass
from enum import StrEnum


class PiiKind(StrEnum):
    EMAIL = "EMAIL"
    PHONE = "PHONE"
    NIC = "NIC"            # Sri Lankan national identity card number
    CARD = "CARD"          # payment card number
    NAME = "NAME"
    ADDRESS = "ADDRESS"
    LOCATION = "LOCATION"  # coordinates


@dataclass(frozen=True)
class Found:
    start: int
    end: int
    kind: PiiKind
    value: str


_EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
# 13-19 digits, optionally grouped by spaces or dashes; confirmed with the Luhn check below.
_CARD = re.compile(r"(?<![\w-])\d(?:[ -]?\d){12,18}(?![\w-])")
# New NIC: 12 digits starting with the birth year; old NIC: 9 digits and V or X.
_NIC = re.compile(r"(?<!\w)(?:(?:19|20)\d{10}|\d{9}[VvXx])(?!\w)")
# International (+94 77 123 4567) or local (077 123 4567, 0771234567) numbers.
_PHONE = re.compile(r"(?<![\w+])(?:\+\d{1,3}[ -]?\d{2,4}[ -]?\d{3,4}[ -]?\d{3,4}|0\d{2}[ -]?\d{3}[ -]?\d{4})(?!\w)")


def luhn_valid(number: str) -> bool:
    digits = [int(c) for c in number if c.isdigit()]
    if not 13 <= len(digits) <= 19:
        return False
    total = 0
    for i, d in enumerate(reversed(digits)):
        if i % 2:
            d = d * 2 - 9 if d > 4 else d * 2
        total += d
    return total % 10 == 0


def find_in_text(text: str) -> list[Found]:
    """Non-overlapping matches, most specific first: emails, cards, NIC numbers, then phone numbers.

    Tracking numbers (SD + letters and digits) never match: every pattern needs a word boundary
    that letters inside a tracking number do not provide.
    """
    found: list[Found] = []
    taken: list[tuple[int, int]] = []

    def add(match: re.Match[str], kind: PiiKind) -> None:
        start, end = match.span()
        if any(start < t_end and t_start < end for t_start, t_end in taken):
            return
        taken.append((start, end))
        found.append(Found(start, end, kind, match.group(0)))

    for m in _EMAIL.finditer(text):
        add(m, PiiKind.EMAIL)
    for m in _CARD.finditer(text):
        if luhn_valid(m.group(0)):
            add(m, PiiKind.CARD)
    for m in _NIC.finditer(text):
        add(m, PiiKind.NIC)
    for m in _PHONE.finditer(text):
        add(m, PiiKind.PHONE)
    return sorted(found, key=lambda f: f.start)


# Field names (compared without case, dashes or underscores) whose values are personal data.
_FIELDS: dict[str, PiiKind] = {
    "email": PiiKind.EMAIL, "senderemail": PiiKind.EMAIL, "recipientemail": PiiKind.EMAIL,
    "phone": PiiKind.PHONE, "mobile": PiiKind.PHONE, "senderphone": PiiKind.PHONE, "recipientphone": PiiKind.PHONE,
    "fullname": PiiKind.NAME, "sendername": PiiKind.NAME, "recipientname": PiiKind.NAME,
    "receivedby": PiiKind.NAME, "customername": PiiKind.NAME,
    "line1": PiiKind.ADDRESS, "line2": PiiKind.ADDRESS, "street": PiiKind.ADDRESS, "address": PiiKind.ADDRESS,
    "postalcode": PiiKind.ADDRESS, "postcode": PiiKind.ADDRESS,
    "lat": PiiKind.LOCATION, "lng": PiiKind.LOCATION, "latitude": PiiKind.LOCATION, "longitude": PiiKind.LOCATION,
    "nic": PiiKind.NIC,
}
# A plain "name" is personal only inside a person-like object: a zone's name is not.
_PERSON_PARENTS = {"recipient", "sender", "contact", "contacts", "customer", "receiver", "user"}


def _norm(key: str) -> str:
    return key.replace("_", "").replace("-", "").lower()


def kind_for_field(path: Sequence[str]) -> PiiKind | None:
    """The kind of personal data stored under ``path`` (keys from the root), or None."""
    if not path:
        return None
    key = _norm(path[-1])
    if key in _FIELDS:
        return _FIELDS[key]
    parents = {_norm(p) for p in path[:-1]}
    if key == "name" and parents & _PERSON_PARENTS:
        return PiiKind.NAME
    if key == "recipient":       # public tracking returns the (already masked) recipient as a string
        return PiiKind.NAME
    return None
