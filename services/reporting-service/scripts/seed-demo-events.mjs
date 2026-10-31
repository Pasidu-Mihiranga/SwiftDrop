#!/usr/bin/env node
// Publishes realistic parcel events to the local Kafka so the reports have data before
// parcel-service and dispatch-service produce real ones.
//
//   node services/reporting-service/scripts/seed-demo-events.mjs [--days 30] [--per-day 25] [--seed 7] [--dry-run]
//
// Needs the infra profile running (`pnpm infra:up`). Events follow contracts/events/*.schema.json.
// The history ends today and ids are derived from --seed, so running it again on the same day with
// the same arguments re-sends the same events and the idempotent consumer ignores them; use another
// --seed to add more parcels.
// Contacts are obviously fake; no real personal data is generated.

import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const args = parseArgs(process.argv.slice(2));
const DAYS = Number(args.days ?? 30);
const PER_DAY = Number(args["per-day"] ?? 25);
const SEED = Number(args.seed ?? 7);
const DRY_RUN = Boolean(args["dry-run"]);
const BOOTSTRAP = args.bootstrap ?? "kafka:19092";
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

const TOPICS = {
  created: "swiftdrop.parcel.created.v1",
  assigned: "swiftdrop.dispatch.parcel-assigned.v1",
  status: "swiftdrop.parcel.status-changed.v1",
};

// Zone codes as seeded by pricing-service (V5__seed_zones_and_districts.sql), weighted by volume.
const DROPOFFS = [
  ["Colombo", "Colombo", "WP", 30], ["Gampaha", "Gampaha", "WP", 12], ["Kandy", "Kandy", "CP", 12],
  ["Galle", "Galle", "SP", 9], ["Matara", "Matara", "SP", 5], ["Kurunegala", "Kurunegala", "NWP", 8],
  ["Jaffna", "Jaffna", "NP", 5], ["Batticaloa", "Batticaloa", "EP", 4], ["Anuradhapura", "Anuradhapura", "NCP", 5],
  ["Badulla", "Badulla", "UP", 4], ["Ratnapura", "Ratnapura", "SGP", 6],
];
const COURIERS = ["demo-courier-1", "demo-courier-2", "demo-courier-3", "demo-courier-4", "demo-courier-5", "demo-courier-6"];
const DISPATCHER = { userId: "demo-dispatcher-1", role: "DISPATCHER" };
const COLOMBO_OFFSET_MIN = 330; // UTC+05:30
const MIN = 60_000;

const random = mulberry32(SEED);
const now = Date.now();
const events = { created: [], assigned: [], status: [] };

for (let d = DAYS - 1; d >= 0; d--) {
  const dayStartUtc = colomboMidnightUtc(now, -d);
  const weekday = new Date(dayStartUtc + COLOMBO_OFFSET_MIN * MIN).getUTCDay();
  const volume = Math.round(PER_DAY * (weekday === 0 ? 0.5 : weekday === 6 ? 0.8 : 1) * (0.8 + random() * 0.4));
  for (let i = 0; i < volume; i++) simulateParcel(dayStartUtc + (8 * 60 + random() * 11 * 60) * MIN);
}

const total = Object.values(events).reduce((n, list) => n + list.length, 0);
for (const [kind, list] of Object.entries(events)) {
  list.sort((a, b) => a.at - b.at);
  const lines = list.map((e) => `${e.key}|${JSON.stringify(e.value)}`).join("\n") + "\n";
  if (DRY_RUN) {
    process.stdout.write(lines);
    continue;
  }
  publish(TOPICS[kind], lines);
  console.log(`published ${list.length} events to ${TOPICS[kind]}`);
}
console.error(`${DRY_RUN ? "generated" : "published"} ${total} events for ${DAYS} days (seed ${SEED})`);

