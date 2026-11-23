import { describe, expect, it } from "vitest";
import {
  bookingFailureMessage,
  bookingRequest,
  buildInitialForm,
  panelsToReveal,
  toQuoteFormValues,
  validateBookingStep,
  vehicleTypeLabel,
  withDistrictChange,
  withHandling,
  withMapPick,
  withSavedPickup,
  type BookingForm,
} from "./booking";

const DISTRICTS = ["Colombo", "Galle", "Kandy"];
const LISTS = { serviced: DISTRICTS, districts: DISTRICTS };
const POINT = { latitude: 6.9271, longitude: 79.8612 };

function filled(overrides: Partial<BookingForm> = {}): BookingForm {
  return {
    ...buildInitialForm(),
    pickupLine1: "12 Galle Road",
    pickupCity: "Colombo 03",
    recipientName: "Nimal Perera",
    recipientPhone: "0771234567",
    dropoffLine1: "45 Peradeniya Road",
    dropoffCity: "Kandy",
    ...overrides,
  };
}

describe("booking form model", () => {
  it("starts with defaults and takes valid values handed over from /quote", () => {
    const blank = buildInitialForm();
    expect(blank).toMatchObject({ pickupDistrict: "Colombo", dropoffDistrict: "Kandy", weightKg: "1.5",
      sizeClass: "S", serviceLevel: "STANDARD", vehicleType: "BIKE", specialHandling: [] });

    const carried = buildInitialForm({ fromDistrict: "Galle", toDistrict: "Colombo", fromAddress: "1 Fort",
      weightKg: "3", sizeClass: "L", serviceLevel: "SAME_DAY", vehicleType: "VAN", codAmount: "500",
      specialHandling: ["FRAGILE"], fromPoint: POINT });
    expect(carried).toMatchObject({ pickupDistrict: "Galle", pickupLine1: "1 Fort", sizeClass: "L",
      serviceLevel: "SAME_DAY", vehicleType: "VAN", codAmount: "500", fromPoint: POINT });
  });

  it("ignores unknown size classes, service levels and vehicles", () => {
    const form = buildInitialForm({ sizeClass: "XXL", serviceLevel: "WARP", vehicleType: "TRUCK" } as never);
    expect(form).toMatchObject({ sizeClass: "S", serviceLevel: "STANDARD", vehicleType: "BIKE" });
  });

  it("maps the form onto the shared quote inputs", () => {
    const values = toQuoteFormValues(filled({ codAmount: "250", toPoint: POINT }));
    expect(values).toMatchObject({ fromAddress: "12 Galle Road", fromDistrict: "Colombo", toDistrict: "Kandy",
      numberOfParcels: 1, codAmount: "250", toPoint: POINT });
  });

  it("labels vehicles and passes unknown ones through", () => {
    expect(vehicleTypeLabel("BIKE")).toMatch(/Motorbike/);
    expect(vehicleTypeLabel("VAN")).toMatch(/Van/);
    expect(vehicleTypeLabel("BOAT")).toBe("BOAT");
  });
});

describe("validateBookingStep", () => {
  it("accepts a complete form on every step", () => {
    for (const step of [1, 2, 3] as const) expect(validateBookingStep(step, filled(), LISTS)).toEqual({});
  });

  it("requires the pickup address and checks the district and postal code", () => {
    const errors = validateBookingStep(1, filled({ pickupLine1: " ", pickupCity: "", pickupDistrict: "Jaffna",
      pickupPostalCode: "123" }), LISTS);
    expect(errors).toEqual({
      pickupLine1: "Street address is required.",
      pickupCity: "City is required.",
      pickupDistrict: "We do not deliver from there yet.",
      pickupPostalCode: "Postal code must be 5 digits.",
    });
    expect(validateBookingStep(1, filled({ pickupDistrict: "" }), LISTS).pickupDistrict).toBe("District is required.");
  });

  it("leaves the district to the server when coverage could not be loaded", () => {
    expect(validateBookingStep(1, filled({ pickupDistrict: "Jaffna" }), { serviced: [], districts: DISTRICTS })).toEqual({});
  });

  it("checks the recipient and the drop-off", () => {
    const errors = validateBookingStep(2, filled({ recipientName: "", recipientPhone: "12345",
      recipientEmail: "nimal@", dropoffLine1: "", dropoffCity: "", dropoffDistrict: "Jaffna", dropoffPostalCode: "2000" }), LISTS);
    expect(Object.keys(errors).sort()).toEqual(["dropoffCity", "dropoffDistrict", "dropoffLine1", "dropoffPostalCode",
      "recipientEmail", "recipientName", "recipientPhone"]);
    expect(errors.recipientPhone).toMatch(/valid Sri Lankan mobile/);
    expect(errors.dropoffDistrict).toBe("We do not deliver there yet.");
    expect(validateBookingStep(2, filled({ recipientPhone: "" }), LISTS).recipientPhone).toMatch(/required/);
    expect(validateBookingStep(2, filled({ dropoffDistrict: "" }), LISTS).dropoffDistrict).toBe("District is required.");
    expect(validateBookingStep(2, filled({ recipientPhone: "+94771234567", recipientEmail: "a@b.lk",
      dropoffPostalCode: "20000" }), LISTS)).toEqual({});
  });

  it("reports pricing errors on the wizard's own fields and reveals their panels", () => {
    const errors = validateBookingStep(3, filled({ weightKg: "0", lengthCm: "abc", codAmount: "-1" }), LISTS);
    expect(errors.weightKg).toBeTruthy();
    expect(errors.lengthCm).toBeTruthy();
    expect(errors.codAmount).toBeTruthy();
    expect(panelsToReveal(errors)).toEqual({ dimensions: true, addons: true });
    expect(panelsToReveal({ weightKg: "x" })).toEqual({ dimensions: false, addons: false });
    expect(validateBookingStep(3, filled({ dropoffDistrict: "Mars" }), LISTS).dropoffDistrict).toBeTruthy();
  });
});

