/**
 * Admin user management (user-service /api/v1/admin/users): types, the same input rules the API
 * enforces, the search query and the mapping from an API answer to what the page shows.
 */

export const STAFF_ROLES = ["COURIER", "DISPATCHER", "ADMIN"] as const;
export type StaffRole = (typeof STAFF_ROLES)[number];
export const VEHICLE_TYPES = ["BIKE", "THREE_WHEELER", "VAN"] as const;
export type VehicleType = (typeof VEHICLE_TYPES)[number];

export const ROLE_LABELS: Record<string, string> = {
  CUSTOMER: "Customer",
  MERCHANT: "Merchant",
  COURIER: "Courier",
  DISPATCHER: "Dispatcher",
  ADMIN: "Admin",
};
export const VEHICLE_LABELS: Record<VehicleType, string> = {
  BIKE: "Motorbike",
  THREE_WHEELER: "Three-wheeler",
  VAN: "Van",
};

export interface AdminUser {
  id: string;
  email: string | null;
  fullName: string;
  enabled: boolean;
  roles: string[];
}

export interface AdminUserPage {
  items: AdminUser[];
  total: number;
  page: number;
  size: number;
}

export interface UserFilters {
  search: string;
  role: string;
  status: "" | "active" | "disabled";
  page: number;
}

export const PAGE_SIZE = 20;

/** Reads the page's URL parameters, ignoring anything unexpected. */
export function filtersFrom(params: Record<string, string | string[] | undefined>): UserFilters {
  const one = (key: string) => {
    const value = params[key];
    return (Array.isArray(value) ? value[0] : value ?? "").trim();
  };
  const role = one("role").toUpperCase();
  const status = one("status");
  const page = Number.parseInt(one("page"), 10);
  return {
    search: one("search").slice(0, 100),
    role: role in ROLE_LABELS ? role : "",
    status: status === "active" || status === "disabled" ? status : "",
    page: Number.isFinite(page) && page > 0 ? page : 0,
  };
}

/** The user-service query for these filters. */
export function searchQuery(filters: UserFilters): string {
  const query = new URLSearchParams();
  if (filters.search) query.set("search", filters.search);
  if (filters.role) query.set("role", filters.role);
  if (filters.status) query.set("enabled", String(filters.status === "active"));
  query.set("page", String(filters.page));
  query.set("size", String(PAGE_SIZE));
  return query.toString();
}

/** The page's own URL for these filters (used by the pager), without empty values. */
export function pageHref(filters: UserFilters, page: number): string {
  const query = new URLSearchParams();
  if (filters.search) query.set("search", filters.search);
  if (filters.role) query.set("role", filters.role);
  if (filters.status) query.set("status", filters.status);
  if (page > 0) query.set("page", String(page));
  const text = query.toString();
  return text ? `/admin/users?${text}` : "/admin/users";
}

export type StaffField = "email" | "fullName" | "phone" | "role" | "vehicleType" | "homeZoneId" | "maxDailyCapacity";

