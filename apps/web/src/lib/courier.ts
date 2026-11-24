/**
 * Courier operations (M4): which steps a courier may take next, the proof and failure rules, and
 * the calls to parcel-service through the BFF. The rules mirror parcel-service's lifecycle
 * (`domain.state.ParcelStates`) so the screen only offers steps the server will accept; the server
 * still decides.
 */

export type ParcelStatus =
  | "CREATED"
  | "PICKED_UP"
  | "IN_TRANSIT"
  | "OUT_FOR_DELIVERY"
  | "DELIVERED"
  | "FAILED"
  | "CANCELLED";

export type FailureReason = "RECIPIENT_UNAVAILABLE" | "WRONG_ADDRESS" | "REFUSED" | "DAMAGED" | "OTHER";

export interface CourierParcel {
  id: string;
  trackingNumber: string;
  status: ParcelStatus;
  pickup?: { line1?: string; line2?: string; city?: string; district?: string };
  dropoff?: { line1?: string; line2?: string; city?: string; district?: string; postalCode?: string };
  recipient?: { name?: string; phone?: string };
  weightKg?: number;
  sizeClass?: string;
  notes?: string;
  history?: { toStatus: ParcelStatus; occurredAt: string; note?: string; failureReason?: FailureReason }[];
}

/** The forward lifecycle, in order, for the stepper. */
export const LIFECYCLE: ParcelStatus[] = ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"];

const COURIER_NEXT: Record<ParcelStatus, ParcelStatus[]> = {
  CREATED: ["PICKED_UP"],
  PICKED_UP: ["IN_TRANSIT"],
  IN_TRANSIT: ["OUT_FOR_DELIVERY"],
  OUT_FOR_DELIVERY: ["DELIVERED", "FAILED"],
  // A failed delivery is sent out again by dispatch, not by the courier.
  FAILED: [],
  DELIVERED: [],
  CANCELLED: [],
};

/** The steps a courier may take from this status. */
export function nextSteps(status: ParcelStatus): ParcelStatus[] {
  return COURIER_NEXT[status] ?? [];
}

export const STATUS_LABELS: Record<ParcelStatus, string> = {
  CREATED: "Booked",
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  FAILED: "Delivery failed",
  CANCELLED: "Cancelled",
};

/** What the button for a step says. */
export const ACTION_LABELS: Partial<Record<ParcelStatus, string>> = {
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  FAILED: "Could not deliver",
};

export const FAILURE_REASONS: { value: FailureReason; label: string }[] = [
  { value: "RECIPIENT_UNAVAILABLE", label: "Nobody available to receive it" },
  { value: "WRONG_ADDRESS", label: "Wrong or incomplete address" },
  { value: "REFUSED", label: "Recipient refused it" },
  { value: "DAMAGED", label: "Parcel damaged" },
  { value: "OTHER", label: "Something else (add a note)" },
];

/** Where a status sits on the stepper: done, current or still to come. FAILED and CANCELLED sit at their step. */
export function stepState(step: ParcelStatus, current: ParcelStatus): "done" | "current" | "todo" {
  const position = (status: ParcelStatus) =>
    status === "FAILED" ? LIFECYCLE.indexOf("OUT_FOR_DELIVERY") : status === "CANCELLED" ? 0 : LIFECYCLE.indexOf(status);
  const here = position(current);
  const at = LIFECYCLE.indexOf(step);
  if (at < here) return "done";
  if (at === here) return current === "DELIVERED" ? "done" : "current";
  return "todo";
}

export const MAX_PROOF_BYTES = 5 * 1024 * 1024;
const PROOF_TYPES = ["image/jpeg", "image/png"];

/** A message for a photo the server would refuse, or null when it is fine. */
export function proofFileProblem(file: { type: string; size: number }): string | null {
  if (!PROOF_TYPES.includes(file.type)) return "Use a JPEG or PNG photo.";
  if (file.size <= 0) return "The photo is empty. Take it again.";
  if (file.size > MAX_PROOF_BYTES) return "The photo is larger than 5 MB. Take it again at a lower size.";
  return null;
}

export interface DeliveryForm {
  receivedBy: string;
  proofId: string | null;
}

export interface FailureForm {
  reason: FailureReason | "";
  note: string;
}

/** The PATCH body for a step, or an error message when the form is incomplete. */
export function statusUpdateBody(
  to: ParcelStatus,
  forms: { delivery?: DeliveryForm; failure?: FailureForm; note?: string } = {},
): { body?: Record<string, unknown>; error?: string } {
  const note = forms.note?.trim() || undefined;
  if (to === "FAILED") {
    const failure = forms.failure;
    if (!failure?.reason) return { error: "Choose why the delivery failed." };
    if (failure.reason === "OTHER" && !failure.note.trim()) return { error: "Add a note saying what happened." };
    return { body: { toStatus: to, failureReason: failure.reason, note: failure.note.trim() || undefined } };
  }
  if (to === "DELIVERED") {
    const delivery = forms.delivery;
    const name = delivery?.receivedBy.trim() ?? "";
    if (delivery?.proofId) {
      return { body: { toStatus: to, note, proof: { type: "PHOTO", proofId: delivery.proofId, receivedBy: name || undefined } } };
    }
    if (name) return { body: { toStatus: to, note, proof: { type: "NAME", receivedBy: name } } };
    return { error: "Enter who received the parcel, or take a photo." };
  }
  return { body: { toStatus: to, note } };
}