describe("form updates", () => {
  it("fills the pickup from a saved address and drops the old pin", () => {
    const form = withSavedPickup(filled({ fromPoint: POINT }), { id: "a1", label: "Home", line1: "7 Lake Rd",
      city: "Galle", district: "Galle", isDefault: true } as never);
    expect(form).toMatchObject({ pickupSavedId: "a1", pickupLine1: "7 Lake Rd", pickupLine2: "",
      pickupDistrict: "Galle", pickupPostalCode: "", fromPoint: undefined });
  });

  it("keeps a pin only while its district stays the same", () => {
    const pinned = filled({ fromPoint: POINT, toPoint: POINT });
    expect(withDistrictChange(pinned, "pickup", "Colombo").fromPoint).toEqual(POINT);
    expect(withDistrictChange(pinned, "pickup", "Galle")).toMatchObject({ pickupDistrict: "Galle", fromPoint: undefined });
    expect(withDistrictChange(pinned, "dropoff", "Kandy").toPoint).toEqual(POINT);
    expect(withDistrictChange(pinned, "dropoff", "Galle")).toMatchObject({ dropoffDistrict: "Galle", toPoint: undefined });
  });

  it("takes the district from a map pick", () => {
    const located = { district: "Galle" } as never;
    expect(withMapPick(filled(), "pickup", POINT, located)).toMatchObject({ pickupDistrict: "Galle", fromPoint: POINT });
    expect(withMapPick(filled(), "dropoff", POINT, located)).toMatchObject({ dropoffDistrict: "Galle", toPoint: POINT });
  });

  it("toggles special handling without duplicates", () => {
    const once = withHandling(filled(), "FRAGILE", true);
    expect(withHandling(once, "FRAGILE", true).specialHandling).toEqual(["FRAGILE"]);
    expect(withHandling(once, "FRAGILE", false).specialHandling).toEqual([]);
  });
});

describe("booking request", () => {
  it("sends trimmed values, leaves blanks out and carries the quote id", () => {
    const body = bookingRequest(filled({ pickupLine1: " 12 Galle Road ", pickupLine2: "  ", recipientEmail: "",
      dropoffPostalCode: "20000", notes: " Ring twice " }), "quote-1");
    expect(body).toEqual({
      quoteId: "quote-1",
      pickup: { line1: "12 Galle Road", line2: undefined, city: "Colombo 03", district: "Colombo", postalCode: undefined },
      dropoff: { line1: "45 Peradeniya Road", line2: undefined, city: "Kandy", district: "Kandy", postalCode: "20000" },
      recipient: { name: "Nimal Perera", phone: "0771234567", email: undefined },
      notes: "Ring twice",
    });
  });

  it("explains refusals", () => {
    expect(bookingFailureMessage(422, { code: "QUOTE_EXPIRED" })).toMatch(/quote expired/);
    expect(bookingFailureMessage(503, {})).toMatch(/currently unavailable/);
    expect(bookingFailureMessage(400, { detail: "Recipient phone is invalid" })).toBe("Recipient phone is invalid");
    expect(bookingFailureMessage(500, {})).toBe("Booking failed with status 500");
  });
});
