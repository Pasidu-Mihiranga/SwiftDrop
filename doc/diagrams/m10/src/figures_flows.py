"""Figures 5-12: sequence diagrams, the agent activity diagram and two class diagrams."""

from draw import shape_rect
from draw import (BAND, BAND_EDGE, BLACK, FILL, GREY, NL, Sequence, band, diamond, label, node, path,
                  rounded, svg, terminator, text, text_block)


def fig_seq_projection() -> str:
    s = Sequence([("Courier", 90), ("parcel-service", 330), ("Kafka", 580), ("reporting-service", 830),
                  ("reporting_db", 1080)], width=1200)
    s.heads(actor_names={"Courier"})
    s.msg("Courier", "parcel-service", "PATCH /courier/parcels/{id}/status  (DELIVERED)")
    s.msg("parcel-service", "parcel-service", "one transaction: parcel + history + outbox row")
    s.msg("parcel-service", "Courier", "200 OK", dashed=True)
    s.msg("parcel-service", "Kafka", "outbox relay publishes ParcelStatusChanged")
    s.note("Kafka", "Kafka", "other consumer groups receive the same event independently")
    s.msg("Kafka", "reporting-service", "deliver to group reporting-service")
    s.msg("reporting-service", "reporting_db", "INSERT processed_event  (ON CONFLICT DO NOTHING)")
    s.msg("reporting_db", "reporting-service", "first delivery of this event id", dashed=True)
    s.msg("reporting-service", "reporting_db", "lock the parcel, insert status_transition")
    s.msg("reporting-service", "reporting_db", "per day the parcel touches: lock the day, rebuild its rows")
    s.note("reporting-service", "reporting_db", "rebuilt from facts: late or out-of-order events still count right")
    s.note("reporting-service", "reporting_db", "a duplicate event rolls back: nothing is counted twice")
    s.msg("reporting-service", "Kafka", "commit consumer offset")
    return s.render("Sequence: a courier status update becomes report data")


def fig_seq_reports() -> str:
    s = Sequence([("Admin", 90), ("Next.js BFF", 330), ("API gateway", 580), ("reporting-service", 830),
                  ("reporting_db", 1080)], width=1200)
    s.heads(actor_names={"Admin"})
    s.msg("Admin", "Next.js BFF", "open /admin/reports  (session cookie)")
    s.msg("Next.js BFF", "Next.js BFF", "read access token from the session, server-side only")
    s.msg("Next.js BFF", "API gateway", "GET /api/v1/reports/daily?from&to  (bearer JWT)")
    s.msg("API gateway", "API gateway", "verify signature, issuer, audience, expiry")
    s.msg("API gateway", "reporting-service", "forward with X-Correlation-Id")
    s.msg("reporting-service", "reporting-service", "check role DISPATCHER or ADMIN")
    s.msg("reporting-service", "reporting_db", "select day, created, delivered, failed")
    s.msg("reporting_db", "reporting-service", "rows", dashed=True)
    s.msg("reporting-service", "Next.js BFF", "200 JSON series", dashed=True)
    s.msg("Next.js BFF", "Admin", "charts: per day, success rate, per zone and courier", dashed=True)
    s.note("API gateway", "reporting-service", "no token: 401   ·   customer token: 403 problem+json")
    return s.render("Sequence: an admin opens the reports dashboard")


def fig_seq_where() -> str:
    AI = "ai-assistant" + NL + "service"
    s = Sequence([("Customer", 80), ("Chat widget" + NL + "and BFF", 340), ("API gateway", 600),
                  ("SwiftDrop" + NL + "APIs", 860), ("ai-assistant" + NL + "service", 1120),
                  ("LLM provider", 1380)], width=1480)
    s.heads(actor_names={"Customer"})
    s.msg("Customer", "Chat widget" + NL + "and BFF", "\"Where is my parcel?\"")
    s.msg("Chat widget" + NL + "and BFF", "API gateway", "POST /assistant/chat  (customer JWT)")
    s.msg("API gateway", AI, "forward")
    s.msg(AI, AI, "verify JWT, load history and PII vault, apply guardrails")
    s.msg(AI, AI, "pseudonymize the message")
    s.msg(AI, "LLM provider", "system rules + history + all five tools")
    s.msg("LLM provider", AI, "tool call: list_my_parcels()", dashed=True)
    s.msg(AI, "API gateway", "GET /parcels/mine  (same JWT)")
    s.msg("API gateway", "SwiftDrop" + NL + "APIs", "forward")
    s.msg("SwiftDrop" + NL + "APIs", AI, "only this customer's parcels", dashed=True)
    s.msg(AI, AI, "pseudonymize: names, phones, addresses become [NAME_1], [PHONE_1]")
    s.msg(AI, "LLM provider", "tool result, placeholders only")
    s.msg("LLM provider", AI, "tool call: track_parcel(SD261020K7Q2M9X)", dashed=True)
    s.msg(AI, "API gateway", "GET /tracking/public/SD261020K7Q2M9X")
    s.msg("API gateway", "SwiftDrop" + NL + "APIs", "forward")
    s.msg("SwiftDrop" + NL + "APIs", AI, "OUT_FOR_DELIVERY, Kandy, 14:02", dashed=True)
    s.msg(AI, "LLM provider", "tool result")
    s.msg("LLM provider", AI, "final answer with placeholders, streamed", dashed=True)
    s.msg(AI, AI, "restore placeholders for this customer only")
    s.msg(AI, "Customer", "SSE: tool chips, tokens, done", dashed=True)
    s.msg(AI, AI, "save pseudonymized turn and encrypted vault; logs masked")
    s.note(AI, "LLM provider", "the LLM never receives raw personal data")
    return s.render("Sequence: Where is my parcel, for a signed-in customer")