const MESSAGES: Record<string, string> = {
  INVALID_STATUS_TRANSITION: "That step is not possible any more. The parcel was updated elsewhere; reload it.",
  FAILURE_REASON_REQUIRED: "Choose why the delivery failed.",
  DELIVERY_PROOF_REQUIRED: "A delivery needs the recipient's name or a photo.",
  PROOF_NOT_FOUND: "The photo did not reach the server. Take it again.",
  PROOF_TYPE_NOT_ALLOWED: "Use a JPEG or PNG photo.",
  PROOF_TOO_LARGE: "The photo is larger than 5 MB.",
  PARCEL_MODIFIED: "Someone else changed this parcel. Reload it and try again.",
  NOT_FOUND: "This parcel is not assigned to you.",
  RESOURCE_NOT_FOUND: "This parcel is not assigned to you.",
};

/** A failed call, with a message a courier can act on. */
export class CourierError extends Error {
  constructor(
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "CourierError";
  }
}

async function problem(response: Response): Promise<CourierError> {
  let code = response.status === 404 ? "NOT_FOUND" : "UNEXPECTED";
  let detail: string | undefined;
  try {
    const body = (await response.json()) as { code?: string; detail?: string };
    code = body.code ?? code;
    detail = body.detail;
  } catch {
    // not problem+json
  }
  return new CourierError(code, MESSAGES[code] ?? detail ?? "Something went wrong. Please try again.");
}

async function call<T>(path: string, init: RequestInit, fetchImpl: typeof fetch): Promise<T> {
  let response: Response;
  try {
    response = await fetchImpl(`/api/bff/api/v1/${path}`, { cache: "no-store", ...init });
  } catch {
    throw new CourierError("UNREACHABLE", "No connection. Check your signal and try again.");
  }
  if (!response.ok) throw await problem(response);
  return (await response.json()) as T;
}

const JSON_HEADERS = { "content-type": "application/json" };

export function loadParcel(id: string, fetchImpl: typeof fetch = fetch): Promise<CourierParcel> {
  return call<CourierParcel>(`parcels/${encodeURIComponent(id)}`, { method: "GET" }, fetchImpl);
}

export function updateStatus(id: string, body: Record<string, unknown>, fetchImpl: typeof fetch = fetch): Promise<CourierParcel> {
  return call<CourierParcel>(`courier/parcels/${encodeURIComponent(id)}/status`,
    { method: "PATCH", headers: JSON_HEADERS, body: JSON.stringify(body) }, fetchImpl);
}

export function requestProofUpload(id: string, file: { type: string; size: number }, fetchImpl: typeof fetch = fetch) {
  return call<{ proofId: string; uploadUrl: string; expiresAt: string }>(
    `courier/parcels/${encodeURIComponent(id)}/proof-upload-url`,
    { method: "POST", headers: JSON_HEADERS, body: JSON.stringify({ contentType: file.type, sizeBytes: file.size }) },
    fetchImpl,
  );
}

/** Minimal XMLHttpRequest surface, so the upload can be tested without a browser. */
export interface UploadRequest {
  upload: { onprogress: ((event: { loaded: number; total: number; lengthComputable: boolean }) => void) | null };
  onload: (() => void) | null;
  onerror: (() => void) | null;
  status: number;
  open(method: string, url: string): void;
  setRequestHeader(name: string, value: string): void;
  send(body: Blob): void;
}

/**
 * PUTs the photo straight to object storage (the presigned URL), reporting progress from 0 to 100.
 * fetch cannot report upload progress, so this uses XMLHttpRequest.
 */
export function uploadProof(
  url: string,
  file: Blob & { type: string },
  onProgress: (percent: number) => void,
  makeRequest: () => UploadRequest = () => new XMLHttpRequest() as unknown as UploadRequest,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const request = makeRequest();
    request.open("PUT", url);
    request.setRequestHeader("Content-Type", file.type);
    request.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) onProgress(Math.round((event.loaded / event.total) * 100));
    };
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        onProgress(100);
        resolve();
      } else {
        reject(new CourierError("UPLOAD_FAILED", "The photo could not be uploaded. Try again."));
      }
    };
    request.onerror = () => reject(new CourierError("UPLOAD_FAILED", "The photo could not be uploaded. Check your signal."));
    request.send(file);
  });
}
