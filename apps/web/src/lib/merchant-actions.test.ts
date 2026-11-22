import { beforeEach, describe, expect, it, vi } from "vitest";

const { authMock, gatewayFetchMock, revalidatePathMock } = vi.hoisted(() => ({
  authMock: vi.fn(),
  gatewayFetchMock: vi.fn(),
  revalidatePathMock: vi.fn(),
}));

vi.mock("@/auth", () => ({ auth: authMock }));
vi.mock("@/lib/gateway", () => ({ gatewayFetch: gatewayFetchMock }));
vi.mock("next/cache", () => ({ revalidatePath: revalidatePathMock }));

import {
  approveMerchant,
  registerMerchant,
  rejectMerchant,
  rotateCredentials,
  suspendMerchant,
} from "@/lib/merchant-actions";

const IDLE = { status: "idle" } as const;
const MERCHANT_ID = "7e726c28-4a72-4fea-9ec1-c56b08262ddc";

function form(entries: Record<string, string>): FormData {
  const data = new FormData();
  for (const [key, value] of Object.entries(entries)) data.set(key, value);
  return data;
}

function application(overrides: Record<string, string> = {}): FormData {
  return form({
    businessName: "Lanka Books",
    brNumber: "PV 12345",
    contactEmail: "lankabooks@example.com",
    contactPhone: "0771234567",
    "pickupAddress.line1": "12 Temple Road",
    "pickupAddress.city": "Kandy",
    "pickupAddress.district": "Kandy",
    ...overrides,
  });
}

function signedInAs(...roles: string[]): void {
  authMock.mockResolvedValue({ user: { roles } });
}

function respondOk(): void {
  gatewayFetchMock.mockResolvedValue(new Response("{}", { status: 200 }));
}

function respondProblem(status: number, body: Record<string, unknown>): void {
  gatewayFetchMock.mockResolvedValue(
    new Response(JSON.stringify({ status, ...body }), { status, headers: { "content-type": "application/problem+json" } }),
  );
}

function sentRequest(): { path: string; init: RequestInit } {
  const [path, init] = gatewayFetchMock.mock.calls[0];
  return { path, init };
}

beforeEach(() => {
  authMock.mockReset();
  gatewayFetchMock.mockReset();
  revalidatePathMock.mockReset();
});

