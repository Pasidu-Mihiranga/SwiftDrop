import { describe, expect, it } from "vitest";
import {
  addressStateFromResponse,
  districtsFromZones,
  formatAddress,
  problemMessage,
  validateAddressInput,
} from "@/lib/addresses";

const HOME = { label: " Home ", line1: "No. 12, Temple Road", line2: "", city: "Kandy", district: "Kandy", postalCode: "20000" };

describe("validateAddressInput", () => {
  it("accepts a complete address and drops empty optional fields", () => {
    expect(validateAddressInput(HOME)).toEqual({
      ok: true,
      input: { label: "Home", line1: "No. 12, Temple Road", city: "Kandy", district: "Kandy", postalCode: "20000" },
    });
    expect(validateAddressInput({ ...HOME, postalCode: "", line2: "Near the temple", isDefault: "on" })).toEqual({
      ok: true,
      input: { label: "Home", line1: "No. 12, Temple Road", line2: "Near the temple", city: "Kandy", district: "Kandy", isDefault: true },
    });
  });

  it("names every missing required field", () => {
    const result = validateAddressInput({ label: "", line1: " ", line2: null, city: undefined, district: "", postalCode: "" });
    expect(result).toEqual({
      ok: false,
      fieldErrors: {
        label: "Give this address a name, e.g. Home.",
        line1: "Enter the street address.",
        city: "Enter the city.",
        district: "Enter the district.",
      },
    });
  });

  it("checks lengths and the postal code", () => {
    expect(validateAddressInput({ ...HOME, line1: "x".repeat(121) })).toEqual({
      ok: false,
      fieldErrors: { line1: "Use 120 characters or fewer." },
    });
    expect(validateAddressInput({ ...HOME, postalCode: "2000" })).toEqual({
      ok: false,
      fieldErrors: { postalCode: "Postal codes have five digits." },
    });
  });
});

describe("addressStateFromResponse", () => {
  it("confirms a save", () => {
    expect(addressStateFromResponse(201, {}, 7)).toEqual({ status: "saved", message: "Address saved.", savedAt: 7 });
    expect(addressStateFromResponse(200, {}, 8).status).toBe("saved");
  });

  it("maps API validation errors onto known fields only", () => {
    const state = addressStateFromResponse(400, { errors: [{ field: "postalCode" }, { field: "lat" }] });
    expect(state.fieldErrors).toEqual({ postalCode: "This value is not valid." });
  });

  it("explains failures in plain words", () => {
    expect(addressStateFromResponse(422, { code: "ADDRESS_LIMIT_REACHED" }).message).toMatch(/up to 10/);
    expect(problemMessage(409, "ADDRESS_BOOK_CHANGED")).toMatch(/another tab/);
    expect(problemMessage(404)).toMatch(/no longer exists/);
    expect(problemMessage(401)).toMatch(/session has expired/);
    expect(problemMessage(400)).toMatch(/not valid/);
    expect(problemMessage(503)).toMatch(/could not update/);
  });
});

describe("formatting and districts", () => {
  it("formats an address on one line", () => {
    expect(formatAddress({ line1: "No. 12, Temple Road", line2: null, city: "Kandy", district: "Kandy", postalCode: "20000" }))
      .toBe("No. 12, Temple Road, Kandy 20000 · Kandy District");
    expect(formatAddress({ line1: "45 Galle Road", line2: "Floor 3", city: "Colombo", district: "Colombo", postalCode: null }))
      .toBe("45 Galle Road, Floor 3, Colombo · Colombo District");
  });

  it("collects the districts of every zone, sorted and without duplicates", () => {
    expect(
      districtsFromZones([
        { code: "CP", districts: ["Matale", "Kandy"] },
        { code: "WP", districts: ["Colombo", "Kandy", " "] },
        { code: "X" },
      ]),
    ).toEqual(["Colombo", "Kandy", "Matale"]);
    expect(districtsFromZones(null)).toEqual([]);
    expect(districtsFromZones({ error: true })).toEqual([]);
  });
});
