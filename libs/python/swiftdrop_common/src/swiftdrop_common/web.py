"""FastAPI integration: correlation ids, problem+json errors, request metrics and auth dependencies."""

import logging
import re
import time
import uuid
from collections.abc import Awaitable, Callable
from datetime import UTC, datetime

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, PlainTextResponse
from prometheus_client import CONTENT_TYPE_LATEST, CollectorRegistry, Histogram, generate_latest
from starlette.exceptions import HTTPException
from starlette.responses import Response

from swiftdrop_common.auth import AuthenticatedUser, InvalidTokenError, JwtVerifier
from swiftdrop_common.log import correlation_id_var

log = logging.getLogger("swiftdrop.web")
CORRELATION_HEADER = "X-Correlation-Id"
_VALID_CORRELATION = re.compile(r"^[A-Za-z0-9-]{8,64}$")
_TITLES = {400: "Bad Request", 401: "Unauthorized", 403: "Forbidden", 404: "Not Found", 422: "Unprocessable Content", 500: "Internal Server Error"}


class ApiError(Exception):
    """Business error mapped to an HTTP status. The message is shown to clients: no personal data."""

    def __init__(self, status: int, code: str, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.code = code


def problem(status: int, code: str, detail: str, request: Request | None = None, **extra: object) -> JSONResponse:
    body = {
        "type": f"https://api.swiftdrop.lk/problems/{code.lower().replace('_', '-')}",
        "title": _TITLES.get(status, "Error"),
        "status": status,
        "detail": detail,
        "code": code,
        "traceId": correlation_id_var.get(),
        "timestamp": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        **({"instance": request.url.path} if request else {}),
        **extra,
    }
    headers = {"WWW-Authenticate": "Bearer"} if status == 401 else None
    return JSONResponse(body, status_code=status, media_type="application/problem+json", headers=headers)


def install_swiftdrop(app: FastAPI, verifier: JwtVerifier) -> None:
    """Adds the SwiftDrop conventions to a FastAPI app (same behaviour as the Java/Node services)."""
    registry = CollectorRegistry()
    requests = Histogram(
        "http_server_requests_seconds", "HTTP server request duration", ["method", "uri", "status"], registry=registry
    )
    app.state.verifier = verifier
    # Services register their own meters here so they appear on the same /metrics endpoint.
    app.state.metrics_registry = registry

    @app.middleware("http")
    async def request_context(request: Request, call_next: Callable[[Request], Awaitable[Response]]) -> Response:
        incoming = request.headers.get(CORRELATION_HEADER)
        correlation_id = incoming if incoming and _VALID_CORRELATION.match(incoming) else str(uuid.uuid4())
        token = correlation_id_var.set(correlation_id)
        start = time.perf_counter()
        try:
            response = await call_next(request)
        finally:
            correlation_id_var.reset(token)
        route = request.scope.get("route")
        uri = getattr(route, "path", "UNKNOWN")
        requests.labels(request.method, uri, str(response.status_code)).observe(time.perf_counter() - start)
        response.headers[CORRELATION_HEADER] = correlation_id
        return response

    @app.exception_handler(ApiError)
    async def api_error(request: Request, exc: ApiError) -> JSONResponse:
        return problem(exc.status, exc.code, str(exc), request)

    @app.exception_handler(HTTPException)
    async def http_error(request: Request, exc: HTTPException) -> JSONResponse:
        code = {404: "NOT_FOUND", 405: "METHOD_NOT_ALLOWED"}.get(exc.status_code, f"HTTP_{exc.status_code}")
        return problem(exc.status_code, code, str(exc.detail) if exc.status_code < 500 else "An unexpected error occurred.", request)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exc: RequestValidationError) -> JSONResponse:
        errors = [{"field": ".".join(str(p) for p in e["loc"][1:]), "message": e["msg"]} for e in exc.errors()]
        return problem(400, "VALIDATION_FAILED", "One or more fields are invalid.", request, errors=errors)

    @app.exception_handler(Exception)
    async def unexpected(request: Request, exc: Exception) -> JSONResponse:
        log.exception("Unhandled exception")
        return problem(500, "INTERNAL_ERROR", "An unexpected error occurred. Quote the traceId when contacting support.", request)

    @app.get("/metrics", include_in_schema=False)
    async def metrics() -> PlainTextResponse:
        return PlainTextResponse(generate_latest(registry), media_type=CONTENT_TYPE_LATEST)

    @app.get("/health/live", include_in_schema=False)
    async def live() -> dict[str, str]:
        return {"status": "UP"}


def _bearer(request: Request) -> str | None:
    header = request.headers.get("Authorization", "")
    return header[7:] if header.lower().startswith("bearer ") else None


async def current_user(request: Request) -> AuthenticatedUser:
    """FastAPI dependency: requires a valid token."""
    token = _bearer(request)
    if not token:
        raise ApiError(401, "UNAUTHORIZED", "A valid access token is required.")
    try:
        return request.app.state.verifier.verify(token)
    except InvalidTokenError as exc:
        raise ApiError(401, "UNAUTHORIZED", "A valid access token is required.") from exc


async def optional_user(request: Request) -> AuthenticatedUser | None:
    """FastAPI dependency: anonymous callers get None; a bad token is still rejected."""
    if not _bearer(request):
        return None
    return await current_user(request)
