"""One chat turn: pseudonymize, let the model call tools, stream the answer back restored, remember it.

The model only ever sees placeholders: the user's message and every tool result are pseudonymized
before they are sent, and tool arguments are restored before a tool runs. Real values reach nobody
but the signed-in user, in the streamed answer.
"""

import json
import logging
import time
from collections.abc import AsyncIterator, Callable
from dataclasses import dataclass, field
from typing import Any

from app.agent.guardrails import RateLimited, RateLimiter, ScopeGate
from app.agent.prompts import OFF_TOPIC_ANSWER, ROUND_LIMIT_ANSWER, UNAVAILABLE_ANSWER, system_prompt
from app.clients.swiftdrop_api import SwiftDropApi
from app.core.metrics import AssistantMetrics
from app.llm.base import ChatMessage, LLMProvider, LLMUnavailable, TextDelta, ToolCall
from app.memory.conversation_store import Conversation, ConversationStore
from app.privacy import PiiRedactor, StreamingRestorer
from app.tools import ToolRegistry, ToolResult
from swiftdrop_common import AuthenticatedUser

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Event:
    """One server-sent event: ``token`` (answer text), ``tool`` (a tool chip), ``error`` or ``done``."""

    name: str
    data: dict[str, Any] = field(default_factory=dict)


class AgentOrchestrator:
    def __init__(self, provider: LLMProvider, registry: ToolRegistry, redactor: PiiRedactor,
                 store: ConversationStore, limiter: RateLimiter,
                 api_for: Callable[[AuthenticatedUser | None], SwiftDropApi], *, max_tool_rounds: int = 5,
                 metrics: AssistantMetrics | None = None) -> None:
        self._provider = provider
        self._registry = registry
        self._redactor = redactor
        self._store = store
        self._limiter = limiter
        self._api_for = api_for
        self._max_rounds = max_tool_rounds
        self._metrics = metrics or AssistantMetrics()

    async def begin(self, user: AuthenticatedUser | None, conversation_id: str | None,
                    client_address: str) -> Conversation:
        """Checks the rate limit and loads the caller's conversation, before any response is started."""
        subject = f"user:{user.id}" if user else f"guest:{client_address}"
        if not await self._limiter.allow(subject):
            self._metrics.turn("rate_limited")
            raise RateLimited()
        return await self._store.load(conversation_id, user.id if user else "guest")

    async def run(self, conversation: Conversation, message: str,
                  user: AuthenticatedUser | None) -> AsyncIterator[Event]:
        started = time.perf_counter()
        vault = conversation.vault
        user_text = self._redactor.pseudonymize_text(message, vault)
        messages = [ChatMessage("system", system_prompt(user)), *conversation.history, ChatMessage("user", user_text)]
        specs = self._registry.specs_for(user)
        api = self._api_for(user)
        tools_used: list[str] = []
        answer: str | None = None
        outcome = "answered"
        streamed = False                     # text already sent this turn, so a later part starts a paragraph

        try:
            for round_no in range(self._max_rounds + 1):
                # The last round offers no tools, which makes the model answer with what it has.
                offered = specs if round_no < self._max_rounds else []
                gate = ScopeGate()
                restorer = StreamingRestorer(lambda text: self._redactor.restore(text, vault))
                parts: list[str] = []
                calls: list[ToolCall] = []
                round_streamed = False

                async for event in self._provider.stream_chat(messages, offered):
                    if isinstance(event, ToolCall):
                        calls.append(event)
                        continue
                    parts.append(event.text)
                    out = restorer.feed(gate.feed(event.text))
                    if out:
                        if streamed and not round_streamed:
                            out = "\n\n" + out.lstrip()
                        round_streamed = streamed = True
                        yield Event("token", {"text": out})

                if gate.off_topic:
                    answer, outcome = OFF_TOPIC_ANSWER, "off_topic"
                    yield Event("token", {"text": ("\n\n" if streamed else "") + answer})
                    break
                tail = restorer.feed(gate.finish()) + restorer.flush()
                if tail:
                    if streamed and not round_streamed:
                        tail = "\n\n" + tail.lstrip()
                    streamed = True
                    yield Event("token", {"text": tail})

                if not calls:
                    answer = "".join(parts)
                    break
                messages.append(ChatMessage("assistant", "".join(parts) or None, tool_calls=tuple(calls)))
                for call in calls:
                    result = await self._run_tool(call, user, api, vault, allowed=round_no < self._max_rounds)
                    tools_used.append(call.name)
                    self._metrics.tool_call(call.name, result.ok)
                    yield Event("tool", {"name": call.name, "label": result.chip, "ok": result.ok})
                    content = self._redactor.pseudonymize_json(result.for_model(), vault)
                    messages.append(ChatMessage("tool", json.dumps(content, ensure_ascii=False), tool_call_id=call.id))
            else:
                answer, outcome = ROUND_LIMIT_ANSWER, "round_limit"
                yield Event("token", {"text": ("\n\n" if streamed else "") + answer})
        except LLMUnavailable:
            outcome = "unavailable"
            yield Event("error", {"code": "ASSISTANT_UNAVAILABLE", "message": UNAVAILABLE_ANSWER})

        if answer is not None:
            # Memory keeps placeholders only; the answer is scanned again in case the model wrote new data.
            await self._store.save(conversation, [
                ChatMessage("user", user_text),
                ChatMessage("assistant", self._redactor.pseudonymize_text(answer, vault)),
            ])
        self._metrics.turn(outcome)
        log.info("assistant turn conversation=%s outcome=%s tools=%s ms=%d", conversation.id, outcome,
                 ",".join(tools_used) or "-", (time.perf_counter() - started) * 1000)
        yield Event("done", {"conversationId": conversation.id})

    async def _run_tool(self, call: ToolCall, user: AuthenticatedUser | None, api: SwiftDropApi, vault: Any,
                        allowed: bool) -> ToolResult:
        if not allowed:
            return ToolResult(call.name, False, "Tool limit reached", "No more tools can be used for this message.",
                              "TOOL_LIMIT")
        try:
            args = json.loads(call.arguments or "{}")
            if not isinstance(args, dict):
                raise ValueError("arguments must be an object")
        except ValueError:
            return ToolResult(call.name, False, f"Could not use {call.name.replace('_', ' ')}",
                              "The arguments were not valid JSON.", "INVALID_ARGUMENTS")
        return await self._registry.call(call.name, self._redactor.restore_json(args, vault), user, api)


__all__ = ["AgentOrchestrator", "Event", "RateLimited"]
