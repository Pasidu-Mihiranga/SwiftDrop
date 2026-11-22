import { beforeEach, describe, expect, it, vi } from "vitest";
import type { NextRequest } from "next/server";

const { authMock, fetchAllMyOrdersMock } = vi.hoisted(() => ({ authMock: vi.fn(), fetchAllMyOrdersMock: vi.fn() }));
vi.mock("@/auth", () => ({ auth: authMock }));
vi.mock("@/lib/merchant-api", () => ({ fetchAllMyOrders: fetchAllMyOrdersMock }));

import { GET } from "./route";

const ORDER = {
  id: "7e726c28-4a72-4fea-9ec1-c56b08262ddc",
  trackingNumber: "SD261121THPJ1AY",
  status: "CREATED",
  merchantOrderRef: "WEB-1001",
  pickup: { line1: "12 Galle Road", city: "Colombo 03", district: "Colombo" },
  dropoff: { line1: "45 Peradeniya Road", city: "Kandy", district: "Kandy" },
  recipient: { name: "Saman Perera", phone: "0712345678" },
  weightKg: 2,
  sizeClass: "S",
  fee: { amount: 631, currency: "LKR" },
  createdAt: "2026-11-21T15:19:50Z",
};

function request(search = ""): NextRequest {
  return { nextUrl: new URL(`http://localhost:3000/merchant/orders/export${search}`) } as NextRequest;
}

describe("GET /merchant/orders/export", () => {
  beforeEach(() => {
    authMock.mockReset();
    fetchAllMyOrdersMock.mockReset();
  });

  it("downloads every matching order as a UTF-8 CSV file", async () => {
    authMock.mockResolvedValue({ user: { roles: ["MERCHANT"] } });
    fetchAllMyOrdersMock.mockResolvedValue({ orders: [ORDER], truncated: false });

    const response = await GET(request("?status=CREATED&from=2026-11-01&to=2026-11-30"));

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("text/csv; charset=utf-8");
    expect(response.headers.get("content-disposition")).toMatch(/^attachment; filename="swiftdrop-orders-\d{4}-\d{2}-\d{2}\.csv"$/);
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(response.headers.get("x-export-truncated")).toBe("false");
    expect(fetchAllMyOrdersMock).toHaveBeenCalledWith({ status: "CREATED", from: "2026-11-01", to: "2026-11-30" });

    // Read the raw bytes: text() would drop the byte-order mark while decoding.
    const bytes = new Uint8Array(await response.arrayBuffer());
    expect(Array.from(bytes.slice(0, 3))).toEqual([0xef, 0xbb, 0xbf]);
    expect(new TextDecoder().decode(bytes)).toContain("SD261121THPJ1AY,WEB-1001,CREATED");
  });

  it("is refused to anyone who is not a merchant", async () => {
    authMock.mockResolvedValue({ user: { roles: ["CUSTOMER"] } });

    const response = await GET(request());

    expect(response.status).toBe(403);
    expect(fetchAllMyOrdersMock).not.toHaveBeenCalled();
  });

  it("is refused without a session", async () => {
    authMock.mockResolvedValue(null);

    expect((await GET(request())).status).toBe(403);
  });

  it("rejects a backwards date range", async () => {
    authMock.mockResolvedValue({ user: { roles: ["MERCHANT"] } });

    const response = await GET(request("?from=2026-11-30&to=2026-11-01"));

    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe("INVALID_DATE_RANGE");
  });

  it("answers plainly when the orders cannot be loaded, instead of writing a partial file", async () => {
    authMock.mockResolvedValue({ user: { roles: ["MERCHANT"] } });
    fetchAllMyOrdersMock.mockRejectedValue(new Error("Could not load your orders (HTTP 503)"));

    const response = await GET(request());

    expect(response.status).toBe(502);
    expect(response.headers.get("content-type")).toBe("application/problem+json");
  });

  it("marks an export cut short at the row limit", async () => {
    authMock.mockResolvedValue({ user: { roles: ["MERCHANT"] } });
    fetchAllMyOrdersMock.mockResolvedValue({ orders: [ORDER], truncated: true });

    expect((await GET(request())).headers.get("x-export-truncated")).toBe("true");
  });
});
