/**
 * Pure data types, validation rules, and helpers for Parcel Booking and Management (Milestone M3).
 *
 * Implements the contracts defined in contracts/openapi/parcel-service.v1.yaml and
 * services/parcel-service/src/main/java/lk/swiftdrop/parcel.
 */

export const SRI_LANKA_DISTRICTS = [
  "Ampara",
  "Anuradhapura",
  "Badulla",
  "Batticaloa",
  "Colombo",
  "Galle",
  "Gampaha",
  "Hambantota",
  "Jaffna",
  "Kalutara",
  "Kandy",
  "Kegalle",
  "Kilinochchi",
  "Kurunegala",
  "Mannar",
  "Matale",
  "Matara",
  "Monaragala",
  "Mullaitivu",
  "Nuwara Eliya",
  "Polonnaruwa",
  "Puttalam",
  "Ratnapura",
  "Trincomalee",
  "Vavuniya",
] as const;

export type District = (typeof SRI_LANKA_DISTRICTS)[number];

export const SIZE_CLASSES = ["S", "M", "L", "XL"] as const;
export type SizeClass = (typeof SIZE_CLASSES)[number];

export const SERVICE_LEVELS = ["STANDARD", "EXPRESS"] as const;
export type ServiceLevel = (typeof SERVICE_LEVELS)[number];

export const PARCEL_STATUSES = [
  "CREATED",
  "PICKED_UP",
  "IN_TRANSIT",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "CANCELLED",
] as const;
export type ParcelStatus = (typeof PARCEL_STATUSES)[number];

export interface AddressDto {
  line1: string;
  line2?: string | null;
  city: string;
  district: string;
  postalCode?: string | null;
  lat?: number | null;
  lng?: number | null;
}

export interface ContactDto {
  name: string;
  phone: string;
  email?: string | null;
}

export interface Money {
  amount: number;
  currency: string;
}

export interface StatusHistoryEntryResponse {
  fromStatus: ParcelStatus | null;
  toStatus: ParcelStatus;
  actorId: string;
  actorRole: string;
  occurredAt: string;
}

export interface ParcelResponse {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  source: string;
  merchantOrderRef?: string | null;
  pickup: AddressDto;
  dropoff: AddressDto;
  recipient: ContactDto;
  weightKg: number;
  sizeClass: SizeClass;
  serviceLevel: ServiceLevel;
  fee: Money;
  notes?: string | null;
  courierId?: string | null;
  history?: StatusHistoryEntryResponse[] | null;
  createdAt: string;
}

export interface ParcelPageResponse {
  content: ParcelResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateParcelRequest {
  quoteId: string;
  pickup: AddressDto;
  dropoff: AddressDto;
  recipient: ContactDto;
  notes?: string | null;
}

export interface QuoteBreakdownLine {
  rule: string;
  amount: number;
}

export interface QuoteResult {
  quoteId: string;
  fee: Money;
  breakdown: QuoteBreakdownLine[];
  fromZone: string;
  toZone: string;
  fromDistrict?: string;
  toDistrict?: string;
  weightKg: number;
  sizeClass: SizeClass;
  serviceLevel: ServiceLevel;
  issuedAt: string;
  expiresAt: string;
}

/** Sri Lankan mobile number regex: +947XXXXXXXX or 07XXXXXXXX */
export const SRI_LANKAN_PHONE_REGEX = /^(\+94|0)7[0-9]{8}$/;

export function isValidSriLankanPhone(phone: string): boolean {
  return SRI_LANKAN_PHONE_REGEX.test(phone.trim());
}

export function formatMoney(amount: number, currency = "LKR"): string {
  if (!Number.isFinite(amount)) return "";
  return `${currency} ${amount.toLocaleString("en-LK", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

export function statusBadgeClass(status: ParcelStatus): string {
  switch (status) {
    case "CREATED":
      return "bg-blue-50 text-blue-700 border-blue-200";
    case "PICKED_UP":
      return "bg-purple-50 text-purple-700 border-purple-200";
    case "IN_TRANSIT":
      return "bg-indigo-50 text-indigo-700 border-indigo-200";
    case "OUT_FOR_DELIVERY":
      return "bg-amber-50 text-amber-700 border-amber-200";
    case "DELIVERED":
      return "bg-emerald-50 text-emerald-700 border-emerald-200";
    case "CANCELLED":
      return "bg-rose-50 text-rose-700 border-rose-200";
    default:
      return "bg-slate-50 text-slate-700 border-slate-200";
  }
}

export function statusLabel(status: ParcelStatus): string {
  switch (status) {
    case "CREATED":
      return "Booking Created";
    case "PICKED_UP":
      return "Picked Up";
    case "IN_TRANSIT":
      return "In Transit";
    case "OUT_FOR_DELIVERY":
      return "Out for Delivery";
    case "DELIVERED":
      return "Delivered";
    case "CANCELLED":
      return "Cancelled";
    default:
      return status;
  }
}

export function sizeClassDescription(size: SizeClass): string {
  switch (size) {
    case "S":
      return "Small (up to 25 cm, documents / accessories)";
    case "M":
      return "Medium (up to 50 cm, shoe box / electronics)";
    case "L":
      return "Large (up to 100 cm, small appliance)";
    case "XL":
      return "Extra Large (heavy goods / bulky)";
    default:
      return size;
  }
}

export function serviceLevelDescription(level: ServiceLevel): string {
  switch (level) {
    case "STANDARD":
      return "Standard delivery (1-2 business days)";
    case "EXPRESS":
      return "Express delivery (Priority same-day or next-day)";
    default:
      return level;
  }
}
