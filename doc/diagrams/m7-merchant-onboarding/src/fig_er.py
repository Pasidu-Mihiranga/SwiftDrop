"""Figure 2: data model. One band per store; relationships run in the free gutters.

Solid crow's-foot lines are real foreign keys inside one database. Dashed lines cross a service
boundary: those values are carried by id or by an event, never by a foreign key.
"""

import paths  # noqa: F401
from draw import band, label, path, svg, text, GREY
from fig_er_shared import table

W, H = 1400, 840


def caption(x, y, body):
    return text(x, y, body, 11, GREY, 500, anchor="start", spacing="1.6")


def build() -> str:
    b = []
    b.append(band(40, 50, 620, 540))
    b.append(caption(58, 70, "MERCHANTS_DB  (POSTGRESQL, MERCHANT-SERVICE)"))
    b.append(band(780, 50, 580, 540))
    b.append(caption(798, 70, "PARCELS_DB  (POSTGRESQL, PARCEL-SERVICE)"))
    b.append(band(40, 620, 1320, 190))
    b.append(caption(760, 640, "KEYCLOAK REALM SWIFTDROP  (NOT A SERVICE DATABASE)"))

    merchant, merchant_h = table(70, 110, 270, "MERCHANT", [
        ("id", "PK"), ("owner_user_id", "ref"), ("business_name", ""), ("br_number", "unique"),
        ("contact_email", ""), ("phone_enc", "encrypted"), ("website", ""),
        ("pickup_address_enc", "encrypted"), ("status", ""), ("reviewed_by", "ref"), ("reviewed_at", ""),
        ("rejection_reason", ""), ("created_at", "")])
    b.append(merchant)
    credential, _ = table(400, 110, 240, "API_CREDENTIAL", [
        ("id", "PK"), ("merchant_id", "FK"), ("keycloak_client_id", ""), ("created_at", ""), ("rotated_at", ""),
        ("revoked_at", "")])
    b.append(credential)
    outbox, _ = table(400, 340, 240, "OUTBOX_EVENT", [
        ("id", "PK"), ("aggregate_id", ""), ("event_type", ""), ("payload", "jsonb"), ("created_at", ""),
        ("published_at", "")])
    b.append(outbox)

    parcel, _ = table(800, 110, 270, "PARCEL", [
        ("id", "PK"), ("tracking_number", "unique"), ("source", "MERCHANT"), ("merchant_id", "ref"),
        ("merchant_order_ref", ""), ("fee", ""), ("status", ""), ("created_at", "")])
    b.append(parcel)
    idem, _ = table(1130, 110, 210, "IDEMPOTENCY_RECORD", [
        ("key", "PK"), ("merchant_id", "PK"), ("parcel_id", "FK"), ("request_hash", "SHA-256"), ("created_at", "")])
    b.append(idem)
    cache, _ = table(800, 420, 270, "MERCHANT_STATUS_CACHE", [
        ("merchant_id", "PK"), ("status", ""), ("updated_at", "")])
    b.append(cache)
    processed, _ = table(1130, 420, 210, "PROCESSED_EVENT", [
        ("event_id", "PK"), ("consumer", "PK"), ("processed_at", "")])
    b.append(processed)

    user, _ = table(70, 660, 270, "USER", [("sub", "id"), ("merchant_id", "attribute"), ("MERCHANT", "realm role")])
    client, _ = table(400, 660, 300, "CLIENT merchant-<id>", [
        ("client secret", "held only here"), ("merchant_id", "hard-coded claim"), ("MERCHANT_API", "realm role")])
    b.append(user)
    b.append(client)

    # real foreign keys
    b.append(path([(340, 160), (400, 160)], start="one", end="many"))
    b.append(label(370, 144, "owns", anchor="middle", size=11))
    b.append(path([(1070, 180), (1130, 180)], start="one", end="zeroone"))
    b.append(label(1100, 160, "keyed", anchor="middle", size=11))

    # references that leave the database: dashed, in their own gutters
    b.append(path([(120, 110 + merchant_h), (120, 660)], dashed=True, end="open"))
    b.append(label(130, 605, "owner_user_id", size=11))
    b.append(path([(400, 250), (370, 250), (370, 606), (550, 606), (550, 660)], dashed=True, end="open"))
    b.append(label(562, 605, "keycloak_client_id", size=11))
    # the status reaches parcels_db as an event, over the top of the band and down the free gutter
    b.append(path([(250, 110), (250, 90), (740, 90), (740, 450), (800, 450)], dashed=True, end="open"))
    b.append(label(668, 300, "status|carried by|lifecycle|events", size=11))
    return svg(W, H, "".join(b), "Data model: merchants, credentials and idempotency records")
