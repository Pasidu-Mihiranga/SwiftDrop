import { randomUUID } from "node:crypto";

/** Prefixed ids, as in the contract examples: whe_ for endpoints, whd_ for deliveries. */
export const endpointId = () => `whe_${randomUUID().replaceAll("-", "")}`;
export const deliveryId = () => `whd_${randomUUID().replaceAll("-", "")}`;
