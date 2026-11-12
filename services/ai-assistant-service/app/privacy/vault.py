"""Per-conversation mapping between placeholders and the personal data they stand for."""

import base64
import json
import logging
import re

from cryptography.fernet import Fernet, InvalidToken
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

from app.privacy.detectors import PiiKind

log = logging.getLogger(__name__)

TOKEN = re.compile(r"\[(" + "|".join(k.value for k in PiiKind) + r")_(\d{1,4})\]")


def _normalise(value: str, kind: PiiKind) -> str:
    """Spellings of the same value share one placeholder: +94 77-123 4567 equals +94771234567."""
    if kind in (PiiKind.PHONE, PiiKind.NIC, PiiKind.CARD):
        return "".join(c for c in value if c.isalnum()).upper()
    return " ".join(value.split()).casefold()


class PseudonymVault:
    """Hands out stable placeholders (the same value always gets the same one) and maps them back."""

    def __init__(self, entries: dict[str, list[str]] | None = None) -> None:
        self._values: dict[str, str] = {}             # placeholder -> original value
        self._tokens: dict[tuple[str, str], str] = {}  # (kind, normalised value) -> placeholder
        self._counters: dict[str, int] = {}
        for token, (kind, value) in (entries or {}).items():
            self._remember(token, PiiKind(kind), value)

    def token_for(self, value: str, kind: PiiKind) -> str:
        key = (kind.value, _normalise(value, kind))
        if key not in self._tokens:
            self._counters[kind.value] = self._counters.get(kind.value, 0) + 1
            self._remember(f"[{kind.value}_{self._counters[kind.value]}]", kind, value)
        return self._tokens[key]

    def value_of(self, token: str) -> str | None:
        return self._values.get(token)

    def known_values(self) -> list[tuple[str, str]]:
        """(value, placeholder) pairs, longest value first, for replacing values the user types again."""
        return sorted(((v, t) for t, v in self._values.items()), key=lambda vt: -len(vt[0]))

    def __len__(self) -> int:
        return len(self._values)

    def _remember(self, token: str, kind: PiiKind, value: str) -> None:
        self._values[token] = value
        self._tokens[(kind.value, _normalise(value, kind))] = token
        match = TOKEN.fullmatch(token)
        if match:
            self._counters[kind.value] = max(self._counters.get(kind.value, 0), int(match.group(2)))

    def to_entries(self) -> dict[str, list[str]]:
        kinds = {t: k for (k, _), t in self._tokens.items()}
        return {token: [kinds[token], value] for token, value in self._values.items()}


class VaultCipher:
    """Encrypts a vault for storage (Fernet: AES-128-CBC with HMAC-SHA256), key derived from a secret."""

    def __init__(self, secret: str | None) -> None:
        if not secret:
            log.warning("ASSISTANT_VAULT_SECRET is not set: using a random key, PII vaults will not survive a restart")
            self._fernet = Fernet(Fernet.generate_key())
            return
        key = HKDF(algorithm=hashes.SHA256(), length=32, salt=b"swiftdrop-assistant", info=b"pii-vault-v1").derive(
            secret.encode("utf-8")
        )
        self._fernet = Fernet(base64.urlsafe_b64encode(key))

    def encrypt(self, vault: PseudonymVault) -> bytes:
        return self._fernet.encrypt(json.dumps(vault.to_entries(), ensure_ascii=False).encode("utf-8"))

    def decrypt(self, blob: bytes | None) -> PseudonymVault:
        """An unreadable vault (other key, tampered) yields an empty one: placeholders just stay unresolved."""
        if not blob:
            return PseudonymVault()
        try:
            return PseudonymVault(json.loads(self._fernet.decrypt(blob)))
        except (InvalidToken, ValueError):
            log.warning("Discarding a PII vault that could not be decrypted")
            return PseudonymVault()
