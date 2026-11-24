/**
 * The customer booking wizard's logic (M3), kept out of the page so it is unit tested and counts
 * toward the web coverage gate: the form model, step validation, form updates, the booking request
 * and how booking failures are explained.
 */
import type { SavedAddress } from "./addresses";
import { isValidSriLankanPhone, type ServiceLevel, type SizeClass } from "./parcels";
import {
  SERVICE_LEVELS,
  SIZE_CLASSES,
  VEHICLE_TYPES,
  validateQuoteForm,
  type GeoPick,
  type LocatedPoint,
  type QuoteFormValues,
  type VehicleType,
} from "./pricing";

export type BookingStep = 1 | 2 | 3 | 4 | 5 | 6;
export type Side = "pickup" | "dropoff";
export type BookingErrors = Record<string, string>;

export interface BookingForm {
  // Step 1: pickup
  pickupSavedId: string;
  pickupLine1: string;
  pickupLine2: string;
  pickupCity: string;
  pickupDistrict: string;
  pickupPostalCode: string;
  fromPoint?: GeoPick;

  // Step 2: drop-off and recipient
  recipientName: string;
  recipientPhone: string;
  recipientEmail: string;
  dropoffLine1: string;
  dropoffLine2: string;
  dropoffCity: string;
  dropoffDistrict: string;
  dropoffPostalCode: string;
  toPoint?: GeoPick;
  notes: string;

  // Step 3: package and pricing options
  weightKg: string;
  sizeClass: SizeClass;
  serviceLevel: ServiceLevel;
  vehicleType: VehicleType;
  lengthCm: string;
  widthCm: string;
  heightCm: string;
  codAmount: string;
  declaredValue: string;
  specialHandling: string[];
}

const oneOf = <T extends string>(allowed: readonly T[], value: string | undefined, fallback: T): T =>
  value && (allowed as readonly string[]).includes(value) ? (value as T) : fallback;

/** A new form, pre-filled with whatever `/quote` handed over. */
export function buildInitialForm(initialPricing?: Partial<QuoteFormValues>): BookingForm {
  return {
    pickupSavedId: "",
    pickupLine1: initialPricing?.fromAddress ?? "",
    pickupLine2: "",
    pickupCity: "",
    pickupDistrict: initialPricing?.fromDistrict ?? "Colombo",
    pickupPostalCode: "",
    fromPoint: initialPricing?.fromPoint,

    recipientName: "",
    recipientPhone: "",
    recipientEmail: "",
    dropoffLine1: initialPricing?.toAddress ?? "",
    dropoffLine2: "",
    dropoffCity: "",
    dropoffDistrict: initialPricing?.toDistrict ?? "Kandy",
    dropoffPostalCode: "",
    toPoint: initialPricing?.toPoint,
    notes: "",

    weightKg: initialPricing?.weightKg ?? "1.5",
    sizeClass: oneOf(SIZE_CLASSES as readonly SizeClass[], initialPricing?.sizeClass, "S"),
    serviceLevel: oneOf(SERVICE_LEVELS as readonly ServiceLevel[], initialPricing?.serviceLevel, "STANDARD"),
    vehicleType: oneOf(VEHICLE_TYPES, initialPricing?.vehicleType, "BIKE"),
    lengthCm: initialPricing?.lengthCm ?? "",
    widthCm: initialPricing?.widthCm ?? "",
    heightCm: initialPricing?.heightCm ?? "",
    codAmount: initialPricing?.codAmount ?? "",
    declaredValue: initialPricing?.declaredValue ?? "",
    specialHandling: initialPricing?.specialHandling ?? [],
  };
}

/** The pricing inputs of the form, in the shape the shared quote validation takes. */
export function toQuoteFormValues(form: BookingForm): QuoteFormValues {
  return {
    fromAddress: form.pickupLine1,
    fromDistrict: form.pickupDistrict,
    toAddress: form.dropoffLine1,
    toDistrict: form.dropoffDistrict,
    weightKg: form.weightKg,
    sizeClass: form.sizeClass,
    numberOfParcels: 1,
    serviceLevel: form.serviceLevel,
    codAmount: form.codAmount,
    declaredValue: form.declaredValue,
    lengthCm: form.lengthCm,
    widthCm: form.widthCm,
    heightCm: form.heightCm,
    vehicleType: form.vehicleType,
    specialHandling: form.specialHandling,
    fromPoint: form.fromPoint,
    toPoint: form.toPoint,
  };
}

export function vehicleTypeLabel(type: VehicleType | string): string {
  switch (type) {
    case "BIKE":
      return "Motorbike (compact same-day parcel)";
    case "VAN":
      return "Delivery Van (bulky or longer route)";
    default:
      return type;
  }
}

const POSTAL_CODE = /^[0-9]{5}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Quote validation field names mapped to the wizard's own field names. */
const QUOTE_FIELDS: Record<string, string> = {
  fromDistrict: "pickupDistrict",
  toDistrict: "dropoffDistrict",
  weightKg: "weightKg",
  sizeClass: "sizeClass",
  serviceLevel: "serviceLevel",
  vehicleType: "vehicleType",
  codAmount: "codAmount",
  declaredValue: "declaredValue",
  lengthCm: "lengthCm",
  widthCm: "widthCm",
  heightCm: "heightCm",
  specialHandling: "specialHandling",
};

/**
 * The field errors that stop the customer leaving a step. `serviced` is the district list from the
 * coverage API (empty when it could not be loaded, so the server decides); `districts` is what the
 * quote validation checks against.
 */
