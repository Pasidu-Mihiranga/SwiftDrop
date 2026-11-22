import { describe, expect, it } from "vitest";
import {
  adminProblemMessage,
  filtersFrom,
  pageHref,
  searchQuery,
  staffRolesOf,
  staffStateFromResponse,
  validateStaffInput,
  visibleRoles,
} from "@/lib/admin-users";

const ZONE = "11111111-1111-4111-8111-111111111111";
const BASE = { email: " Kasun@X.lk ", fullName: " Kasun Silva ", phone: "", role: "courier", vehicleType: "", homeZoneId: "", maxDailyCapacity: "" };

describe("filtersFrom, searchQuery and pageHref", () => {
  it("reads the URL and ignores anything unexpected", () => {
    expect(filtersFrom({ search: "  kasun ", role: "courier", status: "disabled", page: "2" })).toEqual({
      search: "kasun",
      role: "COURIER",
      status: "disabled",
      page: 2,
    });
    expect(filtersFrom({ role: "OWNER", status: "maybe", page: "-3" })).toEqual({ search: "", role: "", status: "", page: 0 });
    expect(filtersFrom({ search: ["first", "second"] }).search).toBe("first");
  });

  it("builds the user-service query and the page links", () => {
    const filters = { search: "kasun", role: "COURIER", status: "active" as const, page: 1 };
    expect(searchQuery(filters)).toBe("search=kasun&role=COURIER&enabled=true&page=1&size=20");
    expect(searchQuery({ search: "", role: "", status: "", page: 0 })).toBe("page=0&size=20");
    expect(pageHref(filters, 2)).toBe("/admin/users?search=kasun&role=COURIER&status=active&page=2");
    expect(pageHref({ search: "", role: "", status: "", page: 3 }, 0)).toBe("/admin/users");
  });
});

describe("validateStaffInput", () => {
  it("accepts a dispatcher with just e-mail, name and role", () => {
    expect(validateStaffInput({ ...BASE, role: "DISPATCHER" })).toEqual({
      ok: true,
      input: { email: "kasun@x.lk", fullName: "Kasun Silva", role: "DISPATCHER" },
    });
  });

  it("accepts a courier with phone and complete dispatch details", () => {
    expect(
      validateStaffInput({ ...BASE, phone: "077 123 4567", vehicleType: "bike", homeZoneId: ZONE, maxDailyCapacity: "25" }),
    ).toEqual({
      ok: true,
      input: {
        email: "kasun@x.lk",
        fullName: "Kasun Silva",
        phone: "0771234567",
        role: "COURIER",
        vehicleType: "BIKE",
        homeZoneId: ZONE,
        maxDailyCapacity: 25,
      },
    });
  });

  it("ignores courier details for other roles", () => {
    const result = validateStaffInput({ ...BASE, role: "ADMIN", vehicleType: "BIKE" });
    expect(result).toEqual({ ok: true, input: { email: "kasun@x.lk", fullName: "Kasun Silva", role: "ADMIN" } });
  });

  it("names every problem", () => {
    expect(validateStaffInput({ email: "nope", fullName: " ", phone: "0112345678", role: "CUSTOMER", vehicleType: "", homeZoneId: "", maxDailyCapacity: "" }))
      .toEqual({
        ok: false,
        fieldErrors: {
          email: "Enter a valid e-mail address.",
          fullName: "Enter the person's full name.",
          phone: "Enter a Sri Lankan mobile number, e.g. 0771234567.",
          role: "Choose courier, dispatcher or admin.",
        },
      });
    expect(validateStaffInput({ ...BASE, fullName: "x".repeat(101) })).toMatchObject({ ok: false, fieldErrors: { fullName: "Use 100 characters or fewer." } });
  });

  it("wants all three courier details or none", () => {
    expect(validateStaffInput({ ...BASE, vehicleType: "BIKE" })).toEqual({
      ok: false,
      fieldErrors: { homeZoneId: "Choose the home zone.", maxDailyCapacity: "Use a whole number from 1 to 200." },
    });
    expect(validateStaffInput({ ...BASE, vehicleType: "PLANE", homeZoneId: ZONE, maxDailyCapacity: "500" })).toEqual({
      ok: false,
      fieldErrors: { vehicleType: "Choose the vehicle.", maxDailyCapacity: "Use a whole number from 1 to 200." },
    });
  });
});

describe("staffStateFromResponse and messages", () => {
  it("confirms a creation and says whether the invitation went out", () => {
    expect(staffStateFromResponse(201, { user: { email: "k@x.lk" }, invitationSent: true }, 5)).toEqual({
      status: "created",
      message: "Account created. A set-your-password e-mail was sent to k@x.lk.",
      createdAt: 5,
    });
    expect(staffStateFromResponse(201, { user: {}, invitationSent: false }).message).toMatch(/could not be sent/);
  });

  it("maps validation errors to known fields and explains the rest", () => {
    expect(staffStateFromResponse(400, { errors: [{ field: "email" }, { field: "other" }] }).fieldErrors).toEqual({
      email: "This value is not valid.",
    });
    expect(staffStateFromResponse(409, { code: "EMAIL_ALREADY_REGISTERED" }).message).toMatch(/already exists/);
    expect(adminProblemMessage(409, "EMAIL_ALREADY_LINKED")).toMatch(/already exists/);
    expect(adminProblemMessage(422, "CANNOT_DISABLE_YOURSELF")).toMatch(/own account/);
    expect(adminProblemMessage(422, "CANNOT_REMOVE_OWN_ADMIN")).toMatch(/own admin role/);
    expect(adminProblemMessage(400, "COURIER_DETAILS_INCOMPLETE")).toMatch(/all three/);
    expect(adminProblemMessage(503, "IDENTITY_PROVIDER_UNAVAILABLE")).toMatch(/not reachable/);
    expect(adminProblemMessage(404)).toMatch(/no longer exists/);
    expect(adminProblemMessage(401)).toMatch(/session has expired/);
    expect(adminProblemMessage(403)).toMatch(/Only administrators/);
    expect(adminProblemMessage(500)).toMatch(/could not update/);
  });
});

describe("roles", () => {
  it("picks out staff roles and hides the default customer role when others exist", () => {
    expect(staffRolesOf({ roles: ["ADMIN", "CUSTOMER", "COURIER"] })).toEqual(["COURIER", "ADMIN"]);
    expect(visibleRoles({ roles: ["CUSTOMER", "DISPATCHER"] })).toEqual(["DISPATCHER"]);
    expect(visibleRoles({ roles: ["CUSTOMER"] })).toEqual(["CUSTOMER"]);
  });
});
