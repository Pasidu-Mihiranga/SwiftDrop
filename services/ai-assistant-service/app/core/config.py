"""Environment-driven settings (12-factor). Locally the repository's .env is read; containers get env vars."""

from pathlib import Path
from typing import Literal

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


_REPO_ROOT = Path(__file__).resolve().parents[4]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=(".env", "../../.env"), extra="ignore")

    port: int = 8095
    keycloak_host_port: int = 8180
    keycloak_issuer: str | None = None
    keycloak_jwks_uri: str | None = None
    gateway_url: str = "http://localhost:8080"

    llm_provider: Literal["gemini", "openai", "vertex"] = "gemini"
    llm_model: str = "gemini-3.8-flash"
    gemini_api_key: SecretStr | None = None
    openai_api_key: SecretStr | None = None
    # Gemini on Vertex AI with a Google Cloud service account (LLM_PROVIDER=vertex). The key comes from a
    # file (local runs and Docker, where it is mounted read-only) or inline JSON (a Kubernetes Secret).
    # A relative path is taken from the repository root, so the same .env works wherever the service starts.
    vertex_credentials_file: Path | None = None
    vertex_credentials_json: SecretStr | None = None
    vertex_project: str | None = None     # defaults to the key's project_id
    vertex_location: str = "global"

    # Conversation memory and the PII vault live in Redis. Docker Compose sets REDIS_URL; Kubernetes sets
    # REDIS_HOST and REDIS_PORT (the chart's shared config); locally the URL is built from the same
    # variables the infra profile uses.
    redis_url: str | None = None
    redis_host: str = "localhost"
    redis_port: int | None = None
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
    def vertex_credentials_path(self) -> Path | None:
        path = self.vertex_credentials_file
        if path is None or path.is_absolute():
            return path
        return _REPO_ROOT / path

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
        return f"redis://{auth}{self.redis_host}:{self.redis_port or self.redis_host_port}/0"
