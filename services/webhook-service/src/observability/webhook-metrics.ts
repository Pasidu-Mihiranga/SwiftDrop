import { createCounter, createHistogram } from "@swiftdrop/common/nest";

// Metric objects stay private; callers use the functions below (keeps the Prometheus types out of this package).
const attempts = createCounter({
  name: "webhook_delivery_attempts_total",
  help: "Webhook delivery attempts, by what happened: delivered, retry, dead, held or skipped",
  labelNames: ["outcome"],
});
const latency = createHistogram({
  name: "webhook_delivery_latency_seconds",
  help: "Time a merchant endpoint took to answer (or until the timeout), by result",
  labelNames: ["result"],
  buckets: [0.05, 0.1, 0.25, 0.5, 1, 2, 5, 10],
});

const OUTCOMES = ["delivered", "retry", "dead", "held", "skipped"] as const;
export type AttemptOutcome = (typeof OUTCOMES)[number];

// Every outcome exists at zero from the start, so dashboards and increase() see the first event too.
for (const outcome of OUTCOMES) attempts.inc({ outcome }, 0);

/** One processed delivery job and how it ended. */
export function countAttempt(outcome: AttemptOutcome): void {
  attempts.inc({ outcome });
}

/** An HTTP request to a merchant endpoint: success is a 2xx answer, anything else is a failure. */
export function observeRequest(success: boolean, latencyMs: number): void {
  latency.observe({ result: success ? "success" : "failure" }, latencyMs / 1000);
}
