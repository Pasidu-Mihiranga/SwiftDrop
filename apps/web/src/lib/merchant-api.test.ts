import { beforeEach, describe, expect, it, vi } from "vitest";

const { gatewayFetchMock } = vi.hoisted(() => ({ gatewayFetchMock: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/gateway", () => ({ gatewayFetch: gatewayFetchMock }));

import { fetchAllMyOrders, fetchMerchantQueue, fetchMyApplication, fetchMyOrders } from "@/lib/merchant-api";

const MERCHANT = { id: "7e726c28-4a72-4fea-9ec1-c56b08262ddc", businessName: "Lanka Books", status: "PENDING" };

function respond(status: number, body?: unknown): void {
  gatewayFetchMock.mockResolvedValue(new Response(body === undefined ? null : JSON.stringify(body), { status }));
}

beforeEach(() => gatewayFetchMock.mockReset());

describe("fetchMyApplication", () => {
  it("returns the caller's application", async () => {
    respond(200, MERCHANT);

    await expect(fetchMyApplication()).resolves.toEqual(MERCHANT);
    expect(gatewayFetchMock).toHaveBeenCalledWith("/api/v1/merchants/me");
  });

  it("returns null for someone who has never applied", async () => {
    respond(404, { code: "MERCHANT_NOT_FOUND" });

    await expect(fetchMyApplication()).resolves.toBeNull();
  });

  it("fails loudly for anything else, so the page shows its error state", async () => {
    respond(503);

    await expect(fetchMyApplication()).rejects.toThrow("HTTP 503");
  });
});

describe("fetchMerchantQueue", () => {
  it("asks for one page of one status", async () => {
    const page = { content: [MERCHANT], page: { page: 1, size: 20, totalElements: 21, totalPages: 2 } };
    respond(200, page);

    await expect(fetchMerchantQueue("PENDING", 1)).resolves.toEqual(page);
    expect(gatewayFetchMock).toHaveBeenCalledWith("/api/v1/admin/merchants?status=PENDING&page=1&size=20");
  });

  it("fails loudly when the service refuses", async () => {
    respond(403);

    await expect(fetchMerchantQueue("APPROVED", 0)).rejects.toThrow("HTTP 403");
  });
});

describe("fetchMyOrders", () => {
  it("asks parcel-service for one page with the merchant's filters and no merchant id", async () => {
    const page = { content: [], page: { page: 0, size: 20, totalElements: 0, totalPages: 0 } };
    respond(200, page);

    await expect(fetchMyOrders({ status: "DELIVERED", from: "2026-11-01" }, 0)).resolves.toEqual(page);
    expect(gatewayFetchMock).toHaveBeenCalledWith(
      "/api/v1/merchant/orders?page=0&size=20&status=DELIVERED&from=2026-10-31T18%3A30%3A00.000Z",
    );
  });

  it("fails loudly when the service refuses, so the dashboard shows its error state", async () => {
    respond(403);

    await expect(fetchMyOrders({}, 0)).rejects.toThrow("HTTP 403");
  });
});

describe("fetchAllMyOrders", () => {
  const order = (n: number) => ({ id: `id-${n}`, trackingNumber: `SD${n}` });
  const pageOf = (count: number, page: number, totalPages: number, offset = 0) => new Response(JSON.stringify({
    content: Array.from({ length: count }, (_, i) => order(offset + i)),
    page: { page, size: 100, totalElements: totalPages * 100, totalPages },
  }), { status: 200 });

  it("reads every page of 100 until the last one", async () => {
    gatewayFetchMock
      .mockResolvedValueOnce(pageOf(100, 0, 2))
      .mockResolvedValueOnce(pageOf(30, 1, 2, 100));

    const result = await fetchAllMyOrders({ status: "CREATED" });

    expect(result.orders).toHaveLength(130);
    expect(result.truncated).toBe(false);
    expect(gatewayFetchMock).toHaveBeenNthCalledWith(1, "/api/v1/merchant/orders?page=0&size=100&status=CREATED");
    expect(gatewayFetchMock).toHaveBeenNthCalledWith(2, "/api/v1/merchant/orders?page=1&size=100&status=CREATED");
  });

  it("stops at the row limit and says the export was cut short", async () => {
    gatewayFetchMock.mockImplementation(async (path: string) => {
      const page = Number(new URL(path, "http://x").searchParams.get("page"));
      return pageOf(100, page, 500, page * 100);
    });

    const result = await fetchAllMyOrders({});

    expect(result.orders).toHaveLength(10_000);
    expect(result.truncated).toBe(true);
    expect(gatewayFetchMock).toHaveBeenCalledTimes(100);
  });

  it("returns nothing for a merchant with no orders", async () => {
    gatewayFetchMock.mockResolvedValueOnce(pageOf(0, 0, 0));

    await expect(fetchAllMyOrders({})).resolves.toEqual({ orders: [], truncated: false });
  });

  it("fails the whole export when any page fails, rather than writing a partial file", async () => {
    gatewayFetchMock.mockResolvedValueOnce(pageOf(100, 0, 3)).mockResolvedValueOnce(new Response(null, { status: 503 }));

    await expect(fetchAllMyOrders({})).rejects.toThrow("HTTP 503");
  });
});
