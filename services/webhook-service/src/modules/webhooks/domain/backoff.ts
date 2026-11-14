/** Default retry delays: 30 s, 2 min, 10 min, 30 min, 2 h, 6 h. Configurable, and shortened for the demo. */
export const DEFAULT_BACKOFF_SECONDS = [30, 120, 600, 1800, 7200, 21600] as const;

/**
 * The retry schedule. It is not a pure exponential, so BullMQ is given these values through a custom
 * backoff strategy rather than its built-in "exponential" type.
 */
export class BackoffSchedule {
  private readonly delaysMs: readonly number[];

  constructor(delaysSeconds: readonly number[] = DEFAULT_BACKOFF_SECONDS) {
    if (delaysSeconds.length === 0 || delaysSeconds.some((s) => !Number.isFinite(s) || s < 0)) {
      throw new Error("The retry schedule needs at least one non-negative delay");
    }
    this.delaysMs = delaysSeconds.map((s) => Math.round(s * 1000));
  }

  /** The first POST plus one retry per delay: seven attempts with the default schedule. */
  get maxAttempts(): number {
    return this.delaysMs.length + 1;
  }

  /** Delay before the next attempt after {@code failedAttempts} failures; the last delay repeats if asked beyond it. */
  delayMs(failedAttempts: number): number {
    const index = Math.min(Math.max(failedAttempts, 1), this.delaysMs.length) - 1;
    return this.delaysMs[index];
  }
}
