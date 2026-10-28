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
  assignDistrict,
  closeRateCard,
  createRateCard,
  createZone,
  updateSizeClass,
  updateZone,
} from "@/lib/pricing-actions";

function sizeForm(): FormData {
  const form = new FormData();
  form.set("code", "M");
  form.set("multiplier", "1.25");
  return form;
}

function form(entries: Record<string, string>): FormData {
  const data = new FormData();
  for (const [key, value] of Object.entries(entries)) data.set(key, value);
  return data;
}

function rateCardForm(overrides: Record<string, string> = {}): FormData {
  return form({
    fromZone: "wp",
    toZone: "cp",
    baseFee: "475",
    perKgFee: "78",
    expressMultiplier: "1.5",
    codFeePct: "0.015",
    effectiveFrom: "2026-11-01",
    ...overrides,
  });
}

function asAdmin(): void {
  authMock.mockResolvedValue({ user: { roles: ["ADMIN"] } });
}

function respondOk(): void {
  gatewayFetchMock.mockResolvedValue(new Response(null, { status: 204 }));
}

/** A problem+json failure, the shape swiftdrop-common-web returns. */
function respondProblem(status: number, body: Record<string, unknown>): void {
  gatewayFetchMock.mockResolvedValue(
    new Response(JSON.stringify(body), {
      status,
      headers: { "content-type": "application/problem+json" },
    }),
  );
}

const IDLE = { status: "idle" } as const;

