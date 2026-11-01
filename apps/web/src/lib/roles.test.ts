import { describe, expect, it } from "vitest";
import { accessFor, adminNavigationFor, homeFor, navigationFor, rolesFromAccessToken } from "./roles";

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

  it("keeps pricing configuration to administrators even inside the shared admin area", () => {
    expect(accessFor("/admin/pricing/zones", ["ADMIN"])).toBe("allowed");
    expect(accessFor("/admin/pricing/rate-cards", ["ADMIN"])).toBe("allowed");
    expect(accessFor("/admin/pricing", ["DISPATCHER"])).toBe("forbidden");
    expect(accessFor("/admin/pricing/rate-cards", ["DISPATCHER"])).toBe("forbidden");
    // The rest of the operations area stays open to dispatchers.
    expect(accessFor("/admin/dispatch", ["DISPATCHER"])).toBe("allowed");
  });

  it("still asks an anonymous visitor to sign in before pricing configuration", () => {
    expect(accessFor("/admin/pricing/zones", undefined)).toBe("login-required");
  });

  it("leaves the public quote page open to everyone", () => {
    expect(accessFor("/quote", undefined)).toBe("public");
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

describe("adminNavigationFor", () => {
  it("offers pricing configuration only to administrators", () => {
    expect(adminNavigationFor(["ADMIN"]).map((i) => i.href)).toEqual([
      "/admin",
      "/admin/dispatch",
      "/admin/pricing/zones",
      "/admin/pricing/rate-cards",
      "/admin/pricing/remote-areas",
      "/admin/pricing/merchants",
    ]);
    expect(adminNavigationFor(["DISPATCHER"]).map((i) => i.href)).toEqual(["/admin", "/admin/dispatch"]);
  });

  it("offers nothing to roles without operations access", () => {
    expect(adminNavigationFor(["CUSTOMER"])).toEqual([]);
    expect(adminNavigationFor([])).toEqual([]);
  });

  it("does not duplicate links for someone who is both", () => {
    expect(adminNavigationFor(["DISPATCHER", "ADMIN"]).map((i) => i.href)).toEqual([
      "/admin",
      "/admin/dispatch",
      "/admin/pricing/zones",
      "/admin/pricing/rate-cards",
      "/admin/pricing/remote-areas",
      "/admin/pricing/merchants",
    ]);
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
