"""Personal-data protection for the assistant.

- The LLM is a third party, so everything sent to it is pseudonymized: personal data becomes
  placeholders such as ``[PHONE_1]`` and only the assistant can map them back (``PseudonymVault``).
- The answer streamed to the signed-in user gets the real values back (``PiiRedactor.restore``).
- MCP clients and logs get irreversible masking (``PiiRedactor.mask_json``).
"""

from app.privacy.detectors import PiiKind
from app.privacy.redactor import PiiRedactor, StreamingRestorer
from app.privacy.vault import PseudonymVault, VaultCipher

__all__ = ["PiiKind", "PiiRedactor", "PseudonymVault", "StreamingRestorer", "VaultCipher"]