describe("admin pricing actions", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("rejects a non-admin session before contacting the gateway", async () => {
    authMock.mockResolvedValue({ user: { roles: ["DISPATCHER"] } });

    const result = await updateSizeClass({ status: "idle" }, sizeForm());

    expect(result).toEqual({ status: "error", message: "You do not have permission to change pricing." });
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("sends an admin's update and refreshes pricing views", async () => {
    authMock.mockResolvedValue({ user: { roles: ["ADMIN"] } });
    gatewayFetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    const result = await updateSizeClass({ status: "idle" }, sizeForm());

    expect(result).toEqual({ status: "ok", message: "Size M updated." });
    expect(gatewayFetchMock).toHaveBeenCalledWith(
      "/api/v1/admin/pricing/size-classes/M",
      expect.objectContaining({ method: "PUT", body: JSON.stringify({ multiplier: 1.25 }) }),
    );
    expect(revalidatePathMock).toHaveBeenCalledWith("/admin/pricing/zones");
    expect(revalidatePathMock).toHaveBeenCalledWith("/admin/pricing/rate-cards");
  });

  it("treats an expired session as a refusal", async () => {
    authMock.mockResolvedValue({ user: { roles: ["ADMIN"] }, error: "RefreshAccessTokenError" });

    const result = await updateSizeClass(IDLE, sizeForm());

    expect(result.status).toBe("error");
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("treats an anonymous caller as a refusal", async () => {
    authMock.mockResolvedValue(null);

    const result = await updateSizeClass(IDLE, sizeForm());

    expect(result.status).toBe("error");
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("reports the service being unreachable instead of throwing", async () => {
    asAdmin();
    gatewayFetchMock.mockRejectedValue(new Error("ECONNREFUSED"));

    const result = await updateSizeClass(IDLE, sizeForm());

    expect(result).toEqual({
      status: "error",
      message: "The pricing service is unreachable. Please try again.",
    });
    expect(revalidatePathMock).not.toHaveBeenCalled();
  });

  it("surfaces the problem+json detail the service returned", async () => {
    asAdmin();
    respondProblem(409, { status: 409, code: "RATE_CARD_OVERLAP", detail: "That lane already has a card." });

    const result = await createRateCard(IDLE, rateCardForm());

    expect(result).toEqual({ status: "error", message: "That lane already has a card.", errors: undefined });
  });

  it("falls back to the problem code when there is no detail", async () => {
    asAdmin();
    respondProblem(422, { status: 422, code: "ZONE_CODE_TAKEN" });

    const result = await createZone(IDLE, form({ code: "WP", name: "Western" }));

    expect(result.status).toBe("error");
    expect(result.message).toContain("ZONE_CODE_TAKEN");
  });

  it("copes with a failure whose body is not JSON", async () => {
    asAdmin();
    gatewayFetchMock.mockResolvedValue(new Response("502 Bad Gateway", { status: 502 }));

    const result = await createZone(IDLE, form({ code: "WP", name: "Western" }));

    expect(result.status).toBe("error");
    expect(result.message).toContain("UNEXPECTED_ERROR");
  });

  it("maps field-level validation errors for the form to list", async () => {
    asAdmin();
    respondProblem(400, {
      status: 400,
      code: "VALIDATION_FAILED",
      detail: "Check the fields.",
      errors: [
        { field: "baseFee", message: "must not be negative" },
        { field: "effectiveFrom", message: "is required" },
      ],
    });

    const result = await createRateCard(IDLE, rateCardForm());

    expect(result.errors).toEqual({ baseFee: "must not be negative", effectiveFrom: "is required" });
  });

  describe("assignDistrict", () => {
    it("assigns a district to a zone", async () => {
      asAdmin();
      respondOk();

      const result = await assignDistrict(IDLE, form({ district: "Nuwara Eliya", zoneCode: "CP" }));

      expect(result).toEqual({ status: "ok", message: "Nuwara Eliya now belongs to CP." });
      // The district is part of the path, so it has to be encoded.
      expect(gatewayFetchMock).toHaveBeenCalledWith(
        "/api/v1/admin/pricing/districts/Nuwara%20Eliya",
        expect.objectContaining({ method: "PUT", body: JSON.stringify({ zoneCode: "CP" }) }),
      );
    });

    it("asks for both fields before calling the service", async () => {
      asAdmin();

      const result = await assignDistrict(IDLE, form({ district: " ", zoneCode: "CP" }));

      expect(result).toEqual({ status: "error", message: "Pick a district and a zone." });
      expect(gatewayFetchMock).not.toHaveBeenCalled();
    });
  });

  describe("createZone", () => {
    it("upper-cases the code and creates the zone active", async () => {
      asAdmin();
      respondOk();

      const result = await createZone(IDLE, form({ code: " sgp ", name: "Sabaragamuwa" }));

      expect(result).toEqual({ status: "ok", message: "Zone SGP created." });
      expect(gatewayFetchMock).toHaveBeenCalledWith(
        "/api/v1/admin/pricing/zones",
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({ code: "SGP", name: "Sabaragamuwa", active: true }),
        }),
      );
    });

    it("requires a code and a name", async () => {
      asAdmin();

      expect(await createZone(IDLE, form({ code: "WP", name: "" }))).toEqual({
        status: "error",
        message: "A zone needs a code and a name.",
      });
      expect(gatewayFetchMock).not.toHaveBeenCalled();
    });
  });

  describe("updateZone", () => {
    it("sends the name and the checkbox state", async () => {
      asAdmin();
      respondOk();

      const result = await updateZone(
        IDLE,
        form({ zoneId: "11111111-2222-3333-4444-555555555555", name: "Western", active: "on" }),
      );

      expect(result).toEqual({ status: "ok", message: "Zone Western updated." });
      expect(gatewayFetchMock).toHaveBeenCalledWith(
        "/api/v1/admin/pricing/zones/11111111-2222-3333-4444-555555555555",
        expect.objectContaining({ body: JSON.stringify({ name: "Western", active: true }) }),
      );
    });

    it("treats a missing checkbox as inactive rather than unchanged", async () => {
      asAdmin();
      respondOk();

      await updateZone(IDLE, form({ zoneId: "zone-1", name: "Western" }));

      expect(gatewayFetchMock).toHaveBeenCalledWith(
        "/api/v1/admin/pricing/zones/zone-1",
        expect.objectContaining({ body: JSON.stringify({ name: "Western", active: false }) }),
      );
    });

    it("requires an id and a name", async () => {
      asAdmin();

      expect((await updateZone(IDLE, form({ zoneId: "", name: "Western" }))).status).toBe("error");
      expect(gatewayFetchMock).not.toHaveBeenCalled();
    });
  });

  describe("createRateCard", () => {
    it("sends numbers, upper-cased zones, and a null open end date", async () => {
      asAdmin();
      respondOk();

      const result = await createRateCard(IDLE, rateCardForm({ effectiveTo: "  " }));

      expect(result).toEqual({
        status: "ok",
        message: "New rate for WP to CP takes effect 2026-11-01.",
      });
      expect(gatewayFetchMock).toHaveBeenCalledWith(
        "/api/v1/admin/pricing/rate-cards",
        expect.objectContaining({
          body: JSON.stringify({
            fromZone: "WP",
            toZone: "CP",
            baseFee: 475,
            perKgFee: 78,
            expressMultiplier: 1.5,
            codFeePct: 0.015,
            effectiveFrom: "2026-11-01",
            effectiveTo: null,
          }),
        }),
      );
    });

    it("keeps a closed end date when one is given", async () => {
      asAdmin();
      respondOk();

      await createRateCard(IDLE, rateCardForm({ effectiveTo: "2026-12-31" }));

      expect(gatewayFetchMock.mock.calls[0][1].body).toContain('"effectiveTo":"2026-12-31"');
    });

    it.each(["baseFee", "perKgFee", "expressMultiplier", "codFeePct"])(
      "requires %s before calling the service",
      async (field) => {
        asAdmin();

        const result = await createRateCard(IDLE, rateCardForm({ [field]: "" }));

        expect(result.status).toBe("error");
        expect(result.errors).toEqual({ [field]: "required" });
        expect(gatewayFetchMock).not.toHaveBeenCalled();
      },
    );

    it("requires both zones and a start date", async () => {
      asAdmin();

      const result = await createRateCard(IDLE, rateCardForm({ effectiveFrom: "" }));

      expect(result).toEqual({ status: "error", message: "Choose both zones and a start date." });
      expect(gatewayFetchMock).not.toHaveBeenCalled();
    });

    it("rejects a fee that is not a number", async () => {
      asAdmin();

      const result = await createRateCard(IDLE, rateCardForm({ baseFee: "free" }));

      expect(result.status).toBe("error");
      expect(result.errors).toEqual({ baseFee: "must be a number" });
      expect(gatewayFetchMock).not.toHaveBeenCalled();
    });
  });

  describe("closeRateCard", () => {
    it("closes the card from the chosen date", async () => {
      asAdmin();
      respondOk();

      const result = await closeRateCard(IDLE, form({ rateCardId: "card-1", effectiveTo: "2026-11-01" }));

      expect(result).toEqual({ status: "ok", message: "Rate closed from 2026-11-01." });
      expect(gatewayFetchMock).toHaveBeenCalledWith(
        "/api/v1/admin/pricing/rate-cards/card-1/closure",
        expect.objectContaining({ method: "PUT", body: JSON.stringify({ effectiveTo: "2026-11-01" }) }),
      );
    });

    it("requires the date", async () => {
      asAdmin();

      const result = await closeRateCard(IDLE, form({ rateCardId: "card-1", effectiveTo: "" }));

      expect(result.status).toBe("error");
      expect(gatewayFetchMock).not.toHaveBeenCalled();
    });
  });

  describe("updateSizeClass", () => {
    it("rejects a multiplier that is not a number", async () => {
      asAdmin();

      const result = await updateSizeClass(IDLE, form({ code: "M", multiplier: "big" }));

      expect(result).toEqual({
        status: "error",
        message: "A size class needs a numeric multiplier.",
      });
      expect(gatewayFetchMock).not.toHaveBeenCalled();
    });
  });
});
