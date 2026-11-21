import type { Timeline } from "./domain/timeline";

/** Port: the application layer depends on this interface, not on MongoDB (dependency inversion). */
export interface TimelineRepositoryPort {
  findByTrackingNumber(trackingNumber: string): Promise<Timeline | null>;
  findByParcelId(parcelId: string): Promise<Timeline | null>;
  /**
   * Saves `timeline`. `expectedVersion` is the version that was read (0 for a new timeline).
   * Throws VersionConflictError when the stored version differs.
   */
  save(timeline: Timeline, expectedVersion: number): Promise<void>;
}

export const TIMELINE_REPOSITORY = Symbol("TIMELINE_REPOSITORY");
