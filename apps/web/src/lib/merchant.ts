/**
 * Merchant onboarding (M7): the shapes merchant-service returns and the plain helpers the pages
 * and forms share. Nothing here talks to the network, so it is safe on the server and in the browser.
 *
 * Schemas: contracts/openapi/merchant-service.v1.yaml
 */

export const MERCHANT_STATUSES = ["PENDING", "APPROVED", "REJECTED", "SUSPENDED"] as const;
export type MerchantStatus = (typeof MERCHANT_STATUSES)[number];

export interface PickupAddress {
  line1: string;
  line2?: string;
  city: string;
  district: string;
  postalCode?: string;
}

export interface Merchant {
  id: string;
  businessName: string;
  brNumber: string;
  contactEmail: string;
  contactPhone: string;
  website?: string;
  pickupAddress: PickupAddress;
  status: MerchantStatus;
  rejectionReason?: string;
  reviewedAt?: string;
  createdAt: string;
}

export interface MerchantPage {
  content: Merchant[];
  page: { page: number; size: number; totalElements: number; totalPages: number };
}

/** Same limit as merchant-service (Merchant.MAX_REASON_LENGTH). */
export const MAX_REASON_LENGTH = 500;

/** Rows per page in the approval queue. */
export const QUEUE_PAGE_SIZE = 20;

const STATUS_LABELS: Record<MerchantStatus, string> = {
  PENDING: "Waiting for review",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  SUSPENDED: "Suspended",
};

const STATUS_PILLS: Record<MerchantStatus, string> = {
  PENDING: "status-pill-warning",
  APPROVED: "status-pill-success",
  REJECTED: "status-pill-neutral",
  SUSPENDED: "status-pill-neutral",
};

export function statusLabel(status: MerchantStatus): string {
  return STATUS_LABELS[status];
}

/** One of the design-system pill classes from globals.css, so no colours are repeated here. */
export function statusPillClass(status: MerchantStatus): string {
  return STATUS_PILLS[status];
}

export function isMerchantStatus(value: unknown): value is MerchantStatus {
  return typeof value === "string" && (MERCHANT_STATUSES as readonly string[]).includes(value);
}

/**
 * Reads the queue's status and page from the address bar. Anything unexpected falls back to the
 * first page of pending applications instead of failing the page.
 */
export function parseQueueQuery(params: { status?: string; page?: string }): { status: MerchantStatus; page: number } {
  const status = isMerchantStatus(params.status) ? params.status : "PENDING";
  const page = Number.parseInt(params.page ?? "", 10);
  return { status, page: Number.isInteger(page) && page >= 0 ? page : 0 };
}

/** The address on one line, for lists. */
export function formatAddress(address: PickupAddress): string {
  return [address.line1, address.line2, address.city, address.district, address.postalCode]
    .filter((part) => part && part.trim() !== "")
    .join(", ");
}

/** A date and time in Sri Lanka, where every merchant and admin works. */
export function formatDateTime(iso: string | undefined): string {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("en-LK", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Asia/Colombo",
  }).format(date);
}

export interface RegistrationBody {
  businessName: string;
  brNumber: string;
  contactEmail: string;
  contactPhone: string;
  website?: string;
  pickupAddress: PickupAddress;
}

export type RegistrationResult =
  | { ok: true; body: RegistrationBody }
  | { ok: false; message: string; errors: Record<string, string> };

const REQUIRED_FIELDS: [keyof Omit<RegistrationBody, "pickupAddress" | "website"> | `pickupAddress.${string}`, string][] = [
  ["businessName", "Business name"],
  ["brNumber", "Business registration number"],
  ["contactEmail", "Contact email"],
  ["contactPhone", "Contact phone"],
  ["pickupAddress.line1", "Pickup address"],
  ["pickupAddress.city", "City"],
  ["pickupAddress.district", "District"],
];

/**
 * Builds the registration request from the form. Only completeness is checked here, so the
 * applicant hears about a missing field at once; merchant-service checks every rule again and its
 * field messages are shown as they come back.
 */
export function registrationBody(form: FormData): RegistrationResult {
  const text = (name: string) => String(form.get(name) ?? "").trim();
  const optional = (name: string) => text(name) || undefined;

  const errors: Record<string, string> = {};
  for (const [field, label] of REQUIRED_FIELDS) {
    if (!text(field)) errors[field] = `${label} is required`;
  }
  if (Object.keys(errors).length > 0) {
    return { ok: false, message: "Fill in every required field.", errors };
  }

  return {
    ok: true,
    body: {
      businessName: text("businessName"),
      brNumber: text("brNumber"),
      contactEmail: text("contactEmail"),
      contactPhone: text("contactPhone").replace(/[\s-]/g, ""),
      website: optional("website"),
      pickupAddress: {
        line1: text("pickupAddress.line1"),
        line2: optional("pickupAddress.line2"),
        city: text("pickupAddress.city"),
        district: text("pickupAddress.district"),
        postalCode: optional("pickupAddress.postalCode"),
      },
    },
  };
}
