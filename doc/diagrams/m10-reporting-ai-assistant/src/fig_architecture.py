"""Figure 3: layered architecture.

Layout rules, so nothing can overlap:
  - columns, left to right: reporting-service (A), other services (B), ai-assistant-service (C),
    then the external clouds. The assistant sits next to the LLM provider, so that link is one
    short straight line that crosses nothing.
  - the transport tier is two full-width layers, BFF above gateway, so every request visibly
    goes browser -> BFF -> gateway -> service.
  - vertical connectors stay inside their own column; the only horizontal runs are the
    Kafka return (in the free gutter between A and B) and the links to the external clouds.
  - arrow labels sit in the empty strip between two tier bands, never on a band edge.
"""

from draw import (ACTOR_H, GREY, band, cloud, cloud_anchor, cloud_cy, cylinder, label, node, path,
                  stickman, svg, text)

W, H = 1570, 1120
LEFT, RIGHT = 110, 1200                       # every tier band spans this x range
A, B, C = 290, 650, 1010                      # column centres
BOX_W = {A: 300, B: 260, C: 300}              # box width in each column
SPAN_L, SPAN_R = A - BOX_W[A] / 2, C + BOX_W[C] / 2      # full-width transport layers
GUTTER = (A + BOX_W[A] / 2 + B - BOX_W[B] / 2) / 2       # free strip between columns A and B

CLOUD_W, CLOUD_H = 200, 110
EXT_CX = 1330 + CLOUD_W / 2                   # external clouds, right of the bands
EXT_LABEL_X = RIGHT + 12                      # labels between the bands and the clouds

# tier bands: (top, height)
CLIENT, TRANSPORT, APP, DATA = (110, 180), (340, 210), (600, 250), (900, 200)


def strip(upper, lower):
    """Middle of the empty strip between two tier bands: where arrow labels go."""
    return (upper[0] + upper[1] + lower[0]) / 2


