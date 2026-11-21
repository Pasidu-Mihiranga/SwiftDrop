"use server";

import { revalidatePath } from "next/cache";
import { gatewayFetch } from "@/lib/gateway";
import {
  adminProblemMessage,
  STAFF_ROLES,
  staffStateFromResponse,
  validateStaffInput,
  type StaffFormState,
  type StaffRole,
} from "@/lib/admin-users";

const BASE = "/api/v1/admin/users";
const JSON_HEADERS = { "Content-Type": "application/json", Accept: "application/json" };
/** Keycloak ids are UUIDs; anything else never reaches the API. */
const USER_ID = /^[0-9a-zA-Z-]{1,64}$/;

export interface UserActionResult {
  ok: boolean;
  message: string;
}

async function problemCode(response: Response): Promise<string | undefined> {
  const body = (await response.json().catch(() => null)) as { code?: string } | null;
  return body?.code;
}

/**
 * Creates a staff account. Runs on the server: the admin's token never reaches the browser, and
 * user-service checks the ADMIN role again.
 */
export async function createStaffAccount(_previous: StaffFormState, formData: FormData): Promise<StaffFormState> {
  const checked = validateStaffInput({
    email: formData.get("email"),
    fullName: formData.get("fullName"),
    phone: formData.get("phone"),
    role: formData.get("role"),
    vehicleType: formData.get("vehicleType"),
    homeZoneId: formData.get("homeZoneId"),
    maxDailyCapacity: formData.get("maxDailyCapacity"),
  });
  if (!checked.ok) return { status: "error", message: "Please fix the highlighted fields.", fieldErrors: checked.fieldErrors };
  try {
    const response = await gatewayFetch(BASE, { method: "POST", headers: JSON_HEADERS, body: JSON.stringify(checked.input) });
    const body: unknown = await response.json().catch(() => null);
    const state = staffStateFromResponse(response.status, body);
    if (state.status === "created") revalidatePath("/admin/users");
    return state;
  } catch {
    return staffStateFromResponse(503, null);
  }
}

async function patchUser(userId: string, change: { enabled?: boolean; roles?: StaffRole[] }, success: string): Promise<UserActionResult> {
  if (!USER_ID.test(userId)) return { ok: false, message: adminProblemMessage(404) };
  try {
    const response = await gatewayFetch(`${BASE}/${encodeURIComponent(userId)}`, {
      method: "PATCH",
      headers: JSON_HEADERS,
      body: JSON.stringify(change),
    });
    if (response.ok) {
      revalidatePath("/admin/users");
      return { ok: true, message: success };
    }
    return { ok: false, message: adminProblemMessage(response.status, await problemCode(response)) };
  } catch {
    return { ok: false, message: adminProblemMessage(503) };
  }
}

export async function setAccountEnabled(userId: string, enabled: boolean, name: string): Promise<UserActionResult> {
  return patchUser(userId, { enabled }, enabled ? `${name} can sign in again.` : `${name} is disabled and signed out.`);
}

export async function setStaffRoles(userId: string, roles: string[], name: string): Promise<UserActionResult> {
  const staff = STAFF_ROLES.filter((role) => roles.includes(role));
  return patchUser(userId, { roles: staff }, `${name}'s roles are updated.`);
}

export async function resendInvitation(userId: string, email: string): Promise<UserActionResult> {
  if (!USER_ID.test(userId)) return { ok: false, message: adminProblemMessage(404) };
  try {
    const response = await gatewayFetch(`${BASE}/${encodeURIComponent(userId)}/invitation`, {
      method: "POST",
      headers: { Accept: "application/json" },
    });
    if (response.status === 202) return { ok: true, message: `A new set-your-password e-mail was sent to ${email}.` };
    return { ok: false, message: adminProblemMessage(response.status, await problemCode(response)) };
  } catch {
    return { ok: false, message: adminProblemMessage(503) };
  }
}