def fig_seq_fee() -> str:
    s = Sequence([("Guest visitor", 110), ("ai-assistant" + NL + "service", 430),
                  ("LLM provider", 740), ("pricing-service" + NL + "via gateway", 1050)], width=1180)
    s.heads(actor_names={"Guest visitor"})
    s.msg("Guest visitor", "ai-assistant" + NL + "service", "\"How much to send 2 kg to Kandy?\"  (no token)")
    s.msg("ai-assistant" + NL + "service", "ai-assistant" + NL + "service", "anonymous caller: expose public tools only")
    s.msg("ai-assistant" + NL + "service", "ai-assistant" + NL + "service", "pseudonymize the message")
    s.msg("ai-assistant" + NL + "service", "LLM provider", "rules + public tools")
    s.msg("LLM provider", "ai-assistant" + NL + "service", "ask for the pickup city", dashed=True)
    s.msg("ai-assistant" + NL + "service", "Guest visitor", "\"Where will it be picked up?\"", dashed=True)
    s.msg("Guest visitor", "ai-assistant" + NL + "service", "\"Colombo\"")
    s.msg("ai-assistant" + NL + "service", "LLM provider", "updated conversation")
    s.msg("LLM provider", "ai-assistant" + NL + "service", "tool call: estimate_delivery_fee(...)", dashed=True)
    s.msg("ai-assistant" + NL + "service", "pricing-service" + NL + "via gateway", "POST /pricing/quotes  (public endpoint)")
    s.msg("pricing-service" + NL + "via gateway", "ai-assistant" + NL + "service", "LKR 650.00, breakdown, expiresAt", dashed=True)
    s.msg("ai-assistant" + NL + "service", "LLM provider", "tool result")
    s.msg("LLM provider", "ai-assistant" + NL + "service", "answer grounded in the quote", dashed=True)
    s.msg("ai-assistant" + NL + "service", "Guest visitor", "\"2 kg Colombo to Kandy costs LKR 650 (standard).\"", dashed=True)
    return s.render("Sequence: a guest asks for a delivery fee")


def fig_seq_mcp() -> str:
    s = Sequence([("MCP client", 120), ("Keycloak", 400), ("API gateway", 670),
                  ("SwiftDrop" + NL + "APIs", 930), ("MCP server" + NL + "/mcp", 1170)], width=1480)
    s.heads()
    s.msg("MCP client", "Keycloak", "authorization code + PKCE  (client swiftdrop-mcp)")
    s.msg("Keycloak", "MCP client", "access token, audience swiftdrop-api", dashed=True)
    s.msg("MCP client", "API gateway", "POST /mcp  initialize  (bearer token)")
    s.msg("API gateway", "MCP server" + NL + "/mcp", "forward")
    s.msg("MCP server" + NL + "/mcp", "MCP client", "server info and capabilities", dashed=True)
    s.msg("MCP client", "API gateway", "tools/list")
    s.msg("API gateway", "MCP server" + NL + "/mcp", "forward")
    s.msg("MCP server" + NL + "/mcp", "MCP client", "five SwiftDrop tools", dashed=True)
    s.msg("MCP client", "API gateway", "tools/call  track_parcel")
    s.msg("API gateway", "MCP server" + NL + "/mcp", "forward")
    s.msg("MCP server" + NL + "/mcp", "API gateway", "GET /tracking/public/{number}")
    s.msg("API gateway", "SwiftDrop" + NL + "APIs", "forward")
    s.msg("SwiftDrop" + NL + "APIs", "MCP server" + NL + "/mcp", "timeline", dashed=True)
    s.msg("MCP server" + NL + "/mcp", "MCP server" + NL + "/mcp", "mask personal data, irreversibly")
    s.msg("MCP server" + NL + "/mcp", "MCP client", "tool result: text and structured content", dashed=True)
    s.note("API gateway", "MCP server" + NL + "/mcp", "the same tool code serves the built-in chat widget")
    s.note("SwiftDrop" + NL + "APIs", "MCP server" + NL + "/mcp", "MCP_MASK_PII=false only for trusted clients")
    return s.render("Sequence: an external MCP client uses SwiftDrop tools")


