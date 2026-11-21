"""Figure 2: data model. Two store groups side by side, relationships routed in free gutters."""

from draw import BLACK, FILL, GREY, band, label, path, shape_rect, svg, text

W, H = 1400, 820
ROW = 26


def table(x, y, w, title, rows):
    h = 32 + len(rows) * ROW
    shape_rect(x, y, w, h, title)
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>',
           f'<rect x="{x}" y="{y}" width="{w}" height="32" fill="{BLACK}"/>',
           text(x + w / 2, y + 16, title, 12.5, "#ffffff", 700)]
    for i, (name, kind) in enumerate(rows):
        ry = y + 32 + i * ROW
        if i:
            out.append(f'<line x1="{x}" y1="{ry}" x2="{x + w}" y2="{ry}" stroke="#d8d8d8" stroke-width="1"/>')
        out.append(text(x + 12, ry + ROW / 2, name, 11.5, BLACK, anchor="start"))
        if kind:
            out.append(text(x + w - 12, ry + ROW / 2, kind, 10.5, GREY, anchor="end"))
    return "".join(out), h


def build() -> str:
    b = []
    b.append(band(40, 50, 760, 720))
    b.append(text(58, 70, "REPORTING_DB  (POSTGRESQL)", 11, GREY, 500, anchor="start", spacing="1.6"))
    b.append(band(840, 50, 530, 420))
    b.append(text(858, 70, "REDIS  (ASSISTANT MEMORY, TTL 24 H)", 11, GREY, 500, anchor="start", spacing="1.6"))
    b.append(band(840, 510, 530, 260))
    b.append(text(858, 530, "OWNED BY OTHER SERVICES", 11, GREY, 500, anchor="start", spacing="1.6"))

    # facts: one row per parcel, one row per status change; both are written from Kafka events
    fact, fact_h = table(70, 110, 290, "PARCEL_FACT", [
        ("parcel_id", "PK"), ("tracking_number", ""), ("zone_code", "dropoff, ref"), ("source", ""),
        ("merchant_id", "ref"), ("created_at", ""), ("created_date", "business day"), ("courier_id", "ref"),
        ("courier_assigned_at", "")])
    b.append(fact)
    trans, trans_h = table(480, 110, 290, "STATUS_TRANSITION", [
        ("event_id", "PK"), ("parcel_id", "ref"), ("from_status", ""), ("to_status", ""),
        ("courier_id", "ref"), ("failure_reason", ""), ("changed_at", ""), ("business_date", "")])
    b.append(trans)
    # rollups: rebuilt for every business day an event touches
    daily, daily_h = table(70, 470, 290, "DAILY_DELIVERY_STATS", [
        ("business_date", "PK"), ("zone_code", "PK"), ("created", ""), ("delivered", ""), ("failed", ""),
        ("cancelled", "")])
    b.append(daily)
    courier, courier_h = table(480, 470, 290, "COURIER_DAILY_STATS", [
        ("business_date", "PK"), ("courier_id", "PK"), ("delivered", ""), ("failed", "")])
    b.append(courier)
    processed, _ = table(480, 650, 290, "PROCESSED_EVENT", [
        ("event_id", "PK"), ("consumer", "PK"), ("processed_at", "")])
    b.append(processed)

    session, session_h = table(870, 110, 470, "CHAT_SESSION", [
        ("conversation_id", "PK"), ("owner", "user id, or guest key"), ("provider", ""), ("started_at", "")])
    b.append(session)
    message, _ = table(870, 300, 260, "CHAT_MESSAGE", [
        ("conversation_id", "FK"), ("seq", "PK"), ("role", ""), ("content", "pseudonymized")])
    b.append(message)
    vault, _ = table(1160, 300, 180, "PII_VAULT", [
        ("conversation_id", "PK, FK"), ("token → value", "encrypted")])
    b.append(vault)
    others, _ = table(870, 570, 470, "PARCEL · ZONE · USER_PROFILE", [
        ("referenced by id only", ""), ("no foreign keys", "")])
    b.append(others)

    # events from parcel-service and dispatch-service become the two fact tables: one route along the
    # top of the reporting band, with a branch down into each table
    b.append(path([(870, 625), (820, 625), (820, 90), (215, 90), (215, 110)], dashed=True, end="open"))
    b.append(path([(625, 90), (625, 110)], dashed=True, end="open"))
    b.append(label(830, 490, "projected|from events", anchor="start", size=11))
    # a transition is attributed through its parcel's zone and courier (joined on parcel_id, no foreign key)
    b.append(path([(480, 170), (360, 170)], dashed=True, head=False))
    b.append(label(420, 160, "parcel_id", anchor="middle", size=10.5))
    # rollups are derived rows, recomputed per day; dashed arrows, each in its own gutter
    b.append(path([(150, 110 + fact_h), (150, 470)], dashed=True, end="open"))
    b.append(label(160, 450, "created per day", size=11))
    b.append(path([(625, 110 + trans_h), (625, 470)], dashed=True, end="open"))
    b.append(label(635, 450, "attempts per day", size=11))
    b.append(path([(480, 330), (420, 330), (420, 520), (360, 520)], dashed=True, end="open"))
    b.append(label(428, 425, "outcomes", size=11))
    # real relationships inside Redis: one session holds many messages and exactly one vault
    b.append(path([(1000, 110 + session_h), (1000, 300)], start="one", end="many"))
    b.append(label(1010, 273, "contains", size=11))
    b.append(path([(1250, 110 + session_h), (1250, 300)], start="one", end="one"))
    b.append(label(1260, 273, "protected by", size=11))
    return svg(W, H, "".join(b), "Data model: reporting projections and assistant memory")
