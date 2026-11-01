"""Writes the M8 figures: <name>.svg and <name>.png next to this folder, and the .mmd for sequences.

Uses the drawing primitives in ../../m10/src. PNGs need Google Chrome and Pillow.
Usage: python3 build_m8.py [--no-png]
"""

import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parent
sys.path.insert(0, str(HERE.parent.parent / "m10" / "src"))

from draw import (BLACK, FILL, GREY, Sequence, band, container, cylinder, label, node, path,  # noqa: E402
                  shape_rect, stickman, svg, text)
from activity import Activity  # noqa: E402

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


# ---------------------------------------------------------------- sequences (one script, two outputs)

BFF = "Merchant page|+ BFF"
GW = "api-gateway"
WH = "webhook-service"
MDB = "MongoDB|webhooks"
RDS = "Redis|BullMQ"
KFK = "Kafka"
EP = "Merchant|endpoint"
ADM = "Admin page|+ BFF"
SIM = "merchant-simulator"
KC = "Keycloak"
PS = "parcel-service"

SEQ = {
    "sequence-m8-endpoint-registration": ("M8 sequence: register an endpoint and send a test ping", [("Merchant", True), (BFF, False), (GW, False), (WH, False), (MDB, False), (RDS, False)], [
        ("m", "Merchant", BFF, "enter https URL and event types"),
        ("m", BFF, GW, "POST /api/v1/webhooks/endpoints (bearer, via BFF)"),
        ("m", GW, WH, "forward (JWT valid)"),
        ("m", WH, WH, "role MERCHANT; merchant_id comes from the token, never the body"),
        ("m", WH, WH, "validate URL: https in production, resolve host, block private, loopback and link-local IPs"),
        ("alt", "URL rejected or body invalid"),
        ("r", WH, BFF, "400 problem+json"),
        ("else", "URL accepted"),
        ("m", WH, WH, "generate random secret, encrypt it for storage"),
        ("m", WH, MDB, "insert endpoint (secret_enc, event_types, active)"),
        ("r", MDB, WH, "endpointId"),
        ("r", WH, BFF, "201 endpoint + secret (returned only this once)"),
        ("r", BFF, "Merchant", "show the secret once, with a copy button"),
        ("end",),
        ("m", "Merchant", BFF, "click Send test ping"),
        ("m", BFF, WH, "POST /endpoints/{id}/test via gateway"),
        ("m", WH, MDB, "find endpoint by id AND merchant_id"),
        ("r", MDB, WH, "endpoint, or nothing"),
        ("alt", "other merchant's endpoint or unknown id"),
        ("r", WH, BFF, "404 problem+json (existence is not revealed)"),
        ("else", "own endpoint"),
        ("m", WH, MDB, "insert delivery (status PENDING)"),
        ("m", WH, RDS, "enqueue delivery job"),
        ("r", WH, BFF, "202 Queued; the worker sends the signed ping"),
        ("end",),
    ]),
    "sequence-m8-signed-delivery": ("M8 sequence: parcel event to signed webhook", [(PS, False), (KFK, False), (WH, False), (MDB, False), (RDS, False), (EP, False)], [
        ("m", PS, KFK, "outbox relay publishes ParcelStatusChanged (key parcelId)"),
        ("m", KFK, WH, "deliver to group webhook-service"),
        ("m", WH, MDB, "markIfFirst(eventId, consumer): atomic insert"),
        ("r", MDB, WH, "first time, or duplicate key"),
        ("alt", "duplicate event, or merchantId is null (customer parcel)"),
        ("m", WH, KFK, "commit offset; no delivery is created"),
        ("else", "first delivery of a merchant parcel event"),
        ("m", WH, MDB, "find active endpoints subscribed to the event type"),
        ("r", MDB, WH, "matching endpoints"),
        ("m", WH, MDB, "per endpoint: insert delivery PENDING (unique endpointId + eventId)"),
        ("alt", "merchant is SUSPENDED"),
        ("m", WH, WH, "hold: the delivery stays PENDING, no job is queued"),
        ("else", "merchant is active"),
        ("m", WH, RDS, "enqueue job (jobId = deliveryId)"),
        ("end",),
        ("m", WH, KFK, "commit offset"),
        ("m", RDS, WH, "worker takes the job"),
        ("m", WH, MDB, "load the delivery and endpoint, decrypt the secret"),
        ("m", WH, WH, "build {id, type, createdAt, data} from an allow-list: no contacts, names or notes"),
        ("m", WH, WH, "t = now; v1 = hex HMAC-SHA256(secret, t.rawBody)"),
        ("m", WH, EP, "POST + X-SwiftDrop-Signature: t=..,v1=.. (10 s timeout)"),
        ("r", EP, WH, "2xx"),
        ("m", WH, MDB, "append attempt (status code, latency); status DELIVERED"),
        ("end",),
    ]),
    "sequence-m8-retry-dead-letter-replay": ("M8 sequence: retries, dead letter and admin replay", [(RDS, False), (WH, False), (EP, False), (MDB, False), (KFK, False), (ADM, False), ("Admin", True)], [
        ("m", RDS, WH, "worker takes the job (attempt 1)"),
        ("m", WH, EP, "signed POST"),
        ("r", EP, WH, "503 or timeout after 10 s"),
        ("m", WH, MDB, "append attempt (status, latency, error); status RETRYING, nextRetryAt"),
        ("m", WH, RDS, "throw: BullMQ schedules the next delay"),
        ("note", RDS, WH, "delays 30 s, 2 m, 10 m, 30 m, 2 h, 6 h: 7 attempts in total; shorter delays by config for the demo"),
        ("m", RDS, WH, "attempts 2 to 7: a fresh t and signature each time"),
        ("alt", "an attempt returns 2xx"),
        ("m", WH, MDB, "status DELIVERED"),
        ("else", "attempt 7 fails"),
        ("m", WH, MDB, "append attempt; status DEAD"),
        ("m", WH, KFK, "publish WebhookDeliveryDeadLettered (key deliveryId, no payload data)"),
        ("r", KFK, WH, "acknowledged"),
        ("end",),
        ("m", "Admin", ADM, "open Admin, Webhooks (later, after the merchant recovers)"),
        ("m", ADM, WH, "GET /api/v1/admin/webhooks/deliveries?status=DEAD via gateway"),
        ("r", WH, ADM, "dead-lettered deliveries"),
        ("m", "Admin", ADM, "click Replay"),
        ("m", ADM, WH, "POST /api/v1/admin/webhooks/deliveries/{id}/replay"),
        ("alt", "caller is not ADMIN"),
        ("r", WH, ADM, "403 problem+json"),
        ("else", "delivery is not DEAD"),
        ("r", WH, ADM, "409 problem+json"),
        ("else", "ADMIN and DEAD"),
        ("m", WH, MDB, "status PENDING; earlier attempts are kept in the log"),
        ("m", WH, RDS, "enqueue a new job with a fresh attempt budget"),
        ("r", WH, ADM, "202 Queued"),
        ("m", RDS, WH, "worker takes the job"),
        ("m", WH, EP, "signed POST (same id, new t and signature)"),
        ("r", EP, WH, "200"),
        ("m", WH, MDB, "status DELIVERED"),
        ("end",),
    ]),
    "sequence-m8-simulator-demo": ("M8 sequence: merchant simulator demo", [("Presenter", True), (SIM, False), (KC, False), (GW, False), (PS, False), (WH, False)], [
        ("m", "Presenter", SIM, "click Create order"),
        ("m", SIM, KC, "POST /token grant_type=client_credentials, client merchant-demo"),
        ("r", KC, SIM, "access token with merchant_id claim"),
        ("m", SIM, GW, "POST /api/v1/merchant/orders, bearer + Idempotency-Key"),
        ("m", GW, PS, "forward (JWT valid, rate limit per client)"),
        ("r", PS, SIM, "201 parcel; the same key returns the same parcel"),
        ("note", PS, WH, "ParcelCreated reaches webhook-service over Kafka (see sequence-m8-signed-delivery)"),
        ("m", WH, SIM, "POST /webhooks, signed parcel.created"),
        ("alt", "fail mode is on"),
        ("r", SIM, WH, "503: webhook-service retries, then dead-letters"),
        ("else", "fail mode is off"),
        ("m", SIM, SIM, "recompute HMAC over the raw body; constant-time compare; t within 5 minutes"),
        ("alt", "signature wrong or t too old"),
        ("r", SIM, WH, "400; shown as rejected on the page"),
        ("else", "valid"),
        ("m", SIM, SIM, "keep the event in memory, de-duplicated by id"),
        ("r", SIM, WH, "200"),
        ("r", SIM, "Presenter", "page lists the event as verified"),
        ("end",),
        ("end",),
        ("m", "Presenter", SIM, "toggle fail mode to show retries and DLQ"),
    ]),
}


