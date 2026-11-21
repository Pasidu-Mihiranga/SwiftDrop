import { Kafka } from "kafkajs";
import { randomUUID } from "node:crypto";

const kafka = new Kafka({
  clientId: "demo-emitter",
  brokers: ["localhost:9092"],
});

async function main() {
  const producer = kafka.producer();
  await producer.connect();
  console.log("Connected to Kafka at localhost:9092");

  const trackingNumber = "SD261115W5N9K4W";
  const customerId = "10000000-0000-4000-8000-000000000005";

  // First emit ParcelCreated so contacts are registered
  const createdEvent = {
    eventId: randomUUID(),
    eventType: "ParcelCreated",
    source: "parcel-service",
    timestamp: new Date().toISOString(),
    correlationId: randomUUID(),
    data: {
      parcelId: "d0000000-0000-4000-8000-000000000001",
      trackingNumber,
      source: "CUSTOMER_WEB",
      customerId,
      pickup: { city: "Colombo 03", district: "Colombo", zoneCode: "COL-03" },
      dropoff: { city: "Kandy", district: "Kandy", zoneCode: "KDY-01" },
      fee: { amount: 650.0, currency: "LKR" },
      contacts: {
        senderName: "Nimali Customer",
        senderEmail: "nimali.customer@swiftdrop.lk",
        senderPhone: "0771234567",
        recipientName: "Kamal Perera",
        recipientPhone: "0719876543",
      },
      createdAt: new Date().toISOString(),
    },
  };

  console.log("Sending ParcelCreated event...");
  await producer.send({
    topic: "swiftdrop.parcel.created.v1",
    messages: [
      {
        key: trackingNumber,
        value: JSON.stringify(createdEvent),
        headers: {
          "swiftdrop-event-id": createdEvent.eventId,
          "swiftdrop-event-type": createdEvent.eventType,
        },
      },
    ],
  });
  console.log("ParcelCreated event sent!");

  // Wait 1.5 seconds
  await new Promise((r) => setTimeout(r, 1500));

  // Now emit DELIVERED status event
  const deliveredEvent = {
    eventId: randomUUID(),
    eventType: "ParcelStatusChanged",
    source: "parcel-service",
    timestamp: new Date().toISOString(),
    correlationId: randomUUID(),
    data: {
      parcelId: "d0000000-0000-4000-8000-000000000001",
      trackingNumber,
      customerId,
      courierId: "c0000000-0000-4000-8000-000000000001",
      fromStatus: "OUT_FOR_DELIVERY",
      toStatus: "DELIVERED",
      note: "Left with recipient at front reception",
      proof: {
        type: "SIGNATURE",
        receivedBy: "Kamal Perera",
        proofId: "prf-" + randomUUID().slice(0, 8),
      },
      changedAt: new Date().toISOString(),
    },
  };

  console.log("Sending ParcelStatusChanged (DELIVERED) event...");
  await producer.send({
    topic: "swiftdrop.parcel.status-changed.v1",
    messages: [
      {
        key: trackingNumber,
        value: JSON.stringify(deliveredEvent),
        headers: {
          "swiftdrop-event-id": deliveredEvent.eventId,
          "swiftdrop-event-type": deliveredEvent.eventType,
        },
      },
    ],
  });
  console.log("ParcelStatusChanged event sent!");

  await producer.disconnect();
}

main().catch((err) => {
  console.error("Error emitting events:", err);
  process.exit(1);
});
