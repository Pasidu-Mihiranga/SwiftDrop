import { type ContractName, contractExample, createEnvelope, Topics, validateEvent } from "../src";

const contracts: ContractName[] = [
  "parcel-created.v1",
  "parcel-status-changed.v1",
  "parcel-assigned.v1",
  "merchant-lifecycle.v1",
  "webhook-dead-letter.v1",
];

describe("event contracts", () => {
  it.each(contracts)("example of %s matches its JSON Schema", (contract) => {
    const result = validateEvent(contract, contractExample(contract));
    expect(result.errors).toEqual([]);
    expect(result.valid).toBe(true);
  });

  it("validates envelopes built in Node", () => {
    const event = createEnvelope(
      "WebhookDeliveryDeadLettered",
      {
        deliveryId: "whd_1",
        merchantId: "00000000-0000-4000-8000-000000000001",
        endpointId: "whe_1",
        sourceEventId: "9a2e5b1c-8d3f-4e6a-b7c8-d9e0f1a2b3c4",
        sourceEventType: "ParcelStatusChanged",
        attempts: 7,
        lastHttpStatus: 503,
        lastError: "503 Service Unavailable",
        deadLetteredAt: new Date().toISOString(),
      },
      "webhook-service",
      "corr-1",
    );
    expect(validateEvent("webhook-dead-letter.v1", event).valid).toBe(true);
    expect(event.eventVersion).toBe(1);
  });

  it("rejects a FAILED status change without a failure reason", () => {
    const example = contractExample("parcel-status-changed.v1") as { data: Record<string, unknown> };
    const invalid = { ...example, data: { ...example.data, toStatus: "FAILED", failureReason: null, proof: null } };
    const result = validateEvent("parcel-status-changed.v1", invalid);
    expect(result.valid).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it("rejects events from the wrong producer", () => {
    const example = contractExample("parcel-assigned.v1") as Record<string, unknown>;
    expect(validateEvent("parcel-assigned.v1", { ...example, producer: "parcel-service" }).valid).toBe(false);
  });

  it("uses the same topic names as the Java services", () => {
    expect(Topics.PARCEL_STATUS_CHANGED).toBe("swiftdrop.parcel.status-changed.v1");
    expect(Topics.PARCEL_ASSIGNED).toBe("swiftdrop.dispatch.parcel-assigned.v1");
  });

  it("fails loudly for unknown contracts", () => {
    expect(() => validateEvent("nope.v1" as ContractName, {})).toThrow("Unknown event contract");
  });
});