def seq_svg(title, parts, script):
    n = len(parts)
    gap = 270
    s = Sequence([(p, 110 + i * gap) for i, (p, _) in enumerate(parts)], width=110 + (n - 1) * gap + 150,
                 autonumber=True, top=100)
    s.heads(actor_names={p for p, a in parts if a})
    for st in script:
        k = st[0]
        if k == "m":
            s.msg(st[1], st[2], st[3])
        elif k == "r":
            s.msg(st[1], st[2], st[3], dashed=True)
        elif k == "note":
            s.note(st[1], st[2], st[3])
        elif k == "alt":
            s.alt(st[1])
        elif k == "else":
            s.orelse(st[1])
        else:
            s.end()
    return s.render(title)


def seq_mmd(title, parts, script):
    ids = {p: f"P{i}" for i, (p, _) in enumerate(parts)}
    out = [f"%% {title}. Planned design: follows contracts/openapi/webhook-service.v1.yaml and issue #21.",
           "sequenceDiagram", "    autonumber"]
    for p, a in parts:
        out.append(f"    {'actor' if a else 'participant'} {ids[p]} as {p.replace('|', '<br/>')}")
    depth = 1
    for st in script:
        k = st[0]
        clean = lambda t: t.replace(";", ",").replace("#", "")  # characters Mermaid treats as syntax
        if k in ("m", "r"):
            out.append("    " * depth + f"    {ids[st[1]]}{'->>' if k == 'm' else '-->>'}{ids[st[2]]}: {clean(st[3])}")
        elif k == "note":
            out.append("    " * depth + f"    Note over {ids[st[1]]},{ids[st[2]]}: {clean(st[3])}")
        elif k == "alt":
            out.append("    " * depth + f"    alt {clean(st[1])}")
            depth += 1
        elif k == "else":
            out.append("    " * (depth - 1) + f"    else {clean(st[1])}")
        else:
            depth -= 1
            out.append("    " * depth + "    end")
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------- use cases


