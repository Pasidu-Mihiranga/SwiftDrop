"""Chooses the LLM endpoint from configuration. Both providers speak the OpenAI chat-completions API,
so one client implementation serves both (Gemini through its OpenAI-compatible endpoint)."""

from dataclasses import dataclass

from app.core.config import Settings

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

    @property
    def configured(self) -> bool:
        return bool(self.api_key)


def provider_config(settings: Settings) -> ProviderConfig:
    key = settings.gemini_api_key if settings.llm_provider == "gemini" else settings.openai_api_key
    return ProviderConfig(
        provider=settings.llm_provider,
        base_url=_BASE_URLS[settings.llm_provider],
        model=settings.llm_model,
        api_key=key.get_secret_value() if key else None,
    )
