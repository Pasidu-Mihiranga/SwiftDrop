"use server";

import { revalidatePath } from "next/cache";
import { gatewayFetch } from "@/lib/gateway";
import {
  addressStateFromResponse,
  problemMessage,
  validateAddressInput,
  type AddressFormState,
  type SavedAddress,
} from "@/lib/addresses";

const BASE = "/api/v1/users/me/addresses";
const JSON_HEADERS = { "Content-Type": "application/json", Accept: "application/json" };
const ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export interface AddressActionResult {
  ok: boolean;
  message: string;
}

async function body(response: Response): Promise<{ code?: string } | null> {
  return (await response.json().catch(() => null)) as { code?: string } | null;
}

/**
 * Adds an address, or replaces one when the form carries an id. Runs on the server: the token never
 * reaches the browser, and user-service decides whose address book this is from the token alone.
 */
export async function saveAddress(_previous: AddressFormState, formData: FormData): Promise<AddressFormState> {
  const checked = validateAddressInput({
    label: formData.get("label"),
    line1: formData.get("line1"),
    line2: formData.get("line2"),
    city: formData.get("city"),
    district: formData.get("district"),
    postalCode: formData.get("postalCode"),
    isDefault: formData.get("isDefault"),
  });
  if (!checked.ok) return { status: "error", message: "Please fix the highlighted fields.", fieldErrors: checked.fieldErrors };

  const id = String(formData.get("id") ?? "");
  if (id && !ID.test(id)) return { status: "error", message: problemMessage(404) };
  try {
    const response = await gatewayFetch(id ? `${BASE}/${id}` : BASE, {
      method: id ? "PUT" : "POST",
      headers: JSON_HEADERS,
      body: JSON.stringify(checked.input),
    });
    const state = addressStateFromResponse(response.status, await body(response));
    if (state.status === "saved") revalidatePath("/account");
    return state;
  } catch {
    return addressStateFromResponse(503, null);
  }
}

export async function deleteAddress(id: string): Promise<AddressActionResult> {
  if (!ID.test(id)) return { ok: false, message: problemMessage(404) };
  try {
    const response = await gatewayFetch(`${BASE}/${id}`, { method: "DELETE", headers: { Accept: "application/json" } });
    if (response.status === 204) {
      revalidatePath("/account");
      return { ok: true, message: "Address deleted." };
    }
    return { ok: false, message: problemMessage(response.status, (await body(response))?.code) };
  } catch {
    return { ok: false, message: problemMessage(503) };
  }
}

/** PUT replaces every field, so the stored address is read first and sent back with isDefault set. */
export async function makeDefaultAddress(id: string): Promise<AddressActionResult> {
  if (!ID.test(id)) return { ok: false, message: problemMessage(404) };
  try {
    const listed = await gatewayFetch(BASE, { headers: { Accept: "application/json" } });
    if (!listed.ok) return { ok: false, message: problemMessage(listed.status) };
    const current = ((await listed.json()) as SavedAddress[]).find((address) => address.id === id);
    if (!current) return { ok: false, message: problemMessage(404) };

    const response = await gatewayFetch(`${BASE}/${id}`, {
      method: "PUT",
      headers: JSON_HEADERS,
      body: JSON.stringify({
        label: current.label,
        line1: current.line1,
        line2: current.line2,
        city: current.city,
        district: current.district,
        postalCode: current.postalCode,
        lat: current.lat,
        lng: current.lng,
        isDefault: true,
      }),
    });
    if (response.ok) {
      revalidatePath("/account");
      return { ok: true, message: `${current.label} is now your default address.` };
    }
    return { ok: false, message: problemMessage(response.status, (await body(response))?.code) };
  } catch {
    return { ok: false, message: problemMessage(503) };
  }
}
