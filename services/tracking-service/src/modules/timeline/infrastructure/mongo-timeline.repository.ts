import type { Model } from "mongoose";
import { VersionConflictError, type Timeline } from "../domain/timeline";
import type { TimelineRepositoryPort } from "../timeline.repository.port";

const DUPLICATE_KEY = 11000;

/** Adapter: stores timelines in the `tracking.timelines` collection. */
export class MongoTimelineRepository implements TimelineRepositoryPort {
  constructor(private readonly model: Model<Timeline>) {}

  async findByTrackingNumber(trackingNumber: string): Promise<Timeline | null> {
    const doc = await this.model.findOne({ trackingNumber }, { _id: 0, __v: 0 }).lean<Timeline>().exec();
    return doc ?? null;
  }

  async findByParcelId(parcelId: string): Promise<Timeline | null> {
    const doc = await this.model.findOne({ parcelId }, { _id: 0, __v: 0 }).lean<Timeline>().exec();
    return doc ?? null;
  }

  async save(timeline: Timeline, expectedVersion: number): Promise<void> {
    if (expectedVersion === 0) {
      try {
        await this.model.create(timeline);
      } catch (error) {
        if ((error as { code?: number }).code === DUPLICATE_KEY) throw new VersionConflictError(timeline.trackingNumber);
        throw error;
      }
      return;
    }
    const result = await this.model
      .updateOne({ trackingNumber: timeline.trackingNumber, version: expectedVersion }, { $set: timeline })
      .exec();
    if (result.matchedCount === 0) throw new VersionConflictError(timeline.trackingNumber);
  }
}