export function validateBookingStep(
  step: BookingStep,
  form: BookingForm,
  { serviced, districts }: { serviced: readonly string[]; districts: readonly string[] },
): BookingErrors {
  const errors: BookingErrors = {};
  const notServiced = (district: string) => serviced.length > 0 && !serviced.includes(district.trim());

  if (step === 1) {
    if (!form.pickupLine1.trim()) errors.pickupLine1 = "Street address is required.";
    if (!form.pickupCity.trim()) errors.pickupCity = "City is required.";
    if (!form.pickupDistrict.trim()) errors.pickupDistrict = "District is required.";
    else if (notServiced(form.pickupDistrict)) errors.pickupDistrict = "We do not deliver from there yet.";
    if (form.pickupPostalCode && !POSTAL_CODE.test(form.pickupPostalCode.trim())) {
      errors.pickupPostalCode = "Postal code must be 5 digits.";
    }
  }

  if (step === 2) {
    if (!form.recipientName.trim()) errors.recipientName = "Recipient name is required.";
    if (!form.recipientPhone.trim()) {
      errors.recipientPhone = "Recipient mobile number is required.";
    } else if (!isValidSriLankanPhone(form.recipientPhone)) {
      errors.recipientPhone = "Must be a valid Sri Lankan mobile number (e.g. 0771234567 or +94771234567).";
    }
    if (form.recipientEmail && !EMAIL.test(form.recipientEmail.trim())) {
      errors.recipientEmail = "Invalid email address format.";
    }
    if (!form.dropoffLine1.trim()) errors.dropoffLine1 = "Delivery street address is required.";
    if (!form.dropoffCity.trim()) errors.dropoffCity = "City is required.";
    if (!form.dropoffDistrict.trim()) errors.dropoffDistrict = "District is required.";
    else if (notServiced(form.dropoffDistrict)) errors.dropoffDistrict = "We do not deliver there yet.";
    if (form.dropoffPostalCode && !POSTAL_CODE.test(form.dropoffPostalCode.trim())) {
      errors.dropoffPostalCode = "Postal code must be 5 digits.";
    }
  }

  if (step === 3) {
    const validation = validateQuoteForm(toQuoteFormValues(form), districts);
    if (!validation.ok) {
      for (const [field, message] of Object.entries(validation.errors)) {
        const target = QUOTE_FIELDS[field];
        if (target && message) errors[target] = message;
      }
    }
  }

  return errors;
}

/** Which collapsed panels must open so the customer can see the errors in them. */
export function panelsToReveal(errors: BookingErrors): { dimensions: boolean; addons: boolean } {
  return {
    dimensions: Boolean(errors.lengthCm || errors.widthCm || errors.heightCm),
    addons: Boolean(errors.codAmount || errors.declaredValue || errors.specialHandling),
  };
}

/** Fills the pickup from a saved address; the pinned point belonged to the old address. */
export function withSavedPickup(form: BookingForm, address: SavedAddress): BookingForm {
  return {
    ...form,
    pickupSavedId: address.id,
    pickupLine1: address.line1,
    pickupLine2: address.line2 || "",
    pickupCity: address.city,
    pickupDistrict: address.district,
    pickupPostalCode: address.postalCode || "",
    fromPoint: undefined,
  };
}

/** Changes a district; a pinned point only survives when the district did not change. */
export function withDistrictChange(form: BookingForm, side: Side, district: string): BookingForm {
  if (side === "pickup") {
    return { ...form, pickupDistrict: district, fromPoint: form.pickupDistrict === district ? form.fromPoint : undefined };
  }
  return { ...form, dropoffDistrict: district, toPoint: form.dropoffDistrict === district ? form.toPoint : undefined };
}

/** Applies a map pick: the point and the district it was located in. */
export function withMapPick(form: BookingForm, side: Side, point: GeoPick, located: LocatedPoint): BookingForm {
  return side === "pickup"
    ? { ...form, pickupDistrict: located.district, fromPoint: point }
    : { ...form, dropoffDistrict: located.district, toPoint: point };
}

export function withHandling(form: BookingForm, code: string, checked: boolean): BookingForm {
  const without = form.specialHandling.filter((existing) => existing !== code);
  return { ...form, specialHandling: checked ? [...without, code] : without };
}

const optional = (value: string) => value.trim() || undefined;

/** The `POST /api/v1/parcels` body (parcel-service contract): the quote fixes the price. */
export function bookingRequest(form: BookingForm, quoteId: string) {
  return {
    quoteId,
    pickup: {
      line1: form.pickupLine1.trim(),
      line2: optional(form.pickupLine2),
      city: form.pickupCity.trim(),
      district: form.pickupDistrict.trim(),
      postalCode: optional(form.pickupPostalCode),
    },
    dropoff: {
      line1: form.dropoffLine1.trim(),
      line2: optional(form.dropoffLine2),
      city: form.dropoffCity.trim(),
      district: form.dropoffDistrict.trim(),
      postalCode: optional(form.dropoffPostalCode),
    },
    recipient: {
      name: form.recipientName.trim(),
      phone: form.recipientPhone.trim(),
      email: optional(form.recipientEmail),
    },
    notes: optional(form.notes),
  };
}

/** What to tell the customer when booking is refused. */
export function bookingFailureMessage(status: number, problem: { code?: string; detail?: string }): string {
  if (status === 422 && problem.code === "QUOTE_EXPIRED") {
    return "Your quote expired while reviewing. Please recalculate your fee quote.";
  }
  if (status === 503) return "Pricing service is currently unavailable. Please try again shortly.";
  return problem.detail || `Booking failed with status ${status}`;
}
