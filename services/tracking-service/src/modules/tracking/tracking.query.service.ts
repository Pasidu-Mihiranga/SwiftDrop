import { Inject, Injectable } from "@nestjs/common";
import type { AuthenticatedUser } from "@swiftdrop/common";
import { type PublicTrackingView, toPublicView } from "../timeline/domain/public-view";
import { TIMELINE_REPOSITORY, type TimelineRepositoryPort } from "../timeline/timeline.repository.port";
import { canViewTimeline } from "./domain/access-policy";

/** Facade over the timeline read model for the tracking controllers. */
@Injectable()
export class TrackingQueryService {
  constructor(@Inject(TIMELINE_REPOSITORY) private readonly repository: TimelineRepositoryPort) {}

  async publicView(trackingNumber: string): Promise<PublicTrackingView | null> {
    const timeline = await this.repository.findByTrackingNumber(trackingNumber);
    return timeline ? toPublicView(timeline) : null;
  }

  /** Returns null both when the parcel does not exist and when the caller may not see it. */
  async ownerTimeline(parcelId: string, user: AuthenticatedUser): Promise<PublicTrackingView | null> {
    const timeline = await this.repository.findByParcelId(parcelId);
    return timeline && canViewTimeline(timeline, user) ? toPublicView(timeline) : null;
  }
}
