"""Pseudonymizes what goes to the LLM, restores it for the user, and masks what goes to MCP clients."""

import re
from collections.abc import Callable
from typing import Any

from swiftdrop_common import mask_pii

from app.privacy.detectors import PiiKind, find_in_text, kind_for_field
from app.privacy.vault import TOKEN, PseudonymVault

# The longest possible placeholder, e.g. "[LOCATION_9999]"; a pending "[" further back than this is not one.
_MAX_TOKEN = max(len(k.value) for k in PiiKind) + 7
_PARTIAL_TOKEN = re.compile(r"\[[A-Z]*_?\d*$")


class PiiRedactor:
    def __init__(self, on_redaction: Callable[[PiiKind], None] | None = None) -> None:
        self._on_redaction = on_redaction or (lambda _kind: None)

    # ---- reversible: what the model sees ------------------------------------------------------

    def pseudonymize_text(self, text: str, vault: PseudonymVault) -> str:
        """Replaces personal data with placeholders.

        Values the vault already knows (for example a recipient name from an earlier tool result) are
        replaced wherever they appear, which catches names that no pattern could recognise.
        """
        for value, token in vault.known_values():
            if len(value) >= 3:
                text = re.sub(rf"(?<!\w){re.escape(value)}(?!\w)", token, text, flags=re.IGNORECASE)
        out, last = [], 0
        for found in find_in_text(text):
            if TOKEN.fullmatch(text[found.start:found.end]):
                continue
            out.append(text[last:found.start])
            out.append(vault.token_for(found.value, found.kind))
            self._on_redaction(found.kind)
            last = found.end
        out.append(text[last:])
        return "".join(out)

    def pseudonymize_json(self, value: Any, vault: PseudonymVault, path: tuple[str, ...] = ()) -> Any:
        """Structured data: personal fields by name become placeholders; other strings are scanned as text."""
        if isinstance(value, dict):
            return {k: self._pseudonymize_field(v, vault, (*path, str(k))) for k, v in value.items()}
        if isinstance(value, list):
            return [self.pseudonymize_json(v, vault, path) for v in value]
        if isinstance(value, str):
            return self.pseudonymize_text(value, vault)
        return value

    def _pseudonymize_field(self, value: Any, vault: PseudonymVault, path: tuple[str, ...]) -> Any:
        kind = kind_for_field(path)
        if kind and value is not None and isinstance(value, str | int | float) and not isinstance(value, bool):
            self._on_redaction(kind)
            return vault.token_for(str(value), kind)
        return self.pseudonymize_json(value, vault, path)

    def restore(self, text: str, vault: PseudonymVault) -> str:
        """Puts the real values back. Unknown placeholders are left as they are."""
        return TOKEN.sub(lambda m: vault.value_of(m.group(0)) or m.group(0), text)

    def restore_json(self, value: Any, vault: PseudonymVault) -> Any:
        """Tool arguments the model wrote with placeholders get the real values before the tool runs."""
        if isinstance(value, dict):
            return {k: self.restore_json(v, vault) for k, v in value.items()}
        if isinstance(value, list):
            return [self.restore_json(v, vault) for v in value]
        if isinstance(value, str):
            return self.restore(value, vault)
        return value

    # ---- irreversible: MCP clients and anything else outside the user's own answer ------------

    def mask_json(self, value: Any, path: tuple[str, ...] = ()) -> Any:
        if isinstance(value, dict):
            return {k: self._mask_field(v, (*path, str(k))) for k, v in value.items()}
        if isinstance(value, list):
            return [self.mask_json(v, path) for v in value]
        if isinstance(value, str):
            return self.mask_text(value)
        return value

    def _mask_field(self, value: Any, path: tuple[str, ...]) -> Any:
        kind = kind_for_field(path)
        if kind is None or value is None or isinstance(value, bool):
            return self.mask_json(value, path)
        if kind is PiiKind.LOCATION:
            return None
        if not isinstance(value, str | int | float):
            return self.mask_json(value, path)
        self._on_redaction(kind)
        return _mask_value(str(value), kind)

    def mask_text(self, text: str) -> str:
        out, last = [], 0
        for found in find_in_text(text):
            out.append(text[last:found.start])
            out.append(_mask_value(found.value, found.kind))
            last = found.end
        out.append(text[last:])
        return "".join(out)


def _mask_value(value: str, kind: PiiKind) -> str:
    if kind is PiiKind.NAME:
        return mask_name(value)
    if kind in (PiiKind.PHONE, PiiKind.EMAIL):
        return mask_pii(value) or ""
    if kind in (PiiKind.CARD, PiiKind.NIC):
        visible = 4 if kind is PiiKind.CARD else 1
        digits = [c for c in value if c.isalnum()]
        return "*" * (len(digits) - visible) + "".join(digits[-visible:])
    return "***"


def mask_name(name: str) -> str:
    """'Kamal Perera' -> 'K*** P.', the style of the public tracking page. Already masked names stay as they are."""
    parts = name.split()
    if not parts or "*" in name:
        return name
    first = f"{parts[0][0]}***"
    return f"{first} {parts[-1][0]}." if len(parts) > 1 else first


class StreamingRestorer:
    """Restores placeholders in a token stream although one can arrive split across chunks ('[PHO', 'NE_1]')."""

    def __init__(self, restore: Callable[[str], str]) -> None:
        self._restore = restore
        self._pending = ""

    def feed(self, chunk: str) -> str:
        text = self._pending + chunk
        cut = text.rfind("[")
        if cut != -1 and len(text) - cut <= _MAX_TOKEN and _PARTIAL_TOKEN.fullmatch(text[cut:]):
            self._pending = text[cut:]
            text = text[:cut]
        else:
            self._pending = ""
        return self._restore(text)

    def flush(self) -> str:
        text, self._pending = self._pending, ""
        return self._restore(text)
