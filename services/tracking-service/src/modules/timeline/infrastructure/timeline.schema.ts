import { Schema } from "mongoose";
import type { Timeline } from "../domain/timeline";

const eventSchema = new Schema(
  {
    eventId: { type: String, required: true },
    status: { type: String, required: true },
    city: { type: String, default: null },
    at: { type: String, required: true },
  },
  { _id: false },
);

/** Collection `tracking.timelines`. Unique keys: one timeline per tracking number and per parcel. */
export const timelineSchema = new Schema<Timeline>(
  {
    trackingNumber: { type: String, required: true, unique: true },
    parcelId: { type: String, required: true, unique: true },
    ownerIds: { type: [String], default: [], index: true },
    customerId: { type: String, default: null },
    courierId: { type: String, default: null },
    merchantId: { type: String, default: null, index: true },
    fromCity: { type: String, default: null },
    toCity: { type: String, default: null },
    recipientMasked: { type: String, default: null },
    currentStatus: { type: String, default: null },
    lastChangedAt: { type: String, default: null },
    events: { type: [eventSchema], default: [] },
    appliedEventIds: { type: [String], default: [] },
    version: { type: Number, required: true, default: 0 },
  },
  { collection: "timelines", versionKey: false, minimize: false },
);
