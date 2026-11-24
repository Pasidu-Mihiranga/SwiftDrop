"""System figure S5: event-driven integration. Who publishes and who consumes each Kafka topic.

A matrix instead of a graph: seven topics and eight services would need about twenty crossing lines.
The pipeline strip on top shows the path every event takes; the table shows which services take part.
"""

from draw import BLACK, FILL, GREY, band, cylinder, label, node, path, svg, text

W = 2000
COLS = ["parcel", "dispatch", "merchant", "reporting", "tracking", "notification", "webhook"]
# topic, key, producer column, consumer columns, note
ROWS = [
    ("swiftdrop.parcel.created.v1", "parcelId", "parcel", ["dispatch", "reporting", "tracking", "notification", "webhook"],
     "booking or merchant order"),
    ("swiftdrop.parcel.status-changed.v1", "parcelId", "parcel", ["dispatch", "reporting", "tracking", "notification", "webhook"],
     "every courier or dispatcher step"),
    ("swiftdrop.dispatch.parcel-assigned.v1", "parcelId", "dispatch", ["parcel", "reporting", "tracking", "notification"],
     "assign and reassign"),
    ("swiftdrop.merchant.lifecycle.v1", "merchantId", "merchant", ["parcel", "notification", "webhook"],
     "approved, rejected, suspended"),
    ("swiftdrop.tracking.dead-letter.v1", "eventId", "tracking", [], "events tracking could not apply"),
    ("swiftdrop.notification.dead-letter.v1", "eventId", "notification", [], "messages that failed after retries"),
    ("swiftdrop.webhook.dead-letter.v1", "deliveryId", "webhook", [], "deliveries out of attempts, replayed by an admin"),
]


def build() -> str:
    b = []
    # ------------------------------------------------------------ pipeline
    b.append(band(40, 30, W - 80, 210, "Every event takes the same path"))
    y, h = 90, 96
    steps = [(80, "Business change", ["one database transaction"]), (390, "outbox_event", ["written in that transaction"]),
             (700, "Outbox relay", ["polls, publishes, marks sent", "W3C traceparent in headers"]),
             (1010, "Kafka topic", ["key keeps one parcel in order"]),
             (1320, "Idempotent consumer", ["processed_event / consumer key", "a redelivery changes nothing"]),
             (1630, "Read model or action", ["projection, e-mail, webhook"])]
    for i, (x, title, lines) in enumerate(steps):
        if i in (1,):
            b.append(cylinder(x, y, 250, h, title, lines))
        else:
            b.append(node(x, y, 250, h, title, lines, header=True))
    for (x1, *_), (x2, *_) in zip(steps, steps[1:]):
        b.append(path([(x1 + 250, y + h / 2), (x2, y + h / 2)]))
    b.append(label(1330, 216, "poison records go to the service's dead-letter topic", size=11.5))

    # ------------------------------------------------------------ matrix
    top = 290
    tw, kw, nw, cw = 400, 130, 300, 140
    x0 = 40
    row_h, head_h = 54, 64
    width = tw + kw + nw + cw * len(COLS)
    height = head_h + row_h * len(ROWS)
    b.append(band(40, top - 30, W - 80, height + 120, "Topics  ·  P = publishes through the outbox, C = consumes"))
    t0 = top + 30
    b.append(f'<rect x="{x0 + 20}" y="{t0}" width="{width}" height="{height}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>')
    b.append(f'<rect x="{x0 + 20}" y="{t0}" width="{width}" height="{head_h}" fill="{BLACK}"/>')
    hx = x0 + 20
    for title, w in [("topic", tw), ("key", kw), ("carries", nw)]:
        b.append(text(hx + 14, t0 + head_h / 2, title, 13, "#ffffff", 700, anchor="start"))
        hx += w
    for c in COLS:
        b.append(text(hx + cw / 2, t0 + head_h / 2 - 8, c, 13, "#ffffff", 700))
        b.append(text(hx + cw / 2, t0 + head_h / 2 + 10, "service", 11, "#d0d0d0"))
        hx += cw
    for i, (topic, key, producer, consumers, note) in enumerate(ROWS):
        ry = t0 + head_h + i * row_h
        if i:
            b.append(f'<line x1="{x0 + 20}" y1="{ry}" x2="{x0 + 20 + width}" y2="{ry}" stroke="#d4d4d4" stroke-width="1"/>')
        cy = ry + row_h / 2
        b.append(text(x0 + 34, cy, topic, 13, BLACK, 600, anchor="start"))
        b.append(text(x0 + 20 + tw + 14, cy, key, 12, GREY, anchor="start"))
        b.append(text(x0 + 20 + tw + kw + 14, cy, note, 12, GREY, anchor="start"))
        for j, c in enumerate(COLS):
            cx = x0 + 20 + tw + kw + nw + j * cw + cw / 2
            if c == producer:
                b.append(f'<rect x="{cx - 17}" y="{cy - 15}" width="34" height="30" fill="{BLACK}"/>')
                b.append(text(cx, cy, "P", 14, "#ffffff", 700))
            elif c in consumers:
                b.append(f'<rect x="{cx - 17}" y="{cy - 15}" width="34" height="30" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>')
                b.append(text(cx, cy, "C", 14, BLACK, 700))
    # vertical rules between column groups
    for vx in [x0 + 20 + tw, x0 + 20 + tw + kw, x0 + 20 + tw + kw + nw]:
        b.append(f'<line x1="{vx}" y1="{t0}" x2="{vx}" y2="{t0 + height}" stroke="#d4d4d4" stroke-width="1"/>')
    foot = t0 + height + 34
    b.append(text(x0 + 20, foot, "user-service, pricing-service and ai-assistant-service use no Kafka: they answer requests "
                  "only. Every consumer group commits its offset after its own transaction.", 12, GREY, anchor="start"))
    return svg(W, foot + 40, "".join(b), "Event-driven integration: Kafka topics, producers and consumers")
