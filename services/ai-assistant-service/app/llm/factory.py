"""Chooses the LLM endpoint from configuration. Every provider speaks the OpenAI chat-completions API, so
one client implementation serves all of them: OpenAI, Gemini through its OpenAI-compatible endpoint, and
Gemini on Vertex AI, which differs only in authenticating with a service account (app/llm/vertex.py)."""

import logging
from dataclasses import dataclass, field
from typing import Any

import httpx

from app.core.config import Settings
from app.llm.base import LLMProvider
from app.llm.openai_compatible import OpenAICompatibleProvider
from app.llm.vertex import (BearerAuth, ServiceAccountTokens, VertexCredentialsError, load_service_account,
                            vertex_base_url, vertex_model)

log = logging.getLogger(__name__)

_BASE_URLS = {
    "openai": "https://api.openai.com/v1",
    "gemini": "https://generativelanguage.googleapis.com/v1beta/openai/",
}


@dataclass(frozen=True)
class ProviderConfig:
    provider: str
    base_url: str
    model: str
    api_key: str | None
    # Vertex AI only: the parsed service account key. Never returned by any endpoint or logged.
    service_account: dict[str, Any] | None = field(default=None, repr=False)

    @property
    def configured(self) -> bool:
        return bool(self.service_account) if self.provider == "vertex" else bool(self.api_key)


def provider_config(settings: Settings) -> ProviderConfig:
    if settings.llm_provider == "vertex":
        raw = settings.vertex_credentials_json.get_secret_value() if settings.vertex_credentials_json else None
        try:
            account = load_service_account(settings.vertex_credentials_path, raw)
        except VertexCredentialsError as exc:
            # Start anyway: chat answers 503 (not configured) and MCP keeps working, as with a missing API key.
            log.warning("Vertex AI is not configured: %s", exc)
            account = None
        project = settings.vertex_project or (account or {}).get("project_id") or ""
        return ProviderConfig(provider="vertex", base_url=vertex_base_url(project, settings.vertex_location),
                              model=vertex_model(settings.llm_model), api_key=None, service_account=account)
    key = settings.gemini_api_key if settings.llm_provider == "gemini" else settings.openai_api_key
    return ProviderConfig(
        provider=settings.llm_provider,
        base_url=_BASE_URLS[settings.llm_provider],
        model=settings.llm_model,
        api_key=key.get_secret_value() if key else None,
    )


def create_provider(config: ProviderConfig) -> LLMProvider | None:
    """The configured provider, or None when no credentials are set (the chat endpoint then answers 503)."""
    if not config.configured:
        return None
    if config.provider == "vertex":
        assert config.service_account is not None
        http = httpx.AsyncClient(auth=BearerAuth(ServiceAccountTokens(config.service_account)), timeout=60.0)
        # The api_key is a placeholder the OpenAI client insists on; BearerAuth replaces it per request.
        return OpenAICompatibleProvider(config.base_url, config.model, "vertex-service-account", http_client=http)
    return OpenAICompatibleProvider(config.base_url, config.model, config.api_key or "")
