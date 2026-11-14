import { describe, expect, it, vi } from "vitest";
import { BffError, bffFetch, fieldErrors, problemMessage } from "./bff";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/problem+json" },
  });
}

describe("bffFetch", () => {
  it("calls the proxy with the versioned path and returns the parsed body", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(200, { quoteId: "q-1" }));

    const result = await bffFetch<{ quoteId: string }>("api/v1/pricing/zones", {}, fetchImpl);

    expect(result).toEqual({ quoteId: "q-1" });
    expect(fetchImpl).toHaveBeenCalledWith("/api/bff/api/v1/pricing/zones", expect.objectContaining({ method: "GET" }));
  });

  it("tolerates a leading slash on the path", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(200, {}));

    await bffFetch("/api/v1/pricing/zones", {}, fetchImpl);

    expect(fetchImpl.mock.calls[0][0]).toBe("/api/bff/api/v1/pricing/zones");
  });

  it("refuses a path the proxy would reject anyway", async () => {
    const fetchImpl = vi.fn();

    await expect(bffFetch("pricing/zones", {}, fetchImpl)).rejects.toThrow(/must start with/);
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it("sends a JSON body only when there is one", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(200, {}));

    await bffFetch("api/v1/pricing/quotes", { method: "POST", body: { weightKg: 2 } }, fetchImpl);

    const init = fetchImpl.mock.calls[0][1];
    expect(init.body).toBe(JSON.stringify({ weightKg: 2 }));
    expect(init.headers).toEqual({ "content-type": "application/json" });
  });

  it("does not set a content type on a plain read", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(200, {}));

    await bffFetch("api/v1/pricing/zones", {}, fetchImpl);

    expect(fetchImpl.mock.calls[0][1].headers).toEqual({});
    expect(fetchImpl.mock.calls[0][1].body).toBeUndefined();
  });

  it("turns a problem response into a BffError carrying the code", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      jsonResponse(422, { status: 422, code: "NO_RATE_CARD", detail: "No rate card is in force for WP to CP" }),
    );

    await expect(bffFetch("api/v1/pricing/quotes", { method: "POST", body: {} }, fetchImpl)).rejects.toSatisfy(
      (error: unknown) => error instanceof BffError && error.code === "NO_RATE_CARD",
    );
  });

  it("still fails cleanly when the error body is not JSON", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response("<html>502</html>", { status: 502 }));

    const error = await bffFetch("api/v1/pricing/zones", {}, fetchImpl).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(BffError);
    expect((error as BffError).problem).toMatchObject({ status: 502, code: "UNEXPECTED_ERROR" });
  });

  it("reports an unreachable service rather than leaking the network error", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new TypeError("Failed to fetch"));

    const error = await bffFetch("api/v1/pricing/zones", {}, fetchImpl).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(BffError);
    expect((error as BffError).code).toBe("SERVICE_UNREACHABLE");
  });

  it("lets an abort through so a superseded request is not shown as a failure", async () => {
    const aborted = new DOMException("The operation was aborted.", "AbortError");
    const fetchImpl = vi.fn().mockRejectedValue(aborted);

    await expect(bffFetch("api/v1/pricing/zones", {}, fetchImpl)).rejects.toBe(aborted);
  });

  it("returns nothing for an empty success response", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));

    await expect(bffFetch("api/v1/pricing/zones", { method: "DELETE" }, fetchImpl)).resolves.toBeUndefined();
  });
});

describe("problemMessage", () => {
  const problem = (code: string, status = 400, detail?: string) =>
    new BffError({ status, code, detail });

  it("explains the pricing failures a customer can actually hit", () => {
    expect(problemMessage(problem("NO_RATE_CARD", 422))).toMatch(/no published rate/i);
    expect(problemMessage(problem("DISTRICT_NOT_SERVICED", 422))).toMatch(/do not deliver/i);
    expect(problemMessage(problem("QUOTE_NOT_FOUND", 404))).toMatch(/expired/i);
    expect(problemMessage(problem("SIZE_CLASS_WEIGHT_EXCEEDED", 422))).toMatch(/too heavy/i);
  });

  it("keeps server trouble vague instead of leaking internals", () => {
    expect(problemMessage(problem("SOME_INTERNAL_THING", 500, "NullPointerException at line 42")))
      .toMatch(/having trouble/i);
  });

  it("passes through a useful client-side detail", () => {
    expect(problemMessage(problem("ODD_BUT_FINE", 400, "Weight must be positive"))).toBe("Weight must be positive");
  });

  it("has a neutral answer for anything that is not a BffError", () => {
    expect(problemMessage(new Error("boom"))).toMatch(/something went wrong/i);
    expect(problemMessage(undefined)).toMatch(/something went wrong/i);
  });
});

describe("fieldErrors", () => {
  it("keys validation messages by field", () => {
    const error = new BffError({
      status: 400,
      code: "VALIDATION_FAILED",
      errors: [{ field: "weightKg", message: "must be greater than 0" }],
    });

    expect(fieldErrors(error)).toEqual({ weightKg: "must be greater than 0" });
  });

  it("is empty when there is nothing field-specific", () => {
    expect(fieldErrors(new BffError({ status: 500, code: "BOOM" }))).toEqual({});
    expect(fieldErrors(new Error("boom"))).toEqual({});
  });
});
