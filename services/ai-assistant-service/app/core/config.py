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

    @property
    def issuer(self) -> str:
        return self.keycloak_issuer or f"http://localhost:{self.keycloak_host_port}/realms/swiftdrop"

    @property
    def jwks_uri(self) -> str:
        return self.keycloak_jwks_uri or f"{self.issuer}/protocol/openid-connect/certs"
