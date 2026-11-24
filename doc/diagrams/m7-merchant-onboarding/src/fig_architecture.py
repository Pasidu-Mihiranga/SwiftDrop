"""Figure 3: layered architecture.

Layout rules, so nothing can overlap:
  - columns, left to right: the merchant's own system (A), the order path (B), onboarding (C). The
    onboarding column sits next to Keycloak, because merchant-service is the one service calling it.
  - the merchant system does not use the BFF: it goes straight to the gateway, which is why the
    BFF layer spans columns B and C only.
  - Kafka sits between the two publishers, so each outbox relay is one short straight line; the
    consumers in column A are fed along the bottom of the data band, under the databases.
  - the token request runs above the actors, clear of everything, into the top of the Keycloak cloud.
"""

import paths  # noqa: F401
from draw import (GREY, band, cloud, cloud_anchor, cloud_cy, cylinder, label, node, path, stickman, svg, text)

W, H = 1570, 1160
LEFT, RIGHT = 110, 1200
A, B, C = 270, 620, 1010
BOX_W = {A: 240, B: 300, C: 300}
GW_L, GW_R = A - BOX_W[A] / 2, C + BOX_W[C] / 2
BFF_L = B - BOX_W[B] / 2
CLOUD_W, CLOUD_H = 200, 110
EXT_CX = 1330 + CLOUD_W / 2
EXT_LABEL_X = RIGHT + 12
TOKEN_Y = 18

CLIENT, TRANSPORT, APP, DATA = (150, 180), (380, 210), (640, 250), (940, 200)


def strip(upper, lower):
    """Middle of the empty strip between two tier bands: where arrow labels go."""
    return (upper[0] + upper[1] + lower[0]) / 2


