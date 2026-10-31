"""System figure S2: the full SwiftDrop architecture.

Layout rules, so that ~40 connections never cross:
  - tiers are stacked bands: clients, edge (API gateway), services, data, messaging (Kafka),
    external systems, observability
  - all ten services sit in one row; the gateway bar spans them from above and every request
    drops straight down into a service; Kafka spans them from below and every publish / consume
    line runs straight down to it
  - each service's store sits directly under it; its Kafka line runs beside the store
  - the three services that call outside systems sit where their outbound line can pass the END
    of the Kafka bar instead of crossing it: notification-service is the leftmost Kafka user,
    webhook-service the rightmost, and ai-assistant-service uses no Kafka at all
  - pricing-service sits next to parcel-service, so the quote check is one short arrow
  - Keycloak and Google are reached from the left, outside every band
"""

from draw import (ACTOR_H, GREY, band, cloud, cloud_anchor, cylinder, label, node, path, stickman, svg,
                  text)

W, H = 2300, 1324
BAND_L, BAND_R = 300, 2260
SW, PITCH, REST_GAP = 168, 180, 86          # service width, normal pitch, extra room for the quote check

# tier bands: (top, height)
CLIENTS, EDGE, SERVICES, DATA, MSG, EXT, OBS = (
    (30, 240), (300, 120), (464, 190), (684, 130), (844, 90), (964, 120), (1114, 180))

# services in row order: (title, lines, store title, store engine, kafka role)
#   kafka role: "pub" publishes, "sub" consumes, "both", or None
SERVICES_ROW = [
    ("user-service", ["Spring Boot 4", "profiles, addresses", "courier profiles"], "users_db", "PostgreSQL", None),
    ("notification-service", ["NestJS", "email and mock SMS", "preferences, log"], "notifications", "MongoDB", "sub"),
    ("merchant-service", ["Spring Boot 4", "onboarding, approval", "creates Keycloak clients"],
     "merchants_db", "PostgreSQL", "pub"),
    ("pricing-service", ["Spring Boot 4", "zones, rate cards", "fee quotes"], "pricing_db", "Postgres + Redis", None),
    ("parcel-service", ["Spring Boot 4", "parcels, status rules", "proofs, receipts"],
     "parcels_db", "Postgres + RustFS", "both"),
    ("dispatch-service", ["Spring Boot 4", "assignment", "run sheets, workload"], "dispatch_db", "PostgreSQL", "both"),
    ("reporting-service", ["Spring Boot 4", "delivery reports", "Kafka projections"],
     "reporting_db", "PostgreSQL", "sub"),
    ("tracking-service", ["NestJS", "timelines", "live updates (Socket.IO)"], "tracking", "MongoDB + Redis", "sub"),
    ("webhook-service", ["NestJS", "signed webhooks", "retry, dead letters"], "webhooks", "MongoDB + Redis", "sub"),
    ("ai-assistant-service", ["FastAPI", "chat agent", "MCP server /mcp"], "chat memory", "Redis", None),
]
NOTIFY, MERCHANT, PRICING, PARCEL, TRACKING, WEBHOOK, AI = 1, 2, 3, 4, 7, 8, 9


def bx(i):
    """Left edge of service i (pricing and parcel get a wider gap for the quote-check arrow)."""
    return BAND_L + 30 + i * PITCH + (REST_GAP if i >= PARCEL else 0)


def lanes(i):
    """x of the store box and of the vertical lines under service i.

    returns (store_left, store_width, kafka_x, exit_x); kafka_x / exit_x are None when unused.
    """
    x = bx(i)
    if i == NOTIFY:                       # outbound line on the far left, Kafka on the right
        return x + 26, 102, x + 150, x + 12
    if i == WEBHOOK:                      # Kafka on the far left, outbound line on the right
        return x + 40, 102, x + 18, x + 156
    if i == AI:                           # no Kafka; outbound line to the model on the right
        return x + 8, 112, None, x + 140
    if SERVICES_ROW[i][4]:                # store on the left, Kafka line on the right
        return x + 6, 114, x + 146, None
    return x + 20, 128, None, None        # store only


def strip(upper, lower):
    """Centre of the empty strip between two bands: where arrow labels go."""
    return (upper[0] + upper[1] + lower[0]) / 2


