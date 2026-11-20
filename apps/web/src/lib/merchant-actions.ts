"use server";

import { revalidatePath } from "next/cache";
import { auth } from "@/auth";
import type { Problem } from "@/lib/bff";
import { gatewayFetch } from "@/lib/gateway";
import { MAX_REASON_LENGTH, registrationBody } from "@/lib/merchant";
import type { ActionState } from "@/lib/pricing-actions";

/**
 * Server actions behind merchant onboarding: applying, and the admin decisions on an application.
 *
 * Each one posts to merchant-service through the gateway with the caller's own token, so the
 * service checks the role again; the checks here only avoid a pointless call. The answer is shown
 * as the service gave it, including field-level validation messages.
 */

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Problem codes whose meaning is clearer in our own words than in the service's detail text. */
const MESSAGES: Record<string, string> = {
  MERCHANT_BR_NUMBER_TAKEN: "A business with this registration number has already applied or is already a merchant.",
  MERCHANT_APPLICATION_EXISTS: "You already have an application. You can apply again only if it was rejected.",
  MERCHANT_INVALID_TRANSITION: "That decision is no longer possible: the application has changed. Reload the page.",
  MERCHANT_MODIFIED: "Another admin changed this application first. Reload the page and check it again.",
  MERCHANT_NOT_FOUND: "That application no longer exists.",
};

export async function registerMerchant(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const session = await auth();
  if (!session || session.error) {
    return { status: "error", message: "Sign in to apply." };
  }

  const request = registrationBody(formData);
  if (!request.ok) {
    return { status: "error", message: request.message, errors: request.errors };
  }

  const result = await send("/api/v1/merchants/register", request.body);
  return result.status === "ok"
    ? { status: "ok", message: "Application sent. An administrator will review it." }
    : result;
}

export async function approveMerchant(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const merchantId = readMerchantId(formData);
  if (!merchantId) return INVALID_ID;
  const denied = await requireAdmin();
  if (denied) return denied;

  const result = await send(`/api/v1/admin/merchants/${merchantId}/approve`);
  return result.status === "ok" ? { status: "ok", message: "Approved." } : result;
}

export async function rejectMerchant(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const merchantId = readMerchantId(formData);
  if (!merchantId) return INVALID_ID;
  const reason = String(formData.get("reason") ?? "").trim();
  if (!reason) {
    return { status: "error", message: "Tell the applicant why.", errors: { reason: "A reason is required" } };
  }
  if (reason.length > MAX_REASON_LENGTH) {
    return {
      status: "error",
      message: `Keep the reason to ${MAX_REASON_LENGTH} characters.`,
      errors: { reason: `At most ${MAX_REASON_LENGTH} characters` },
    };
  }
  const denied = await requireAdmin();
  if (denied) return denied;

  const result = await send(`/api/v1/admin/merchants/${merchantId}/reject`, { reason });
  return result.status === "ok" ? { status: "ok", message: "Rejected. The applicant can see your reason." } : result;
}

export async function suspendMerchant(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const merchantId = readMerchantId(formData);
  if (!merchantId) return INVALID_ID;
  if (formData.get("confirm") !== "on") {
    return { status: "error", message: "Tick the box to confirm the suspension." };
  }
  const denied = await requireAdmin();
  if (denied) return denied;

  const result = await send(`/api/v1/admin/merchants/${merchantId}/suspend`);
  return result.status === "ok" ? { status: "ok", message: "Suspended. Its API orders will be refused." } : result;
}

const INVALID_ID: ActionState = { status: "error", message: "That is not a valid merchant id." };

function readMerchantId(formData: FormData): string | null {
  const id = String(formData.get("merchantId") ?? "").trim();
  return UUID.test(id) ? id : null;
}

async function requireAdmin(): Promise<ActionState | null> {
  const session = await auth();
  if (!session || session.error || !session.user.roles.includes("ADMIN")) {
    return { status: "error", message: "Only an administrator can review merchants." };
  }
  return null;
}

/** One place that calls merchant-service and turns a problem+json failure into form state. */
async function send(path: string, body?: unknown): Promise<ActionState> {
  let response: Response;
  try {
    response = await gatewayFetch(path, {
      method: "POST",
      ...(body === undefined ? {} : { headers: { "content-type": "application/json" }, body: JSON.stringify(body) }),
    });
  } catch {
    return { status: "error", message: "The merchant service is unreachable. Please try again." };
  }

  if (response.ok) {
    revalidatePath("/merchant/register");
    revalidatePath("/admin/merchants");
    return { status: "ok" };
  }

  const problem = await readProblem(response);
  return {
    status: "error",
    message: MESSAGES[problem.code] ?? problem.detail ?? `The merchant service rejected that (${problem.code}).`,
    errors: problem.errors
      ? Object.fromEntries(problem.errors.map((entry) => [entry.field, entry.message]))
      : undefined,
  };
}

async function readProblem(response: Response): Promise<Problem> {
  try {
    const body = (await response.json()) as Partial<Problem>;
    return {
      status: body.status ?? response.status,
      code: body.code ?? "UNEXPECTED_ERROR",
      detail: body.detail,
      errors: body.errors,
    };
  } catch {
    return { status: response.status, code: "UNEXPECTED_ERROR" };
  }
}
