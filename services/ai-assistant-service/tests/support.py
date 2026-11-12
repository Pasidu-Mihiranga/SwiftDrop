"""Test doubles shared by the agent, API and MCP tests."""

import json
import time
from collections.abc import AsyncIterator, Sequence
from types import SimpleNamespace

import jwt
from cryptography.hazmat.primitives.asymmetric import rsa

from app.llm.base import ChatMessage, LLMUnavailable, StreamEvent, TextDelta, ToolCall, ToolSpec
from swiftdrop_common import JwtVerifier

ISSUER = "http://localhost:8180/realms/swiftdrop"
_KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)


class _Resolver:
    def get_signing_key_from_jwt(self, _token):
        return SimpleNamespace(key=_KEY.public_key())


def verifier() -> JwtVerifier:
    return JwtVerifier(ISSUER, key_resolver=_Resolver())


def token(roles: list[str], sub: str = "user-1") -> str:
    claims = {"sub": sub, "iss": ISSUER, "aud": "swiftdrop-api", "exp": int(time.time()) + 60,
              "realm_access": {"roles": roles}}
    return jwt.encode(claims, _KEY, algorithm="RS256")


def call(name: str, args: dict | str, call_id: str = "call_1") -> ToolCall:
    return ToolCall(call_id, name, args if isinstance(args, str) else json.dumps(args))


class ScriptedProvider:
    """Plays back scripted replies, one per model call, and records exactly what the model was sent."""

    def __init__(self, *replies: Sequence[StreamEvent] | Exception) -> None:
        self.replies = list(replies)
        self.requests: list[tuple[list[ChatMessage], list[ToolSpec]]] = []

    async def stream_chat(self, messages: Sequence[ChatMessage], tools: Sequence[ToolSpec]) -> AsyncIterator[StreamEvent]:
        self.requests.append((list(messages), list(tools)))
        reply = self.replies.pop(0) if self.replies else [TextDelta("(script finished)")]
        if isinstance(reply, Exception):
            raise reply
        for event in reply:
            yield event

    def everything_sent(self) -> str:
        """All text the model received, including tool results, as one string for leak checks."""
        return "\n".join(f"{m.content} {[(c.name, c.arguments) for c in m.tool_calls]}"
                         for messages, _ in self.requests for m in messages)


def text(*parts: str) -> list[TextDelta]:
    return [TextDelta(p) for p in parts]


UNAVAILABLE = LLMUnavailable("down")
