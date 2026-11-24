"""Figure 4: integration patterns.

Boxes are laid out left to right from a running cursor, so each gap is wide enough for the
label that sits in it: the gap is max(MIN_GAP, label width + padding). Nothing can overlap.
"""

from draw import BLACK, GREY, band, cloud, cloud_anchor, cylinder, label, node, path, svg, text

W, H = 1660, 880
MIN_GAP = 76
LABEL_SIZE = 11


def label_width(caption: str) -> float:
    """Same formula the label helper uses for its white backing box."""
    longest = max(caption.split("|"), key=len)
    return len(longest) * LABEL_SIZE * 0.52 + 12


def gap_for(caption: str) -> float:
    return max(MIN_GAP, label_width(caption) + 26)


def arrow_with_label(x1, y, x2, caption):
    """Horizontal arrow with its caption centred above, inside the gap."""
    return path([(x1, y), (x2, y)]) + label((x1 + x2) / 2, y - 18, caption, anchor="middle", size=LABEL_SIZE)


def build() -> str:
    b = []
    b.append(band(40, 60, W - 80, 320))
    b.append(text(58, 80, "ASYNCHRONOUS  ·  BUILDS THE REPORTS", 11, GREY, 500, anchor="start", spacing="1.6"))
    b.append(band(40, 430, W - 80, 410))
    b.append(text(58, 450, "SYNCHRONOUS  ·  SERVES USERS AND THE ASSISTANT", 11, GREY, 500, anchor="start", spacing="1.6"))

    # ---- asynchronous lane: cursor walks right, each gap sized for its label
    y = 150
    mid = y + 40
    x = 75

    b.append(node(x, y, 180, 80, "parcel-service", ["status change"]))
    x += 180
    cap = "1 · transactional outbox"
    b.append(arrow_with_label(x, mid, x + gap_for(cap), cap))
    x += gap_for(cap)

    b.append(cylinder(x, y - 4, 160, 88, "outbox_event"))
    x += 160
    cap = "2 · publish / subscribe"
    b.append(arrow_with_label(x, mid, x + gap_for(cap), cap))
    x += gap_for(cap)

    b.append(node(x, y, 160, 80, "Kafka broker", ["key = parcelId"]))
    x += 160
    cap = "3 · consumer group"
    b.append(arrow_with_label(x, mid, x + gap_for(cap), cap))
    x += gap_for(cap)

    reporting_x = x
    b.append(node(x, y, 190, 80, "reporting-service", ["competing consumers"]))
    x += 190
    cap = "5 · CQRS read model"
    b.append(arrow_with_label(x, mid, x + gap_for(cap), cap))
    x += gap_for(cap)

    stats_x = x
    b.append(cylinder(x, y - 4, 170, 88, "daily stats", ["read model"]))

    # idempotency store hangs below reporting-service
    b.append(cylinder(reporting_x + 10, 265, 170, 80, "processed_event"))
    b.append(path([(reporting_x + 95, y + 80), (reporting_x + 95, 265)]))
    b.append(label(reporting_x + 105, 250, "4 · idempotent consumer", size=LABEL_SIZE))

    # ---- synchronous lane, row 1: reports
    r1 = 500
    b.append(node(75, r1, 180, 80, "Reports dashboard", ["browser"]))
    cap = "6 · request / reply (REST)"
    b.append(arrow_with_label(255, r1 + 40, 255 + gap_for(cap), cap))
    api_x = 255 + gap_for(cap)
    b.append(node(api_x, r1, 190, 80, "Reports API", ["reporting-service"]))
    # reads the read model: along the corridor between the two bands (380..430), up into the
    # bottom of the daily stats cylinder; the label sits under the line, clear of both band edges
    stats_bottom = (y - 4) + 88
    corridor = 395
    b.append(path([(api_x + 95, r1), (api_x + 95, corridor), (stats_x + 85, corridor), (stats_x + 85, stats_bottom)]))
    b.append(label((api_x + 95 + stats_x + 85) / 2, corridor + 18, "reads the daily stats", anchor="middle",
                   size=LABEL_SIZE))

    # ---- synchronous lane, row 2: assistant, gateway and the model
    r2 = 650
    b.append(node(75, r2, 180, 80, "Chat widget", ["browser"]))
    cap = "7 · request + SSE stream"
    b.append(arrow_with_label(255, r2 + 40, 255 + gap_for(cap), cap))
    agent_x = 255 + gap_for(cap)
    b.append(node(agent_x, r2, 190, 80, "Assistant agent", ["ai-assistant-service"]))
    cap = "9 · token relay"
    b.append(arrow_with_label(agent_x + 190, r2 + 40, agent_x + 190 + gap_for(cap), cap))
    gw_x = agent_x + 190 + gap_for(cap)
    b.append(node(gw_x, r2, 180, 80, "API gateway", ["SwiftDrop APIs"]))

    llm_cx = gw_x + 420
    b.append(cloud(llm_cx, r2 + 40, 190, 96, "LLM provider", ["function calling"]))
    # agent to model: below the row so it crosses nothing. Personal data is swapped for placeholders
    # before the request leaves the assistant (11), so the label carries both patterns.
    llm_bx, llm_by = cloud_anchor(llm_cx, r2 + 40, 190, 96, "bottom")
    b.append(path([(agent_x + 95, r2 + 80), (agent_x + 95, 808), (llm_bx, 808), (llm_bx, llm_by)]))
    b.append(label((agent_x + 95 + llm_cx) / 2, 784,
                   "8 · request / reply with function calling|11 · pseudonymization (PII redaction)",
                   anchor="middle", size=LABEL_SIZE))

    # MCP server above the gateway, external client to its right
    b.append(node(gw_x, 480, 180, 70, "MCP server", ["/mcp endpoint"], dashed=True))
    b.append(path([(gw_x + 90, 550), (gw_x + 90, r2)]))
    b.append(label(gw_x + 100, 600, "same tools", size=LABEL_SIZE))
    cap = "10 · MCP JSON-RPC"
    client_x = gw_x + 180 + gap_for(cap)
    b.append(node(client_x, 480, 170, 70, "MCP client", ["external"], dashed=True))
    b.append(arrow_with_label(client_x, 515, gw_x + 180, cap))
    return svg(W, H, "".join(b), "Integration design: the patterns M10 relies on")
