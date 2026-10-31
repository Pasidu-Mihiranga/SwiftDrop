import json

import httpx
import pytest
from fakeredis import FakeAsyncRedis
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.llm.factory import provider_config
from app.main import create_app
from tests.support import ScriptedProvider, call, text, token, verifier


def gateway(request: httpx.Request) -> httpx.Response:
    if request.url.path == "/api/v1/tracking/public/SD261020K7Q2M9X":
        return httpx.Response(200, json={"trackingNumber": "SD261020K7Q2M9X", "currentStatus": "IN_TRANSIT",
                                         "recipient": "K*** P.", "events": []})
    return httpx.Response(404)


def make_client(provider=None, **settings) -> TestClient:
    config = Settings(_env_file=None, llm_provider="gemini", gemini_api_key="test-key", **settings)
    app = create_app(config, verifier(), provider=provider, redis=FakeAsyncRedis(),
                     gateway_transport=httpx.MockTransport(gateway))
    return TestClient(app)


def events_of(response) -> list[tuple[str, dict]]:
    out = []
    for block in response.text.strip().split("\n\n"):
        name, data = block.split("\n", 1)
        out.append((name.removeprefix("event: "), json.loads(data.removeprefix("data: "))))
    return out


@pytest.fixture
def client():
    with make_client(ScriptedProvider(
        [call("track_parcel", {"tracking_number": "SD261020K7Q2M9X"})],
        text("It is in transit to ", "[NAME_1]."),
    )) as c:
        yield c


def test_whoami_requires_and_reads_the_token(client):
    assert client.get("/api/v1/assistant/meta/whoami").status_code == 401
    res = client.get("/api/v1/assistant/meta/whoami", headers={"Authorization": f"Bearer {token(['CUSTOMER'])}"})
    assert res.json() == {"service": "ai-assistant-service", "userId": "user-1", "roles": ["CUSTOMER"], "merchantId": None}


def test_provider_endpoint_never_leaks_the_key(client):
    res = client.get("/api/v1/assistant/meta/provider").json()
    assert res == {"provider": "gemini", "model": "gemini-2.5-flash", "configured": True}


def test_chat_streams_server_sent_events(client):
    res = client.post("/api/v1/assistant/chat", json={"message": "Where is SD261020K7Q2M9X?"})
    assert res.status_code == 200
    assert res.headers["content-type"].startswith("text/event-stream")
    assert res.headers["cache-control"] == "no-cache"
    events = events_of(res)
    assert [name for name, _ in events] == ["tool", "token", "token", "done"]
    assert events[0][1] == {"name": "track_parcel", "label": "Checked tracking SD261020K7Q2M9X", "ok": True}
    # the public view's masked recipient is restored for the user exactly as tracking returned it
    assert "".join(d["text"] for n, d in events if n == "token") == "It is in transit to K*** P.."
    conversation_id = events[-1][1]["conversationId"]
    assert len(conversation_id) == 32

    metrics = client.get("/metrics").text
    assert 'assistant_turns_total{outcome="answered"} 1.0' in metrics
    assert 'assistant_tool_calls_total{result="ok",tool="track_parcel"} 1.0' in metrics


def test_chat_accepts_the_conversation_id_in_camel_case():
    with make_client(ScriptedProvider(text("One."), text("Two."))) as c:
        first = events_of(c.post("/api/v1/assistant/chat", json={"message": "hi"}))[-1][1]["conversationId"]
        again = c.post("/api/v1/assistant/chat", json={"message": "hi again", "conversationId": first})
        assert events_of(again)[-1][1]["conversationId"] == first


def test_chat_validates_input_and_tokens(client):
    assert client.post("/api/v1/assistant/chat", json={"message": ""}).json()["code"] == "VALIDATION_FAILED"
    assert client.post("/api/v1/assistant/chat", json={"message": "   "}).status_code == 400
    assert client.post("/api/v1/assistant/chat", json={"message": "x" * 2001}).status_code == 400
    res = client.post("/api/v1/assistant/chat", json={"message": "hi"}, headers={"Authorization": "Bearer bad"})
    assert res.status_code == 401


def test_chat_without_a_provider_is_503():
    with make_client(provider=None) as c:
        res = c.post("/api/v1/assistant/chat", json={"message": "hi"})
    assert res.status_code == 503 and res.json()["code"] == "ASSISTANT_NOT_CONFIGURED"


def test_chat_is_rate_limited_per_caller():
    with make_client(ScriptedProvider(text("a"), text("b")), assistant_rate_limit_per_min=1) as c:
        assert c.post("/api/v1/assistant/chat", json={"message": "hi"}).status_code == 200
        limited = c.post("/api/v1/assistant/chat", json={"message": "hi"})
        assert limited.status_code == 429 and limited.json()["code"] == "RATE_LIMITED"
        other = c.post("/api/v1/assistant/chat", json={"message": "hi"}, headers={"X-Forwarded-For": "203.0.113.9"})
        assert other.status_code == 200


def test_provider_factory_switches_between_openai_and_gemini():
    openai = provider_config(Settings(_env_file=None, llm_provider="openai", llm_model="gpt-4.1-mini", openai_api_key="k"))
    gemini = provider_config(Settings(_env_file=None, llm_provider="gemini"))
    assert openai.base_url == "https://api.openai.com/v1" and openai.configured
    assert gemini.base_url.startswith("https://generativelanguage.googleapis.com") and not gemini.configured


def test_settings_derive_keycloak_and_redis_urls():
    settings = Settings(_env_file=None, keycloak_host_port=9999, redis_password="pw")
    assert settings.issuer == "http://localhost:9999/realms/swiftdrop"
    assert settings.jwks_uri.endswith("/realms/swiftdrop/protocol/openid-connect/certs")
    assert settings.redis_dsn == "redis://:pw@localhost:6380/0"
    assert Settings(_env_file=None, redis_url="redis://redis:6379/0").redis_dsn == "redis://redis:6379/0"
