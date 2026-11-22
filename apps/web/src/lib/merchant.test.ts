import { describe, expect, it } from "vitest";
import {
  formatAddress,
  formatDateTime,
  isMerchantStatus,
  parseQueueQuery,
  registrationBody,
  statusLabel,
  statusPillClass,
} from "./merchant";

function form(entries: Record<string, string>): FormData {
  const data = new FormData();
  for (const [key, value] of Object.entries(entries)) data.set(key, value);
  return data;
}

const COMPLETE = {
  businessName: " Lanka Books ",
  brNumber: "PV 12345",
  contactEmail: "lankabooks@example.com",
  contactPhone: "077 123-4567",
  website: "",
  "pickupAddress.line1": "12 Temple Road",
  "pickupAddress.line2": "",
  "pickupAddress.city": "Kandy",
  "pickupAddress.district": "Kandy",
  "pickupAddress.postalCode": "20000",
};

describe("registrationBody", () => {
  it("builds the request merchant-service expects, trimming text and dropping empty optional fields", () => {
    const result = registrationBody(form(COMPLETE));

    expect(result).toEqual({
      ok: true,
      body: {
        businessName: "Lanka Books",
        brNumber: "PV 12345",
        contactEmail: "lankabooks@example.com",
        contactPhone: "0771234567",
        website: undefined,
        pickupAddress: { line1: "12 Temple Road", line2: undefined, city: "Kandy", district: "Kandy", postalCode: "20000" },
      },
    });
  });

  it("names every missing required field, using the service's field names", () => {
    const result = registrationBody(form({ ...COMPLETE, brNumber: " ", "pickupAddress.district": "" }));

    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(Object.keys(result.errors)).toEqual(["brNumber", "pickupAddress.district"]);
    expect(result.errors.brNumber).toBe("Business registration number is required");
  });

  it("refuses an empty form", () => {
    const result = registrationBody(new FormData());

    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(Object.keys(result.errors)).toHaveLength(7);
  });
});

describe("parseQueueQuery", () => {
  it("reads a valid status and page", () => {
    expect(parseQueueQuery({ status: "APPROVED", page: "2" })).toEqual({ status: "APPROVED", page: 2 });
  });

  it("falls back to the first page of pending applications for anything unexpected", () => {
    expect(parseQueueQuery({})).toEqual({ status: "PENDING", page: 0 });
    expect(parseQueueQuery({ status: "approved", page: "-1" })).toEqual({ status: "PENDING", page: 0 });
    expect(parseQueueQuery({ status: "DELETED", page: "two" })).toEqual({ status: "PENDING", page: 0 });
  });
});

describe("status presentation", () => {
  it("labels every status and uses only design-system pill classes", () => {
    expect(statusLabel("PENDING")).toBe("Waiting for review");
    expect(statusLabel("REJECTED")).toBe("Rejected");
    expect(statusPillClass("APPROVED")).toBe("status-pill-success");
    expect(statusPillClass("PENDING")).toBe("status-pill-warning");
    expect(statusPillClass("SUSPENDED")).toBe("status-pill-neutral");
  });

  it("recognises only the four lifecycle statuses", () => {
    expect(isMerchantStatus("SUSPENDED")).toBe(true);
    expect(isMerchantStatus("suspended")).toBe(false);
    expect(isMerchantStatus(undefined)).toBe(false);
  });
});

describe("formatting", () => {
  it("joins the parts of an address that are present", () => {
    expect(formatAddress({ line1: "12 Temple Road", city: "Kandy", district: "Kandy" })).toBe("12 Temple Road, Kandy, Kandy");
    expect(formatAddress({ line1: "1 A St", line2: "Level 2", city: "Galle", district: "Galle", postalCode: "80000" }))
      .toBe("1 A St, Level 2, Galle, Galle, 80000");
  });

  it("shows times in Sri Lanka and ignores a missing or broken value", () => {
    expect(formatDateTime("2026-11-19T08:00:00Z")).toContain("1:30");
    expect(formatDateTime(undefined)).toBe("");
    expect(formatDateTime("not a date")).toBe("");
  });
});
