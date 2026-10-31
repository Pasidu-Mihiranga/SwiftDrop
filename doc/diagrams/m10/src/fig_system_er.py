"""System figure S1: the full SwiftDrop data model, every store of every service.

Notation (crow's foot):
  - a solid line is a real foreign key inside ONE database:  || exactly one,  o< zero or many,
    o| zero or one
  - a dashed open arrow is a row derived by a projection (no foreign key)
  - "ref <thing>" in the right-hand column marks an id owned by ANOTHER service. Services share
    ids, never foreign keys, so these are tags and not lines.

Layout: one band per database, three rows of bands. Inside a band, tables sit on a grid with
50-60px gutters and every relationship line runs in its own gutter, so no line crosses a
table, another line or a label.
"""

from draw import BLACK, FILL, GREY, band, label, path, shape_rect, svg, text

W, H = 2040, 2032
ROW, HEAD = 20, 28
TW = 250                      # usual table width


def table(x, y, w, title, rows):
    h = HEAD + len(rows) * ROW
    shape_rect(x, y, w, h, title)
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>',
           f'<rect x="{x}" y="{y}" width="{w}" height="{HEAD}" fill="{BLACK}"/>',
           text(x + w / 2, y + HEAD / 2, title, 12, "#ffffff", 700)]
    for i, (name, kind) in enumerate(rows):
        ry = y + HEAD + i * ROW
        if i:
            out.append(f'<line x1="{x}" y1="{ry}" x2="{x + w}" y2="{ry}" stroke="#d8d8d8" stroke-width="1"/>')
        out.append(text(x + 10, ry + ROW / 2, name, 11, BLACK, anchor="start"))
        if kind:
            out.append(text(x + w - 10, ry + ROW / 2, kind, 10, GREY, anchor="end"))
    return "".join(out), h


def rel(points, start="one", end="many"):
    """Foreign key inside one database, crow's-foot ends."""
    return path(points, start=start, end=end)


def derived(points):
    """Row built by a projection: dashed, open arrowhead, no foreign key."""
    return path(points, dashed=True, end="open")


