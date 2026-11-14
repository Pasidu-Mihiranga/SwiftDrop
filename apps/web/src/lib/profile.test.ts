import { describe, expect, it } from "vitest";
import { formatDate, formatPhone, stateFromResponse, validateProfileInput, type Profile } from "@/lib/profile";

const PROFILE: Profile = {
  id: "sub-1",
  email: "nimal@example.lk",
  fullName: "Nimal Perera",
  phone: "+94771234567",
  status: "ACTIVE",
  roles: ["CUSTOMER"],
  createdAt: "2026-11-13T08:58:07Z",
  updatedAt: "2026-11-13T08:58:07Z",
};

describe("validateProfileInput", () => {
  it("accepts a name with a local or international mobile number", () => {
    expect(validateProfileInput(" Nimal Perera ", "077 123 4567")).toEqual({
      ok: true,
      input: { fullName: "Nimal Perera", phone: "0771234567" },
    });
    expect(validateProfileInput("Nimal", "+94771234567")).toEqual({
      ok: true,
      input: { fullName: "Nimal", phone: "+94771234567" },
    });
  });

  it("leaves the phone out when it is empty, which removes it", () => {
    expect(validateProfileInput("Nimal", "")).toEqual({ ok: true, input: { fullName: "Nimal" } });
    expect(validateProfileInput("Nimal", null)).toEqual({ ok: true, input: { fullName: "Nimal" } });
  });

  it("rejects a blank or too long name", () => {
    expect(validateProfileInput("   ", "")).toEqual({ ok: false, fieldErrors: { fullName: "Enter your full name." } });
    expect(validateProfileInput(undefined, "")).toMatchObject({ ok: false });
    expect(validateProfileInput("x".repeat(101), "")).toEqual({
      ok: false,
      fieldErrors: { fullName: "Use 100 characters or fewer." },
    });
  });

  it("rejects numbers that are not Sri Lankan mobiles", () => {
    for (const phone of ["0112345678", "077123456", "94771234567", "abc"]) {
      expect(validateProfileInput("Nimal", phone)).toEqual({
        ok: false,
        fieldErrors: { phone: "Enter a Sri Lankan mobile number, e.g. 0771234567." },
      });
    }
  });
});

describe("stateFromResponse", () => {
  it("returns the stored profile after a save", () => {
    expect(stateFromResponse(200, PROFILE, 42)).toEqual({
      status: "saved",
      message: "Profile saved.",
      profile: PROFILE,
      savedAt: 42,
    });
  });

  it("maps API validation errors onto the matching fields", () => {
    const state = stateFromResponse(400, {
      code: "VALIDATION_FAILED",
      errors: [
        { field: "phone", message: "must be a Sri Lankan mobile number such as 0771234567 or +94771234567" },
        { field: "fullName" },
        { field: "unknown", message: "ignored" },
      ],
    });
    expect(state.status).toBe("error");
    expect(state.fieldErrors).toEqual({
      phone: "Must be a Sri Lankan mobile number such as 0771234567 or +94771234567.",
      fullName: "This value is not valid.",
    });
  });

  it("explains every other failure in plain words", () => {
    expect(stateFromResponse(401, null).message).toMatch(/session has expired/);
    expect(stateFromResponse(403, null).message).toMatch(/cannot have a profile/);
    expect(stateFromResponse(409, { code: "EMAIL_ALREADY_LINKED" }).message).toMatch(/already linked/);
    expect(stateFromResponse(400, { code: "VALIDATION_FAILED" }).message).toMatch(/could not save/);
    expect(stateFromResponse(503, null).message).toMatch(/could not save/);
  });
});

describe("formatting", () => {
  it("groups an E.164 number for reading", () => {
    expect(formatPhone("+94771234567")).toBe("+94 77 123 4567");
    expect(formatPhone(null)).toBe("Not added");
    expect(formatPhone("+15551234567")).toBe("+15551234567");
  });

  it("shows dates in Sri Lankan time", () => {
    expect(formatDate("2026-11-13T20:00:00Z")).toBe("14 Nov 2026");
    expect(formatDate("not a date")).toBe("");
  });
});