def usecase(title, boundary, groups, left, right, rels):
    """groups: [(band, [(key, title, sub)])]; left/right: [(name, sub, [keys], is_actor)]; rels: (a, b, label)."""
    pill_x, pill_w, pill_h, pitch = 400, 420, 52, 76
    bx, bw = 340, 540
    y, pos, b = 110, {}, []
    bodies = []
    for name, cases in groups:
        h = 44 + len(cases) * pitch - (pitch - pill_h) + 16
        bodies.append(band(bx + 20, y, bw - 40, h, name))
        cy = y + 44
        for key, t, sub in cases:
            shape_rect(pill_x, cy, pill_w, pill_h, t)
            bodies.append(f'<rect x="{pill_x}" y="{cy}" width="{pill_w}" height="{pill_h}" rx="26" fill="{FILL}" '
                          f'stroke="{BLACK}" stroke-width="1.4"/>')
            bodies.append(text(pill_x + pill_w / 2, cy + 18, t, 13, BLACK, 600))
            bodies.append(text(pill_x + pill_w / 2, cy + 36, sub, 11, GREY))
            pos[key] = cy
            cy += pitch
        y += h + 20
    height = y + 10
    container(bx, 60, bw, height - 60, "boundary")
    b.append(f'<rect x="{bx}" y="60" width="{bw}" height="{height - 60}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>')
    b.append(text(bx + bw / 2, 84, boundary, 12.5, GREY, 600))
    b += bodies
    mid = lambda k: pos[k] + pill_h / 2
    for name, sub, keys, is_actor in left:
        ys = [mid(k) for k in keys]
        ay = sum(ys) / len(ys)
        bus = 300
        if is_actor:
            b.append(stickman(100, ay - 30, name))
            b.append(text(100, ay + 60, sub, 11, GREY))
            b.append(path([(116, ay), (bus, ay)], head=False))
        else:
            b.append(node(20, ay - 34, 170, 68, name, [sub], dashed=True))
            b.append(path([(190, ay), (bus, ay)], head=False))
        b.append(path([(bus, min(ys + [ay])), (bus, max(ys + [ay]))], head=False))
        for k in keys:
            b.append(path([(bus, mid(k)), (pill_x, mid(k))]))
    width = 1190
    for name, sub, keys, is_actor in right:
        ys = [mid(k) for k in keys]
        ay = sum(ys) / len(ys)
        bus = 920
        b.append(node(960, ay - 37, 210, 74, name, [sub], dashed=True))
        b.append(path([(960, ay), (bus, ay)], head=False))
        b.append(path([(bus, min(ys + [ay])), (bus, max(ys + [ay]))], head=False))
        for k in keys:
            b.append(path([(bus, mid(k)), (pill_x + pill_w, mid(k))]))
    for a, c, cap in rels:
        cx = pill_x + pill_w / 2
        y1, y2 = (pos[a] + pill_h, pos[c]) if pos[c] > pos[a] else (pos[a], pos[c] + pill_h)
        b.append(path([(cx, y1), (cx, y2)], dashed=True, end="open"))
        b.append(label(cx + 8, (y1 + y2) / 2, cap, size=10.5))
    return svg(width, height + 20, "".join(b), title)


