import { describe, expect, it, vi } from "vitest";
import {
  addressSearchPath,
  normaliseAddressQuery,
  searchAddresses,
  suggestionToPick,
  type AddressSuggestion,
} from "./address-search";

const COLOMBO: AddressSuggestion = {
  label: "45, Galle Road, Colombo 03, Sri Lanka",
  latitude: 6.91,
  longitude: 79.851,
  district: "Colombo",
  zone: "WP",
};

function respond(status: number, body: unknown) {
  return vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": status < 300 ? "application/json" : "application/problem+json" },
  }));
}

describe("normaliseAddressQuery", () => {
  it("trims and collapses spaces", () => {
    expect(normaliseAddressQuery("  45   Galle\tRoad ")).toBe("45 Galle Road");
  });

  it("refuses a query too short or too long", () => {
    expect(normaliseAddressQuery(" ab ")).toBeUndefined();
    expect(normaliseAddressQuery("x".repeat(121))).toBeUndefined();
    expect(normaliseAddressQuery("x".repeat(120))).toHaveLength(120);
  });
});

describe("searchAddresses", () => {
  it("asks pricing-service through the BFF with the query encoded", async () => {
    const fetchImpl = respond(200, [COLOMBO]);

    const result = await searchAddresses(" 45 Galle Road, Colombo ", fetchImpl);

    expect(result).toEqual({ ok: true, suggestions: [COLOMBO] });
    expect(fetchImpl.mock.calls[0][0]).toBe(
      "/api/bff/api/v1/pricing/address-search?q=45+Galle+Road%2C+Colombo",
    );
    expect(addressSearchPath("a&b")).toBe("api/v1/pricing/address-search?q=a%26b");
  });

  it("says so when nothing in the service area matches", async () => {
    const result = await searchAddresses("Chennai Central", respond(200, []));

    expect(result.ok && result.suggestions).toEqual([]);
    expect(result.message).toContain("No matching place");
  });

  it("does not call the service for a query that is too short", async () => {
    const fetchImpl = respond(200, []);

    const result = await searchAddresses("ab", fetchImpl);

    expect(result).toEqual({ ok: false, message: "Type at least 3 characters of the address." });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it("tells the customer to use the map when search is off or the geocoder is down", async () => {
    const result = await searchAddresses("Kandy", respond(503, { status: 503, code: "ADDRESS_SEARCH_UNAVAILABLE" }));

    expect(result).toEqual({ ok: false, unavailable: true, message: "Address search is unavailable just now. Tap the map instead." });
  });

  it("explains a refused query and any other failure", async () => {
    const refused = await searchAddresses("Kandy", respond(400, { status: 400, code: "INVALID_ADDRESS_QUERY" }));
    expect(refused.ok).toBe(false);
    expect(refused.message).toContain("between 3 and 120");

    const broken = await searchAddresses("Kandy", vi.fn<typeof fetch>().mockRejectedValue(new TypeError("offline")));
    expect(broken).toEqual({ ok: false, message: "We could not search just now. Please try again, or tap the map." });
  });
});

describe("suggestionToPick", () => {
  it("gives the same point and district a tap on the map would", () => {
    expect(suggestionToPick(COLOMBO)).toEqual({
      point: { latitude: 6.91, longitude: 79.851 },
      located: { district: "Colombo", zoneCode: "WP" },
    });
  });
});
