"""ai-assistant-service entry point: `uvicorn app.main:app --port 8095`."""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

import httpx
from fastapi import FastAPI
from mcp.server.transport_security import TransportSecuritySettings
from redis.asyncio import Redis

from app.agent.guardrails import RateLimiter
from app.agent.orchestrator import AgentOrchestrator
from app.api.v1.routes import router
from app.clients.swiftdrop_api import SwiftDropApi
from app.core.config import Settings
from app.core.metrics import AssistantMetrics
from app.llm.base import LLMProvider
from app.llm.factory import create_provider, provider_config
from app.mcp_server.server import build_mcp_server
from app.memory.conversation_store import ConversationStore
from app.privacy import PiiRedactor, VaultCipher
from app.tools import build_registry
from swiftdrop_common import AuthenticatedUser, JwtVerifier, configure_logging, install_swiftdrop

_FROM_SETTINGS: Any = object()


def create_app(settings: Settings | None = None, verifier: JwtVerifier | None = None,
               provider: LLMProvider | None = _FROM_SETTINGS, redis: Redis | None = None,
               gateway_transport: httpx.AsyncBaseTransport | None = None) -> FastAPI:
    """Builds the app. Tests pass a scripted provider, an in-memory Redis and a mock gateway transport."""
    settings = settings or Settings()
    configure_logging("ai-assistant-service")

    redis_client = redis or Redis.from_url(settings.redis_dsn)
    gateway = httpx.AsyncClient(base_url=settings.gateway_url, timeout=10.0, transport=gateway_transport)

    mcp_server: Any = None

    @asynccontextmanager
    async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
        async with mcp_server.session_manager.run():
            yield
        await gateway.aclose()
        await redis_client.aclose()

    app = FastAPI(title="SwiftDrop AI Assistant", version="0.1.0", docs_url="/docs", lifespan=lifespan)
    jwt_verifier = verifier or JwtVerifier(settings.issuer, jwks_uri=settings.jwks_uri)
    install_swiftdrop(app, jwt_verifier)
    metrics = AssistantMetrics(app.state.metrics_registry)
    redactor = PiiRedactor(metrics.redaction)
    registry = build_registry()

    def api_for(user: AuthenticatedUser | None) -> SwiftDropApi:
        return SwiftDropApi(gateway, user.token if user else None)

    config = provider_config(settings)
    llm = create_provider(config) if provider is _FROM_SETTINGS else provider
    secret = settings.assistant_vault_secret.get_secret_value() if settings.assistant_vault_secret else None
    store = ConversationStore(redis_client, VaultCipher(secret), settings.assistant_conversation_ttl_seconds,
                              settings.assistant_history_messages)

    app.state.settings = settings
    app.state.provider = config
    app.state.registry = registry
    app.state.redactor = redactor
    app.state.api_for = api_for
    app.state.orchestrator = None if llm is None else AgentOrchestrator(
        llm, registry, redactor, store, RateLimiter(redis_client, settings.assistant_rate_limit_per_min), api_for,
        max_tool_rounds=settings.assistant_max_tool_rounds, metrics=metrics)
    app.include_router(router)

    # MCP over streamable HTTP at /mcp. Stateless JSON responses suit a service behind the gateway; the
    # DNS-rebinding check is off because requests arrive through the gateway, not from a local browser.
    mcp_server = build_mcp_server(registry, jwt_verifier, api_for, redactor, mask_pii=settings.mcp_mask_pii)
    mcp_app = mcp_server.streamable_http_app(
        streamable_http_path="/mcp", stateless_http=True, json_response=True,
        transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=False))
    app.router.routes.extend(mcp_app.routes)
    return app


app = create_app()
