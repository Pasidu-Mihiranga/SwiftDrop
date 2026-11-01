"use server";

import { revalidatePath } from "next/cache";
import { auth } from "@/auth";
import { gatewayFetch } from "@/lib/gateway";
import type { Problem } from "@/lib/bff";

/**
 * Server actions behind the admin pricing forms.
 *
 * These post to pricing-service through the gateway with the administrator's own token, so the
 * service re-checks the ADMIN role itself. Returned state is plain data: the form re-renders with
 * whatever the service said rather than guessing.
 */

export interface ActionState {
  status: "idle" | "ok" | "error";
  message?: string;
  /** Field-level messages from a problem+json validation response. */
  errors?: Record<string, string>;
}

export async function assignDistrict(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const district = String(formData.get("district") ?? "").trim();
  const zoneCode = String(formData.get("zoneCode") ?? "").trim();
  if (!district || !zoneCode) {
    return { status: "error", message: "Pick a district and a zone." };
  }

  const result = await send(`/api/v1/admin/pricing/districts/${encodeURIComponent(district)}`, "PUT", { zoneCode });
  if (result.status === "ok") {
    return { status: "ok", message: `${district} now belongs to ${zoneCode}.` };
  }
  return result;
}

export async function createZone(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const code = String(formData.get("code") ?? "").trim().toUpperCase();
  const name = String(formData.get("name") ?? "").trim();
  if (!code || !name) {
    return { status: "error", message: "A zone needs a code and a name." };
  }

  const result = await send("/api/v1/admin/pricing/zones", "POST", { code, name, active: true });
  return result.status === "ok" ? { status: "ok", message: `Zone ${code} created.` } : result;
}

export async function updateZone(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const zoneId = String(formData.get("zoneId") ?? "");
  const name = String(formData.get("name") ?? "").trim();
  const active = formData.get("active") === "on";
  if (!zoneId || !name) {
    return { status: "error", message: "A zone needs a name." };
  }

  const result = await send(`/api/v1/admin/pricing/zones/${encodeURIComponent(zoneId)}`, "PUT", { name, active });
  return result.status === "ok" ? { status: "ok", message: `Zone ${name} updated.` } : result;
}

export async function createRateCard(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const requiredAmounts = ["baseFee", "perKgFee", "expressMultiplier", "codFeePct"];
  for (const field of requiredAmounts) {
    if (String(formData.get(field) ?? "").trim() === "") {
      return { status: "error", message: "Enter every fee and multiplier.", errors: { [field]: "required" } };
    }
  }
  const body = {
    fromZone: String(formData.get("fromZone") ?? "").trim().toUpperCase(),
    toZone: String(formData.get("toZone") ?? "").trim().toUpperCase(),
    baseFee: Number(formData.get("baseFee")),
    perKgFee: Number(formData.get("perKgFee")),
    expressMultiplier: Number(formData.get("expressMultiplier")),
    codFeePct: Number(formData.get("codFeePct")),
    effectiveFrom: String(formData.get("effectiveFrom") ?? ""),
    effectiveTo: emptyToNull(formData.get("effectiveTo")),
  };

  if (!body.fromZone || !body.toZone || !body.effectiveFrom) {
    return { status: "error", message: "Choose both zones and a start date." };
  }
  for (const [field, value] of Object.entries(body)) {
    if (typeof value === "number" && !Number.isFinite(value)) {
      return { status: "error", message: "Every fee must be a number.", errors: { [field]: "must be a number" } };
    }
  }

  const result = await send("/api/v1/admin/pricing/rate-cards", "POST", body);
  return result.status === "ok"
    ? { status: "ok", message: `New rate for ${body.fromZone} to ${body.toZone} takes effect ${body.effectiveFrom}.` }
    : result;
}

export async function closeRateCard(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const rateCardId = String(formData.get("rateCardId") ?? "");
  const effectiveTo = String(formData.get("effectiveTo") ?? "");
  if (!rateCardId || !effectiveTo) {
    return { status: "error", message: "Choose the date the rate should stop applying." };
  }

  const result = await send(
    `/api/v1/admin/pricing/rate-cards/${encodeURIComponent(rateCardId)}/closure`,
    "PUT",
    { effectiveTo },
  );
  return result.status === "ok" ? { status: "ok", message: `Rate closed from ${effectiveTo}.` } : result;
}

export async function updateSizeClass(_previous: ActionState, formData: FormData): Promise<ActionState> {
  const code = String(formData.get("code") ?? "").trim().toUpperCase();
  const multiplier = Number(formData.get("multiplier"));
  if (!code || !Number.isFinite(multiplier)) {
    return { status: "error", message: "A size class needs a numeric multiplier." };
  }

  // The three sides go together or not at all, matching the API. A class with two of them
  // would have no enforceable shape.
  const sides = ["maxLengthCm", "maxWidthCm", "maxHeightCm"] as const;
  const given = sides.map((side) => emptyToNull(formData.get(side)));
  if (given.some((side) => side !== null) && given.some((side) => side === null)) {
    return { status: "error", message: "Give all three measurements, or leave all blank." };
  }
  const dimensions = given[0] === null
    ? {}
    : Object.fromEntries(sides.map((side, index) => [side, Number(given[index])]));
  if (Object.values(dimensions).some((side) => !Number.isFinite(side) || (side as number) <= 0)) {
    return { status: "error", message: "Each measurement must be more than 0 cm." };
  }

  const result = await send(`/api/v1/admin/pricing/size-classes/${encodeURIComponent(code)}`, "PUT",
    { multiplier, ...dimensions });
  return result.status === "ok" ? { status: "ok", message: `Size ${code} updated.` } : result;
}

function emptyToNull(value: FormDataEntryValue | null): string | null {
  const text = String(value ?? "").trim();
  return text === "" ? null : text;
}

/** One place that knows how to call the service and read a problem+json failure. */
async function send(path: string, method: "POST" | "PUT", body: unknown): Promise<ActionState> {
  const session = await auth();
  if (!session || session.error || !session.user.roles.includes("ADMIN")) {
    return { status: "error", message: "You do not have permission to change pricing." };
  }

  let response: Response;
  try {
    response = await gatewayFetch(path, {
      method,
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch {
    return { status: "error", message: "The pricing service is unreachable. Please try again." };
  }

  if (response.ok) {
    // Pricing data drives both admin screens and the public coverage list.
    revalidatePath("/admin/pricing/zones");
    revalidatePath("/admin/pricing/rate-cards");
    revalidatePath("/quote");
    return { status: "ok" };
  }

  const problem = await readProblem(response);
  return {
    status: "error",
    message: problem.detail ?? `The pricing service rejected that (${problem.code}).`,
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