def _uml_class(x, y, w, name, stereotype, members, row_h=22):
    head_h = 36 if stereotype else 30
    h = head_h + len(members) * row_h + 8
    shape_rect(x, y, w, h, name)
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>']
    if stereotype:
        out.append(text(x + w / 2, y + 13, stereotype, 10.5, GREY))
        out.append(text(x + w / 2, y + 27, name, 13, BLACK, 700))
    else:
        out.append(text(x + w / 2, y + head_h / 2, name, 13, BLACK, 700))
    out.append(f'<line x1="{x}" y1="{y + head_h}" x2="{x + w}" y2="{y + head_h}" stroke="{BLACK}" stroke-width="1.2"/>')
    for i, m in enumerate(members):
        out.append(text(x + 10, y + head_h + 14 + i * row_h, m, 11, BLACK, anchor="start"))
    return "".join(out), h


# UML relationship styles used by both class diagrams
def _uses(points):
    """Dependency: dashed line, open arrowhead."""
    return path(points, dashed=True, end="open")


def _has(points):
    """Directed association (the source keeps a field of the target type): solid line, open arrowhead."""
    return path(points, end="open")


def _realizes(points):
    """Realization (class implements interface): dashed line, hollow triangle on the interface."""
    return path(points, dashed=True, end="hollow")


def fig_class_assistant() -> str:
    """Columns: left (60-360), middle (430-730), right (840-1120), privacy (1190-1460).
    Lanes: x=770 and x=805 between middle and right, x=1155 between right and privacy, y=30 above all."""
    W, H = 1520, 680
    b = []
    store, _ = _uml_class(60, 60, 300, "ConversationStore", "«repository»",
                          ["+ load(conversation_id, owner)", "+ append(conversation_id, msg)",
                           "+ save_vault(conversation_id, vault)"])
    factory, _ = _uml_class(60, 400, 290, "ProviderFactory", "", ["+ provider_config(settings)"])
    agent, agent_h = _uml_class(430, 60, 300, "AgentOrchestrator", "«facade»",
                                ["+ chat(message, user): Stream", "- provider: LLMProvider", "- tools: ToolRegistry"])
    iface, iface_h = _uml_class(430, 250, 300, "LLMProvider", "«interface»", ["+ stream_chat(messages, tools)"])
    impl, _ = _uml_class(430, 400, 300, "OpenAICompatibleProvider", "«strategy»",
                         ["- base_url, model, api_key", "+ stream_chat(messages, tools)"])
    mcp, _ = _uml_class(430, 560, 300, "McpServer", "", ["+ tools_list()", "+ tools_call(name, args)"])
    guard, _ = _uml_class(840, 60, 280, "Guardrails", "", ["+ check_input(message)", "+ check_scope(answer): bool"])
    registry, registry_h = _uml_class(840, 250, 280, "ToolRegistry", "",
                                      ["+ public_tools()", "+ tools_for(user)", "+ call(name, args, user)"])
    api, _ = _uml_class(840, 430, 280, "SwiftDropApi", "«adapter»",
                        ["+ track(number)", "+ quote(...)", "+ my_parcels(status)"])
    redactor, redactor_h = _uml_class(1190, 60, 270, "PiiRedactor", "«service»",
                                      ["+ pseudonymize(text | json, vault)", "+ restore(text, vault)",
                                       "+ mask(json)"])
    vault, _ = _uml_class(1190, 250, 270, "PseudonymVault", "«value object»",
                          ["+ token_for(value, kind)", "+ value_of(token)", "+ encrypted(): bytes"])
    b += [store, factory, agent, iface, impl, mcp, guard, registry, api, redactor, vault]

    b.append(_uses([(430, 110), (360, 110)]))                                    # loads / saves turns
    b.append(_uses([(730, 100), (840, 100)]))                                    # checks input and scope
    b.append(_has([(580, 60 + agent_h), (580, 250)]))                            # field: provider
    b.append(label(590, (60 + agent_h + 250) / 2, "depends on the interface", size=11))
    b.append(_realizes([(580, 400), (580, 250 + iface_h)]))
    b.append(label(590, (250 + iface_h + 400) / 2, "implements", size=11))
    b.append(_uses([(350, 430), (430, 430)]))
    b.append(label(390, 414, "«create»", anchor="middle", size=11))
    b.append(_has([(730, 150), (770, 150), (770, 280), (840, 280)]))             # field: tools
    b.append(_uses([(980, 250 + registry_h), (980, 430)]))                       # tools call the API adapter
    b.append(_uses([(730, 600), (805, 600), (805, 320), (840, 320)]))            # MCP serves the same registry
    b.append(label(817, 570, "same tools as the chat agent", size=11))
    # privacy: the agent pseudonymizes before the LLM and restores after it, over the top lane;
    # the MCP server masks tool results irreversibly, up the lane between the right and privacy columns
    b.append(_uses([(660, 60), (660, 30), (1325, 30), (1325, 60)]))
    b.append(label(992, 44, "pseudonymize before the LLM, restore after", anchor="middle", size=11))
    b.append(_uses([(730, 630), (1155, 630), (1155, 120), (1190, 120)]))
    b.append(label(1145, 600, "masks MCP results", anchor="end", size=11))
    b.append(_uses([(1325, 60 + redactor_h), (1325, 250)]))                      # one vault per conversation
    b.append(label(1335, (60 + redactor_h + 250) / 2, "per conversation", size=11))
    return svg(W, H, "".join(b), "Design patterns in the AI assistant")


