"""LLM provider abstraction (Strategy pattern): the agent depends on this protocol, not on a vendor SDK."""

from collections.abc import AsyncIterator, Sequence
from dataclasses import dataclass, field
from typing import Any, Literal, Protocol


@dataclass(frozen=True)
class ToolSpec:
    """A function the model may call; implemented by the tools in app/tools."""

    name: str
    description: str
    parameters: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class ToolCall:
    id: str
    name: str
    arguments: str            # raw JSON text exactly as the model wrote it


@dataclass(frozen=True)
class ChatMessage:
    role: Literal["system", "user", "assistant", "tool"]
    content: str | None = None
    tool_call_id: str | None = None
    tool_calls: tuple[ToolCall, ...] = ()


@dataclass(frozen=True)
class TextDelta:
    """A piece of the answer, streamed as the model produces it."""

    text: str


StreamEvent = TextDelta | ToolCall


class LLMUnavailable(Exception):
    """The provider could not be reached or refused the request (bad key, quota, outage)."""


class LLMProvider(Protocol):
    def stream_chat(self, messages: Sequence[ChatMessage], tools: Sequence[ToolSpec]) -> AsyncIterator[StreamEvent]:
        """Streams text deltas as they arrive; complete tool calls are yielded once the reply has finished."""
        ...
