import { beforeEach, describe, expect, it, vi } from "vitest";

const { gatewayFetchMock } = vi.hoisted(() => ({ gatewayFetchMock: vi.fn() }));

vi.mock("server-only", () => ({}));
vi.mock("@/lib/gateway", () => ({ gatewayFetch: gatewayFetchMock }));

import { fetchMerchantQueue, fetchMyApplication } from "@/lib/merchant-api";

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
