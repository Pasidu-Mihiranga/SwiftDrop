import type { SendResult, WebhookSender } from "../application/ports";

/**
 * POSTs with the built-in fetch. Redirects are not followed: a redirect could lead to an internal
 * address that the URL check never saw, so it counts as a failed attempt.
 */
export class FetchWebhookSender implements WebhookSender {
  async post(url: string, body: string, headers: Record<string, string>, timeoutMs: number): Promise<SendResult> {
    const started = performance.now();
    try {
      const response = await fetch(url, { method: "POST", body, headers, redirect: "manual", signal: AbortSignal.timeout(timeoutMs) });
      await response.body?.cancel();
      return { httpStatus: response.status, latencyMs: Math.round(performance.now() - started) };
    } catch (error) {
      const timedOut = error instanceof Error && (error.name === "TimeoutError" || error.name === "AbortError");
      return { latencyMs: Math.round(performance.now() - started), error: timedOut ? `No answer within ${timeoutMs} ms` : "Connection failed" };
    }
  }
}
