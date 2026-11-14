"use server";

import { gatewayFetch } from "@/lib/gateway";
import { stateFromResponse, validateProfileInput, type ProfileFormState } from "@/lib/profile";

/**
 * Saves the caller's name and phone. Runs on the server: the access token is read from the session
 * cookie here and never reaches the browser. user-service validates again and decides who the caller is.
 */
export async function updateProfile(_previous: ProfileFormState, formData: FormData): Promise<ProfileFormState> {
  const checked = validateProfileInput(formData.get("fullName"), formData.get("phone"));
  if (!checked.ok) {
    return { status: "error", message: "Please fix the highlighted field.", fieldErrors: checked.fieldErrors };
  }

  try {
    const response = await gatewayFetch("/api/v1/users/me", {
      method: "PUT",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(checked.input),
    });
    const body: unknown = await response.json().catch(() => null);
    return stateFromResponse(response.status, body);
  } catch {
    return stateFromResponse(503, null);
  }
}
