"""One client for OpenAI and Gemini: both speak the OpenAI chat-completions API (Gemini through its
OpenAI-compatible endpoint), so switching provider is configuration, not code."""

import logging
from collections.abc import AsyncIterator, Sequence
from typing import Any

import httpx
import openai
from openai import AsyncOpenAI

from app.llm.base import ChatMessage, LLMUnavailable, StreamEvent, TextDelta, ToolCall, ToolSpec

log = logging.getLogger(__name__)


class OpenAICompatibleProvider:
    def __init__(self, base_url: str, model: str, api_key: str, http_client: httpx.AsyncClient | None = None,
                 timeout: float = 60.0) -> None:
        self.model = model
        self._client = AsyncOpenAI(base_url=base_url, api_key=api_key, timeout=timeout, max_retries=1,
                                   http_client=http_client)

    async def stream_chat(self, messages: Sequence[ChatMessage], tools: Sequence[ToolSpec]) -> AsyncIterator[StreamEvent]:
        request: dict[str, Any] = {"model": self.model, "messages": [_to_openai(m) for m in messages], "stream": True,
                                   "temperature": 0.2}
        if tools:
            request["tools"] = [{"type": "function", "function": {"name": t.name, "description": t.description,
                                                                  "parameters": t.parameters}} for t in tools]
        # Tool calls arrive in fragments keyed by index: the id and name first, the JSON arguments in pieces.
        partial: dict[int, dict[str, str]] = {}
        try:
            stream = await self._client.chat.completions.create(**request)
            async for chunk in stream:
                if not chunk.choices:
                    continue
                delta = chunk.choices[0].delta
                if delta.content:
                    yield TextDelta(delta.content)
                for call in delta.tool_calls or []:
                    entry = partial.setdefault(call.index, {"id": "", "name": "", "arguments": ""})
                    if call.id:
                        entry["id"] = call.id
                    if call.function and call.function.name:
                        entry["name"] += call.function.name
                    if call.function and call.function.arguments:
                        entry["arguments"] += call.function.arguments
        except openai.OpenAIError as exc:
            log.warning("LLM request failed: %s", type(exc).__name__)
            raise LLMUnavailable(type(exc).__name__) from exc
        for index in sorted(partial):
            entry = partial[index]
            yield ToolCall(entry["id"] or f"call_{index}", entry["name"], entry["arguments"] or "{}")


def _to_openai(message: ChatMessage) -> dict[str, Any]:
    out: dict[str, Any] = {"role": message.role, "content": message.content}
    if message.tool_call_id:
        out["tool_call_id"] = message.tool_call_id
    if message.tool_calls:
        out["tool_calls"] = [{"id": c.id, "type": "function", "function": {"name": c.name, "arguments": c.arguments}}
                             for c in message.tool_calls]
    return out