def uc_webhooks():
    return usecase("M8 use cases: webhooks", "SwiftDrop webhooks (M8)  ·  web app + webhook-service  ·  system boundary", [
        ("Merchant  ·  scoped by merchant_id", [
            ("reg", "Register a webhook endpoint", "URL + event types, SSRF checks"),
            ("sec", "Reveal the signing secret once", "encrypted at rest afterwards"),
            ("ping", "Send a signed test ping", "POST /endpoints/{id}/test"),
            ("del", "List and remove endpoints", "GET, DELETE /endpoints"),
            ("log", "View the delivery log", "attempts, response codes, next retry"),
        ]),
        ("Admin only", [
            ("dead", "List dead-lettered deliveries", "GET /admin/webhooks/deliveries?status=DEAD"),
            ("rep", "Replay a dead-lettered delivery", "POST .../{id}/replay"),
        ]),
        ("Automatic  ·  driven by Kafka events", [
            ("send", "Deliver a signed parcel update", "parcel.created, parcel.status_changed"),
            ("retry", "Retry with exponential backoff", "30 s, 2 m, 10 m, 30 m, 2 h, 6 h"),
            ("dlq", "Dead-letter after the last attempt", "status DEAD + Kafka event"),
            ("pause", "Pause a suspended merchant", "MerchantLifecycleChanged"),
        ]),
    ], [("Merchant", "(MERCHANT)", ["reg", "ping", "del", "log"], True),
        ("Admin", "(ADMIN)", ["dead", "rep"], True),
        ("Kafka", "parcel and merchant events", ["send", "pause"], False)],
       [("Merchant endpoint", "external HTTPS receiver", ["ping", "send", "retry"], False)],
       [("reg", "sec", "«include»"), ("rep", "dead", "«include»"), ("retry", "send", "«extend» [non-2xx or timeout]"),
        ("dlq", "retry", "«extend» [attempts used up]")])


