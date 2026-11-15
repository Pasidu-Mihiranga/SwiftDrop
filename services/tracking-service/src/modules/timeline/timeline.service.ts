import { Inject, Injectable } from "@nestjs/common";
import type { ParcelAssigned, ParcelCreated, ParcelStatusChanged } from "@swiftdrop/common";
import { applyAssigned, applyCreated, applyStatus } from "./domain/timeline-projector";
import { VersionConflictError, type Timeline } from "./domain/timeline";
import { TIMELINE_REPOSITORY, type TimelineRepositoryPort } from "./timeline.repository.port";

const MAX_ATTEMPTS = 3;

/**
 * Application service: applies one parcel event to its timeline. Each call loads the current
 * timeline, applies the pure projection rule and saves with optimistic concurrency, retrying when
 * another event changed the timeline in between.
 */
@Injectable()
export class TimelineService {
  constructor(@Inject(TIMELINE_REPOSITORY) private readonly repository: TimelineRepositoryPort) {}

  onCreated(event: ParcelCreated, eventId: string): Promise<void> {
    return this.apply(event.trackingNumber, event.parcelId, (current) => applyCreated(current, event, eventId));
  }

  onStatusChanged(event: ParcelStatusChanged, eventId: string): Promise<void> {
    return this.apply(event.trackingNumber, event.parcelId, (current) => applyStatus(current, event, eventId));
  }

  onAssigned(event: ParcelAssigned, eventId: string): Promise<void> {
    return this.apply(event.trackingNumber, event.parcelId, (current) => applyAssigned(current, event, eventId));
  }

  private async apply(
    trackingNumber: string,
    parcelId: string,
    project: (current: Timeline | null) => Timeline,
  ): Promise<void> {
    for (let attempt = 1; ; attempt++) {
      const current = (await this.repository.findByTrackingNumber(trackingNumber)) ??
        (await this.repository.findByParcelId(parcelId));
      const next = project(current);
      if (current && next === current) return; // duplicate event: nothing to save
      try {
        await this.repository.save(next, current?.version ?? 0);
        return;
      } catch (error) {
        if (!(error instanceof VersionConflictError) || attempt === MAX_ATTEMPTS) throw error;
      }
    }
  }
}
