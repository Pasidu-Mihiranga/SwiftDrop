/** Public tracking view, as returned by GET /api/v1/tracking/public/{trackingNumber} and pushed as parcel.status. */

export type ParcelStatus =
  | "CREATED"
  | "PICKED_UP"
  | "IN_TRANSIT"
  | "OUT_FOR_DELIVERY"
  | "DELIVERED"
  | "FAILED"
  | "CANCELLED";

export interface TrackingEvent {
  status: ParcelStatus;
  city: string | null;
  /** ISO-8601 instant. */
  at: string;
}

export interface TrackingView {
  trackingNumber: string;
  currentStatus: ParcelStatus | null;
  from: string | null;
  to: string | null;
  /** Masked name only, e.g. "K*** P.". */
  recipient: string | null;
  events: TrackingEvent[];
}

export const TRACKING_NUMBER_PATTERN = /^SD[0-9A-Z]{8,20}$/;

/** Steps shown on the stepper, in order. FAILED and CANCELLED are shown as separate outcomes. */
export const STEPS = ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"] as const satisfies readonly ParcelStatus[];

export const STATUS_LABELS: Record<ParcelStatus, string> = {
  CREATED: "Created",
  PICKED_UP: "Picked up",
  IN_TRANSIT: "In transit",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  FAILED: "Delivery failed",
  CANCELLED: "Cancelled",
};

export type StepState = "done" | "current" | "todo";

export interface StepperStep {
  status: (typeof STEPS)[number];
  label: string;
  state: StepState;
}

export interface Stepper {
  steps: StepperStep[];
  /** Set when the parcel ended in a non-delivery outcome. */
  outcome: "FAILED" | "CANCELLED" | null;
}

/** Index of the furthest stepper step the parcel has reached, or -1 when none (no events yet). */
function reachedIndex(view: TrackingView): number {
  let furthest = -1;
  for (const event of view.events) {
    const index = (STEPS as readonly ParcelStatus[]).indexOf(event.status);
    if (index > furthest) furthest = index;
  }
  if (view.currentStatus) {
    const index = (STEPS as readonly ParcelStatus[]).indexOf(view.currentStatus);
    if (index > furthest) furthest = index;
  }
  return furthest;
}

export function stepperFor(view: TrackingView): Stepper {
  const reached = reachedIndex(view);
  const outcome = view.currentStatus === "FAILED" || view.currentStatus === "CANCELLED" ? view.currentStatus : null;
  // A failed or cancelled parcel has no "current" step: the furthest reached step is done.
  const currentIndex = outcome ? -1 : reached;
  const steps: StepperStep[] = STEPS.map((status, index) => {
    let state: StepState = "todo";
    if (index === currentIndex) state = "current";
    else if (index <= reached) state = "done";
    return { status, label: STATUS_LABELS[status], state };
  });
  return { steps, outcome };
}

/**
 * Applies a live update. The pushed payload is the whole public view, so the newer view replaces the
 * shown one. Updates for another tracking number, and older ones, are ignored.
 */
export function applyUpdate(current: TrackingView, incoming: TrackingView): TrackingView {
  if (incoming.trackingNumber !== current.trackingNumber) return current;
  const currentAt = current.events.at(-1)?.at ?? "";
  const incomingAt = incoming.events.at(-1)?.at ?? "";
  return incomingAt >= currentAt ? incoming : current;
}

/** "14 Oct 2026, 14:02" in Sri Lanka time, or null when there are no events. */
export function lastUpdateLabel(view: TrackingView): string | null {
  const at = view.events.at(-1)?.at;
  if (!at) return null;
  const date = new Date(at);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Colombo",
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}