describe("registerMerchant", () => {
  it("sends the application with the caller's own token and refreshes both pages", async () => {
    signedInAs("CUSTOMER");
    respondOk();

    const state = await registerMerchant(IDLE, application());

    expect(state.status).toBe("ok");
    const { path, init } = sentRequest();
    expect(path).toBe("/api/v1/merchants/register");
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).toMatchObject({
      brNumber: "PV 12345",
      pickupAddress: { line1: "12 Temple Road", district: "Kandy" },
    });
    expect(revalidatePathMock).toHaveBeenCalledWith("/merchant/register");
    expect(revalidatePathMock).toHaveBeenCalledWith("/admin/merchants");
  });

  it("lets a user with no role apply", async () => {
    signedInAs();
    respondOk();

    expect((await registerMerchant(IDLE, application())).status).toBe("ok");
  });

  it("asks a signed-out visitor to sign in without calling the service", async () => {
    authMock.mockResolvedValue(null);

    const state = await registerMerchant(IDLE, application());

    expect(state).toEqual({ status: "error", message: "Sign in to apply." });
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("reports missing fields without calling the service", async () => {
    signedInAs("CUSTOMER");

    const state = await registerMerchant(IDLE, application({ businessName: "" }));

    expect(state.status).toBe("error");
    expect(state.errors).toEqual({ businessName: "Business name is required" });
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("explains a registration number that is already taken", async () => {
    signedInAs("CUSTOMER");
    respondProblem(409, { code: "MERCHANT_BR_NUMBER_TAKEN", detail: "service text" });

    const state = await registerMerchant(IDLE, application());

    expect(state.status).toBe("error");
    expect(state.message).toContain("registration number has already applied");
    expect(revalidatePathMock).not.toHaveBeenCalled();
  });

  it("shows the service's field messages against the right fields", async () => {
    signedInAs("CUSTOMER");
    respondProblem(400, {
      code: "VALIDATION_FAILED",
      detail: "One or more fields are invalid.",
      errors: [{ field: "contactPhone", message: "must be a Sri Lankan phone number" }],
    });

    const state = await registerMerchant(IDLE, application());

    expect(state.message).toBe("One or more fields are invalid.");
    expect(state.errors).toEqual({ contactPhone: "must be a Sri Lankan phone number" });
  });

  it("says so when the service cannot be reached", async () => {
    signedInAs("CUSTOMER");
    gatewayFetchMock.mockRejectedValue(new TypeError("fetch failed"));

    expect((await registerMerchant(IDLE, application())).message).toContain("unreachable");
  });

  it("copes with a failure that has no problem body", async () => {
    signedInAs("CUSTOMER");
    gatewayFetchMock.mockResolvedValue(new Response("Bad gateway", { status: 502 }));

    expect((await registerMerchant(IDLE, application())).message).toContain("UNEXPECTED_ERROR");
  });
});

describe("admin decisions", () => {
  it("approves through the admin endpoint", async () => {
    signedInAs("ADMIN");
    respondOk();

    const state = await approveMerchant(IDLE, form({ merchantId: MERCHANT_ID }));

    expect(state).toEqual({ status: "ok", message: "Approved." });
    expect(sentRequest().path).toBe(`/api/v1/admin/merchants/${MERCHANT_ID}/approve`);
    expect(sentRequest().init.body).toBeUndefined();
  });

  it("sends a trimmed rejection reason", async () => {
    signedInAs("ADMIN");
    respondOk();

    const state = await rejectMerchant(IDLE, form({ merchantId: MERCHANT_ID, reason: "  BR certificate is unreadable " }));

    expect(state.status).toBe("ok");
    expect(sentRequest().path).toBe(`/api/v1/admin/merchants/${MERCHANT_ID}/reject`);
    expect(JSON.parse(String(sentRequest().init.body))).toEqual({ reason: "BR certificate is unreadable" });
  });

  it("refuses a blank or overlong reason before calling the service", async () => {
    signedInAs("ADMIN");

    expect((await rejectMerchant(IDLE, form({ merchantId: MERCHANT_ID, reason: "   " }))).errors).toEqual({
      reason: "A reason is required",
    });
    expect((await rejectMerchant(IDLE, form({ merchantId: MERCHANT_ID, reason: "x".repeat(501) }))).status).toBe("error");
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("suspends only after the confirmation box is ticked", async () => {
    signedInAs("ADMIN");
    respondOk();

    expect((await suspendMerchant(IDLE, form({ merchantId: MERCHANT_ID }))).status).toBe("error");
    expect(gatewayFetchMock).not.toHaveBeenCalled();

    const state = await suspendMerchant(IDLE, form({ merchantId: MERCHANT_ID, confirm: "on" }));
    expect(state.status).toBe("ok");
    expect(sentRequest().path).toBe(`/api/v1/admin/merchants/${MERCHANT_ID}/suspend`);
  });

  it.each(["CUSTOMER", "MERCHANT", "DISPATCHER"])("never calls the service for a %s", async (role) => {
    signedInAs(role);

    const results = await Promise.all([
      approveMerchant(IDLE, form({ merchantId: MERCHANT_ID })),
      rejectMerchant(IDLE, form({ merchantId: MERCHANT_ID, reason: "No" })),
      suspendMerchant(IDLE, form({ merchantId: MERCHANT_ID, confirm: "on" })),
    ]);

    expect(results.every((state) => state.message === "Only an administrator can review merchants.")).toBe(true);
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("refuses a merchant id that is not a uuid, so nothing odd reaches the path", async () => {
    signedInAs("ADMIN");

    const state = await approveMerchant(IDLE, form({ merchantId: "../../users" }));

    expect(state.message).toBe("That is not a valid merchant id.");
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("explains a decision the application no longer allows", async () => {
    signedInAs("ADMIN");
    respondProblem(422, { code: "MERCHANT_INVALID_TRANSITION", detail: "A merchant in status APPROVED cannot be moved to REJECTED." });

    const state = await rejectMerchant(IDLE, form({ merchantId: MERCHANT_ID, reason: "Late" }));

    expect(state.message).toContain("no longer possible");
  });

  it("falls back to the service's own words for a code it does not know", async () => {
    signedInAs("ADMIN");
    respondProblem(403, { code: "FORBIDDEN", detail: "Access is denied." });

    expect((await approveMerchant(IDLE, form({ merchantId: MERCHANT_ID }))).message).toBe("Access is denied.");
  });
});

describe("rotateCredentials", () => {
  const CREDENTIALS = {
    clientId: `merchant-${MERCHANT_ID}`,
    clientSecret: "s3cr3t-value",
    tokenUrl: "http://localhost:8180/realms/swiftdrop/protocol/openid-connect/token",
  };
  const confirmed = () => form({ confirm: "on" });

  it("returns the new credentials once, from merchant-service, with nothing else sent", async () => {
    signedInAs("CUSTOMER", "MERCHANT");
    gatewayFetchMock.mockResolvedValue(new Response(JSON.stringify(CREDENTIALS), { status: 200 }));

    const state = await rotateCredentials(IDLE, confirmed());

    expect(state).toEqual({ status: "ok", credentials: CREDENTIALS });
    expect(sentRequest().path).toBe("/api/v1/merchants/me/credentials/rotate");
    expect(sentRequest().init).toEqual({ method: "POST" });
  });

  it("needs the box ticked, because the old secret stops working", async () => {
    signedInAs("MERCHANT");

    const state = await rotateCredentials(IDLE, form({}));

    expect(state.status).toBe("error");
    expect(state.message).toMatch(/Tick the box/);
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("is refused to someone who is not a merchant", async () => {
    signedInAs("CUSTOMER");

    const state = await rotateCredentials(IDLE, confirmed());

    expect(state).toEqual({ status: "error", message: "Sign in as a merchant to manage API credentials." });
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("explains a suspended merchant in plain words", async () => {
    signedInAs("MERCHANT");
    respondProblem(422, { code: "MERCHANT_NOT_APPROVED", detail: "Merchant is SUSPENDED" });

    const state = await rotateCredentials(IDLE, confirmed());

    expect(state.status).toBe("error");
    expect(state.message).toMatch(/suspended/);
    expect(state.credentials).toBeUndefined();
  });

  it("says retrying is safe when Keycloak did not confirm", async () => {
    signedInAs("MERCHANT");
    respondProblem(503, { code: "IDENTITY_PROVIDER_UNAVAILABLE" });

    expect((await rotateCredentials(IDLE, confirmed())).message).toMatch(/safe to retry/);
  });

  it("falls back to the service's own detail for an unknown problem", async () => {
    signedInAs("MERCHANT");
    respondProblem(409, { code: "SOMETHING_NEW", detail: "Something new happened" });

    expect((await rotateCredentials(IDLE, confirmed())).message).toBe("Something new happened");
  });

  it("reports an unreachable service", async () => {
    signedInAs("MERCHANT");
    gatewayFetchMock.mockRejectedValue(new TypeError("fetch failed"));

    expect((await rotateCredentials(IDLE, confirmed())).message).toMatch(/unreachable/);
  });

  it("shows no credentials when the answer is incomplete", async () => {
    signedInAs("MERCHANT");
    gatewayFetchMock.mockResolvedValue(new Response(JSON.stringify({ clientId: CREDENTIALS.clientId }), { status: 200 }));

    const state = await rotateCredentials(IDLE, confirmed());

    expect(state.status).toBe("error");
    expect(state.credentials).toBeUndefined();
  });
});
