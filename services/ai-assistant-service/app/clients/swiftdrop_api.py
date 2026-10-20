"""Calls SwiftDrop's own APIs through the gateway, forwarding the end user's token so the assistant
can never see more than the user could see in the web app."""

from typing import Any

import httpx

from swiftdrop_common import ApiError


class SwiftDropApi:
    def __init__(self, base_url: str, token: str | None = None, transport: httpx.AsyncBaseTransport | None = None) -> None:
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        self._client = httpx.AsyncClient(base_url=base_url, headers=headers, timeout=10.0, transport=transport)

    async def track(self, tracking_number: str) -> dict[str, Any]:
        """Public, PII-masked tracking view."""
        return await self._get(f"/api/v1/tracking/public/{tracking_number}")

    async def zones(self) -> list[dict[str, Any]]:
        return await self._get("/api/v1/pricing/zones")

    async def _get(self, path: str) -> Any:
        response = await self._client.get(path)
        if response.status_code == 404:
            raise ApiError(404, "NOT_FOUND", "The requested item was not found.")
        if response.is_error:
            raise ApiError(502, "UPSTREAM_ERROR", "A SwiftDrop service is temporarily unavailable.")
        return response.json()

    async def aclose(self) -> None:
        await self._client.aclose()
