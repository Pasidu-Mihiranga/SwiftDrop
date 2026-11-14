"""Figure 4: integration patterns.

Each row is a chain laid out left to right from a running cursor, so every gap is wide enough for
the label that sits in it: the gap is max(MIN_GAP, label width + padding). Nothing can overlap.
"""

import paths  # noqa: F401
from draw import GREY, band, cylinder, label, node, path, svg, text

W, H = 1660, 900
MIN_GAP = 76
LABEL_SIZE = 11
BOX_H = 80


def gap_for(caption: str) -> float:
    longest = max(caption.split("|"), key=len)
    return max(MIN_GAP, len(longest) * LABEL_SIZE * 0.52 + 12 + 26)


def chain(y, x, items, parts):
    """items: (kind, width, title, lines[, caption of the arrow after this box])."""
    for i, item in enumerate(items):
        kind, w, title, lines = item[:4]
        if kind == "db":
            parts.append(cylinder(x, y - 4, w, BOX_H + 8, title, lines))
        else:
            parts.append(node(x, y, w, BOX_H, title, lines, dashed=kind == "ext"))
        x += w
        if i < len(items) - 1:
            caption = item[4]
            gap = gap_for(caption)
            mid = y + BOX_H / 2
            parts.append(path([(x, mid), (x + gap, mid)]))
            parts.append(label(x + gap / 2, mid - 18, caption, anchor="middle", size=LABEL_SIZE))
            x += gap


def build() -> str:
    b = []
    b.append(band(40, 50, W - 80, 340))
    b.append(text(58, 70, "ASYNCHRONOUS  ·  STATUS AND ORDER EVENTS", 11, GREY, 500, anchor="start", spacing="1.6"))
    b.append(band(40, 430, W - 80, 430))
    b.append(text(58, 450, "SYNCHRONOUS  ·  ONBOARDING AND ORDER CREATION", 11, GREY, 500, anchor="start", spacing="1.6"))

    chain(115, 75, [
        ("box", 180, "merchant-service", ["status change"], "1 · transactional outbox"),
        ("db", 150, "outbox_event", [], "2 · publish / subscribe"),
        ("box", 210, "Kafka", ["merchant.lifecycle.v1"], "3 · idempotent consumer"),
        ("box", 240, "parcel-service", ["4 · event-carried state", "merchant status cache"])], b)
    chain(265, 75, [
        ("box", 180, "parcel-service", ["order accepted"], "1 · transactional outbox"),
        ("db", 150, "outbox_event", [], "2 · publish / subscribe"),
        ("box", 180, "Kafka", ["parcel.created.v1"], "3 · idempotent consumer"),
        ("box", 170, "webhook-service", ["signs and retries"], "5 · webhook callback"),
        ("ext", 160, "Merchant system", ["verifies the signature"])], b)
    chain(495, 75, [
        ("ext", 180, "Merchant system", ["confidential client"], "6 · OAuth 2.0 client credentials"),
        ("ext", 190, "Keycloak", ["/token"])], b)
    chain(625, 75, [
        ("ext", 180, "Merchant system", ["Idempotency-Key"], "7 · API gateway + JWT"),
        ("box", 180, "API gateway", ["rate limit per client"], "8 · idempotent receiver"),
        ("box", 180, "parcel-service", ["merchant order API"], "9 · request / reply + circuit breaker"),
        ("box", 180, "pricing-service", ["fee quote"])], b)
    chain(755, 75, [
        ("box", 180, "merchant-service", ["approve · rotate"], "10 · adapter over a request / reply API"),
        ("ext", 190, "Keycloak", ["Admin API"])], b)
    return svg(W, H, "".join(b), "Integration design: the patterns behind merchant onboarding and orders")