def uc_simulator():
    return usecase("M8 use cases: merchant simulator", "tools/merchant-simulator  ·  demo online store  ·  system boundary", [
        ("Online store", [
            ("order", "Create a delivery order", "POST /api/v1/merchant/orders + Idempotency-Key"),
            ("token", "Get a client-credentials token", "client merchant-demo"),
        ]),
        ("Webhook receiver", [
            ("recv", "Receive a SwiftDrop webhook", "POST /webhooks"),
            ("ver", "Verify signature and timestamp", "HMAC-SHA256, reject if older than 5 min"),
            ("page", "See received webhooks on a page", "verified or rejected, in memory"),
            ("fail", "Toggle fail mode", "receiver answers 503"),
        ]),
    ], [("Presenter", "(demo)", ["order", "page", "fail"], True)],
       [("api-gateway", "to parcel-service", ["order"], False), ("Keycloak", "token endpoint", ["token"], False),
        ("webhook-service", "signed POST + retries", ["recv"], False)],
       [("order", "token", "«include»"), ("recv", "ver", "«include»")])


# ---------------------------------------------------------------- data model


def table(x, y, w, title, rows):
    row = 26
    h = 32 + len(rows) * row
    shape_rect(x, y, w, h, title)
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>',
           f'<rect x="{x}" y="{y}" width="{w}" height="32" fill="{BLACK}"/>', text(x + w / 2, y + 16, title, 12.5, "#ffffff", 700)]
    for i, (name, kind) in enumerate(rows):
        ry = y + 32 + i * row
        if i:
            out.append(f'<line x1="{x}" y1="{ry}" x2="{x + w}" y2="{ry}" stroke="#d8d8d8" stroke-width="1"/>')
        out.append(text(x + 12, ry + row / 2, name, 11.5, BLACK, anchor="start"))
        if kind:
            out.append(text(x + w - 12, ry + row / 2, kind, 10.5, GREY, anchor="end"))
    return "".join(out), h


def er():
    b = [band(40, 50, 1110, 720), text(58, 70, "MONGODB  ·  DATABASE WEBHOOKS", 11, GREY, 500, anchor="start", spacing="1.6"),
         band(1190, 50, 330, 330), text(1208, 70, "REDIS  ·  BULLMQ", 11, GREY, 500, anchor="start", spacing="1.6"),
         band(1190, 420, 330, 350), text(1208, 440, "OWNED BY OTHER SERVICES", 11, GREY, 500, anchor="start", spacing="1.6")]
    t, eh = table(70, 110, 290, "WEBHOOK_ENDPOINT", [("_id", "PK whe_..."), ("merchant_id", "ref, indexed"), ("url", "https"),
              ("event_types", "array"), ("secret_enc", "encrypted"), ("active", ""), ("created_at", ""), ("updated_at", "")])
    b.append(t)
    t, dh = table(480, 110, 310, "WEBHOOK_DELIVERY", [("_id", "PK whd_..."), ("merchant_id", "ref, indexed"), ("endpoint_id", "FK"),
              ("event_id", "unique with endpoint_id"), ("event_type", ""), ("payload", "PII stripped"), ("url", "copied at creation"),
              ("status", "PENDING..DEAD"), ("attempt_count", ""), ("next_retry_at", ""), ("created_at", ""), ("delivered_at", "")])
    b.append(t)
    t, _ = table(880, 110, 240, "DELIVERY_ATTEMPT", [("at", "embedded"), ("http_status", "null on timeout"), ("latency_ms", ""), ("error", "max 500")])
    b.append(t)
    t, _ = table(70, 540, 290, "MERCHANT_STATE", [("_id", "merchantId"), ("status", "from lifecycle events"), ("changed_at", "")])
    b.append(t)
    t, _ = table(480, 540, 310, "PARCEL_REF", [("_id", "parcelId"), ("merchant_id", "ref"), ("tracking_number", ""), ("merchant_order_ref", ""), ("created_at", "TTL")])
    b.append(t)
    t, _ = table(880, 540, 240, "PROCESSED_EVENT", [("_id", "consumer:eventId"), ("event_id", ""), ("consumer", ""), ("processed_at", "TTL 14 days")])
    b.append(t)
    t, _ = table(1220, 110, 270, "DELIVERY_JOB", [("job_id", "= delivery _id"), ("delivery_id", "ref"), ("attempts_made", ""), ("delay", "next backoff")])
    b.append(t)
    t, _ = table(1220, 480, 270, "MERCHANT · PARCEL", [("referenced by id only", ""), ("no foreign keys", "")])
    b.append(t)
    b.append(path([(360, 180), (480, 180)], start="one", end="many"))
    b.append(label(420, 165, "receives", anchor="middle", size=11))
    b.append(path([(790, 180), (880, 180)], start="one", end="many"))
    b.append(label(835, 165, "logs", anchor="middle", size=11))
    # job refers to its delivery: over the top of the attempt table
    b.append(path([(1355, 110), (1355, 90), (690, 90), (690, 110)], dashed=True, end="open"))
    b.append(label(1000, 74, "runs one delivery", anchor="middle", size=11))
    # events fill the read models from other services
    b.append(path([(1220, 510), (1170, 510), (1170, 800), (215, 800), (215, 650)], dashed=True, end="open"))
    b.append(path([(635, 800), (635, 702)], dashed=True, end="open"))
    b.append(label(830, 784, "projected from Kafka events", anchor="middle", size=11))
    return svg(1560, 830, "".join(b), "M8 data model: webhooks database and delivery queue")


