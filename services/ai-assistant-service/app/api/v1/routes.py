import json
from collections.abc import AsyncIterator

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.agent.guardrails import RateLimited
from app.agent.orchestrator import Event
from swiftdrop_common import ApiError, AuthenticatedUser
from swiftdrop_common.web import current_user, optional_user

router = APIRouter(prefix="/api/v1/assistant")


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message: str = Field(min_length=1, max_length=2000)
    conversation_id: str | None = Field(default=None, max_length=64, alias="conversationId")

    @field_validator("message")
    @classmethod
    def _not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value


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
async def chat(body: ChatRequest, request: Request,
               user: AuthenticatedUser | None = Depends(optional_user)) -> StreamingResponse:
    """Streams the answer as server-sent events: ``token``, ``tool``, ``error`` and finally ``done``.

    Anonymous callers get the public tools only; a signed-in caller's token is forwarded to every tool.
    """
    orchestrator = request.app.state.orchestrator
    if orchestrator is None:
        raise ApiError(503, "ASSISTANT_NOT_CONFIGURED", "The delivery assistant is not configured.")
    try:
        conversation = await orchestrator.begin(user, body.conversation_id, _client_address(request))
    except RateLimited as exc:
        raise ApiError(429, "RATE_LIMITED", "Too many messages. Please wait a minute and try again.") from exc
    return StreamingResponse(
        _sse(orchestrator.run(conversation, body.message, user)),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


async def _sse(events: AsyncIterator[Event]) -> AsyncIterator[str]:
    async for event in events:
        yield f"event: {event.name}\ndata: {json.dumps(event.data, ensure_ascii=False)}\n\n"


def _client_address(request: Request) -> str:
    """The original client behind the gateway, used to rate-limit guests."""
    forwarded = request.headers.get("X-Forwarded-For", "")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"
