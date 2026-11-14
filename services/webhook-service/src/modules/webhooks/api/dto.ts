import { ApiError } from "@swiftdrop/common";
import { z } from "zod";
import { WEBHOOK_EVENT_TYPES, type WebhookDelivery, type WebhookEndpoint } from "../domain/types";

export const registerEndpointSchema = z.object({
  url: z.string().trim().min(1).max(2048),
  eventTypes: z.array(z.enum(WEBHOOK_EVENT_TYPES)).min(1),
}).strict();

export const deliveryQuerySchema = z.object({
  status: z.enum(["PENDING", "DELIVERED", "RETRYING", "DEAD"]).optional(),
  page: z.coerce.number().int().min(0).default(0),
  size: z.coerce.number().int().min(1).max(100).default(20),
});

/** Parses input or answers 400 naming the fields; the rejected values are never echoed back. */
export function parse<T>(schema: z.ZodType<T>, input: unknown): T {
  const result = schema.safeParse(input);
  if (result.success) return result.data;
  const fields = [...new Set(result.error.issues.map((issue) => issue.path.join(".") || "body"))];
  throw new ApiError(400, "VALIDATION_FAILED", `One or more fields are invalid: ${fields.join(", ")}.`);
}

export function endpointResponse(endpoint: WebhookEndpoint) {
  return { id: endpoint.id, url: endpoint.url, eventTypes: endpoint.eventTypes, active: endpoint.active,
    createdAt: endpoint.createdAt.toISOString() };
}

export function deliveryResponse(delivery: WebhookDelivery) {
  return {
    id: delivery.id,
    endpointId: delivery.endpointId,
    merchantId: delivery.merchantId,
    eventId: delivery.eventId,
    eventType: delivery.eventType,
    url: delivery.url,
    status: delivery.status,
    attemptCount: delivery.attempts.length,
    attempts: delivery.attempts.map((attempt) => ({ ...attempt, at: attempt.at.toISOString() })),
    nextRetryAt: delivery.nextRetryAt?.toISOString() ?? null,
    createdAt: delivery.createdAt.toISOString(),
    deliveredAt: delivery.deliveredAt?.toISOString() ?? null,
    deadLetteredAt: delivery.deadLetteredAt?.toISOString() ?? null,
  };
}

export function deliveryPageResponse(items: WebhookDelivery[], total: number, page: number, size: number) {
  return { content: items.map(deliveryResponse), page: { page, size, totalElements: total, totalPages: Math.ceil(total / size) } };
}
