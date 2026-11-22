import { describe, expect, it } from "vitest";
import {
  extractParcelPageMeta,
  formatMoney,
  isValidSriLankanPhone,
  serviceLevelDescription,
  sizeClassDescription,
  statusBadgeClass,
  statusLabel,
} from "./parcels";

describe("parcels helper library", () => {
  describe("Sri Lankan phone validation", () => {
    it("accepts valid local format starting with 07", () => {
      expect(isValidSriLankanPhone("0712345678")).toBe(true);
      expect(isValidSriLankanPhone("0779876543")).toBe(true);
      expect(isValidSriLankanPhone("0701122334")).toBe(true);
    });

    it("accepts valid international format starting with +947", () => {
      expect(isValidSriLankanPhone("+94712345678")).toBe(true);
      expect(isValidSriLankanPhone("+94779876543")).toBe(true);
    });

    it("rejects non-Sri Lankan or landline numbers", () => {
      expect(isValidSriLankanPhone("0112345678")).toBe(false); // Colombo landline
      expect(isValidSriLankanPhone("071234567")).toBe(false); // too short
      expect(isValidSriLankanPhone("07123456789")).toBe(false); // too long
      expect(isValidSriLankanPhone("+1234567890")).toBe(false); // US format
      expect(isValidSriLankanPhone("abc")).toBe(false);
      expect(isValidSriLankanPhone("")).toBe(false);
    });
  });

  describe("formatMoney", () => {
    it("formats currency with 2 decimal places and thousands separators", () => {
      expect(formatMoney(631)).toBe("LKR 631.00");
      expect(formatMoney(1250.5)).toBe("LKR 1,250.50");
      expect(formatMoney(0)).toBe("LKR 0.00");
    });

    it("handles non-finite numbers safely", () => {
      expect(formatMoney(NaN)).toBe("");
      expect(formatMoney(Infinity)).toBe("");
    });
  });

  describe("statusLabel and statusBadgeClass", () => {
    it("returns correct labels for all statuses", () => {
      expect(statusLabel("CREATED")).toBe("Booking Created");
      expect(statusLabel("PICKED_UP")).toBe("Picked Up");
      expect(statusLabel("IN_TRANSIT")).toBe("In Transit");
      expect(statusLabel("OUT_FOR_DELIVERY")).toBe("Out for Delivery");
      expect(statusLabel("DELIVERED")).toBe("Delivered");
      expect(statusLabel("FAILED")).toBe("Delivery Failed");
      expect(statusLabel("CANCELLED")).toBe("Cancelled");
    });

    it("returns appropriate badge CSS classes", () => {
      expect(statusBadgeClass("CREATED")).toContain("text-blue-700");
      expect(statusBadgeClass("DELIVERED")).toContain("text-emerald-700");
      expect(statusBadgeClass("FAILED")).toContain("text-rose-700");
      expect(statusBadgeClass("CANCELLED")).toContain("text-rose-700");
    });
  });

  describe("descriptions", () => {
    it("describes size classes", () => {
      expect(sizeClassDescription("S")).toContain("Small");
      expect(sizeClassDescription("XL")).toContain("Extra Large");
    });

    it("describes service levels", () => {
      expect(serviceLevelDescription("STANDARD")).toContain("Standard");
      expect(serviceLevelDescription("EXPRESS")).toContain("Express");
      expect(serviceLevelDescription("SAME_DAY")).toContain("Same-day");
    });
  });

  describe("extractParcelPageMeta", () => {
    it("extracts nested page metadata from parcel-service responses", () => {
      expect(
        extractParcelPageMeta({
          content: [],
          page: { page: 1, size: 10, totalElements: 24, totalPages: 3 },
        }),
      ).toEqual({ page: 1, size: 10, totalElements: 24, totalPages: 3 });
    });

    it("supports flat fallback metadata", () => {
      expect(
        extractParcelPageMeta({ content: [], page: 0, size: 10, totalElements: 5, totalPages: 1 }),
      ).toEqual({ page: 0, size: 10, totalElements: 5, totalPages: 1 });
    });
  });
});
