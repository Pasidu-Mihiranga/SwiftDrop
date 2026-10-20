"""LLM provider abstraction (Strategy pattern): the agent depends on this protocol, not on a vendor SDK."""

from collections.abc import AsyncIterator, Sequence
from dataclasses import dataclass, field
from typing import Any, Literal, Protocol


@dataclass(frozen=True)
class ChatMessage:
    role: Literal["system", "user", "assistant", "tool"]
    content: str
    tool_call_id: str | None = None


@dataclass(frozen=True)
class ToolSpec:
    """A function the model may call; implemented by the MCP tools in app/mcp_server."""

    name: str
    description: str
    parameters: dict[str, Any] = field(default_factory=dict)


class LLMProvider(Protocol):
    def stream_chat(self, messages: Sequence[ChatMessage], tools: Sequence[ToolSpec]) -> AsyncIterator[str]:
        """Streams the assistant's reply, invoking tools as needed."""
        ...