def build() -> str:
    b = []
    for name, (y, h) in (("Client tier", CLIENT), ("Transport", TRANSPORT),
                         ("Application", APP), ("Data and infrastructure", DATA)):
        b.append(band(LEFT, y, RIGHT - LEFT, h, name, vertical_label=True))
    b.append(text(EXT_CX - 90, TOKEN_Y + 40, "EXTERNAL SERVICES", 11, GREY, 500, spacing="1.6"))

    # ---- client tier: people use the web app; the merchant's own system calls the API directly
    ui_top, ui_h = CLIENT[0] + 30, 120
    actor_y = 44
    for col, who, title, lines in ((B, "Merchant user", "Merchant portal (Next.js)",
                                    ["register · credentials", "orders dashboard · CSV export"]),
                                   (C, "Admin", "Admin approvals (Next.js)",
                                    ["approval queue", "approve · reject · suspend"])):
        b.append(stickman(col, actor_y, who))
        b.append(path([(col, actor_y + 82), (col, ui_top)]))
        b.append(node(col - BOX_W[col] / 2, ui_top, BOX_W[col], ui_h, title, lines, header=True))
    b.append(node(A - BOX_W[A] / 2, ui_top, BOX_W[A], ui_h, "Merchant system",
                  ["online store or simulator", "client credentials"], header=True, dashed=True))

    # ---- transport tier
    layer_h = 56
    bff_y = TRANSPORT[0] + 30
    gw_y = bff_y + layer_h + 44
    b.append(node(BFF_L, bff_y, GW_R - BFF_L, layer_h, "Next.js BFF proxy",
                  ["route handlers add the access token server-side; the browser never holds it"]))
    b.append(node(GW_L, gw_y, GW_R - GW_L, layer_h, "Spring Cloud Gateway",
                  ["single entry point · JWT validation with Keycloak keys · routing · per-client rate limiting"]))
    for col in (B, C):
        b.append(path([(col, ui_top + ui_h), (col, bff_y)]))
        b.append(label(col + 12, strip(CLIENT, TRANSPORT), "REST / JSON", size=11))
    b.append(path([(C - 100, bff_y + layer_h), (C - 100, gw_y)]))
    b.append(label(C - 88, bff_y + layer_h + 22, "adds the bearer token", size=11))
    b.append(path([(A, ui_top + ui_h), (A, gw_y)]))
    b.append(label(A + 12, strip(CLIENT, TRANSPORT) + 60, "POST /api/v1/merchant/orders|bearer JWT, Idempotency-Key",
                   size=11))

    # ---- application tier
    svc_top, svc_h = APP[0] + 40, 180
    b.append(node(B - BOX_W[B] / 2, svc_top, BOX_W[B], svc_h, "parcel-service",
                  ["Spring Boot 4", "MerchantOrderController", "idempotency records",
                   "merchant status cache"], header=True))
    b.append(node(C - BOX_W[C] / 2, svc_top, BOX_W[C], svc_h, "merchant-service",
                  ["Spring Boot 4", "registration · approval workflow", "Keycloak client provisioning",
                   "credential rotation"], header=True))
    pricing_h, feed_h = 76, 88
    a_x = A - BOX_W[A] / 2
    b.append(node(a_x, svc_top, BOX_W[A], pricing_h, "pricing-service", ["fee quotes"]))
    feed_y = svc_top + svc_h - feed_h
    b.append(node(a_x, feed_y, BOX_W[A], feed_h, "Event consumers", ["webhook · notification", "dispatch · tracking"]))

    gw_bottom = gw_y + layer_h
    for x in (B, C):
        b.append(path([(x, gw_bottom), (x, svc_top)]))
        b.append(label(x + 12, strip(TRANSPORT, APP), "bearer JWT", size=11))
    quote_y = svc_top + 38
    b.append(path([(B - BOX_W[B] / 2, quote_y), (A + BOX_W[A] / 2, quote_y)]))
    b.append(label((B - BOX_W[B] / 2 + A + BOX_W[A] / 2) / 2, quote_y - 24, "inline|quote", anchor="middle", size=11))

    # ---- data tier
    db_top, db_h = DATA[0] + 36, 100
    kafka_x, kafka_w = (B + C) / 2, 300
    stores = {550: ("PostgreSQL", ["parcels_db"], 210), 1080: ("PostgreSQL", ["merchants_db"], 190),
              kafka_x: ("Kafka", ["parcel.* · merchant.lifecycle"], kafka_w)}
    for x, (title, lines, w) in stores.items():
        b.append(cylinder(x - w / 2, db_top, w, db_h, title, lines))
    svc_bottom = svc_top + svc_h
    row = strip(APP, DATA)
    b.append(path([(550, svc_bottom), (550, db_top)]))
    b.append(label(538, row, "orders", anchor="end", size=11))
    b.append(path([(725, svc_bottom), (725, db_top + 4)]))
    b.append(label(713, row, "outbox|relay", anchor="end", size=11))
    b.append(path([(755, db_top), (755, svc_bottom)]))
    b.append(label(767, row, "lifecycle|events", size=11))
    b.append(path([(905, svc_bottom), (905, db_top + 4)]))
    b.append(label(917, row, "outbox|relay", size=11))
    b.append(path([(1080, svc_bottom), (1080, db_top)]))
    b.append(label(1092, row, "merchants", size=11))

    # Kafka feeds the consumers along the bottom of the data band, under parcels_db
    run_y = db_top + db_h + 26
    b.append(path([(kafka_x, db_top + db_h), (kafka_x, run_y), (A, run_y), (A, svc_bottom)]))
    b.append(label(A + 12, row, "ParcelCreated, status and|MerchantLifecycleChanged", size=11))

    # ---- Keycloak: every line meets the cloud where its outline is square to it
    kc_row = bff_y + layer_h / 2
    kc_cy = cloud_cy(kc_row, CLOUD_H)
    b.append(cloud(EXT_CX, kc_cy, CLOUD_W, CLOUD_H, "Keycloak", ["OIDC · client credentials"]))
    kx, ky = cloud_anchor(EXT_CX, kc_cy, CLOUD_W, CLOUD_H, "left")
    b.append(path([(GW_R, ky), (kx, ky)], dashed=True))
    b.append(label(EXT_LABEL_X, ky - 26, "OIDC login|(Auth.js, PKCE)", size=11))

    top_x, top_y = cloud_anchor(EXT_CX, kc_cy, CLOUD_W, CLOUD_H, "top")
    b.append(path([(A, ui_top), (A, TOKEN_Y), (top_x, TOKEN_Y), (top_x, top_y)]))
    b.append(label(A + 12, 100, "client_credentials|token request", size=11))

    jwks_x, jwks_y = cloud_anchor(EXT_CX, kc_cy, CLOUD_W, CLOUD_H, "bottom", at=EXT_CX - 44)
    gw_row = gw_y + layer_h / 2
    b.append(path([(GW_R, gw_row), (jwks_x, gw_row), (jwks_x, jwks_y)], dashed=True))
    b.append(label(EXT_LABEL_X, gw_row + 26, "JWKS keys|(token validation)", size=11))

    admin_x, admin_y = cloud_anchor(EXT_CX, kc_cy, CLOUD_W, CLOUD_H, "bottom", at=EXT_CX + 44)
    admin_row = svc_top + 90
    b.append(path([(GW_R, admin_row), (admin_x, admin_row), (admin_x, admin_y)]))
    b.append(label(EXT_LABEL_X, admin_row + 26, "Admin API|(service account)", size=11))
    return svg(W, H, "".join(b), "Architecture: layered microservices with a BFF")