export interface StaffInput {
  email: string;
  fullName: string;
  phone?: string;
  role: StaffRole;
  vehicleType?: VehicleType;
  homeZoneId?: string;
  maxDailyCapacity?: number;
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^(\+94|0)7[0-9]{8}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

/** Same rules as user-service. Courier details are optional, but all three or none. */
export function validateStaffInput(
  form: Record<StaffField, unknown>,
): { ok: true; input: StaffInput } | { ok: false; fieldErrors: Partial<Record<StaffField, string>> } {
  const email = text(form.email).toLowerCase();
  const fullName = text(form.fullName);
  const phone = text(form.phone).replace(/\s+/g, "");
  const role = text(form.role).toUpperCase();
  const fieldErrors: Partial<Record<StaffField, string>> = {};

  if (!EMAIL.test(email) || email.length > 254) fieldErrors.email = "Enter a valid e-mail address.";
  if (!fullName) fieldErrors.fullName = "Enter the person's full name.";
  else if (fullName.length > 100) fieldErrors.fullName = "Use 100 characters or fewer.";
  if (phone && !PHONE.test(phone)) fieldErrors.phone = "Enter a Sri Lankan mobile number, e.g. 0771234567.";
  if (!(STAFF_ROLES as readonly string[]).includes(role)) fieldErrors.role = "Choose courier, dispatcher or admin.";

  const input: StaffInput = { email, fullName, role: role as StaffRole };
  if (phone) input.phone = phone;

  if (role === "COURIER") {
    const vehicle = text(form.vehicleType).toUpperCase();
    const zone = text(form.homeZoneId);
    const capacityText = text(form.maxDailyCapacity);
    const given = Boolean(vehicle || zone || capacityText);
    if (given) {
      const capacity = Number(capacityText);
      if (!(VEHICLE_TYPES as readonly string[]).includes(vehicle)) fieldErrors.vehicleType = "Choose the vehicle.";
      if (!UUID.test(zone)) fieldErrors.homeZoneId = "Choose the home zone.";
      if (!Number.isInteger(capacity) || capacity < 1 || capacity > 200) {
        fieldErrors.maxDailyCapacity = "Use a whole number from 1 to 200.";
      }
      input.vehicleType = vehicle as VehicleType;
      input.homeZoneId = zone;
      input.maxDailyCapacity = capacity;
    }
  }

  if (Object.keys(fieldErrors).length > 0) return { ok: false, fieldErrors };
  return { ok: true, input };
}

export interface StaffFormState {
  status: "idle" | "created" | "error";
  message?: string;
  fieldErrors?: Partial<Record<StaffField, string>>;
  /** Changes on every successful creation so the form can reset and the confirmation re-announce. */
  createdAt?: number;
}

interface Problem {
  code?: string;
  errors?: { field?: string }[];
}

const KNOWN_FIELDS: StaffField[] = ["email", "fullName", "phone", "role", "vehicleType", "homeZoneId", "maxDailyCapacity"];

/** Turns the answer to POST /admin/users into what the "Add staff account" form shows. */
export function staffStateFromResponse(status: number, body: unknown, now: number = Date.now()): StaffFormState {
  if (status === 201) {
    const created = body as { user?: AdminUser; invitationSent?: boolean };
    const who = created.user?.email ?? "the new account";
    return {
      status: "created",
      message: created.invitationSent
        ? `Account created. A set-your-password e-mail was sent to ${who}.`
        : `Account created, but the set-your-password e-mail could not be sent. Use "Send invite again".`,
      createdAt: now,
    };
  }
  const problem = (body ?? {}) as Problem;
  if (status === 400 && problem.errors?.length) {
    const fieldErrors: Partial<Record<StaffField, string>> = {};
    for (const error of problem.errors) {
      if (error.field && (KNOWN_FIELDS as string[]).includes(error.field)) {
        fieldErrors[error.field as StaffField] = "This value is not valid.";
      }
    }
    return { status: "error", message: "Please fix the highlighted fields.", fieldErrors };
  }
  return { status: "error", message: adminProblemMessage(status, problem.code) };
}

/** Plain-words message for an admin user-management failure. */
export function adminProblemMessage(status: number, code?: string): string {
  switch (code) {
    case "EMAIL_ALREADY_REGISTERED":
    case "EMAIL_ALREADY_LINKED":
      return "An account with this e-mail address already exists.";
    case "CANNOT_DISABLE_YOURSELF":
      return "You cannot disable your own account.";
    case "CANNOT_REMOVE_OWN_ADMIN":
      return "You cannot remove your own admin role.";
    case "COURIER_DETAILS_INCOMPLETE":
      return "Give the vehicle, home zone and daily capacity together, or leave all three empty.";
    case "IDENTITY_PROVIDER_UNAVAILABLE":
      return "The sign-in service is not reachable right now. Please try again shortly.";
    default:
      break;
  }
  if (status === 404) return "That account no longer exists. Reload the page.";
  if (status === 401) return "Your session has expired. Sign in again.";
  if (status === 403) return "Only administrators can manage accounts.";
  return "We could not update the account right now. Please try again.";
}

/** Staff roles a user holds (CUSTOMER and MERCHANT are not managed here). */
export function staffRolesOf(user: Pick<AdminUser, "roles">): StaffRole[] {
  return STAFF_ROLES.filter((role) => user.roles.includes(role));
}

/** Roles worth showing as badges: CUSTOMER is everyone's default, so it is shown only when it is the only role. */
export function visibleRoles(user: Pick<AdminUser, "roles">): string[] {
  const others = user.roles.filter((role) => role !== "CUSTOMER");
  return others.length > 0 ? others : ["CUSTOMER"];
}
