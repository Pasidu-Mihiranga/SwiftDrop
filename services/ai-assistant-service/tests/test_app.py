import time
from types import SimpleNamespace

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.llm.factory import provider_config
from app.main import create_app
from swiftdrop_common import JwtVerifier

ISSUER = "http://localhost:8180/realms/swiftdrop"
_KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)


class _Resolver:
    def get_signing_key_from_jwt(self, _token):
        return SimpleNamespace(key=_KEY.public_key())


def token(roles: list[str]) -> str:
    claims = {"sub": "user-1", "iss": ISSUER, "aud": "swiftdrop-api", "exp": int(time.time()) + 60, "realm_access": {"roles": roles}}
    return jwt.encode(claims, _KEY, algorithm="RS256")


@pytest.fixture
def client() -> TestClient:
    settings = Settings(_env_file=None, llm_provider="gemini", gemini_api_key="test-key")
    return TestClient(create_app(settings, JwtVerifier(ISSUER, key_resolver=_Resolver())))


def test_whoami_requires_and_reads_the_token(client):
    assert client.get("/api/v1/assistant/meta/whoami").status_code == 401
    res = client.get("/api/v1/assistant/meta/whoami", headers={"Authorization": f"Bearer {token(['CUSTOMER'])}"})
    assert res.json() == {"service": "ai-assistant-service", "userId": "user-1", "roles": ["CUSTOMER"], "merchantId": None}


def test_provider_endpoint_never_leaks_the_key(client):
    res = client.get("/api/v1/assistant/meta/provider").json()
    assert res == {"provider": "gemini", "model": "gemini-2.5-flash", "configured": True}


def test_chat_is_a_placeholder_until_phase_4(client):
    res = client.post("/api/v1/assistant/chat", json={"message": "Where is my parcel?"})
    assert res.status_code == 501
    assert res.json()["code"] == "NOT_IMPLEMENTED"
    assert client.post("/api/v1/assistant/chat", json={"message": ""}).json()["code"] == "VALIDATION_FAILED"


def test_provider_factory_switches_between_openai_and_gemini():
    openai = provider_config(Settings(_env_file=None, llm_provider="openai", llm_model="gpt-4.1-mini", openai_api_key="k"))
    gemini = provider_config(Settings(_env_file=None, llm_provider="gemini"))
    assert openai.base_url == "https://api.openai.com/v1" and openai.configured
    assert gemini.base_url.startswith("https://generativelanguage.googleapis.com") and not gemini.configured


def test_settings_derive_keycloak_urls():
    settings = Settings(_env_file=None, keycloak_host_port=9999)
    assert settings.issuer == "http://localhost:9999/realms/swiftdrop"
    assert settings.jwks_uri.endswith("/realms/swiftdrop/protocol/openid-connect/certs")
