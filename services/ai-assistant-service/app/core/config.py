"""Environment-driven settings (12-factor). Locally the repository's .env is read; containers get env vars."""

from typing import Literal

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=(".env", "../../.env"), extra="ignore")

    port: int = 8095
    keycloak_host_port: int = 8180
    keycloak_issuer: str | None = None
    keycloak_jwks_uri: str | None = None
    gateway_url: str = "http://localhost:8080"

    llm_provider: Literal["gemini", "openai"] = "gemini"
    llm_model: str = "gemini-2.5-flash"
    gemini_api_key: SecretStr | None = None
    openai_api_key: SecretStr | None = None

    # Conversation memory and the PII vault live in Redis. Inside Docker REDIS_URL is set; locally the
    # URL is built from the same variables the infra profile uses.
    redis_url: str | None = None
    redis_host_port: int = 6380
    redis_password: SecretStr | None = None
    # Encrypts the per-conversation PII vault. Without it a random key is used, so vaults do not survive
    # a restart (fine for development; set it in every shared environment).
    assistant_vault_secret: SecretStr | None = None

    assistant_rate_limit_per_min: int = 20
    assistant_max_tool_rounds: int = 5
    assistant_conversation_ttl_seconds: int = 24 * 3600
    assistant_history_messages: int = 20
    # MCP clients are third-party AI apps: tool results leave with personal data masked unless this is false.
    mcp_mask_pii: bool = True

    @property
    def issuer(self) -> str:
        return self.keycloak_issuer or f"http://localhost:{self.keycloak_host_port}/realms/swiftdrop"

    @property
    def jwks_uri(self) -> str:
        return self.keycloak_jwks_uri or f"{self.issuer}/protocol/openid-connect/certs"

    @property
    def redis_dsn(self) -> str:
        if self.redis_url:
            return self.redis_url
        password = self.redis_password.get_secret_value() if self.redis_password else ""
        auth = f":{password}@" if password else ""
        return f"redis://{auth}localhost:{self.redis_host_port}/0"
