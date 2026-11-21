import { VersionConflictError, type Timeline } from "../../src/modules/timeline/domain/timeline";
import type { TimelineRepositoryPort } from "../../src/modules/timeline/timeline.repository.port";
import { TimelineService } from "../../src/modules/timeline/timeline.service";
import type { ParcelCreated } from "@swiftdrop/common";

/** In-memory repository that can fail the next save with a version conflict, to test the retry. */
function memoryRepository(conflicts = 0) {
  const rows = new Map<string, Timeline>();
  let pendingConflicts = conflicts;
  const repo: TimelineRepositoryPort & { rows: Map<string, Timeline> } = {
    rows,
    findByTrackingNumber: async (no) => rows.get(no) ?? null,
    findByParcelId: async (id) => [...rows.values()].find((t) => t.parcelId === id) ?? null,
    save: async (timeline, expectedVersion) => {
      if (pendingConflicts > 0) {
        pendingConflicts--;
        throw new VersionConflictError(timeline.trackingNumber);
      }
      const stored = rows.get(timeline.trackingNumber);
      if ((stored?.version ?? 0) !== expectedVersion) throw new VersionConflictError(timeline.trackingNumber);
      rows.set(timeline.trackingNumber, timeline);
    },
  };
  return repo;
}

const created: ParcelCreated = {
  parcelId: "p-1",
  trackingNumber: "SD261020K7Q2M9X",
  source: "CUSTOMER",
  customerId: "c-1",
  merchantId: null,
  merchantOrderRef: null,
  pickup: { city: "Colombo", district: "Colombo" },
  dropoff: { city: "Kandy", district: "Kandy" },
  sizeClass: "S",
  weightKg: 1,
  serviceLevel: "STANDARD",
  fee: { amount: 650, currency: "LKR" },
  contacts: { recipientName: "Kamal Perera", recipientPhone: "0771234567", senderName: null, senderEmail: null, senderPhone: null },
  createdAt: "2026-10-20T08:00:00Z",
};

describe("TimelineService", () => {
  it("creates and then reuses the stored timeline", async () => {
    const repo = memoryRepository();
    const service = new TimelineService(repo);
    await service.onCreated(created, "e-1");
    await service.onCreated(created, "e-1"); // redelivery
    expect(repo.rows.get("SD261020K7Q2M9X")?.version).toBe(1);
  });

  it("retries after a version conflict and still stores the change", async () => {
    const repo = memoryRepository(1);
    const service = new TimelineService(repo);
    await service.onCreated(created, "e-1");
    expect(repo.rows.get("SD261020K7Q2M9X")?.events).toHaveLength(1);
  });

  it("gives up after repeated conflicts", async () => {
    const repo = memoryRepository(5);
    const service = new TimelineService(repo);
    await expect(service.onCreated(created, "e-1")).rejects.toBeInstanceOf(VersionConflictError);
  });
});