def build() -> str:
    b = []

    def add(x, y, w, title, rows):
        svg_part, h = table(x, y, w, title, rows)
        b.append(svg_part)
        return h

    # ================================================================ row A
    ay, ah = 40, 508
    # ---- users_db
    b.append(band(40, ay, 630, ah, "users_db  ·  user-service  ·  PostgreSQL"))
    ox, oy = 70, ay + 50
    add(ox, oy, TW, "USER_PROFILE", [("id", "PK (Keycloak sub)"), ("full_name", ""), ("email", ""),
                                     ("phone_enc", "enc"), ("status", "enum"), ("created_at", "")])
    add(ox + 300, oy, TW, "USER_ROLE", [("user_id", "PK, FK"), ("role_code", "PK, FK")])
    add(ox + 300, oy + 120, TW, "ROLE", [("code", "PK")])
    add(ox, oy + 200, TW, "ADDRESS", [("id", "PK"), ("user_id", "FK"), ("label", ""), ("line1_enc", "enc"),
                                      ("line2_enc", "enc"), ("city", ""), ("district", ""), ("postal_code", ""),
                                      ("lat", ""), ("lng", "")])
    add(ox + 300, oy + 230, TW, "COURIER_PROFILE", [("user_id", "PK, FK"), ("vehicle_type", ""),
                                                    ("home_zone_id", "ref zone"), ("max_daily_capacity", ""),
                                                    ("active", "")])
    b.append(rel([(ox + TW, oy + 40), (ox + 300, oy + 40)]))
    b.append(rel([(ox + 425, oy + 120), (ox + 425, oy + 68)]))
    b.append(rel([(ox + 125, oy + 148), (ox + 125, oy + 200)]))
    b.append(rel([(ox + TW, oy + 120), (ox + 275, oy + 120), (ox + 275, oy + 294), (ox + 300, oy + 294)],
                 end="zeroone"))

    # ---- merchants_db
    b.append(band(700, ay, 630, ah, "merchants_db  ·  merchant-service  ·  PostgreSQL"))
    ox, oy = 740, ay + 50
    add(ox, oy, TW, "MERCHANT", [("id", "PK"), ("owner_user_id", "ref user"), ("business_name", ""),
                                 ("br_number", ""), ("contact_email", ""), ("phone_enc", "enc"), ("status", "enum"),
                                 ("reviewed_by", "ref user"), ("reviewed_at", ""), ("rejection_reason", "")])
    add(ox + 300, oy, TW, "API_CREDENTIAL", [("id", "PK"), ("merchant_id", "FK"),
                                             ("keycloak_client_id", "ref Keycloak"), ("created_at", ""),
                                             ("rotated_at", ""), ("revoked_at", "")])
    add(ox + 300, oy + 190, TW, "WEBHOOK_ENDPOINT", [("id", "PK"), ("merchant_id", "FK"), ("url", ""),
                                                     ("secret_enc", "enc"), ("event_types", "text[]"),
                                                     ("active", "")])
    b.append(rel([(ox + TW, oy + 60), (ox + 300, oy + 60)]))
    b.append(rel([(ox + TW, oy + 210), (ox + 300, oy + 210)]))

    # ---- pricing_db
    b.append(band(1360, ay, 640, ah, "pricing_db  ·  pricing-service  ·  PostgreSQL"))
    ox, oy = 1400, ay + 50
    add(ox, oy, TW, "ZONE", [("id", "PK"), ("code", "UQ"), ("name", ""), ("active", "")])
    add(ox + 300, oy, TW, "DISTRICT_ZONE", [("district", "PK"), ("zone_id", "FK")])
    add(ox, oy + 180, TW, "RATE_CARD", [("id", "PK"), ("from_zone_id", "FK"), ("to_zone_id", "FK"),
                                        ("base_fee", ""), ("per_kg_fee", ""), ("express_multiplier", ""),
                                        ("cod_fee_pct", ""), ("effective_from", ""), ("effective_to", "")])
    add(ox + 300, oy + 180, TW, "SIZE_CLASS", [("code", "PK (S M L XL)"), ("max_weight_kg", ""),
                                               ("max_dimensions_cm", ""), ("multiplier", "")])
    b.append(rel([(ox + TW, oy + 40), (ox + 300, oy + 40)]))
    b.append(rel([(ox + 60, oy + 108), (ox + 60, oy + 180)]))
    b.append(label(ox + 68, oy + 144, "from", size=10.5))
    b.append(rel([(ox + 170, oy + 108), (ox + 170, oy + 180)]))
    b.append(label(ox + 178, oy + 144, "to", size=10.5))

    # ================================================================ row B
    by_, bh = ay + ah + 40, 738
    # ---- parcels_db
    b.append(band(40, by_, 680, bh, "parcels_db  ·  parcel-service  ·  PostgreSQL"))
    ox, oy = 70, by_ + 50
    add(ox, oy, 290, "PARCEL", [
        ("id", "PK"), ("tracking_number", "UQ"), ("source", "enum"), ("customer_id", "ref user"),
        ("merchant_id", "ref merchant"), ("merchant_order_ref", ""), ("pickup_address_enc", "enc jsonb"),
        ("dropoff_address_enc", "enc jsonb"), ("pickup_zone_id", "ref zone"), ("dropoff_zone_id", "ref zone"),
        ("recipient_name", ""), ("recipient_phone_enc", "enc"), ("weight_kg", ""), ("size_class", "ref size"),
        ("service_level", ""), ("fee", ""), ("currency", ""), ("quote_id", "ref quote"), ("status", "enum"),
        ("courier_id", "ref user"), ("created_at", ""), ("updated_at", ""), ("version", "optimistic lock")])
    add(ox + 350, oy, 270, "PARCEL_STATUS_HISTORY", [
        ("id", "PK"), ("parcel_id", "FK"), ("from_status", ""), ("to_status", ""), ("actor_id", "ref user"),
        ("actor_role", ""), ("note", ""), ("failure_reason", "enum"), ("proof_id", "FK"), ("lat", ""),
        ("lng", ""), ("occurred_at", "")])
    add(ox + 350, oy + 330, 270, "DELIVERY_PROOF", [
        ("id", "PK"), ("parcel_id", "FK"), ("type", "NAME PHOTO SIGNATURE"), ("recipient_name", ""),
        ("object_key", "RustFS object"), ("created_at", "")])
    add(ox + 350, oy + 530, 270, "IDEMPOTENCY_RECORD", [
        ("key", "PK"), ("merchant_id", "ref merchant"), ("parcel_id", "FK"), ("request_hash", ""),
        ("created_at", "")])
    b.append(rel([(ox + 290, oy + 60), (ox + 350, oy + 60)]))
    b.append(rel([(ox + 290, oy + 400), (ox + 350, oy + 400)]))
    b.append(rel([(ox + 485, oy + 268), (ox + 485, oy + 330)], start="many", end="zeroone"))
    b.append(label(ox + 493, oy + 299, "proof_id", size=10.5))
    b.append(rel([(ox + 145, oy + 488), (ox + 145, oy + 594), (ox + 350, oy + 594)], end="zeroone"))

    # ---- dispatch_db
    b.append(band(750, by_, 610, bh, "dispatch_db  ·  dispatch-service  ·  PostgreSQL"))
    ox, oy = 780, by_ + 50
    add(ox, oy, TW, "PARCEL_SUMMARY", [("parcel_id", "PK, ref parcel"), ("tracking_number", ""),
                                       ("pickup_zone_id", "ref zone"), ("dropoff_zone_id", "ref zone"),
                                       ("size_class", ""), ("status", ""), ("created_at", "")])
    add(ox + 300, oy, TW, "ASSIGNMENT", [("id", "PK"), ("parcel_id", "FK"), ("courier_id", "ref user"),
                                         ("assigned_by", "ref user"), ("run_date", ""), ("status", "enum"),
                                         ("assigned_at", "")])
    add(ox, oy + 230, TW, "COURIER_WORKLOAD", [("courier_id", "PK, ref user"), ("active_count", ""),
                                               ("delivered_today", ""), ("failed_today", ""), ("updated_at", "")])
    b.append(rel([(ox + TW, oy + 60), (ox + 300, oy + 60)]))
    b.append(text(ox, oy + 400, "PARCEL_SUMMARY is a read model built from parcel events.", 11, GREY,
                  anchor="start"))

    # ---- reporting_db
    b.append(band(1390, by_, 610, bh, "reporting_db  ·  reporting-service  ·  PostgreSQL"))
    ox, oy = 1420, by_ + 50
    fact_h = add(ox, oy, TW, "PARCEL_FACT", [("parcel_id", "PK, ref parcel"), ("tracking_number", ""),
                                             ("zone_code", "dropoff, ref zone"), ("source", ""),
                                             ("merchant_id", "ref merchant"), ("created_at", ""),
                                             ("created_date", "business day"), ("courier_id", "ref user"),
                                             ("courier_assigned_at", "")])
    trans_h = add(ox + 300, oy, TW, "STATUS_TRANSITION", [("event_id", "PK"), ("parcel_id", "ref parcel"),
                                                          ("from_status", ""), ("to_status", ""),
                                                          ("courier_id", "ref user"), ("failure_reason", ""),
                                                          ("changed_at", ""), ("business_date", "")])
    add(ox, oy + 260, TW, "DAILY_DELIVERY_STATS", [("business_date", "PK"), ("zone_code", "PK"), ("created", ""),
                                                   ("delivered", ""), ("failed", ""), ("cancelled", "")])
    add(ox + 300, oy + 260, TW, "COURIER_DAILY_STATS", [("business_date", "PK"), ("courier_id", "PK, ref user"),
                                                        ("delivered", ""), ("failed", "")])
    # both rollups are rebuilt from the facts for every business day an event touches
    b.append(derived([(ox + 125, oy + fact_h), (ox + 125, oy + 260)]))
    b.append(label(ox + 133, oy + (fact_h + 260) / 2, "created", size=10.5))
    b.append(derived([(ox + 425, oy + trans_h), (ox + 425, oy + 260)]))
    b.append(label(ox + 433, oy + (trans_h + 260) / 2, "attempts", size=10.5))
    b.append(derived([(ox + 300, oy + 150), (ox + 275, oy + 150), (ox + 275, oy + 300), (ox + TW, oy + 300)]))

    # ================================================================ row C
    cy, ch = by_ + bh + 40, 590
    # ---- shared tables from libs/java/swiftdrop-outbox
    b.append(band(40, cy, 650, ch, "shared tables  ·  libs/java/swiftdrop-outbox"))
    ox, oy = 70, cy + 50
    add(ox, oy, 300, "OUTBOX_EVENT", [("id", "PK"), ("aggregate_id", ""), ("event_type", ""), ("topic", ""),
                                      ("message_key", "Kafka key"), ("payload", "jsonb"), ("correlation_id", ""),
                                      ("created_at", ""), ("published_at", "null = pending"), ("attempts", ""),
                                      ("last_error", "")])
    add(ox + 340, oy, TW, "PROCESSED_EVENT", [("event_id", "PK"), ("consumer", "PK"), ("processed_at", "")])
    b.append(label(ox + 340, oy + 150, "Created in each database that|publishes events (parcels, merchants,|"
                                       "dispatch) or consumes them|(dispatch, reporting).", size=11))

    # ---- MongoDB
    b.append(band(720, cy, 870, ch, "MongoDB  ·  tracking, notification and webhook services"))
    ox, oy = 750, cy + 50
    add(ox, oy, TW, "tracking.timelines", [("_id", ""), ("trackingNumber", "unique"), ("parcelId", "ref parcel"),
                                           ("ownerIds", "array"), ("currentStatus", ""), ("events", "embedded []"),
                                           ("publicView", "city, masked name")])
    add(ox, oy + 210, TW, "notifications.preferences", [("userId", "ref user"), ("email", "bool"),
                                                        ("sms", "bool"), ("eventTypes", "array")])
    add(ox + 280, oy, TW, "notifications.logs", [("_id", ""), ("eventId", ""), ("channel", "EMAIL SMS"),
                                                 ("recipient", "masked"), ("template", ""), ("status", ""),
                                                 ("sentAt", "")])
    add(ox + 280, oy + 210, TW, "notifications.sms_outbox", [("_id", ""), ("to", "masked"), ("body", ""),
                                                             ("createdAt", "")])
    add(ox + 560, oy, TW, "webhooks.deliveries", [("deliveryId", ""), ("merchantId", "ref merchant"),
                                                  ("eventId", ""), ("url", ""), ("attempts", "embedded []"),
                                                  ("status", "")])
    add(ox + 560, oy + 210, TW, "webhooks.merchant_configs", [("merchantId", "ref merchant"), ("url", ""),
                                                              ("secretRef", ""), ("eventTypes", "array"),
                                                              ("active", "")])
    b.append(text(ox, oy + 390, "merchant_configs is a cache filled from the compacted merchant.webhook-config "
                                "topic.", 11, GREY, anchor="start"))

    # ---- Redis
    b.append(band(1620, cy, 380, ch, "Redis  ·  key-value, with TTL"))
    ox, oy = 1650, cy + 50
    add(ox, oy, TW, "quote:{id}  (15 min)", [("quoteId", ""), ("fee", ""), ("breakdown", ""), ("expiresAt", "")])
    add(ox, oy + 150, TW, "CHAT_SESSION  (24 h)", [("conversation_id", "PK"), ("owner", "user or guest key"),
                                                   ("provider", ""), ("started_at", "")])
    add(ox, oy + 320, TW, "CHAT_MESSAGE", [("conversation_id", "FK"), ("seq", "PK"), ("role", ""),
                                           ("content", "pseudonymized")])
    add(ox, oy + 460, TW, "PII_VAULT  (24 h)", [("conversation_id", "PK, FK"), ("token → value", "enc")])
    b.append(rel([(ox + 125, oy + 258), (ox + 125, oy + 320)]))
    b.append(label(ox + 133, oy + 289, "contains", size=10.5))
    # one vault per session, routed down the band's right-hand gutter
    b.append(rel([(ox + TW, oy + 200), (ox + 300, oy + 200), (ox + 300, oy + 494), (ox + TW, oy + 494)],
                 end="one"))

    # ---- key
    ky = cy + ch + 30
    b.append(text(40, ky, "Key:  PK primary key  ·  FK foreign key inside the same database, drawn as a solid line  "
                          "·  ref <x>  id owned by another service, never a foreign key  ·  enc  encrypted at rest "
                          "(AES-GCM)", 11.5, GREY, anchor="start"))
    b.append(text(40, ky + 22, "Line ends:  ||  exactly one   ·   o<  zero or many   ·   o|  zero or one   ·   "
                               "dashed open arrow  row derived by a projection, no foreign key", 11.5, GREY,
                  anchor="start"))
    return svg(W, H, "".join(b), "SwiftDrop data model: every database of every service")
