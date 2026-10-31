import json

import httpx
import pytest

from app.core.config import Settings
from app.llm.base import ChatMessage, LLMUnavailable, TextDelta, ToolCall, ToolSpec
from app.llm.factory import create_provider, provider_config
from app.llm.openai_compatible import OpenAICompatibleProvider

URL = "http://llm.test/v1/chat/completions"


def sse(*chunks: dict) -> bytes:
    body = "".join(f"data: {json.dumps(c)}\n\n" for c in chunks) + "data: [DONE]\n\n"
    return body.encode()


def chunk(delta: dict) -> dict:
    return {"id": "c1", "object": "chat.completion.chunk", "created": 1, "model": "m",
            "choices": [{"index": 0, "delta": delta, "finish_reason": None}]}


async def collect(provider, messages, tools=()):
    return [e async for e in provider.stream_chat(messages, tools)]


def provider_with(responder, model="m", key="k"):
    """The OpenAI SDK has its own transport, so the test injects an httpx client with a mock transport."""
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return responder(request)

    http = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    return OpenAICompatibleProvider("http://llm.test/v1", model, key, http_client=http), calls


def streaming(body: bytes):
    return lambda _request: httpx.Response(200, headers={"content-type": "text/event-stream"}, content=body)


async def test_streams_text_and_assembles_fragmented_tool_calls():
    provider, calls = provider_with(streaming(sse(
        chunk({"role": "assistant", "content": "Let me "}),
        chunk({"content": "check."}),
        chunk({"tool_calls": [{"index": 0, "id": "call_1", "type": "function",
                               "function": {"name": "track_parcel", "arguments": ""}}]}),
        chunk({"tool_calls": [{"index": 0, "function": {"arguments": '{"tracking_number": '}}]}),
        chunk({"tool_calls": [{"index": 0, "function": {"arguments": '"SD261020K7Q2M9X"}'}}]}),
        {"id": "c1", "object": "chat.completion.chunk", "created": 1, "model": "m", "choices": []},
    )), model="test-model", key="secret-key")
    history = [ChatMessage("system", "rules"), ChatMessage("user", "Where is it?"),
               ChatMessage("assistant", None, tool_calls=(ToolCall("call_0", "list_my_parcels", "{}"),)),
               ChatMessage("tool", '{"ok": true}', tool_call_id="call_0")]
    events = await collect(provider, history, [ToolSpec("track_parcel", "Track", {"type": "object"})])

    assert events == [TextDelta("Let me "), TextDelta("check."),
                      ToolCall("call_1", "track_parcel", '{"tracking_number": "SD261020K7Q2M9X"}')]
    assert str(calls[-1].url) == URL
    sent = json.loads(calls[-1].read())
    assert sent["model"] == "test-model" and sent["stream"] is True
    assert sent["tools"][0]["function"]["name"] == "track_parcel"
    assert sent["messages"][2]["tool_calls"][0]["function"]["name"] == "list_my_parcels"
    assert sent["messages"][3] == {"role": "tool", "content": '{"ok": true}', "tool_call_id": "call_0"}
    assert calls[-1].headers["authorization"] == "Bearer secret-key"


async def test_without_tools_no_tools_field_is_sent():
    provider, calls = provider_with(streaming(sse(chunk({"content": "Hello"}))))
    assert await collect(provider, [ChatMessage("user", "hi")]) == [TextDelta("Hello")]
    assert "tools" not in json.loads(calls[-1].read())


async def test_provider_failures_become_llm_unavailable():
    provider, _ = provider_with(lambda _r: httpx.Response(401, json={"error": {"message": "bad key"}}))
    with pytest.raises(LLMUnavailable):
        await collect(provider, [ChatMessage("user", "hi")])


def test_factory_creates_a_provider_only_with_a_key():
    assert create_provider(provider_config(Settings(_env_file=None, llm_provider="gemini"))) is None
    provider = create_provider(provider_config(Settings(_env_file=None, llm_provider="openai", openai_api_key="k",
                                                        llm_model="gpt-test")))
    assert isinstance(provider, OpenAICompatibleProvider) and provider.model == "gpt-test"