# ---------------------------------------------------------------- architecture and integration


def architecture():
    b = [band(60, 50, 1250, 150, "Callers and external systems"), band(60, 230, 1250, 330, "webhook-service  ·  NestJS"),
         band(60, 590, 1250, 150, "Stores")]
    w, h = 240, 80
    for x, t, sub in [(100, "Web pages + BFF", "merchant, admin"), (400, "api-gateway", "JWT, routes /webhooks/**"), (700, "Kafka", "parcel, merchant, DLQ"), (1000, "Merchant endpoint", "external HTTPS")]:
        b.append(node(x, 90, w, h, t, [sub], dashed=x == 1000))
    b.append(path([(340, 130), (400, 130)]))
    b.append(node(400, 270, w, h, "Controllers", ["endpoints, deliveries, admin"]))
    b.append(node(700, 270, w, h, "Kafka consumers", ["parcel, merchant lifecycle"]))
    b.append(node(1000, 270, w, h, "DeliveryProcessor", ["BullMQ worker, 10 s timeout"]))
    b.append(node(400, 440, w, h, "Application services", ["EndpointService, ReplayService"]))
    b.append(node(700, 440, w, h, "FanOutService", ["match endpoints, enqueue"]))
    b.append(node(1000, 440, w, h, "Signer, PayloadMapper", ["SsrfGuard, SecretCipher"]))
    b.append(node(100, 270, w, h, "swiftdrop-common", ["guard, problem+json, consumer"]))
    b.append(path([(520, 170), (520, 270)]))
    b.append(path([(520, 350), (520, 440)]))
    b.append(path([(340, 310), (400, 310)], dashed=True, end="open"))
    b.append(path([(760, 170), (760, 270)]))
    b.append(path([(820, 350), (820, 440)]))
    b.append(path([(1120, 350), (1120, 440)]))
    b.append(path([(1120, 270), (1120, 170)]))
    b.append(label(1130, 215, "signed POST", size=11))
    b.append(path([(1040, 270), (1040, 225), (880, 225), (880, 170)]))
    b.append(label(1048, 212, "DLQ event", size=11))
    b.append(cylinder(400, 620, 240, 100, "MongoDB", ["webhooks"]))
    b.append(cylinder(700, 620, 240, 100, "Redis", ["BullMQ queue"]))
    b.append(path([(520, 520), (520, 620)]))
    b.append(path([(760, 520), (760, 570), (580, 570), (580, 620)]))
    b.append(path([(880, 520), (880, 620)]))
    b.append(path([(940, 670), (970, 670), (970, 310), (1000, 310)]))
    b.append(label(978, 395, "jobs", size=11))
    b.append(path([(1240, 310), (1275, 310), (1275, 765), (520, 765), (520, 720)]))
    b.append(label(860, 752, "attempts and status", anchor="middle", size=11))
    return svg(1340, 790, "".join(b), "M8 architecture: webhook-service modules")


