import { createCounter, createGauge, createHistogram } from "@swiftdrop/common/nest";

// Metric objects stay private; callers use the functions below (keeps the Prometheus types out of this package).
const eventsProcessed = createCounter({
  name: "tracking_events_processed_total",
  help: "Parcel events handled by the tracking consumer, by outcome",
  labelNames: ["outcome", "topic"],
});
const projectionLatency = createHistogram({
  name: "tracking_projection_latency_seconds",
  help: "Time from an event occurring to its timeline being updated",
  labelNames: ["event"],
  buckets: [0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, 30, 60],
});
const socketConnections = createGauge({
  name: "tracking_socket_connections",
  help: "Live tracking sockets connected to this replica",
});
const rateLimited = createCounter({
  name: "tracking_public_rate_limited_total",
  help: "Public tracking lookups rejected by the per-IP limit",
});

/** Kafka message outcome: handled, duplicate, skipped or dead-lettered. */
export function countOutcome(outcome: string, topic: string): void {
  eventsProcessed.inc({ outcome, topic });
}

/** Seconds from the event occurring to the timeline being saved. */
export function observeProjection(event: string, seconds: number): void {
  projectionLatency.observe({ event }, seconds);
}

export function socketOpened(): void {
  socketConnections.inc();
}

export function socketClosed(): void {
  socketConnections.dec();
}

export function countRateLimited(): void {
  rateLimited.inc();
}
