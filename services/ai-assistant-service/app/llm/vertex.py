"""Gemini on Vertex AI, authenticated with a Google Cloud service account.

Vertex AI serves Gemini through an OpenAI-compatible endpoint, so the same OpenAICompatibleProvider is
used; only authentication differs. A service account cannot send a static API key: it signs a short
JWT with its private key and exchanges it for an access token (OAuth 2.0 JWT bearer grant, RFC 7523)
that lasts an hour. ServiceAccountTokens does that exchange and caches the token; BearerAuth puts the
current token on every request the OpenAI client makes.

The private key never leaves this process and is never logged.
"""

import asyncio
import json
import time
from collections.abc import AsyncGenerator, Callable
from pathlib import Path
from typing import Any

import httpx
import jwt

SCOPE = "https://www.googleapis.com/auth/cloud-platform"
_REQUIRED = ("client_email", "private_key", "token_uri")
# Refresh this long before expiry, so a request never leaves with a token about to lapse.
_EARLY_REFRESH_SECONDS = 300


class VertexCredentialsError(ValueError):
    """The service account key is missing, unreadable or incomplete. The message never contains the key."""


def load_service_account(path: Path | None, raw_json: str | None) -> dict[str, Any] | None:
    """The service account key from inline JSON (Kubernetes Secret) or a file (local and Docker), or None."""
    if raw_json:
        text, source = raw_json, "VERTEX_CREDENTIALS_JSON"
    elif path:
        if not path.is_file():
            raise VertexCredentialsError(f"Vertex AI credentials file not found: {path}")
        text, source = path.read_text(encoding="utf-8"), str(path)
    else:
        return None
    try:
        info = json.loads(text)
    except json.JSONDecodeError as exc:
        raise VertexCredentialsError(f"{source} is not valid JSON") from exc
    missing = [k for k in _REQUIRED if not info.get(k)]
    if info.get("type") != "service_account" or missing:
        raise VertexCredentialsError(f"{source} is not a service account key (missing {', '.join(missing) or 'type'})")
    return info


def vertex_base_url(project: str, location: str) -> str:
    """The OpenAI-compatible endpoint. "global" has no regional host prefix."""
    host = "aiplatform.googleapis.com" if location == "global" else f"{location}-aiplatform.googleapis.com"
    return f"https://{host}/v1/projects/{project}/locations/{location}/endpoints/openapi"


def vertex_model(model: str) -> str:
    """Vertex names Google's models with a publisher prefix: gemini-2.5-flash -> google/gemini-2.5-flash."""
    return model if "/" in model else f"google/{model}"


class ServiceAccountTokens:
    """Access tokens for one service account, fetched on demand and reused until shortly before expiry."""

    def __init__(self, info: dict[str, Any], http: httpx.AsyncClient | None = None,
                 clock: Callable[[], float] = time.time) -> None:
        self._email = info["client_email"]
        self._key = info["private_key"]
        self._key_id = info.get("private_key_id")
        self._token_uri = info["token_uri"]
        self._http = http
        self._clock = clock
        self._token: str | None = None
        self._expires_at = 0.0
        self._lock = asyncio.Lock()

    async def token(self) -> str:
        if self._token and self._clock() < self._expires_at - _EARLY_REFRESH_SECONDS:
            return self._token
        async with self._lock:  # one refresh at a time; the others reuse its result
            if not self._token or self._clock() >= self._expires_at - _EARLY_REFRESH_SECONDS:
                await self._refresh()
        assert self._token is not None
        return self._token

    async def _refresh(self) -> None:
        now = int(self._clock())
        assertion = jwt.encode(
            {"iss": self._email, "scope": SCOPE, "aud": self._token_uri, "iat": now, "exp": now + 3600},
            self._key, algorithm="RS256", headers={"kid": self._key_id} if self._key_id else None)
        data = {"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": assertion}
        if self._http is not None:
            response = await self._http.post(self._token_uri, data=data)
        else:
            async with httpx.AsyncClient(timeout=10.0) as http:
                response = await http.post(self._token_uri, data=data)
        if response.status_code != 200:
            # Google's error code (e.g. invalid_grant) is safe to surface; the body never echoes the key.
            reason = _error_code(response)
            raise httpx.HTTPStatusError(f"Google token exchange failed: {response.status_code} {reason}",
                                        request=response.request, response=response)
        body = response.json()
        self._token = body["access_token"]
        self._expires_at = now + float(body.get("expires_in", 3600))


def _error_code(response: httpx.Response) -> str:
    try:
        return str(response.json().get("error", ""))
    except ValueError:
        return ""


class BearerAuth(httpx.Auth):
    """Replaces whatever Authorization header the OpenAI client set with a current access token."""

    def __init__(self, tokens: ServiceAccountTokens) -> None:
        self._tokens = tokens

    async def async_auth_flow(self, request: httpx.Request) -> AsyncGenerator[httpx.Request, httpx.Response]:
        request.headers["Authorization"] = f"Bearer {await self._tokens.token()}"
        yield request
