"""System figure S3: EER diagram (Chen notation) of the whole SwiftDrop domain.

Conceptual model across all services (not one schema per database): entities, attributes,
relationships, weak entities, specializations, cardinality and participation.

Build order: entities, relationships, their orthogonal lines and cardinalities first; then the
attribute ovals are placed automatically (attr_place) in the directions each entity leaves free,
so no oval or attribute line can land on something already drawn.
"""

from attr_place import place
from draw import GREY, svg, text
from eer import card, entity, isa, link, relation

W, H = 3800, 2700
K, P, D, M, N_ = "key", "partial", "derived", "multi", "plain"


def build() -> str:
    b = []
    attrs = []                      # (owner, items, ranges) placed after the structure

    def E(cx, cy, name, items=(), ranges=(), flat=0.62, **kw):
        s, shp = entity(cx, cy, name, **kw)
        b.append(s)
        if items:
            attrs.append((shp, items, ranges, flat))
        return shp

    def R(cx, cy, name, items=(), ranges=(), **kw):
        s, shp = relation(cx, cy, name, **kw)
        b.append(s)
        if items:
            attrs.append((shp, items, ranges, 0.62))
        return shp

    def L(points, total=False):
        b.append(link(points, total))

    def C(x, y, value):
        b.append(card(x, y, value))

    def ISA(cx, cy, letter):
        s, shp = isa(cx, cy, letter)
        b.append(s)
        return shp

    # ================================================================ identity
    user = E(1400, 270, "USER",
             [("user_id", K), ("full_name", N_), ("email", N_), ("phone", N_), ("status", N_), ("created_at", N_),
              ("roles", M)], [(190, 350)])
    address = E(600, 270, "ADDRESS",
                [("address_id", K), ("label", N_), ("line1", N_), ("city", N_), ("district", N_),
                 ("postal_code", N_), ("location", N_)], [(95, 265)])
    chat = E(2500, 270, "CHAT_SESSION",
             [("conversation_id", K), ("provider", N_), ("started_at", N_), ("expires_at", D)], [(200, 340)])
    msg = E(3350, 270, "CHAT_MESSAGE", [("seq", P), ("role", N_), ("content", N_), ("sent_at", N_)],
            [(200, 520)], weak=True)
    has = R(1000, 270, "has")
    starts = R(1950, 270, "starts")
    contains = R(2930, 270, "contains", identifying=True)
    L([(user.left, 270), (has.right, 270)]); C(user.left - 16, 286, "1")
    L([(has.left, 270), (address.right, 270)], total=True); C(address.right + 16, 290, "N")
    L([(user.right, 270), (starts.left, 270)]); C(user.right + 16, 286, "1")
    L([(starts.right, 270), (chat.left, 270)]); C(chat.left - 16, 286, "N")
    L([(chat.right, 270), (contains.left, 270)]); C(chat.right + 16, 286, "1")
    L([(contains.right, 270), (msg.left, 270)], total=True); C(msg.left - 16, 290, "N")

    # USER specialization: overlapping (a person can hold several roles), total
    circle = ISA(1400, 410, "o")
    L([(1400, user.bottom), (1400, circle.top)], total=True)
    subs_y, bus_y = 600, 490
    admin = E(350, subs_y, "ADMIN")
    owner = E(800, subs_y, "MERCHANT_OWNER")
    customer = E(1250, subs_y, "CUSTOMER")
    dispatcher = E(2000, subs_y, "DISPATCHER")
    courier = E(2600, subs_y, "COURIER",
                [("vehicle_type", N_), ("max_daily_capacity", N_), ("active", N_), ("active_count", D),
                 ("delivered_today", D)], [(12, 80), (100, 168), (192, 250)])
    L([(1400, circle.bottom), (1400, bus_y)])
    L([(admin.cx, bus_y), (courier.cx, bus_y)])
    for sub in (admin, owner, customer, dispatcher, courier):
        L([(sub.cx, bus_y), (sub.cx, sub.top)])

    summ_c = R(3030, subs_y, "summarised in", identifying=True)
    daily_c = E(3450, subs_y, "COURIER_DAILY_STATS",
                [("day", P), ("delivered", N_), ("failed", N_), ("success_rate", D)],
                [(200, 520)], weak=True)
    L([(courier.right, subs_y), (summ_c.left, subs_y)]); C(courier.right + 16, subs_y - 14, "1")
    L([(summ_c.right, subs_y), (daily_c.left, subs_y)], total=True); C(daily_c.left - 16, subs_y - 18, "N")

    # ================================================================ merchants
    rel_y = 880
    reviews = R(350, rel_y, "reviews")
    owns = R(800, rel_y, "owns")
    merchant = E(600, 1200, "MERCHANT",
                 [("merchant_id", K), ("business_name", N_), ("br_number", N_), ("contact_email", N_),
                  ("status", N_), ("reviewed_at", N_)], [(125, 212)], w=240)
    L([(350, admin.bottom), (350, reviews.top)]); C(364, admin.bottom + 18, "1")
    L([(350, reviews.bottom), (350, 1110), (560, 1110), (560, merchant.top)]); C(546, merchant.top - 18, "N")
    L([(800, owner.bottom), (800, owns.top)]); C(814, owner.bottom + 18, "1")
    L([(800, owns.bottom), (800, 1110), (680, 1110), (680, merchant.top)], total=True)
    C(694, merchant.top - 18, "1")

    places = R(835, 1200, "places")
    morder = E(1020, 1200, "MERCHANT_ORDER", [("merchant_order_ref", N_), ("idempotency_key", N_)], [(205, 335)])
    L([(merchant.right, 1200), (places.left, 1200)]); C(merchant.right + 14, 1218, "1")
    L([(places.right, 1200), (morder.left, 1200)], total=True); C(morder.left - 14, 1222, "N")

    holds = R(560, 1400, "holds")
    cred = E(500, 1600, "API_CREDENTIAL",
             [("credential_id", K), ("keycloak_client_id", N_), ("created_at", N_), ("rotated_at", N_),
              ("revoked_at", N_)], [(60, 250)])
    L([(560, merchant.bottom), (560, holds.top)]); C(546, merchant.bottom + 18, "1")
    L([(560, holds.bottom), (560, cred.top)], total=True); C(546, cred.top - 18, "N")

    configures = R(680, 1400, "configures")
    endpoint = E(720, 1600, "WEBHOOK_ENDPOINT",
                 [("endpoint_id", K), ("url", N_), ("secret", N_), ("event_types", M), ("active", N_)],
                 [(-70, 75)], w=170)
    L([(680, merchant.bottom), (680, configures.top)]); C(694, merchant.bottom + 18, "1")
    L([(680, configures.bottom), (680, endpoint.top)], total=True); C(694, endpoint.top - 18, "N")

    receives = R(720, 1780, "receives")
    delivery = E(740, 1950, "WEBHOOK_DELIVERY",
                 [("delivery_id", K), ("event_id", N_), ("status", N_), ("attempts", M), ("last_http_status", N_)],
                 [(60, 250)], w=180)
    L([(720, endpoint.bottom), (720, receives.top)]); C(734, endpoint.bottom + 18, "1")
    L([(720, receives.bottom), (720, delivery.top)], total=True); C(734, delivery.top - 18, "N")

    # ================================================================ parcels
    books = R(1250, rel_y, "books")
    cparcel = E(1250, 1200, "CUSTOMER_PARCEL")
    L([(1250, customer.bottom), (1250, books.top)]); C(1264, customer.bottom + 18, "1")
    L([(1250, books.bottom), (1250, cparcel.top)], total=True); C(1264, cparcel.top - 18, "N")

    parcel = E(2000, 1560, "PARCEL",
               [("parcel_id", K), ("tracking_number", K), ("status", N_), ("weight_kg", N_), ("service_level", N_),
                ("fee", N_), ("created_at", N_)], [(186, 262)], w=220, h=100, flat=0.95)
    pisa = ISA(1135, 1380, "d")
    pbus = 1300
    L([(morder.cx, morder.bottom), (morder.cx, pbus)])
    L([(cparcel.cx, cparcel.bottom), (cparcel.cx, pbus)])
    L([(morder.cx, pbus), (cparcel.cx, pbus)])
    L([(1135, pbus), (1135, pisa.top)])
    L([(1135, pisa.bottom), (1135, 1590), (parcel.left, 1590)], total=True)

    assigns = R(2000, rel_y, "assigns", [("run_date", N_), ("assigned_at", N_), ("status", N_)], [(110, 250)])
    L([(2000, dispatcher.bottom), (2000, assigns.top)]); C(2014, dispatcher.bottom + 18, "1")
    L([(2600, courier.bottom), (2600, rel_y), (assigns.right, rel_y)]); C(2614, courier.bottom + 18, "1")
    L([(2000, assigns.bottom), (2000, parcel.top)]); C(2014, parcel.top - 18, "N")

    quote = E(2600, 1200, "QUOTE", [("quote_id", K), ("fee", N_), ("breakdown", N_), ("expires_at", N_)],
              [(205, 335)])
    rate = E(3350, 1200, "RATE_CARD",
             [("rate_card_id", K), ("base_fee", N_), ("per_kg_fee", N_), ("express_multiplier", N_),
              ("cod_fee_pct", N_), ("effective_from", N_), ("effective_to", N_)], [(200, 375)])
    uses = R(2960, 1200, "uses")
    L([(quote.right, 1200), (uses.left, 1200)]); C(quote.right + 14, 1218, "N")
    L([(uses.right, 1200), (rate.left, 1200)]); C(rate.left - 14, 1218, "1")

    size = E(2250, 1200, "SIZE_CLASS",
             [("code", K), ("max_weight_kg", N_), ("max_dimensions_cm", N_), ("multiplier", N_)],
             [(30, 160), (200, 340)])
    forsize = R(2425, 1200, "for size", w=96)
    L([(quote.left, 1200), (forsize.right, 1200)]); C(quote.left - 14, 1218, "N")
    L([(forsize.left, 1200), (size.right, 1200)]); C(size.right + 14, 1218, "1")

    priced = R(2600, 1370, "priced by")
    L([(2600, quote.bottom), (2600, priced.top)]); C(2614, quote.bottom + 18, "1")
    L([(2600, priced.bottom), (2600, 1440), (2080, 1440), (2080, parcel.top)], total=True)
    C(2094, parcel.top - 18, "1")

    zone = E(3350, 1560, "ZONE", [("zone_id", K), ("code", N_), ("name", N_), ("active", N_), ("districts", M)],
             [(-35, 70)], h=100)
    picked = R(2540, 1530, "picked up in")
    delivered = R(2820, 1590, "delivered in")
    L([(parcel.right, 1530), (picked.left, 1530)], total=True); C(parcel.right + 16, 1512, "N")
    L([(picked.right, 1530), (zone.left, 1530)]); C(zone.left - 14, 1514, "1")
    L([(parcel.right, 1590), (delivered.left, 1590)], total=True); C(parcel.right + 16, 1610, "N")
    L([(delivered.right, 1590), (zone.left, 1590)]); C(zone.left - 14, 1608, "1")
    frm = R(3300, 1370, "from", w=80)
    to = R(3410, 1370, "to", w=80)
    L([(3300, rate.bottom), (3300, frm.top)], total=True); C(3284, rate.bottom + 18, "N")
    L([(3300, frm.bottom), (3300, zone.top)]); C(3284, zone.top - 18, "1")
    L([(3410, rate.bottom), (3410, to.top)], total=True); C(3426, rate.bottom + 18, "N")
    L([(3410, to.bottom), (3410, zone.top)]); C(3426, zone.top - 18, "1")

    summ_z = R(3350, 1760, "summarised in", identifying=True)
    daily_z = E(3350, 1960, "DAILY_DELIVERY_STATS",
                [("day", P), ("created", N_), ("delivered", N_), ("failed", N_), ("cancelled", N_),
                 ("success_rate", D)], [(-80, 150)], weak=True)
    L([(3350, zone.bottom), (3350, summ_z.top)]); C(3364, zone.bottom + 18, "1")
    L([(3350, summ_z.bottom), (3350, daily_z.top)], total=True); C(3364, daily_z.top - 18, "N")

    # ================================================================ status history, proof, notifications
    hist = R(2000, 1780, "has history", identifying=True)
    change = E(2000, 1950, "STATUS_CHANGE",
               [("occurred_at", P), ("from_status", N_), ("to_status", N_), ("note", N_), ("failure_reason", N_),
                ("location", N_), ("actor_role", N_)], [(22, 78), (102, 158), (200, 252), (288, 340)], weak=True)
    L([(2000, parcel.bottom), (2000, hist.top)]); C(2014, parcel.bottom + 18, "1")
    L([(2000, hist.bottom), (2000, change.top)], total=True); C(2014, change.top - 18, "N")

    reports = R(1350, 1950, "reports")
    L([(delivery.right, 1950), (reports.left, 1950)], total=True); C(delivery.right + 14, 1932, "N")
    L([(reports.right, 1950), (change.left, 1950)]); C(change.left - 14, 1932, "1")

    evidenced = R(2000, 2160, "evidenced by")
    proof = E(2000, 2350, "DELIVERY_PROOF",
              [("proof_id", K), ("type", N_), ("recipient_name", N_), ("object_key", N_)], [(-60, 240)])
    L([(2000, change.bottom), (2000, evidenced.top)]); C(2014, change.bottom + 18, "1")
    L([(2000, evidenced.bottom), (2000, proof.top)], total=True); C(2014, proof.top - 18, "1")

    triggers = R(2450, 1950, "triggers")
    notif = E(2850, 1950, "NOTIFICATION",
              [("notification_id", K), ("template", N_), ("status", N_), ("sent_at", N_), ("recipient", N_)],
              [(200, 340)])
    L([(change.right, 1950), (triggers.left, 1950)]); C(change.right + 14, 1932, "1")
    L([(triggers.right, 1950), (notif.left, 1950)], total=True); C(notif.left - 14, 1932, "N")
    nisa = ISA(2850, 2100, "d")
    L([(2850, notif.bottom), (2850, nisa.top)], total=True)
    email = E(2660, 2300, "EMAIL_NOTIFICATION", [("email_address", N_), ("subject", N_)], [(40, 140)])
    sms = E(3040, 2300, "SMS_NOTIFICATION", [("phone_number", N_)], [(40, 140)])
    nbus = 2190
    L([(2850, nisa.bottom), (2850, nbus)])
    L([(email.cx, nbus), (sms.cx, nbus)])
    L([(email.cx, nbus), (email.cx, email.top)])
    L([(sms.cx, nbus), (sms.cx, sms.top)])

    # ---- key (drawn before the attributes so they keep clear of it)
    ky = H - 60
    b.append(text(60, ky, "Notation (Chen / EER): rectangle entity · double rectangle weak entity · diamond relationship · "
                          "double diamond identifying relationship · oval attribute · underlined key · dashed underline "
                          "partial key · dashed oval derived · double oval multivalued", 12, GREY, anchor="start"))
    b.append(text(60, ky + 24, "Circle: specialization, d = disjoint, o = overlapping · double line: total participation · "
                               "1, N: cardinality, written beside the entity it counts", 12, GREY, anchor="start"))

    svg_open = svg(W, H, "", "")                         # registers the canvas so placement keeps inside it
    del svg_open
    for shp, items, ranges, flat in attrs:
        b.append(place(shp, items, ranges, flat=flat))
    return svg(W, H, "".join(b), "SwiftDrop EER diagram: the whole domain in Chen notation")
