/**
 * The signed-in user's profile (user-service GET/PUT /api/v1/users/me): types, the same input rules
 * the API enforces, and the mapping from an API response to what the profile form shows.
 */

export interface Profile {
  id: string;
  email: string;
  fullName: string;
  /** E.164, e.g. +94771234567; null when the user has not given one. */
  phone: string | null;
  status: "ACTIVE" | "DISABLED";
  roles: string[];
  createdAt: string;
  updatedAt: string;
}

export type ProfileField = "fullName" | "phone";

export interface ProfileFormState {
  status: "idle" | "saved" | "error";
  /** Message for the whole form (shown under the Save button). */
  message?: string;
  fieldErrors?: Partial<Record<ProfileField, string>>;
  /** The profile as stored after a successful save. */
  profile?: Profile;
  /** Changes on every save so the confirmation is announced (and re-shown) each time. */
  savedAt?: number;
}

export const MAX_NAME_LENGTH = 100;
const PHONE = /^(\+94|0)7[0-9]{8}$/;

export interface ProfileInput {
  fullName: string;
  /** Omitted when empty: PUT semantics remove the stored number. */
  phone?: string;
}

/** Same rules as user-service, so most mistakes are caught before a round trip. */
export function validateProfileInput(
  rawName: unknown,
  rawPhone: unknown,
): { ok: true; input: ProfileInput } | { ok: false; fieldErrors: Partial<Record<ProfileField, string>> } {
  const fullName = typeof rawName === "string" ? rawName.trim() : "";
  const phone = typeof rawPhone === "string" ? rawPhone.replace(/\s+/g, "") : "";
  const fieldErrors: Partial<Record<ProfileField, string>> = {};

  if (!fullName) fieldErrors.fullName = "Enter your full name.";
  else if (fullName.length > MAX_NAME_LENGTH) fieldErrors.fullName = `Use ${MAX_NAME_LENGTH} characters or fewer.`;

  if (phone && !PHONE.test(phone)) fieldErrors.phone = "Enter a Sri Lankan mobile number, e.g. 0771234567.";

  if (Object.keys(fieldErrors).length > 0) return { ok: false, fieldErrors };
  return { ok: true, input: phone ? { fullName, phone } : { fullName } };
}

interface Problem {
  code?: string;
  detail?: string;
  errors?: { field?: string; message?: string }[];
}

/** Turns the user-service answer to PUT /users/me into the state the form renders. */
export function stateFromResponse(status: number, body: unknown, now: number = Date.now()): ProfileFormState {
  if (status === 200) {
    return { status: "saved", message: "Profile saved.", profile: body as Profile, savedAt: now };
  }
  const problem = (body ?? {}) as Problem;
  if (status === 400 && problem.errors?.length) {
    const fieldErrors: Partial<Record<ProfileField, string>> = {};
    for (const error of problem.errors) {
      if (error.field === "fullName" || error.field === "phone") {
        fieldErrors[error.field] = error.message ? sentence(error.message) : "This value is not valid.";
      }
    }
    return { status: "error", message: "Please fix the highlighted field.", fieldErrors };
  }
  if (status === 401) return { status: "error", message: "Your session has expired. Sign in again to save changes." };
  if (status === 403) return { status: "error", message: "This account cannot have a profile." };
  if (status === 409) return { status: "error", message: "This e-mail address is already linked to another profile." };
  return { status: "error", message: "We could not save your profile right now. Please try again." };
}

/** "+94771234567" → "+94 77 123 4567", for reading; the form edits the raw value. */
export function formatPhone(phone: string | null): string {
  if (!phone) return "Not added";
  const match = /^\+94(\d{2})(\d{3})(\d{4})$/.exec(phone);
  return match ? `+94 ${match[1]} ${match[2]} ${match[3]}` : phone;
}

/** "2026-11-13T08:58:07Z" → "13 Nov 2026". */
export function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "Asia/Colombo" });
}

function sentence(message: string): string {
  const trimmed = message.trim();
  const capitalised = trimmed.charAt(0).toUpperCase() + trimmed.slice(1);
  return /[.!?]$/.test(capitalised) ? capitalised : `${capitalised}.`;
}