function simulateParcel(createdAt) {
  const parcelId = uuid();
  const tracking = "SD" + new Date(createdAt).toISOString().slice(2, 10).replaceAll("-", "") + code(7);
  const [city, district, zoneCode] = weighted(DROPOFFS);
  const merchant = random() < 0.6;
  const courier = COURIERS[Math.floor(random() * COURIERS.length)];
  // Couriers differ a little in how often their first attempt succeeds.
  const skill = 0.82 + (COURIERS.indexOf(courier) % 3) * 0.05;

  emit("created", createdAt, parcelId, "ParcelCreated", "parcel-service", {
    parcelId, trackingNumber: tracking, source: merchant ? "MERCHANT" : "CUSTOMER",
    customerId: merchant ? null : `demo-customer-${1 + Math.floor(random() * 40)}`,
    merchantId: merchant ? "00000000-0000-4000-8000-000000000001" : null,
    merchantOrderRef: merchant ? `DEMO-${code(6)}` : null,
    pickup: { city: "Colombo 03", district: "Colombo", zoneCode: "WP" },
    dropoff: { city, district, zoneCode },
    sizeClass: ["S", "S", "M", "M", "L", "XL"][Math.floor(random() * 6)],
    weightKg: Math.round((0.2 + random() * 9.8) * 100) / 100,
    serviceLevel: random() < 0.25 ? "EXPRESS" : "STANDARD",
    fee: { amount: 350 + Math.round(random() * 1500), currency: "LKR" },
    contacts: { senderName: "Demo Sender", senderEmail: "sender@example.invalid", senderPhone: null,
      recipientName: "Demo Recipient", recipientPhone: "+94770000000" },
    createdAt: iso(createdAt),
  });

  if (random() < 0.03) {
    status(parcelId, tracking, null, "CREATED", "CANCELLED", createdAt + (20 + random() * 120) * MIN, { role: "CUSTOMER" });
    return;
  }

  let t = createdAt + (30 + random() * 120) * MIN;
  emit("assigned", t, parcelId, "ParcelAssigned", "dispatch-service", {
    assignmentId: uuid(), parcelId, trackingNumber: tracking, courierId: courier, previousCourierId: null,
    runDate: colomboDate(t), assignedBy: DISPATCHER, assignedAt: iso(t),
  });

  let from = "CREATED";
  for (const to of ["PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"]) {
    t += (30 + random() * 180) * MIN;
    from = status(parcelId, tracking, courier, from, to, t);
  }
  for (let attempt = 1; attempt <= 2; attempt++) {
    t += (20 + random() * 120) * MIN;
    if (random() < skill || attempt === 2 && random() < 0.6) {
      status(parcelId, tracking, courier, from, "DELIVERED", t, null, {
        proof: { type: "PHOTO", receivedBy: "Demo Recipient", proofId: `proofs/${parcelId}/demo.jpg` },
      });
      return;
    }
    from = status(parcelId, tracking, courier, from, "FAILED", t, null, {
      failureReason: ["RECIPIENT_UNAVAILABLE", "RECIPIENT_UNAVAILABLE", "WRONG_ADDRESS", "REFUSED"][Math.floor(random() * 4)],
    });
    t += 18 * 60 * MIN; // retried the next day
    from = status(parcelId, tracking, courier, from, "OUT_FOR_DELIVERY", t);
  }
}

function status(parcelId, trackingNumber, courierId, fromStatus, toStatus, at, actor, extra = {}) {
  emit("status", at, parcelId, "ParcelStatusChanged", "parcel-service", {
    parcelId, trackingNumber, customerId: null, merchantId: null, courierId, fromStatus, toStatus,
    note: null, failureReason: null, proof: null, location: null,
    changedBy: { userId: courierId ?? "demo-customer", role: actor?.role ?? "COURIER" },
    changedAt: iso(at), ...extra,
  });
  return toStatus;
}

function emit(kind, at, parcelId, eventType, producer, data) {
  // Draw the ids first so the generator advances the same way whether or not the event is kept.
  const eventId = uuid();
  const correlationId = uuid();
  if (at > now) return; // keep the history in the past
  events[kind].push({
    at, key: parcelId,
    value: { eventId, eventType, eventVersion: 1, occurredAt: iso(at), correlationId, producer, data },
  });
}

function publish(topic, lines) {
  const result = spawnSync("docker", ["compose", "exec", "-T", "kafka", "/opt/kafka/bin/kafka-console-producer.sh",
    "--bootstrap-server", BOOTSTRAP, "--topic", topic, "--property", "parse.key=true", "--property", "key.separator=|"],
  { cwd: REPO_ROOT, input: lines, stdio: ["pipe", "inherit", "inherit"] });
  if (result.status !== 0) {
    console.error(`Publishing to ${topic} failed. Is the infra profile up? (pnpm infra:up)`);
    process.exit(result.status ?? 1);
  }
}

function colomboMidnightUtc(nowMs, dayOffset) {
  const local = new Date(nowMs + COLOMBO_OFFSET_MIN * MIN);
  return Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate() + dayOffset) - COLOMBO_OFFSET_MIN * MIN;
}

function colomboDate(ms) {
  return new Date(ms + COLOMBO_OFFSET_MIN * MIN).toISOString().slice(0, 10);
}

function iso(ms) {
  return new Date(Math.round(ms / 1000) * 1000).toISOString().replace(".000Z", "Z");
}

function weighted(options) {
  let pick = random() * options.reduce((sum, o) => sum + o[3], 0);
  for (const option of options) if ((pick -= option[3]) < 0) return option;
  return options[options.length - 1];
}

function code(length) {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from({ length }, () => alphabet[Math.floor(random() * alphabet.length)]).join("");
}

/** RFC 4122 version-4 layout, but drawn from the seeded generator so reruns are reproducible. */
function uuid() {
  const hex = Array.from({ length: 16 }, () => Math.floor(random() * 256));
  hex[6] = (hex[6] & 0x0f) | 0x40;
  hex[8] = (hex[8] & 0x3f) | 0x80;
  const s = hex.map((b) => b.toString(16).padStart(2, "0")).join("");
  return `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`;
}

function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    if (!argv[i].startsWith("--")) continue;
    const key = argv[i].slice(2);
    out[key] = argv[i + 1] && !argv[i + 1].startsWith("--") ? argv[++i] : true;
  }
  return out;
}
