import { describe, expect, it } from "vitest";
import { accessFor, homeFor, navigationFor, rolesFromAccessToken } from "./roles";

const token = (claims: object) => `h.${Buffer.from(JSON.stringify(claims)).toString("base64url")}.s`;

describe("accessFor", () => {
  it("keeps public pages public", () => {
    expect(accessFor("/", undefined)).toBe("public");
    expect(accessFor("/track/SD261020ABCDEFG", undefined)).toBe("public");
    expect(accessFor("/login", undefined)).toBe("public");
  });

  it("requires login for role areas and unknown pages", () => {
    expect(accessFor("/customer", undefined)).toBe("login-required");
    expect(accessFor("/settings", undefined)).toBe("login-required");
  });

  it("allows matching roles and forbids others", () => {
    expect(accessFor("/courier/runs", ["COURIER"])).toBe("allowed");
    expect(accessFor("/courier", ["CUSTOMER"])).toBe("forbidden");
    expect(accessFor("/admin/dispatch", ["DISPATCHER"])).toBe("allowed");
    expect(accessFor("/admin", ["MERCHANT"])).toBe("forbidden");
  });

  it("lets any signed-in user apply to become a merchant", () => {
    expect(accessFor("/merchant/register", ["CUSTOMER"])).toBe("allowed");
    expect(accessFor("/merchant/orders", ["CUSTOMER"])).toBe("forbidden");
  });

  it("does not confuse prefixes", () => {
    expect(accessFor("/couriers-info", ["CUSTOMER"])).toBe("allowed");
  });
});

describe("homeFor and navigationFor", () => {
  it("sends users to their most privileged area", () => {
    expect(homeFor(["CUSTOMER", "ADMIN"])).toBe("/admin");
    expect(homeFor(["DISPATCHER"])).toBe("/admin/dispatch");
    expect(homeFor(["COURIER", "CUSTOMER"])).toBe("/courier");
    expect(homeFor(["MERCHANT"])).toBe("/merchant");
    expect(homeFor([])).toBe("/customer");
  });

  it("builds role-aware navigation", () => {
    expect(navigationFor(["CUSTOMER", "MERCHANT"]).map((i) => i.href)).toEqual(["/customer", "/merchant"]);
    expect(navigationFor(["ADMIN"]).map((i) => i.href)).toEqual(["/admin"]);
  });
});

describe("rolesFromAccessToken", () => {
  it("keeps only SwiftDrop roles", () => {
    expect(rolesFromAccessToken(token({ realm_access: { roles: ["COURIER", "offline_access"] } }))).toEqual(["COURIER"]);
  });

  it("is safe with malformed tokens", () => {
    expect(rolesFromAccessToken("not-a-jwt")).toEqual([]);
    expect(rolesFromAccessToken(token({}))).toEqual([]);
  });
});