def integration():
    b = [band(40, 50, 1620, 350, "Asynchronous  ·  parcel change to merchant"), band(40, 420, 1620, 230, "Synchronous  ·  merchant calls SwiftDrop, admin recovers")]
    y, h = 130, 90
    mid = y + h // 2
    b.append(node(70, y, 150, h, "parcel-service", ["status change"]))
    b.append(cylinder(330, y, 130, h, "outbox_event"))
    b.append(node(570, y, 130, h, "Kafka", ["key parcelId"]))
    b.append(node(810, y, 170, h, "webhook-service", ["consumer group"]))
    b.append(node(1090, y, 130, h, "BullMQ", ["delayed jobs"]))
    b.append(node(1330, y, 150, h, "Delivery worker", ["HMAC signer"]))
    for x1, x2, cap in [(220, 330, "1 outbox"), (460, 570, "2 pub / sub"), (700, 810, "3 idempotent|consumer"), (980, 1090, "4 work queue"), ]:
        b.append(path([(x1, mid), (x2, mid)]))
        b.append(label((x1 + x2) / 2, mid - 26, cap, anchor="middle", size=11))
    b.append(path([(1220, mid), (1330, mid)]))
    b.append(node(1500, 290, 140, 90, "Merchant", ["endpoint"], dashed=True))
    b.append(path([(1480, mid), (1570, mid), (1570, 290)]))
    b.append(label(1578, 240, "5 webhook|+ HMAC", size=11))
    b.append(path([(1370, y + h), (1370, 262), (1155, 262), (1155, y + h)], dashed=True, end="open"))
    b.append(label(1262, 244, "6 retry + backoff", anchor="middle", size=11))
    b.append(path([(1440, y + h), (1440, 335), (635, 335), (635, y + h)], dashed=True, end="open"))
    b.append(label(950, 317, "7 dead-letter channel", anchor="middle", size=11))
    y2, m2 = 480, 525
    b.append(node(70, y2, 170, h, "Merchant system", ["simulator"], dashed=True))
    b.append(node(420, y2, 150, h, "api-gateway", ["JWT, rate limit"]))
    b.append(node(760, y2, 170, h, "parcel-service", ["Idempotency-Key"]))
    b.append(path([(240, m2), (420, m2)]))
    b.append(label(330, m2 - 24, "8 request / reply|client credentials", anchor="middle", size=11))
    b.append(path([(570, m2), (760, m2)]))
    b.append(label(665, m2 - 24, "9 idempotent|receiver", anchor="middle", size=11))
    b.append(node(1090, y2, 130, h, "Admin", ["web + BFF"]))
    b.append(node(1330, y2, 150, h, "webhook-service", ["replay API"]))
    b.append(path([(1220, m2), (1330, m2)]))
    b.append(label(1275, m2 - 24, "10 manual|replay", anchor="middle", size=11))
    return svg(1700, 680, "".join(b), "M8 integration design: numbered patterns")


# ---------------------------------------------------------------- activity