def fig_class_reporting() -> str:
    """Columns: left (60-370), middle (470-780), right (870-1120); one lane in each gutter (420, 825)."""
    W, H = 1180, 680
    b = []
    listeners, listeners_h = _uml_class(60, 60, 310, "ParcelEventListeners", "«infrastructure»",
                                        ["+ onParcelCreated(value)", "+ onStatusChanged(value)",
                                         "+ onParcelAssigned(value)"])
    reader, _ = _uml_class(60, 250, 310, "EventReader", "«infrastructure»", ["+ read(json, type, version)"])
    idem, _ = _uml_class(60, 420, 310, "IdempotentConsumer", "«shared library»",
                         ["+ firstDelivery(eventId, consumer)"])
    projection, proj_h = _uml_class(470, 60, 310, "ProjectionService", "«application»",
                                    ["+ onParcelCreated(event)", "+ onStatusChanged(event)",
                                     "+ onParcelAssigned(event)"])
    calendar, _ = _uml_class(470, 250, 310, "BusinessCalendar", "«domain»", ["+ dateOf(instant): LocalDate"])
    pstore, pstore_h = _uml_class(470, 400, 310, "ProjectionStore", "«port»",
                                  ["+ lockParcel(parcelId)", "+ insertTransition(fact)", "+ recomputeDay(day)"])
    jdbc_p, _ = _uml_class(470, 570, 310, "JdbcProjectionStore", "«adapter»", ["upserts, advisory locks"])
    controller, controller_h = _uml_class(870, 60, 250, "ReportController", "«api.v1»",
                                          ["+ daily · successRate", "+ zones · couriers"])
    query, query_h = _uml_class(870, 230, 250, "ReportQueryService", "«application»",
                                ["+ daily(period, zone)", "+ zones · couriers(period)"])
    rstore, rstore_h = _uml_class(870, 400, 250, "ReportStore", "«port»",
                                  ["+ dailyTotals(period, zone)", "+ zoneTotals(period)", "+ courierTotals(period)"])
    jdbc_r, _ = _uml_class(870, 570, 250, "JdbcReportStore", "«adapter»", ["set-based SQL"])
    b += [listeners, reader, idem, projection, calendar, pstore, jdbc_p, controller, query, rstore, jdbc_r]

    b.append(_uses([(215, 60 + listeners_h), (215, 250)]))
    b.append(label(225, (60 + listeners_h + 250) / 2, "parses and checks the version", size=11))
    b.append(_uses([(370, 100), (470, 100)]))
    b.append(label(420, 86, "calls", anchor="middle", size=11))
    # the projection drops duplicates first, inside the same transaction
    b.append(_uses([(470, 150), (420, 150), (420, 450), (370, 450)]))
    b.append(label(412, 360, "first delivery?", anchor="end", size=11))
    b.append(_uses([(625, 60 + proj_h), (625, 250)]))
    b.append(label(635, (60 + proj_h + 250) / 2, "business day", size=11))
    # facts and day rebuilds go through the port, down the gutter right of the middle column
    b.append(_uses([(780, 140), (825, 140), (825, 450), (780, 450)]))
    b.append(label(817, 360, "writes facts,|rebuilds days", anchor="end", size=11))
    b.append(_realizes([(625, 570), (625, 400 + pstore_h)]))
    b.append(label(635, (400 + pstore_h + 570) / 2, "implements", size=11))
    b.append(_uses([(995, 60 + controller_h), (995, 230)]))
    b.append(_uses([(995, 230 + query_h), (995, 400)]))
    b.append(_realizes([(995, 570), (995, 400 + rstore_h)]))
    b.append(label(1005, (400 + rstore_h + 570) / 2, "implements", size=11))
    return svg(W, H, "".join(b), "Design patterns in reporting-service")
