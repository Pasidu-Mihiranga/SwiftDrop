/**
 * The signed-in user's address book (user-service /api/v1/users/me/addresses): types, the same input
 * rules the API enforces, and the mapping from an API answer to what the address form shows.
 */

export interface SavedAddress {
  id: string;
  label: string;
  line1: string;
  line2: string | null;
  city: string;
  district: string;
  postalCode: string | null;
  lat: number | null;
  lng: number | null;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export const MAX_ADDRESSES = 10;

export type AddressField = "label" | "line1" | "line2" | "city" | "district" | "postalCode";

export interface AddressInput {
  label: string;
  line1: string;
  line2?: string;
  city: string;
  district: string;
  postalCode?: string;
  isDefault?: boolean;
}

export interface AddressFormState {
  status: "idle" | "saved" | "error";
  message?: string;
  fieldErrors?: Partial<Record<AddressField, string>>;
  /** Changes on every successful save so the form can close and the confirmation re-announce. */
  savedAt?: number;
}

const LIMITS: Record<AddressField, number> = { label: 40, line1: 120, line2: 120, city: 60, district: 40, postalCode: 5 };
const REQUIRED: Partial<Record<AddressField, string>> = {
  label: "Give this address a name, e.g. Home.",
  line1: "Enter the street address.",
  city: "Enter the city.",
  district: "Enter the district.",
};

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

/** Same rules as user-service, so most mistakes are caught before a round trip. */
export function validateAddressInput(
  form: Record<AddressField, unknown> & { isDefault?: unknown },
): { ok: true; input: AddressInput } | { ok: false; fieldErrors: Partial<Record<AddressField, string>> } {
  const values = Object.fromEntries(
    (Object.keys(LIMITS) as AddressField[]).map((field) => [field, text(form[field])]),
  ) as Record<AddressField, string>;
  const fieldErrors: Partial<Record<AddressField, string>> = {};

  for (const field of Object.keys(LIMITS) as AddressField[]) {
    if (!values[field] && REQUIRED[field]) fieldErrors[field] = REQUIRED[field];
    else if (values[field].length > LIMITS[field] && field !== "postalCode") {
      fieldErrors[field] = `Use ${LIMITS[field]} characters or fewer.`;
    }
  }
  if (values.postalCode && !/^[0-9]{5}$/.test(values.postalCode)) fieldErrors.postalCode = "Postal codes have five digits.";

  if (Object.keys(fieldErrors).length > 0) return { ok: false, fieldErrors };
  const input: AddressInput = { label: values.label, line1: values.line1, city: values.city, district: values.district };
  if (values.line2) input.line2 = values.line2;
  if (values.postalCode) input.postalCode = values.postalCode;
  if (form.isDefault === "on" || form.isDefault === true) input.isDefault = true;
  return { ok: true, input };
}

interface Problem {
  code?: string;
  errors?: { field?: string; message?: string }[];
}

/** Turns the user-service answer to POST / PUT into the state the address form renders. */
export function addressStateFromResponse(status: number, body: unknown, now: number = Date.now()): AddressFormState {
  if (status === 200 || status === 201) return { status: "saved", message: "Address saved.", savedAt: now };
  const problem = (body ?? {}) as Problem;
  if (status === 400 && problem.errors?.length) {
    const fieldErrors: Partial<Record<AddressField, string>> = {};
    for (const error of problem.errors) {
      if (error.field && error.field in LIMITS) fieldErrors[error.field as AddressField] = "This value is not valid.";
    }
    return { status: "error", message: "Please fix the highlighted fields.", fieldErrors };
  }
  return { status: "error", message: problemMessage(status, problem.code) };
}

/** Plain-words message for an address-book failure, shared by save, delete and "make default". */
export function problemMessage(status: number, code?: string): string {
  if (code === "ADDRESS_LIMIT_REACHED") return `You can keep up to ${MAX_ADDRESSES} addresses. Delete one first.`;
  if (code === "ADDRESS_BOOK_CHANGED") return "Your address book changed in another tab. Reload and try again.";
  if (status === 404) return "That address no longer exists. Reload the page.";
  if (status === 401) return "Your session has expired. Sign in again.";
  if (status === 400) return "Some details are not valid. Check the address and try again.";
  return "We could not update your address book right now. Please try again.";
}

/** "No. 12, Temple Road, Near the temple, Kandy 20000 · Kandy District". */
export function formatAddress(address: Pick<SavedAddress, "line1" | "line2" | "city" | "district" | "postalCode">): string {
  const street = [address.line1, address.line2].filter(Boolean).join(", ");
  const town = [address.city, address.postalCode].filter(Boolean).join(" ");
  return `${street}, ${town} · ${address.district} District`;
}

/** The 25 districts from pricing-service's public zones, sorted; empty when pricing is not available. */
export function districtsFromZones(zones: unknown): string[] {
  if (!Array.isArray(zones)) return [];
  const names = new Set<string>();
  for (const zone of zones as { districts?: unknown }[]) {
    if (Array.isArray(zone?.districts)) {
      for (const district of zone.districts) if (typeof district === "string" && district.trim()) names.add(district.trim());
    }
  }
  return [...names].sort((a, b) => a.localeCompare(b));
}
