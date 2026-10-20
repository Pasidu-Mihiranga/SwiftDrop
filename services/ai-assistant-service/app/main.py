"""ai-assistant-service entry point: `uvicorn app.main:app --port 8095`."""

from fastapi import FastAPI

from app.api.v1.routes import router
from app.core.config import Settings
from app.llm.factory import provider_config
from swiftdrop_common import JwtVerifier, configure_logging, install_swiftdrop


def create_app(settings: Settings | None = None, verifier: JwtVerifier | None = None) -> FastAPI:
    settings = settings or Settings()
    configure_logging("ai-assistant-service")
    app = FastAPI(title="SwiftDrop AI Assistant", version="0.1.0", docs_url="/docs")
    install_swiftdrop(app, verifier or JwtVerifier(settings.issuer, jwks_uri=settings.jwks_uri))
    app.state.settings = settings
    app.state.provider = provider_config(settings)
    app.include_router(router)
    return app


app = create_app()
