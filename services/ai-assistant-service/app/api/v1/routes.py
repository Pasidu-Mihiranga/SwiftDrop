from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel, Field

from swiftdrop_common import ApiError, AuthenticatedUser
from swiftdrop_common.web import current_user, optional_user

router = APIRouter(prefix="/api/v1/assistant")


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=2000)
    conversation_id: str | None = Field(default=None, max_length=64)


@router.get("/meta/whoami")
async def who_am_i(user: AuthenticatedUser = Depends(current_user)) -> dict[str, object]:
    """Smoke-test endpoint: proves the gateway → service → JWT chain works end to end."""
    return {"service": "ai-assistant-service", "userId": user.id, "roles": sorted(user.roles), "merchantId": user.merchant_id}


@router.get("/meta/provider")
async def provider(request: Request) -> dict[str, object]:
    """Which LLM provider is configured (never returns the key)."""
    config = request.app.state.provider
    return {"provider": config.provider, "model": config.model, "configured": config.configured}


@router.post("/chat")
async def chat(body: ChatRequest, user: AuthenticatedUser | None = Depends(optional_user)) -> None:
    """Streaming chat (SSE) — implemented in Phase 4 by M10. Anonymous users will get public tools only."""
    raise ApiError(501, "NOT_IMPLEMENTED", "The delivery assistant is not available yet.")