def activity():
    a = Activity("Deliver one webhook", [("webhook-service worker", 700), ("Merchant endpoint", 280), ("Admin", 320)])
    main, right, loop = 360, 610, 60
    ep, adm = a.lane(1), a.lane(2)
    s = a.start(main, 125)
    job = a.action(main, 190, "Take a delivery job|queued, delayed retry or replay", w=340)
    a.flow([s.b, job.t])
    d1 = a.decision(main, 270, "merchant active?")
    a.flow([job.b, d1.t])
    hold = a.action(right - 20, 270, "Hold the delivery|stays PENDING", w=150)
    a.flow([d1.r, hold.l], guard="suspended", at=(440, 256), anchor="middle")
    fin0 = a.final(right - 20, 345)
    a.flow([hold.b, fin0.t])
    build = a.action(main, 355, "Sign the payload|PII stripped, fresh t, HMAC-SHA256", w=320)
    a.flow([d1.b, build.t], guard="active", at=(main + 10, 305))
    call = a.action(ep, 440, "Handle the POST|10 s timeout applies", w=200)
    send = a.action(main, 440, "Re-check the URL, then POST|a blocked IP counts as a failed attempt", w=340)
    a.flow([build.b, send.t])
    a.flow([send.r, call.l])
    rec = a.action(main, 530, "Record the attempt|status code, latency, error", w=300)
    a.flow([send.b, rec.t])
    a.flow([call.b, (ep, 530), rec.r])
    d2 = a.decision(main, 610, "outcome?")
    a.flow([rec.b, d2.t])
    ok = a.action(right - 20, 680, "Mark DELIVERED", w=170)
    a.flow([d2.r, (right - 20, 610), ok.t], guard="2xx", at=(500, 596), anchor="middle")
    fin1 = a.final(right - 20, 750)
    a.flow([ok.b, fin1.t])
    d3 = a.decision(main, 690, "attempts left?")
    a.flow([d2.b, d3.t], guard="non-2xx or timeout", at=(main + 10, 650))
    wait = a.action(190, 770, "Mark RETRYING, wait|30 s, 2 m, 10 m, 30 m, 2 h, 6 h", w=250)
    a.flow([d3.l, (190, 690), wait.t], guard="yes, under 7", at=(265, 676), anchor="middle")
    a.flow([wait.l, (loop, 770), (loop, 190), job.l])
    dead = a.action(main, 860, "Mark DEAD", w=200)
    a.flow([d3.b, dead.t], guard="no", at=(main + 10, 770))
    pub = a.action(main, 940, "Publish WebhookDeliveryDeadLettered|Kafka, no payload data", w=320)
    a.flow([dead.b, pub.t])
    look = a.action(adm, 940, "Review the dead-letter list|after the merchant recovers", w=260)
    a.flow([pub.r, look.l])
    d4 = a.decision(adm, 1020, "replay?")
    a.flow([look.b, d4.t])
    fin2 = a.final(adm + 110, 1020)
    a.flow([d4.r, fin2.l], guard="no", at=(adm + 50, 1006), anchor="middle")
    rep = a.action(adm, 1100, "Replay|status PENDING, new job, log kept", w=270)
    a.flow([d4.b, rep.t], guard="yes", at=(adm + 10, 1058))
    a.items.append(path([rep.l, (loop, 1100), (loop, 770)], head=False))
    return a.render(1160, "M8 activity: webhook retry")


def build_all():
    figs = {name: (lambda v=v: seq_svg(*v)) for name, v in SEQ.items()}
    figs.update({"usecase-m8-webhooks": uc_webhooks, "usecase-m8-merchant-simulator": uc_simulator, "er-m8-webhooks": er,
                 "architecture-m8-webhooks": architecture, "integration-m8-patterns": integration, "activity-m8-webhook-retry": activity})
    return figs


FIGURES = build_all()


def png(name):
    from PIL import Image, ImageChops
    import re
    src = OUT / f"{name}.svg"
    w, h = map(int, re.search(r'width="(\d+)" height="(\d+)"', src.read_text()[:400]).groups())
    with tempfile.TemporaryDirectory() as tmp:
        raw = Path(tmp) / "r.png"
        subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=2",
                        f"--window-size={w + 40},{h + 40}", f"--user-data-dir={tmp}/p", f"--screenshot={raw}", src.as_uri()],
                       check=True, capture_output=True)
        im = Image.open(raw).convert("RGB")
        box = ImageChops.difference(im, Image.new("RGB", im.size, (255, 255, 255))).convert("L").point(lambda v: 255 if v > 12 else 0).getbbox()
        p = 32
        im.crop((max(box[0] - p, 0), max(box[1] - p, 0), min(box[2] + p, im.width), min(box[3] + p, im.height))).save(OUT / f"{name}.png", optimize=True)


if __name__ == "__main__":
    for name, fn in FIGURES.items():
        (OUT / f"{name}.svg").write_text(fn(), encoding="utf-8")
        if name in SEQ:
            (OUT / f"{name}.mmd").write_text(seq_mmd(*SEQ[name]), encoding="utf-8")
        if "--no-png" not in sys.argv:
            png(name)
        print(name)
