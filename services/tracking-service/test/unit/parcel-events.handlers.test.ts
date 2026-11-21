import { contractExample, type EventEnvelope } from "@swiftdrop/common";
import { trackingHandlers } from "../../src/consumers/parcel-events.consumer";
import type { Timeline } from "../../src/modules/timeline/domain/timeline";
import type { TimelineBroadcaster } from "../../src/modules/timeline/timeline.broadcast.port";
import type { TimelineService } from "../../src/modules/timeline/timeline.service";

const saved = { trackingNumber: "SD261020K7Q2M9X" } as Timeline;

function fakes(result: Timeline | null = saved) {
  const timelines = {
    onCreated: jest.fn().mockResolvedValue(result),
    onStatusChanged: jest.fn().mockResolvedValue(result),
    onAssigned: jest.fn().mockResolvedValue(result),
  };
  const broadcaster: TimelineBroadcaster = { timelineChanged: jest.fn().mockResolvedValue(undefined) };
  const handlers = trackingHandlers(timelines as unknown as TimelineService, broadcaster);
  return { timelines, broadcaster, handlers };
}

const envelope = (eventType: string, contract: "parcel-created.v1" | "parcel-status-changed.v1" | "parcel-assigned.v1") =>
  contractExample(contract) as EventEnvelope<unknown> & { eventType: string };

describe("tracking event handlers", () => {
  it("applies a valid ParcelCreated and broadcasts the saved timeline", async () => {
    const { timelines, broadcaster, handlers } = fakes();
    const event = envelope("ParcelCreated", "parcel-created.v1");
    await handlers.ParcelCreated(event, {} as never);
    expect(timelines.onCreated).toHaveBeenCalledWith(event.data, event.eventId);
    expect(broadcaster.timelineChanged).toHaveBeenCalledWith(saved, "ParcelCreated");
  });

  it("applies a valid ParcelStatusChanged", async () => {
    const { timelines, handlers } = fakes();
    const event = envelope("ParcelStatusChanged", "parcel-status-changed.v1");
    await handlers.ParcelStatusChanged(event, {} as never);
    expect(timelines.onStatusChanged).toHaveBeenCalledWith(event.data, event.eventId);
  });

  it("applies a valid ParcelAssigned", async () => {
    const { timelines, handlers } = fakes();
    const event = envelope("ParcelAssigned", "parcel-assigned.v1");
    await handlers.ParcelAssigned(event, {} as never);
    expect(timelines.onAssigned).toHaveBeenCalledWith(event.data, event.eventId);
  });

  it("rejects a payload that breaks the contract, so the template retries and dead-letters it", async () => {
    const { timelines, handlers } = fakes();
    const bad = { ...envelope("ParcelStatusChanged", "parcel-status-changed.v1"), data: { parcelId: "not-a-uuid" } };
    await expect(handlers.ParcelStatusChanged(bad, {} as never)).rejects.toThrow(/Invalid parcel-status-changed.v1 event/);
    expect(timelines.onStatusChanged).not.toHaveBeenCalled();
  });

  it("does not broadcast when the event was a duplicate", async () => {
    const { broadcaster, handlers } = fakes(null);
    await handlers.ParcelAssigned(envelope("ParcelAssigned", "parcel-assigned.v1"), {} as never);
    expect(broadcaster.timelineChanged).not.toHaveBeenCalled();
  });
});
