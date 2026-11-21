import { type APIRequestContext, expect, test } from "@playwright/test";
import { gatewayAs } from "../support/api";
import { users } from "../support/env";

/**
 * An external MCP client (what MCP Inspector or Claude Desktop does): JSON-RPC over streamable
 * HTTP at /mcp, through the gateway. Needs no LLM key.
 */
async function rpc(api: APIRequestContext, method: string, params: Record<string, unknown> = {}) {
  const response = await api.post("/mcp", {
    headers: { accept: "application/json, text/event-stream", "content-type": "application/json" },
    data: { jsonrpc: "2.0", id: 1, method, params },
  });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()).result;
}

test.describe("MCP server", () => {
  test("lists the five SwiftDrop tools", async () => {
    const result = await rpc(await gatewayAs(), "tools/list");
    expect(result.tools.map((t: { name: string }) => t.name)).toEqual([
      "track_parcel",
      "estimate_delivery_fee",
      "list_service_zones",
      "list_my_parcels",
      "get_parcel_details",
    ]);
  });

  test("an anonymous client gets a real quote for a town", async () => {
    const result = await rpc(await gatewayAs(), "tools/call", {
      name: "estimate_delivery_fee",
      arguments: { from_city: "Nugegoda", to_city: "Kandy", weight_kg: 2 },
    });
    expect(result.isError, JSON.stringify(result)).toBeFalsy();
    const quote = result.structuredContent.result;
    expect(quote.fromDistrict).toBe("Colombo");
    expect(quote.toDistrict).toBe("Kandy");
    expect(quote.fee.amount).toBeGreaterThan(0);
  });

  test("an anonymous client cannot list anyone's parcels", async () => {
    const result = await rpc(await gatewayAs(), "tools/call", { name: "list_my_parcels", arguments: {} });
    expect(result.isError).toBe(true);
    expect(result.content[0].text).toContain("SIGN_IN_REQUIRED");
  });

  test("a customer's token lists only their own parcels", async () => {
    const result = await rpc(await gatewayAs(users.customer), "tools/call", { name: "list_my_parcels", arguments: {} });
    expect(result.isError, JSON.stringify(result)).toBeFalsy();
    expect(Array.isArray(result.structuredContent.result)).toBe(true);
  });
});
