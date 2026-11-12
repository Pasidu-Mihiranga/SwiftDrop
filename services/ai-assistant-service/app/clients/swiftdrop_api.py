"""Calls SwiftDrop's own APIs through the gateway, forwarding the end user's token so the assistant
can never see more than the user could see in the web app."""

from typing import Any

import httpx

from swiftdrop_common import ApiError
from swiftdrop_common.log import correlation_id_var

_ERRORS = {
    400: (400, "INVALID_REQUEST", "SwiftDrop could not accept those details."),
    401: (401, "SIGN_IN_REQUIRED", "Signing in is required for this."),
    403: (403, "FORBIDDEN", "This account is not allowed to see that."),
    404: (404, "NOT_FOUND", "The requested item was not found."),
    422: (400, "INVALID_REQUEST", "SwiftDrop could not accept those details."),
    429: (429, "RATE_LIMITED", "SwiftDrop is busy; try again in a moment."),
}


class SwiftDropApi:
    """One instance per caller; the HTTP connection pool is shared across instances."""

    def __init__(self, client: httpx.AsyncClient, token: str | None = None) -> None:
        self._client = client
        self._token = token

    async def track(self, tracking_number: str) -> dict[str, Any]:
        """Public, PII-masked tracking view."""
        return await self._request("GET", f"/api/v1/tracking/public/{tracking_number}")

    async def zones(self) -> list[dict[str, Any]]:
        return await self._request("GET", "/api/v1/pricing/zones")

    async def quote(self, request: dict[str, Any]) -> dict[str, Any]:
        return await self._request("POST", "/api/v1/pricing/quotes", json=request)

    async def my_parcels(self, status: str | None = None, size: int = 10) -> dict[str, Any]:
        params: dict[str, Any] = {"size": size}
        if status:
            params["status"] = status
        return await self._request("GET", "/api/v1/parcels/mine", params=params)

    async def parcel(self, parcel_id: str) -> dict[str, Any]:
        return await self._request("GET", f"/api/v1/parcels/{parcel_id}")

    async def _request(self, method: str, path: str, **kwargs: Any) -> Any:
        headers = {"Accept": "application/json"}
        if self._token:
            headers["Authorization"] = f"Bearer {self._token}"
        correlation_id = correlation_id_var.get()
        if correlation_id:
            headers["X-Correlation-Id"] = correlation_id
        try:
            response = await self._client.request(method, path, headers=headers, **kwargs)
        except httpx.HTTPError as exc:
            raise ApiError(502, "UPSTREAM_ERROR", "A SwiftDrop service is temporarily unavailable.") from exc
        if response.is_success:
            return response.json()
        status, code, message = _ERRORS.get(
            response.status_code, (502, "UPSTREAM_ERROR", "A SwiftDrop service is temporarily unavailable.")
        )
        raise ApiError(status, code, message)