def build() -> str:
    b = []
    for name, (y, h) in (("Clients", CLIENTS), ("Edge", EDGE), ("Services", SERVICES), ("Data", DATA),
                         ("Messaging", MSG), ("External", EXT), ("Observability", OBS)):
        b.append(band(BAND_L, y, BAND_R - BAND_L, h, name, vertical_label=True))

    # ------------------------------------------------------------ clients
    web_l, web_r = bx(0), bx(6) + SW
    web_y, web_h = CLIENTS[0] + 135, 80
    for ax, who in ((480, "Customer · guest"), (800, "Merchant staff"), (1120, "Courier"),
                    (1440, "Dispatcher · admin")):
        b.append(stickman(ax, CLIENTS[0] + 15, who))
        b.append(path([(ax, CLIENTS[0] + 15 + ACTOR_H), (ax, web_y)]))
    b.append(node(web_l, web_y, web_r - web_l, web_h, "Next.js web app and BFF",
                  ["customer, merchant, courier and admin UIs · public tracking page",
                   "Auth.js session: the access token stays on the server"]))
    shop_l = bx(7) + 44
    b.append(node(shop_l, web_y, bx(AI) + SW - shop_l, web_h, "Merchant e-commerce systems",
                  ["merchant API with a client-credentials token", "Idempotency-Key on every order"], dashed=True))

    # ------------------------------------------------------------ edge
    gw_y, gw_h = EDGE[0] + 30, 60
    b.append(node(bx(0), gw_y, bx(AI) + SW - bx(0), gw_h, "API gateway  ·  Spring Cloud Gateway",
                  ["single entry point /api/v1 · JWT check with Keycloak keys · routing · "
                   "rate limits in Redis · WebSocket upgrade"]))
    web_x = (web_l + web_r) / 2
    b.append(path([(web_x, web_y + web_h), (web_x, gw_y)]))
    b.append(label(web_x + 12, strip(CLIENTS, EDGE), "REST · SSE · WebSocket", size=11))
    shop_x = bx(AI) - 76
    b.append(path([(shop_x, web_y + web_h), (shop_x, gw_y)]))
    b.append(label(shop_x + 12, strip(CLIENTS, EDGE), "client-credentials token", size=11))

    # identity, left of every band
    kc_x, kc_w, kc_y, kc_h = 20, 180, gw_y - 5, 70
    b.append(text(20, CLIENTS[0] + 130, "IDENTITY", 11, GREY, 500, anchor="start", spacing="1.6"))
    b.append(node(kc_x, kc_y, kc_w, kc_h, "Keycloak 26", ["realm swiftdrop", "roles · clients"]))
    b.append(path([(bx(0), gw_y + 8), (kc_x + kc_w, gw_y + 8)]))                    # gateway fetches keys
    b.append(label(kc_x + kc_w + 12, gw_y - 6, "JWKS keys", size=11))
    login_y = web_y + 40
    b.append(path([(web_l, login_y), (170, login_y), (170, kc_y)], dashed=True))        # BFF signs users in
    b.append(label(186, login_y - 14, "OIDC login (PKCE)", size=11))
    g_cx, g_w, g_h = 70, 110, 70
    g_cy = login_y + 23
    b.append(cloud(g_cx, g_cy, g_w, g_h, "Google"))
    gx, gy = cloud_anchor(g_cx, g_cy, g_w, g_h, "bottom")
    b.append(path([(gx, kc_y), (gx, gy)], dashed=True))
    b.append(label(gx + 8, (kc_y + gy) / 2, "Google login", size=11))

    # ------------------------------------------------------------ services, stores, Kafka
    s_y, s_h = SERVICES[0] + 25, 130
    s_bottom = s_y + s_h
    db_y, db_h = DATA[0] + 25, 80
    bus_y, bus_h = MSG[0] + 20, 50
    ext_y = EXT[0] + 25
    kafka_xs = []
    for i, (title, lines, store, engine, role) in enumerate(SERVICES_ROW):
        x = bx(i)
        b.append(node(x, s_y, SW, s_h, title, lines, header=True))
        store_l, store_w, kafka_x, exit_x = lanes(i)
        b.append(cylinder(store_l, db_y, store_w, db_h, store, [engine]))
        b.append(path([(store_l + store_w / 2, s_bottom), (store_l + store_w / 2, db_y)]))
        if kafka_x:
            kafka_xs.append(kafka_x)
            ends = {"pub": dict(end="filled"), "sub": dict(head=False, start="filled"),
                    "both": dict(start="filled", end="filled")}[role]
            b.append(path([(kafka_x, s_bottom), (kafka_x, bus_y)], **ends))
        # requests from the gateway
        drop_x = x + 45 if i == AI else x + SW / 2
        b.append(path([(drop_x, gw_y + gw_h), (drop_x, s_y)]))
    b.append(label(bx(TRACKING) + SW / 2 + 8, strip(EDGE, SERVICES), "REST + WebSocket", size=11))
    tool_x = bx(AI) + 125                       # the assistant's tool calls go back up to the gateway
    b.append(path([(tool_x, s_y), (tool_x, gw_y + gw_h)]))
    b.append(label(tool_x + 8, strip(EDGE, SERVICES), "tool calls|user's JWT", size=11))

    # parcel-service checks a quote with pricing-service
    rest_y = s_y + 65
    b.append(path([(bx(PARCEL), rest_y), (bx(PRICING) + SW, rest_y)]))
    b.append(label((bx(PRICING) + SW + bx(PARCEL)) / 2, rest_y - 26, "quote check|(REST)", anchor="middle",
                   size=11))

    bus_l, bus_r = min(kafka_xs) - 20, max(kafka_xs) + 20
    b.append(node(bus_l, bus_y, bus_r - bus_l, bus_h, "Apache Kafka (KRaft)",
                  ["topics parcel.*, dispatch.*, merchant.* · key = parcelId · arrow into Kafka = publish "
                   "(outbox relay), arrow out = consume"]))

    # ------------------------------------------------------------ external systems
    _, _, _, n_exit = lanes(NOTIFY)
    b.append(node(n_exit - 100, ext_y, 200, 70, "Mailpit SMTP · mock SMS", ["development inbox and outbox"],
                  dashed=True))
    b.append(path([(n_exit, s_bottom), (n_exit, ext_y)]))
    b.append(label(n_exit + 8, strip(DATA, MSG), "email, SMS", size=11))

    _, _, _, w_exit = lanes(WEBHOOK)
    b.append(node(w_exit - 162, ext_y, 190, 70, "Merchant endpoints", ["HMAC-signed webhooks", "retry, dead letters"],
                  dashed=True))
    b.append(path([(w_exit, s_bottom), (w_exit, ext_y)]))
    b.append(label(w_exit + 8, strip(DATA, MSG), "signed POST", size=11))

    _, _, _, a_exit = lanes(AI)
    llm_w, llm_h = 170, 80
    llm_cx = a_exit - 0.12 * llm_w              # puts the cloud's crest exactly under the line
    llm_cy = EXT[0] + 20 + llm_h / 2
    b.append(cloud(llm_cx, llm_cy, llm_w, llm_h, "LLM provider", ["OpenAI or Gemini"]))
    cx_, cy_ = cloud_anchor(llm_cx, llm_cy, llm_w, llm_h, "top")
    b.append(path([(a_exit, s_bottom), (cx_, cy_)]))
    b.append(label(a_exit + 8, strip(DATA, MSG), "chat completions", size=11))

    # ------------------------------------------------------------ observability (LGTM)
    oy = OBS[0]
    b.append(node(360, oy + 46, 200, 60, "Grafana Alloy", ["collector"]))
    rows = (("Loki", "logs"), ("Prometheus", "metrics"), ("Tempo", "traces"))
    for k, (name, what) in enumerate(rows):
        b.append(node(700, oy + 6 + k * 50, 180, 40, f"{name}  ·  {what}"))
    mid = [oy + 26 + k * 50 for k in range(3)]
    b.append(path([(560, oy + 76), (630, oy + 76)], head=False))
    b.append(path([(630, mid[0]), (630, mid[2])], head=False))
    for m in mid:
        b.append(path([(630, m), (700, m)]))
        b.append(path([(880, m), (950, m)], head=False))
    b.append(path([(950, mid[0]), (950, mid[2])], head=False))
    b.append(node(1020, oy + 46, 220, 60, "Grafana", ["dashboards · logs to traces"]))
    b.append(path([(950, oy + 76), (1020, oy + 76)]))
    b.append(label(1300, oy + 76, "Every container ships JSON logs, Prometheus metrics|"
                                  "and OTLP traces to Alloy. Kafka lag and business KPIs|"
                                  "are panels in the same Grafana.", size=11))
    return svg(W, H, "".join(b), "SwiftDrop architecture: every service, store and integration")
