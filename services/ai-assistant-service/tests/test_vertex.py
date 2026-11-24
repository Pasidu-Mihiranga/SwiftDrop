import json
from urllib.parse import parse_qs

import httpx
import jwt
import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

from app.core.config import Settings
from app.llm.factory import create_provider, provider_config
from app.llm.openai_compatible import OpenAICompatibleProvider
from app.llm.vertex import (BearerAuth, ServiceAccountTokens, VertexCredentialsError, load_service_account,
                            vertex_base_url, vertex_model)

_KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)
PEM = _KEY.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                         serialization.NoEncryption()).decode()
ACCOUNT = {"type": "service_account", "project_id": "demo-project", "private_key_id": "kid-1",
           "private_key": PEM, "client_email": "bot@demo-project.iam.gserviceaccount.com",
           "token_uri": "https://oauth2.example/token"}


class Clock:
    def __init__(self, now: float = 1_800_000_000) -> None:
        self.now = now

    def __call__(self) -> float:
        return self.now


def token_server(log: list[httpx.Request], status: int = 200):
    def handle(request: httpx.Request) -> httpx.Response:
        log.append(request)
        if status != 200:
            return httpx.Response(status, json={"error": "invalid_grant", "error_description": "no"})
        return httpx.Response(200, json={"access_token": f"token-{len(log)}", "expires_in": 3600})
    return httpx.MockTransport(handle)


async def test_the_token_is_a_signed_jwt_bearer_grant_and_is_reused_until_near_expiry():
    calls: list[httpx.Request] = []
    clock = Clock()
    async with httpx.AsyncClient(transport=token_server(calls)) as http:
        tokens = ServiceAccountTokens(ACCOUNT, http, clock)
        assert await tokens.token() == "token-1"
        assert await tokens.token() == "token-1"
        clock.now += 3600 - 301          # still more than five minutes left
        assert await tokens.token() == "token-1"
        clock.now += 2                   # inside the refresh window
        assert await tokens.token() == "token-2"

    form = parse_qs(calls[0].content.decode())
    assert form["grant_type"] == ["urn:ietf:params:oauth:grant-type:jwt-bearer"]
    claims = jwt.decode(form["assertion"][0], _KEY.public_key(), algorithms=["RS256"],
                        audience="https://oauth2.example/token",
                        options={"verify_iat": False, "verify_exp": False})  # the test clock is fixed
    assert claims["iss"] == ACCOUNT["client_email"]
    assert claims["scope"] == "https://www.googleapis.com/auth/cloud-platform"
    assert claims["exp"] - claims["iat"] == 3600
    assert jwt.get_unverified_header(form["assertion"][0])["kid"] == "kid-1"


async def test_a_refused_exchange_names_googles_error_but_never_the_key():
    async with httpx.AsyncClient(transport=token_server([], status=400)) as http:
        with pytest.raises(httpx.HTTPStatusError) as failure:
            await ServiceAccountTokens(ACCOUNT, http).token()
    assert "invalid_grant" in str(failure.value)
    assert "PRIVATE KEY" not in str(failure.value)


async def test_every_llm_request_carries_the_current_token():
    seen: list[str] = []

    def vertex(request: httpx.Request) -> httpx.Response:
        if request.url.host == "oauth2.example":
            return httpx.Response(200, json={"access_token": "fresh", "expires_in": 3600})
        seen.append(request.headers["authorization"])
        return httpx.Response(200, json={})

    transport = httpx.MockTransport(vertex)
    tokens = ServiceAccountTokens(ACCOUNT, httpx.AsyncClient(transport=transport))
    async with httpx.AsyncClient(transport=transport, auth=BearerAuth(tokens)) as client:
        await client.post("https://aiplatform.example/chat", headers={"Authorization": "Bearer placeholder"})
    assert seen == ["Bearer fresh"]


def test_the_key_loads_from_a_file_or_inline_json(tmp_path):
    key_file = tmp_path / "sa.json"
    key_file.write_text(json.dumps(ACCOUNT), encoding="utf-8")
    assert load_service_account(key_file, None)["client_email"] == ACCOUNT["client_email"]
    assert load_service_account(None, json.dumps(ACCOUNT))["project_id"] == "demo-project"
    assert load_service_account(None, None) is None


@pytest.mark.parametrize("content, message", [
    (None, "not found"),
    ("{not json", "not valid JSON"),
    (json.dumps({"type": "authorized_user"}), "not a service account key"),
    (json.dumps({**ACCOUNT, "private_key": ""}), "missing private_key"),
])
def test_unusable_keys_are_explained_without_their_contents(tmp_path, content, message):
    key_file = tmp_path / "sa.json"
    if content is not None:
        key_file.write_text(content, encoding="utf-8")
    with pytest.raises(VertexCredentialsError, match=message) as failure:
        load_service_account(key_file, None)
    assert "BEGIN" not in str(failure.value)


def test_endpoint_and_model_names():
    assert vertex_base_url("p", "global") == "https://aiplatform.googleapis.com/v1/projects/p/locations/global/endpoints/openapi"
    assert vertex_base_url("p", "us-central1").startswith("https://us-central1-aiplatform.googleapis.com/")
    assert vertex_model("gemini-2.5-flash") == "google/gemini-2.5-flash"
    assert vertex_model("google/gemini-2.5-pro") == "google/gemini-2.5-pro"


def test_the_factory_builds_a_vertex_provider_from_settings():
    settings = Settings(_env_file=None, llm_provider="vertex", vertex_credentials_json=json.dumps(ACCOUNT),
                        vertex_location="europe-west1")
    config = provider_config(settings)
    assert config.configured and config.model == "google/gemini-3.8-flash"
    assert "/projects/demo-project/locations/europe-west1/" in config.base_url
    assert "PRIVATE" not in repr(config)
    assert isinstance(create_provider(config), OpenAICompatibleProvider)

    explicit = provider_config(Settings(_env_file=None, llm_provider="vertex", vertex_project="other",
                                        vertex_credentials_json=json.dumps(ACCOUNT)))
    assert "/projects/other/" in explicit.base_url


def test_a_missing_key_leaves_vertex_unconfigured_instead_of_failing(tmp_path):
    settings = Settings(_env_file=None, llm_provider="vertex", vertex_credentials_file=tmp_path / "absent.json")
    config = provider_config(settings)
    assert not config.configured
    assert create_provider(config) is None


def test_a_relative_key_path_is_taken_from_the_repository_root():
    settings = Settings(_env_file=None, vertex_credentials_file="secrets/key.json")
    assert settings.vertex_credentials_path is not None
    assert settings.vertex_credentials_path.parts[-2:] == ("secrets", "key.json")
    assert settings.vertex_credentials_path.is_absolute()
