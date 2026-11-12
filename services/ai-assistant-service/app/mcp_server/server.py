"""MCP server at /mcp (streamable HTTP): the chat agent's tools, for external AI clients.

The caller is whoever the bearer token says, verified exactly like the chat endpoint; without a token
only the public tools work. MCP clients are third-party AI apps, so personal data in results is masked
irreversibly unless MCP_MASK_PII=false.
"""

from collections.abc import Callable
from typing import Any, Literal

from mcp.server import MCPServer
from mcp.server.mcpserver import Context
from mcp.server.mcpserver.exceptions import ToolError

from app.clients.swiftdrop_api import SwiftDropApi
from app.privacy import PiiRedactor
from app.tools import ToolRegistry
from swiftdrop_common import AuthenticatedUser, InvalidTokenError, JwtVerifier

INSTRUCTIONS = (
    "SwiftDrop parcel delivery in Sri Lanka. Track a parcel by tracking number, estimate a delivery fee between "
    "districts, and list service zones without signing in. Send a SwiftDrop access token as a bearer token to "
    "list and open your own parcels. Personal data in results is masked."
)


class McpToolError(ToolError):
    """An anticipated failure: the MCP client gets isError with this message, and no traceback is logged.
    The message never contains personal data."""


def build_mcp_server(registry: ToolRegistry, verifier: JwtVerifier,
                     api_for: Callable[[AuthenticatedUser | None], SwiftDropApi], redactor: PiiRedactor,
                     mask_pii: bool = True) -> MCPServer:
    server = MCPServer("swiftdrop", instructions=INSTRUCTIONS)

    def caller(ctx: Context) -> AuthenticatedUser | None:
        # Headers are client input: the token is verified, never trusted as it is.
        header = (ctx.headers or {}).get("authorization", "")
        if not header.lower().startswith("bearer "):
            return None
        try:
            return verifier.verify(header[7:])
        except InvalidTokenError as exc:
            raise McpToolError("The access token is invalid or has expired.") from exc

    async def run(name: str, args: dict[str, Any], ctx: Context) -> dict[str, Any]:
        user = caller(ctx)
        result = await registry.call(name, args, user, api_for(user))
        if not result.ok:
            raise McpToolError(f"{result.error_code}: {result.data}")
        data = redactor.mask_json(result.data) if mask_pii else result.data
        return {"result": data}

    async def track_parcel(tracking_number: str, ctx: Context) -> dict[str, Any]:
        return await run("track_parcel", {"tracking_number": tracking_number}, ctx)

    async def estimate_delivery_fee(from_district: str, to_district: str, weight_kg: float, ctx: Context,
                                    size_class: Literal["S", "M", "L", "XL"] = "S",
                                    service_level: Literal["STANDARD", "EXPRESS"] = "STANDARD") -> dict[str, Any]:
        return await run("estimate_delivery_fee", {
            "from_district": from_district, "to_district": to_district, "weight_kg": weight_kg,
            "size_class": size_class, "service_level": service_level}, ctx)

    async def list_service_zones(ctx: Context) -> dict[str, Any]:
        return await run("list_service_zones", {}, ctx)

    async def list_my_parcels(ctx: Context, status: str | None = None) -> dict[str, Any]:
        return await run("list_my_parcels", {"status": status} if status else {}, ctx)

    async def get_parcel_details(parcel_id: str, ctx: Context) -> dict[str, Any]:
        return await run("get_parcel_details", {"parcel_id": parcel_id}, ctx)

    for fn in (track_parcel, estimate_delivery_fee, list_service_zones, list_my_parcels, get_parcel_details):
        tool = registry.get(fn.__name__)
        assert tool is not None, fn.__name__
        server.add_tool(fn, name=tool.name, description=tool.description)
    return server