def build() -> str:
    b = []
    for name, (y, h) in (("Client tier", CLIENT), ("Transport", TRANSPORT),
                         ("Application", APP), ("Data and infrastructure", DATA)):
        b.append(band(LEFT, y, RIGHT - LEFT, h, name, vertical_label=True))
    b.append(text(EXT_CX, CLIENT[0] + 16, "EXTERNAL SERVICES", 11, GREY, 500, spacing="1.6"))

    # ---- client tier: each actor is linked to the UI it uses
    ui_top, ui_h = CLIENT[0] + 30, 120
    actor_y = 12
    for col, who, title, lines in (
            (A, "Dispatchers · admins", "Reports dashboard (Next.js)",
             ["deliveries per day", "success rate · per courier"]),
            (C, "Customers · merchants · guests", "Chat widget (Next.js)",
             ["streaming answers", "tool-call chips"])):
        b.append(stickman(col, actor_y, who))
        b.append(path([(col, actor_y + ACTOR_H), (col, ui_top)]))
        b.append(node(col - BOX_W[col] / 2, ui_top, BOX_W[col], ui_h, title, lines, header=True))

    # ---- transport tier: BFF layer above the gateway layer
    layer_h = 56
    bff_y = TRANSPORT[0] + 30
    gw_y = bff_y + layer_h + 44
    b.append(node(SPAN_L, bff_y, SPAN_R - SPAN_L, layer_h, "Next.js BFF proxy",
                  ["route handlers add the access token server-side; the browser never holds it"]))
    b.append(node(SPAN_L, gw_y, SPAN_R - SPAN_L, layer_h, "Spring Cloud Gateway",
                  ["single entry point · JWT validation with Keycloak keys · routing · rate limiting"]))

    for col, caption in ((A, "REST / JSON|report queries"), (C, "REST / SSE|chat stream")):
        b.append(path([(col, ui_top + ui_h), (col, bff_y)]))
        b.append(label(col + 12, strip(CLIENT, TRANSPORT), caption, size=11))
    b.append(path([(B, bff_y + layer_h), (B, gw_y)]))
    b.append(label(B + 12, bff_y + layer_h + 22, "adds the bearer token", size=11))

    # ---- application tier
    svc_top, svc_h = APP[0] + 40, 180
    services = {
        A: ("reporting-service", ["Spring Boot 4", "Kafka consumers · projections", "report APIs",
                                  "role DISPATCHER or ADMIN"]),
        B: ("Other services", ["parcel · tracking", "pricing · dispatch", "merchant", "(other team members)"]),
        C: ("ai-assistant-service", ["FastAPI · agent loop", "guardrails · PII redaction", "MCP server at /mcp",
                                     "tools call SwiftDrop APIs"]),
    }
    for col, (title, lines) in services.items():
        b.append(node(col - BOX_W[col] / 2, svc_top, BOX_W[col], svc_h, title, lines, header=True))

    gw_bottom = gw_y + layer_h
    for x in (A, B, C - 60):                   # gateway routes each request with the user's JWT
        b.append(path([(x, gw_bottom), (x, svc_top)]))
        b.append(label(x + 12, strip(TRANSPORT, APP), "bearer JWT", size=11))
    tool_x = C + 80                            # the assistant's tool calls go back up to the gateway
    b.append(path([(tool_x, svc_top), (tool_x, gw_bottom)]))
    b.append(label(tool_x + 12, strip(TRANSPORT, APP), "tool calls|user's token", size=11))

    # ---- external services: each line meets its cloud where the outline is square to it
    kc_row = bff_y + layer_h / 2               # Auth.js on the BFF signs the user in
    kc_cy = cloud_cy(kc_row, CLOUD_H)
    b.append(cloud(EXT_CX, kc_cy, CLOUD_W, CLOUD_H, "Keycloak", ["OIDC · Google login"]))
    kx, ky = cloud_anchor(EXT_CX, kc_cy, CLOUD_W, CLOUD_H, "left")
    b.append(path([(SPAN_R, ky), (kx, ky)], dashed=True))
    b.append(label(EXT_LABEL_X, ky - 26, "OIDC login|(Auth.js, PKCE)", size=11))
    base_x, base_y = cloud_anchor(EXT_CX, kc_cy, CLOUD_W, CLOUD_H, "bottom")
    gw_row = gw_y + layer_h / 2                # the gateway fetches the signing keys
    b.append(path([(SPAN_R, gw_row), (base_x, gw_row), (base_x, base_y)], dashed=True))
    b.append(label(base_x + 14, (gw_row + base_y) / 2, "JWKS keys|(token validation)", size=11))

    llm_row = svc_top + 100
    llm_cy = cloud_cy(llm_row, CLOUD_H)
    b.append(cloud(EXT_CX, llm_cy, CLOUD_W, CLOUD_H, "LLM provider", ["OpenAI or Gemini"]))
    lx, ly = cloud_anchor(EXT_CX, llm_cy, CLOUD_W, CLOUD_H, "left")
    b.append(path([(SPAN_R, ly), (lx, ly)]))
    # personal data is replaced by placeholders before it leaves SwiftDrop for the third-party model
    b.append(label(EXT_LABEL_X, ly - 26, "chat completions|pseudonymized data", size=11))

    # ---- data tier: each service writes straight down into its own store
    db_top, db_h = DATA[0] + 50, 100
    stores = {A: ("PostgreSQL", ["reporting_db projections"], 260, "projections"),
              B: ("Kafka", ["parcel event topics"], 240, "outbox relay"),
              C: ("Redis", ["pseudonymized chat, TTL 24h"], 260, "conversation state")}
    for col, (title, lines, w, caption) in stores.items():
        b.append(cylinder(col - w / 2, db_top, w, db_h, title, lines))
        b.append(path([(col, svc_top + svc_h), (col, db_top)]))
        b.append(label(col + 12, strip(APP, DATA), caption, size=11))

    # Kafka feeds reporting-service: off the cylinder's straight side, up the free gutter
    kafka_left = B - stores[B][2] / 2
    side_y = db_top + db_h / 2 + 10
    entry_y = svc_top + 120
    b.append(path([(kafka_left, side_y), (GUTTER, side_y), (GUTTER, entry_y), (A + BOX_W[A] / 2, entry_y)]))
    b.append(label(GUTTER + 14, strip(APP, DATA), "parcel events", size=11))
    return svg(W, H, "".join(b), "Architecture: layered microservices with a BFF")
