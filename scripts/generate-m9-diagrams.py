"""
SwiftDrop M9 Diagram Generator — Complete Suite (Figures 1 to 12)
Outputs clean, professional SVG diagrams matching the team design documentation style.
"""
import os
import re
import fitz

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "doc", "diagrams", "m9")
os.makedirs(OUT_DIR, exist_ok=True)

FONT_FAMILY = "Segoe UI, Helvetica Neue, Helvetica, Arial, sans-serif"

DEFS = """<defs>
<marker id="a" viewBox="0 0 10 10" refX="9.2" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 z" fill="#111111"/></marker>
<marker id="o" viewBox="0 0 10 10" refX="9.2" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0.5 0.5 L9.5 5 L0.5 9.5 z" fill="#ffffff" stroke="#111111" stroke-width="1.2"/></marker>
<marker id="v" viewBox="0 0 10 10" refX="9.4" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M1 1 L9.4 5 L1 9" fill="none" stroke="#111111" stroke-width="1.2"/></marker>
<marker id="one" viewBox="0 0 16 16" refX="16" refY="8" markerWidth="16" markerHeight="16" markerUnits="userSpaceOnUse" orient="auto-start-reverse"><path d="M8 2 V14 M12 2 V14" fill="none" stroke="#111111" stroke-width="1.3"/></marker>
<marker id="many" viewBox="0 0 16 16" refX="16" refY="8" markerWidth="16" markerHeight="16" markerUnits="userSpaceOnUse" orient="auto-start-reverse"><path d="M8 8 L16 1 M8 8 L16 8 M8 8 L16 15" fill="none" stroke="#111111" stroke-width="1.3"/><circle cx="4" cy="8" r="3" fill="#ffffff" stroke="#111111" stroke-width="1.3"/></marker>
<marker id="zeroone" viewBox="0 0 16 16" refX="16" refY="8" markerWidth="16" markerHeight="16" markerUnits="userSpaceOnUse" orient="auto-start-reverse"><path d="M12 2 V14" fill="none" stroke="#111111" stroke-width="1.3"/><circle cx="5" cy="8" r="3" fill="#ffffff" stroke="#111111" stroke-width="1.3"/></marker>
</defs>"""

def svg_wrap(content, w=1100, h=560):
    # Ensure all & are valid XML entities
    cleaned = re.sub(r'&(?!amp;|lt;|gt;|quot;|apos;)', '&amp;', content)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">
{DEFS}
<rect width="{w}" height="{h}" fill="#ffffff"/>
{cleaned}
</svg>'''

def stick_actor(cx, cy, label, sub=""):
    sub_text = f'<text x="{cx}" y="{cy + 76}" font-size="10" fill="#888888" text-anchor="middle" font-family="{FONT_FAMILY}">{sub}</text>' if sub else ""
    return f'''
    <g class="actor">
      <circle cx="{cx}" cy="{cy + 14}" r="11" fill="#ffffff" stroke="#111111" stroke-width="1.4"/>
      <path d="M{cx} {cy + 25} V{cy + 49} M{cx - 16} {cy + 33} H{cx + 16} M{cx} {cy + 49} l-12 18 M{cx} {cy + 49} l12 18" fill="none" stroke="#111111" stroke-width="1.4" stroke-linecap="round"/>
      <text x="{cx}" y="{cy + 82}" font-size="12" font-weight="500" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">{label}</text>
      {sub_text}
    </g>'''

def pill_usecase(cx, cy, w, h, label):
    rx = h // 2
    return f'''
    <g class="usecase">
      <rect x="{cx - w//2}" y="{cy - h//2}" width="{w}" height="{h}" rx="{rx}" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>
      <text x="{cx}" y="{cy + 4}" font-size="12" font-weight="500" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">{label}</text>
    </g>'''

def line_arrow(x1, y1, x2, y2, dashed=False, marker="url(#a)", text="", tx=None, ty=None):
    d_attr = ' stroke-dasharray="5 4"' if dashed else ''
    m_attr = f' marker-end="{marker}"' if marker else ''
    t_elem = ""
    if text:
        mx = (x1 + x2) / 2 if tx is None else tx
        my = (y1 + y2) / 2 if ty is None else ty
        t_elem = f'''<rect x="{mx - len(text)*3.2 - 6}" y="{my - 9}" width="{len(text)*6.4 + 12}" height="18" fill="#ffffff"/>
        <text x="{mx}" y="{my + 4}" font-size="10.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">{text}</text>'''
    return f'<path d="M{x1} {y1} L{x2} {y2}" fill="none" stroke="#111111" stroke-width="1.2"{d_attr}{m_attr}/>{t_elem}'

def table_box(x, y, w, title, rows):
    h = 32 + len(rows) * 22
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>']
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="28" fill="#111111"/>')
    out.append(f'<text x="{x + w/2}" y="{y + 18}" font-size="11.5" font-weight="700" fill="#ffffff" text-anchor="middle" font-family="{FONT_FAMILY}">{title}</text>')
    cy = y + 46
    for left_col, right_col in rows:
        out.append(f'<text x="{x + 12}" y="{cy}" font-size="11" fill="#111111" font-family="{FONT_FAMILY}">{left_col}</text>')
        if right_col:
            out.append(f'<text x="{x + w - 12}" y="{cy}" font-size="10" fill="#777777" text-anchor="end" font-family="{FONT_FAMILY}">{right_col}</text>')
        cy += 22
    return "\n".join(out)

def sequence_base(lifelines, w=1100, h=560, top_y=50, bottom_y=510):
    out = []
    for x, label, sub in lifelines:
        out.append(f'<rect x="{x - 65}" y="{top_y}" width="130" height="34" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
        out.append(f'<text x="{x}" y="{top_y + 21}" font-size="11.5" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">{label}</text>')
        if sub:
            out.append(f'<text x="{x}" y="{top_y + 44}" font-size="9.5" fill="#777777" text-anchor="middle" font-family="{FONT_FAMILY}">{sub}</text>')
        out.append(f'<line x1="{x}" y1="{top_y + 34}" x2="{x}" y2="{bottom_y}" stroke="#111111" stroke-width="1" stroke-dasharray="4 4"/>')
    return "\n".join(out)

# -------------------------------------------------------------
# FIGURE 1: USE CASES
# -------------------------------------------------------------
def gen_fig1():
    w, h = 1100, 560
    c = []
    c.append(f'<rect x="270" y="30" width="560" height="500" rx="4" fill="#fafafa" stroke="#d4d4d4" stroke-width="1.2"/>')
    c.append(f'<text x="550" y="55" font-size="11.5" font-weight="500" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}" letter-spacing="1.2">SwiftDrop · system boundary of Sapuni Madhushika\'s part</text>')

    c.append(stick_actor(110, 80, "Customer", "(signed in)"))
    c.append(stick_actor(110, 240, "Admin", "(dispatcher/staff)"))
    c.append(stick_actor(110, 400, "Courier", "(delivering)"))

    c.append(stick_actor(970, 140, "Mailpit SMTP", "(email host :1025)"))
    c.append(stick_actor(970, 360, "Message Broker", "(Apache Kafka)"))

    c.append(pill_usecase(430, 110, 240, 38, "View delivery history"))
    c.append(pill_usecase(670, 110, 200, 38, "Filter by status and date"))
    c.append(pill_usecase(430, 180, 240, 38, "Download delivery receipt"))
    c.append(pill_usecase(430, 250, 240, 38, "View notification centre & bell"))
    c.append(pill_usecase(430, 320, 240, 38, "Configure channel preferences"))
    c.append(pill_usecase(430, 390, 240, 38, "Review demo SMS outbox"))
    c.append(pill_usecase(430, 460, 240, 38, "Consume status events & dispatch"))

    c.append(line_arrow(150, 110, 310, 110))
    c.append(line_arrow(150, 120, 310, 180))
    c.append(line_arrow(150, 130, 310, 250))
    c.append(line_arrow(150, 140, 310, 320))

    c.append(line_arrow(550, 110, 570, 110, dashed=True, text="«include»"))
    c.append(line_arrow(430, 148, 430, 161, dashed=True, text="«extend»"))

    c.append(line_arrow(150, 260, 310, 180))
    c.append(line_arrow(150, 280, 310, 390))

    c.append(line_arrow(150, 440, 310, 460, dashed=True, text="triggers via status update"))

    c.append(line_arrow(550, 460, 930, 420, dashed=True, text="consumes status events"))
    c.append(line_arrow(550, 450, 930, 180, text="delivers email"))
    c.append(line_arrow(550, 380, 930, 200, text="stores SMS record"))

    return svg_wrap("\n".join(c), w, h)

# -------------------------------------------------------------
# FIGURE 2: DATA MODEL
# -------------------------------------------------------------
def gen_fig2():
    w, h = 1100, 560
    c = []
    c.append('<rect x="40" y="30" width="500" height="500" rx="4" fill="#fafafa" stroke="#d4d4d4" stroke-width="1.2"/>')
    c.append(f'<text x="60" y="52" font-size="11" font-weight="600" fill="#666666" font-family="{FONT_FAMILY}" letter-spacing="1.2">MONGODB (NOTIFICATIONS_DB)</text>')

    c.append(table_box(60, 70, 220, "NOTIFICATION_LOG", [
        ("id", "PK"),
        ("user_id", "ref USER"),
        ("event_id", "ref KAFKA"),
        ("tracking_number", "ref PARCEL"),
        ("title", ""),
        ("body", ""),
        ("channels", "json"),
        ("status", "SENT/FAIL"),
        ("created_at", "")
    ]))

    c.append(table_box(300, 70, 220, "USER_PREFERENCES", [
        ("user_id", "PK, ref USER"),
        ("email", "bool"),
        ("sms", "bool"),
        ("event_types", "array"),
        ("updated_at", "")
    ]))

    c.append(table_box(60, 330, 220, "SMS_OUTBOX", [
        ("id", "PK"),
        ("to", "phone (masked)"),
        ("body", "text"),
        ("created_at", "")
    ]))

    c.append(table_box(300, 240, 220, "PROCESSED_EVENT", [
        ("event_id", "PK"),
        ("consumer", "PK"),
        ("processed_at", "TTL 7d")
    ]))

    c.append('<rect x="560" y="30" width="500" height="500" rx="4" fill="#fafafa" stroke="#d4d4d4" stroke-width="1.2"/>')
    c.append(f'<text x="580" y="52" font-size="11" font-weight="600" fill="#666666" font-family="{FONT_FAMILY}" letter-spacing="1.2">POSTGRESQL (PARCELS_DB — RECEIPT SOURCE)</text>')

    c.append(table_box(580, 70, 225, "PARCEL", [
        ("id", "PK"),
        ("tracking_number", "UQ"),
        ("customer_id", "ref USER"),
        ("pickup_address_enc", "AES-GCM"),
        ("dropoff_address_enc", "AES-GCM"),
        ("weight_kg", "float"),
        ("size_class", "S/M/L/XL"),
        ("fee", "decimal"),
        ("status", "DELIVERED"),
        ("created_at", "")
    ]))

    c.append(table_box(825, 70, 215, "PARCEL_STATUS_HISTORY", [
        ("id", "PK"),
        ("parcel_id", "FK"),
        ("from_status", ""),
        ("to_status", ""),
        ("actor_id", "ref USER"),
        ("note", ""),
        ("occurred_at", "")
    ]))

    c.append(table_box(825, 290, 215, "DELIVERY_PROOF", [
        ("id", "PK"),
        ("parcel_id", "FK"),
        ("type", "NAME/PHOTO"),
        ("recipient_name", ""),
        ("object_key", "RustFS S3"),
        ("created_at", "")
    ]))

    c.append(line_arrow(805, 120, 825, 120, marker="url(#many)"))
    c.append(line_arrow(750, 310, 825, 310, marker="url(#one)"))
    c.append(line_arrow(300, 120, 280, 120, dashed=True, marker="url(#a)", text="configures"))
    c.append(line_arrow(280, 360, 580, 360, dashed=True, marker="url(#a)", text="receipt reads delivered parcel facts"))

    return svg_wrap("\n".join(c), w, h)

# -------------------------------------------------------------
# FIGURE 3: ARCHITECTURE (Four Tiers)
# -------------------------------------------------------------
def gen_fig3():
    w, h = 1100, 560
    c = []
    tiers = [
        (30, 70, "CLIENT TIER"),
        (120, 60, "TRANSPORT"),
        (200, 150, "APPLICATION"),
        (370, 150, "DATA AND INFRASTRUCTURE")
    ]
    for y, th, name in tiers:
        c.append(f'<rect x="30" y="{y}" width="1040" height="{th}" rx="4" fill="#fafafa" stroke="#e0e0e0" stroke-width="1"/>')
        c.append(f'<text x="50" y="{y + 20}" font-size="10" font-weight="700" fill="#888888" font-family="{FONT_FAMILY}" letter-spacing="1.5">{name}</text>')

    c.append(stick_actor(160, 45, "Customer / Guest"))
    c.append(stick_actor(360, 45, "Admin / Staff"))
    c.append(stick_actor(900, 45, "External Systems"))

    c.append('<rect x="100" y="130" width="380" height="40" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="290" y="154" font-size="12" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Next.js BFF (Delivery History & Notification Bell)</text>')

    c.append('<rect x="520" y="130" width="380" height="40" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="710" y="154" font-size="12" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Spring Cloud Gateway (:8080) — JWT validation & routing</text>')

    c.append('<rect x="80" y="230" width="300" height="90" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append('<rect x="80" y="230" width="300" height="26" fill="#111111"/>')
    c.append(f'<text x="230" y="248" font-size="12" font-weight="700" fill="#ffffff" text-anchor="middle" font-family="{FONT_FAMILY}">notification-service (M9)</text>')
    c.append(f'<text x="230" y="275" font-size="11" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">NestJS 11 · 4 Kafka Consumers</text>')
    c.append(f'<text x="230" y="295" font-size="10.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">Channel Strategy: Email & Mock SMS</text>')

    c.append('<rect x="420" y="230" width="280" height="90" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append('<rect x="420" y="230" width="280" height="26" fill="#111111"/>')
    c.append(f'<text x="560" y="248" font-size="12" font-weight="700" fill="#ffffff" text-anchor="middle" font-family="{FONT_FAMILY}">parcel-service (M3/M4/M9)</text>')
    c.append(f'<text x="560" y="275" font-size="11" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Spring Boot 4 · OpenPDF Receipt</text>')
    c.append(f'<text x="560" y="295" font-size="10.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">GET /api/v1/parcels/id/receipt</text>')

    c.append('<rect x="740" y="230" width="280" height="90" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="880" y="270" font-size="12" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Other Microservices Fleet</text>')
    c.append(f'<text x="880" y="290" font-size="11" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">user, merchant, dispatch, tracking</text>')

    c.append('<rect x="80" y="400" width="200" height="80" rx="3" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="180" y="435" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">MongoDB 7</text>')
    c.append(f'<text x="180" y="455" font-size="11" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">notifications db</text>')

    c.append('<rect x="310" y="400" width="240" height="80" rx="3" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="430" y="435" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Apache Kafka (KRaft)</text>')
    c.append(f'<text x="430" y="455" font-size="11" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">parcel.*, dispatch.* topics</text>')

    c.append('<rect x="580" y="400" width="200" height="80" rx="3" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="680" y="435" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">PostgreSQL 16</text>')
    c.append(f'<text x="680" y="455" font-size="11" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">parcels_db</text>')

    c.append('<rect x="810" y="400" width="220" height="80" rx="3" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="920" y="435" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Mailpit SMTP (:1025)</text>')
    c.append(f'<text x="920" y="455" font-size="11" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">Email verification UI (:8026)</text>')

    c.append(line_arrow(230, 320, 180, 400))
    c.append(line_arrow(430, 400, 230, 320, marker="url(#a)", text="consume events"))
    c.append(line_arrow(560, 320, 470, 400, marker="url(#a)", text="outbox publish"))
    c.append(line_arrow(560, 320, 680, 400))
    c.append(line_arrow(290, 170, 560, 230, marker="url(#a)", text="GET /receipt"))
    c.append(line_arrow(230, 320, 850, 400, marker="url(#a)", text="SMTP dispatch"))

    return svg_wrap("\n".join(c), w, h)

# -------------------------------------------------------------
# FIGURE 4: INTEGRATION PATTERNS
# -------------------------------------------------------------
def gen_fig4():
    w, h = 1100, 560
    c = []
    c.append('<rect x="40" y="30" width="1020" height="230" rx="4" fill="#fafafa" stroke="#d4d4d4" stroke-width="1.2"/>')
    c.append(f'<text x="60" y="55" font-size="11" font-weight="700" fill="#666666" font-family="{FONT_FAMILY}" letter-spacing="1.2">ASYNCHRONOUS · CONSUMES STATUS EVENTS & DISPATCHES NOTIFICATIONS</text>')

    c.append('<rect x="40" y="280" width="1020" height="240" rx="4" fill="#fafafa" stroke="#d4d4d4" stroke-width="1.2"/>')
    c.append(f'<text x="60" y="305" font-size="11" font-weight="700" fill="#666666" font-family="{FONT_FAMILY}" letter-spacing="1.2">SYNCHRONOUS · SERVES DELIVERY HISTORY & RECEIPT DOWNLOADS</text>')

    c.append('<rect x="60" y="90" width="150" height="60" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="135" y="118" font-size="12" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">parcel-service</text>')
    c.append(f'<text x="135" y="136" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">status change</text>')

    c.append('<ellipse cx="270" cy="120" rx="45" ry="30" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="270" y="124" font-size="11" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">outbox_event</text>')

    c.append('<rect x="380" y="90" width="160" height="60" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="460" y="118" font-size="12" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Kafka Broker</text>')
    c.append(f'<text x="460" y="136" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">key = parcelId</text>')

    c.append('<rect x="610" y="90" width="170" height="60" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="695" y="118" font-size="12" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">notification-service</text>')
    c.append(f'<text x="695" y="136" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">consumer group</text>')

    c.append('<ellipse cx="860" cy="120" rx="55" ry="30" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="860" y="124" font-size="11" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">processed_event</text>')

    c.append('<rect x="940" y="90" width="100" height="60" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="990" y="118" font-size="11" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Mailpit SMTP</text>')
    c.append(f'<text x="990" y="136" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">email inbox</text>')

    c.append(line_arrow(210, 120, 225, 120, text="1·outbox"))
    c.append(line_arrow(315, 120, 380, 120, text="2·pub/sub"))
    c.append(line_arrow(540, 120, 610, 120, text="3·consume"))
    c.append(line_arrow(780, 120, 805, 120, text="4·dedupe"))
    c.append(line_arrow(780, 140, 940, 140, text="5·SMTP"))

    c.append('<rect x="60" y="360" width="160" height="60" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="140" y="388" font-size="12" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">History Dashboard</text>')
    c.append(f'<text x="140" y="406" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">browser client</text>')

    c.append('<rect x="290" y="360" width="170" height="60" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="375" y="388" font-size="12" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Next.js BFF</text>')
    c.append(f'<text x="375" y="406" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">token server-side</text>')

    c.append('<rect x="540" y="360" width="170" height="60" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="625" y="388" font-size="12" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">API Gateway</text>')
    c.append(f'<text x="625" y="406" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">JWT validation</text>')

    c.append('<rect x="790" y="360" width="180" height="60" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="880" y="388" font-size="12" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">parcel-service</text>')
    c.append(f'<text x="880" y="406" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">OpenPDF engine</text>')

    c.append(line_arrow(220, 390, 290, 390, text="6·request/reply"))
    c.append(line_arrow(460, 390, 540, 390, text="9·token relay"))
    c.append(line_arrow(710, 390, 790, 390, text="8·OpenPDF stream"))

    return svg_wrap("\n".join(c), w, h)

# -------------------------------------------------------------
# FIGURE 5: SEQUENCE: STATUS UPDATE -> NOTIFICATION
# -------------------------------------------------------------
def gen_fig5():
    w, h = 1100, 560
    lifelines = [
        (120, "Courier", ""),
        (300, "parcel-service", ""),
        (480, "Kafka", ""),
        (680, "notification-svc", ""),
        (860, "notifications_db", ""),
        (1000, "Mailpit SMTP", "")
    ]
    c = [sequence_base(lifelines, w, h)]

    c.append(line_arrow(120, 110, 300, 110, text="PATCH /courier/parcels/{id}/status (DELIVERED)"))
    c.append('<rect x="290" y="125" width="160" height="30" rx="3" fill="#fafafa" stroke="#d4d4d4"/>')
    c.append(f'<text x="370" y="144" font-size="9.5" fill="#555" text-anchor="middle" font-family="{FONT_FAMILY}">TX: parcel + outbox_event</text>')
    c.append(line_arrow(300, 175, 120, 175, dashed=True, text="200 OK"))

    c.append(line_arrow(300, 205, 480, 205, text="outbox relay publishes ParcelStatusChanged"))
    c.append(line_arrow(480, 245, 680, 245, text="deliver to group notification-service"))

    c.append(line_arrow(680, 275, 860, 275, text="INSERT processed_event (eventId)"))
    c.append(line_arrow(860, 295, 680, 295, dashed=True, text="first delivery: OK"))

    c.append(line_arrow(680, 335, 860, 335, text="read user_preferences"))
    c.append(line_arrow(860, 355, 680, 355, dashed=True, text="email=true, sms=true"))

    c.append(line_arrow(680, 395, 1000, 395, text="send SMTP message (nodemailer)"))
    c.append(line_arrow(1000, 415, 680, 415, dashed=True, text="250 Message queued"))

    c.append(line_arrow(680, 445, 860, 445, text="insert sms_outbox & notification_log"))
    c.append(line_arrow(680, 475, 480, 475, dashed=True, text="commit consumer offset"))

    return svg_wrap("\n".join(c), w, h)

# -------------------------------------------------------------
# FIGURE 6: SEQUENCE: RECEIPT DOWNLOAD
# -------------------------------------------------------------
def gen_fig6():
    w, h = 1100, 560
    lifelines = [
        (120, "Customer", ""),
        (320, "Next.js BFF", ""),
        (540, "API Gateway", ""),
        (760, "parcel-service", ""),
        (960, "parcels_db", "")
    ]
    c = [sequence_base(lifelines, w, h)]

    c.append(line_arrow(120, 110, 320, 110, text="click Download Receipt (/customer/history)"))
    c.append(line_arrow(320, 150, 540, 150, text="GET /api/v1/parcels/{id}/receipt (Bearer JWT)"))
    c.append(line_arrow(540, 190, 760, 190, text="forward with X-Correlation-Id"))

    c.append('<rect x="750" y="205" width="160" height="30" rx="3" fill="#fafafa" stroke="#d4d4d4"/>')
    c.append(f'<text x="830" y="224" font-size="9.5" fill="#555" text-anchor="middle" font-family="{FONT_FAMILY}">verify caller is owner/admin</text>')

    c.append(line_arrow(760, 255, 960, 255, text="SELECT parcel, history, proof WHERE id=?"))
    c.append(line_arrow(960, 285, 760, 285, dashed=True, text="status=DELIVERED, proof data"))

    c.append('<rect x="750" y="305" width="180" height="40" rx="3" fill="#fafafa" stroke="#d4d4d4"/>')
    c.append(f'<text x="840" y="322" font-size="9.5" font-weight="600" fill="#111" text-anchor="middle" font-family="{FONT_FAMILY}">OpenPDF Document Builder</text>')
    c.append(f'<text x="840" y="337" font-size="8.5" fill="#666" text-anchor="middle" font-family="{FONT_FAMILY}">branding, barcode, fees, timeline</text>')

    c.append(line_arrow(760, 375, 540, 375, dashed=True, text="200 OK application/pdf stream"))
    c.append(line_arrow(540, 405, 320, 405, dashed=True, text="binary stream"))
    c.append(line_arrow(320, 435, 120, 435, dashed=True, text="browser triggers receipt-SD...pdf download"))

    c.append('<rect x="420" y="465" width="220" height="25" rx="3" fill="#ffffff" stroke="#888" stroke-dasharray="3 3"/>')
    c.append(f'<text x="530" y="481" font-size="9.5" fill="#555" text-anchor="middle" font-family="{FONT_FAMILY}">foreign parcel: 404 · pending: 409 Conflict</text>')

    return svg_wrap("\n".join(c), w, h)

# -------------------------------------------------------------
# FIGURE 7: SEQUENCE: PREFERENCES
# -------------------------------------------------------------
def gen_fig7():
    w, h = 1100, 560
    lifelines = [
        (120, "User", ""),
        (340, "Next.js BFF", ""),
        (580, "API Gateway", ""),
        (800, "notification-svc", ""),
        (980, "notifications_db", "")
    ]
    c = [sequence_base(lifelines, w, h)]

    c.append(line_arrow(120, 110, 340, 110, text="open /settings/notifications"))
    c.append(line_arrow(340, 145, 580, 145, text="GET /api/v1/notifications/preferences"))
    c.append(line_arrow(580, 175, 800, 175, text="verify JWT signature & forward"))
    c.append(line_arrow(800, 205, 980, 205, text="findOne({ userId: sub })"))
    c.append(line_arrow(980, 235, 800, 235, dashed=True, text="{ email: true, sms: false }"))
    c.append(line_arrow(800, 265, 120, 265, dashed=True, text="200 OK (render toggle form)"))

    c.append(line_arrow(120, 315, 340, 315, text="toggle SMS on, save changes"))
    c.append(line_arrow(340, 345, 580, 345, text="PUT /api/v1/notifications/preferences { sms: true }"))
    c.append(line_arrow(580, 375, 800, 375, text="forward payload with user token"))
    c.append(line_arrow(800, 405, 980, 405, text="updateOne({ userId }, { $set: prefs }, { upsert: true })"))
    c.append(line_arrow(980, 435, 800, 435, dashed=True, text="acknowledged"))
    c.append(line_arrow(800, 465, 120, 465, dashed=True, text="200 OK (toast: preferences saved)"))

    return svg_wrap("\n".join(c), w, h)

# -------------------------------------------------------------
# FIGURE 8: SEQUENCE: IN-APP BELL
# -------------------------------------------------------------
def gen_fig8():
    w, h = 1100, 560
    lifelines = [
        (100, "Kafka", ""),
        (280, "notification-svc", ""),
        (480, "notifications_db", ""),
        (660, "Redis Pub/Sub", ""),
        (840, "tracking-service", "(Socket.IO)"),
        (1000, "Customer Web", "")
    ]
    c = [sequence_base(lifelines, w, h)]

    c.append(line_arrow(100, 110, 280, 110, text="ParcelStatusChanged event"))
    c.append(line_arrow(280, 145, 480, 145, text="insert notification_log { read: false }"))
    c.append(line_arrow(280, 185, 660, 185, text="PUBLISH notification.new { userId, title }"))
    c.append(line_arrow(660, 215, 840, 215, text="Redis message received"))
    c.append(line_arrow(840, 245, 1000, 245, text="emit('notification:new') to user room"))
    c.append('<rect x="910" y="260" width="160" height="30" rx="3" fill="#fafafa" stroke="#d4d4d4"/>')
    c.append(f'<text x="990" y="279" font-size="9" fill="#555" text-anchor="middle" font-family="{FONT_FAMILY}">bell badge increments (1)</text>')

    c.append(line_arrow(1000, 320, 840, 320, text="click notification bell dropdown"))
    c.append(line_arrow(1000, 355, 280, 355, text="GET /api/v1/notifications/me?unreadOnly=false"))
    c.append(line_arrow(280, 385, 480, 385, text="find({ userId }).sort({ createdAt: -1 })"))
    c.append(line_arrow(480, 415, 280, 415, dashed=True, text="items list + unreadCount"))
    c.append(line_arrow(280, 445, 1000, 445, dashed=True, text="200 OK (render popup items)"))
    c.append(line_arrow(1000, 480, 280, 480, text="POST /notifications/me/{id}/read"))

    return svg_wrap("\n".join(c), w, h)

# -------------------------------------------------------------
# FIGURE 9: SEQUENCE: ADMIN SMS INBOX
# -------------------------------------------------------------
def gen_fig9():
    w, h = 1100, 560
    lifelines = [
        (120, "Admin", ""),
        (340, "Next.js BFF", ""),
        (580, "API Gateway", ""),
        (800, "notification-svc", ""),
        (980, "notifications_db", "")
    ]
    c = [sequence_base(lifelines, w, h)]

    c.append(line_arrow(120, 110, 340, 110, text="open /admin/sms-inbox (demo)"))
    c.append(line_arrow(340, 150, 580, 150, text="GET /api/v1/admin/notifications/sms-inbox"))
    c.append('<rect x="520" y="170" width="160" height="30" rx="3" fill="#fafafa" stroke="#d4d4d4"/>')
    c.append(f'<text x="600" y="189" font-size="9.5" fill="#555" text-anchor="middle" font-family="{FONT_FAMILY}">enforce ADMIN role in JWT</text>')

    c.append(line_arrow(580, 220, 800, 220, text="forward request with correlation ID"))
    c.append(line_arrow(800, 260, 980, 260, text="find().sort({ createdAt: -1 }).limit(50)"))
    c.append(line_arrow(980, 290, 800, 290, dashed=True, text="sms_outbox documents"))

    c.append('<rect x="740" y="310" width="180" height="35" rx="3" fill="#fafafa" stroke="#d4d4d4"/>')
    c.append(f'<text x="830" y="327" font-size="9" font-weight="600" fill="#111" text-anchor="middle" font-family="{FONT_FAMILY}">Masking in Admin View</text>')
    c.append(f'<text x="830" y="340" font-size="8.5" fill="#666" text-anchor="middle" font-family="{FONT_FAMILY}">show full number for demo only</text>')

    c.append(line_arrow(800, 375, 540, 375, dashed=True, text="200 OK { items, total }"))
    c.append(line_arrow(540, 410, 320, 410, dashed=True, text="JSON series"))
    c.append(line_arrow(320, 445, 120, 445, dashed=True, text="renders table with recipient, text, sentAt"))

    c.append('<rect x="360" y="475" width="220" height="25" rx="3" fill="#ffffff" stroke="#888" stroke-dasharray="3 3"/>')
    c.append(f'<text x="470" y="491" font-size="9.5" fill="#555" text-anchor="middle" font-family="{FONT_FAMILY}">non-admin token: 403 problem+json</text>')

    return svg_wrap("\n".join(c), w, h)

# -------------------------------------------------------------
# FIGURE 10: ACTIVITY: DISPATCH LOOP
# -------------------------------------------------------------
def gen_fig10():
    w, h = 1100, 560
    c = []
    c.append('<rect x="50" y="30" width="1000" height="500" rx="4" fill="#fafafa" stroke="#d4d4d4" stroke-width="1.2"/>')
    c.append(f'<text x="550" y="55" font-size="11.5" font-weight="600" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}" letter-spacing="1.2">ACTIVITY: NOTIFICATION INGESTION, IDEMPOTENCY & DISPATCH LOOP</text>')

    c.append('<circle cx="100" cy="140" r="10" fill="#111111"/>')
    c.append(line_arrow(110, 140, 160, 140))

    c.append('<rect x="160" y="115" width="130" height="50" rx="4" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="225" y="138" font-size="11" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Ingest Kafka Event</text>')
    c.append(f'<text x="225" y="152" font-size="9.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">deserialize envelope</text>')

    c.append(line_arrow(290, 140, 350, 140))

    c.append('<polygon points="400,115 450,140 400,165 350,140" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="400" y="143" font-size="9.5" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Duplicate?</text>')

    c.append(line_arrow(400, 165, 400, 240, text="yes"))
    c.append('<rect x="340" y="240" width="120" height="40" rx="4" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="400" y="264" font-size="10.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">Acknowledge & skip</text>')

    c.append(line_arrow(450, 140, 520, 140, text="no"))
    c.append('<rect x="520" y="115" width="150" height="50" rx="4" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="595" y="138" font-size="11" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Load Preferences</text>')
    c.append(f'<text x="595" y="152" font-size="9.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">resolve active channels</text>')

    c.append(line_arrow(670, 140, 740, 140))
    c.append('<rect x="740" y="115" width="150" height="50" rx="4" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="815" y="138" font-size="11" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Compile Handlebars</text>')
    c.append(f'<text x="815" y="152" font-size="9.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">brand layout + tracking link</text>')

    c.append(line_arrow(815, 165, 815, 230))
    c.append('<line x1="680" y1="230" x2="950" y2="230" stroke="#111111" stroke-width="2.5"/>')

    c.append(line_arrow(720, 230, 720, 280))
    c.append('<rect x="650" y="280" width="140" height="50" rx="4" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="720" y="303" font-size="11" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">EmailChannel</text>')
    c.append(f'<text x="720" y="318" font-size="9.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">nodemailer -> Mailpit</text>')

    c.append(line_arrow(910, 230, 910, 280))
    c.append('<rect x="840" y="280" width="140" height="50" rx="4" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="910" y="303" font-size="11" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">MockSmsChannel</text>')
    c.append(f'<text x="910" y="318" font-size="9.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">insert sms_outbox</text>')

    c.append(line_arrow(720, 330, 720, 380))
    c.append(line_arrow(910, 330, 910, 380))
    c.append('<line x1="680" y1="380" x2="950" y2="380" stroke="#111111" stroke-width="2.5"/>')

    c.append(line_arrow(815, 380, 815, 430))
    c.append('<rect x="730" y="430" width="170" height="50" rx="4" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="815" y="453" font-size="11" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Store In-App Log</text>')
    c.append(f'<text x="815" y="468" font-size="9.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">commit offset & notify bell</text>')

    c.append(line_arrow(900, 455, 960, 455))
    c.append('<circle cx="975" cy="455" r="12" fill="none" stroke="#111111" stroke-width="1.3"/>')
    c.append('<circle cx="975" cy="455" r="7" fill="#111111"/>')

    return svg_wrap("\n".join(c), w, h)

# -------------------------------------------------------------
# FIGURE 11: DESIGN PATTERNS IN NOTIFICATION-SERVICE
# -------------------------------------------------------------
def gen_fig11():
    w, h = 1100, 560
    c = []
    c.append('<rect x="80" y="60" width="260" height="90" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="210" y="80" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">«facade»</text>')
    c.append(f'<text x="210" y="98" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">ChannelDispatcherService</text>')
    c.append(f'<text x="100" y="122" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">+ dispatch(event, recipient): Promise</text>')

    c.append('<rect x="440" y="60" width="240" height="90" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="560" y="80" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">«interface»</text>')
    c.append(f'<text x="560" y="98" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">NotificationChannel</text>')
    c.append(f'<text x="460" y="122" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">+ channelType(): ChannelType</text>')
    c.append(f'<text x="460" y="138" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">+ send(recipient, content): Promise</text>')

    c.append(line_arrow(340, 105, 440, 105, text="delegates to"))

    c.append('<rect x="370" y="230" width="220" height="90" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="480" y="250" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">«strategy»</text>')
    c.append(f'<text x="480" y="268" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">EmailChannel</text>')
    c.append(f'<text x="390" y="292" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">- transporter: Transporter</text>')
    c.append(f'<text x="390" y="308" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">+ send(): SMTP Mailpit</text>')

    c.append('<rect x="630" y="230" width="220" height="90" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="740" y="250" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">«strategy»</text>')
    c.append(f'<text x="740" y="268" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">MockSmsChannel</text>')
    c.append(f'<text x="650" y="292" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">- smsOutboxModel: Model</text>')
    c.append(f'<text x="650" y="308" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">+ send(): sms_outbox record</text>')

    c.append(line_arrow(480, 230, 520, 150, marker="url(#o)", text="implements"))
    c.append(line_arrow(740, 230, 600, 150, marker="url(#o)", text="implements"))

    c.append('<rect x="80" y="230" width="240" height="80" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="200" y="255" font-size="12" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">TemplateService</text>')
    c.append(f'<text x="100" y="280" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">+ render(template, data): HTML</text>')

    c.append('<rect x="80" y="360" width="240" height="80" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="200" y="385" font-size="12" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">UserPreferencesService</text>')
    c.append(f'<text x="100" y="410" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">+ getPreferences(userId): Prefs</text>')

    c.append(line_arrow(200, 150, 200, 230, text="uses"))
    c.append(line_arrow(200, 310, 200, 360, text="queries"))

    return svg_wrap("\n".join(c), w, h)

# -------------------------------------------------------------
# FIGURE 12: DESIGN PATTERNS IN PARCEL-SERVICE (RECEIPT)
# -------------------------------------------------------------
def gen_fig12():
    w, h = 1100, 560
    c = []
    c.append('<rect x="80" y="80" width="240" height="80" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="200" y="100" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">«presentation / REST»</text>')
    c.append(f'<text x="200" y="120" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">ReceiptController</text>')
    c.append(f'<text x="100" y="145" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">+ getReceipt(id): ResponseEntity</text>')

    c.append('<rect x="420" y="80" width="250" height="90" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="545" y="100" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">«application / usecase»</text>')
    c.append(f'<text x="545" y="120" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">ReceiptService</text>')
    c.append(f'<text x="440" y="145" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">+ generateReceipt(id, user): byte[]</text>')

    c.append(line_arrow(320, 120, 420, 120, text="calls"))

    c.append('<rect x="420" y="240" width="250" height="90" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="545" y="260" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">«domain / policy»</text>')
    c.append(f'<text x="545" y="280" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">ParcelAccessPolicy</text>')
    c.append(f'<text x="440" y="305" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">+ canRead(parcel, user): boolean</text>')

    c.append(line_arrow(545, 170, 545, 240, text="evaluates"))

    c.append('<rect x="760" y="80" width="250" height="90" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="885" y="100" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">«adapter / OpenPDF»</text>')
    c.append(f'<text x="885" y="120" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">PdfReceiptGenerator</text>')
    c.append(f'<text x="780" y="145" font-size="10.5" fill="#333333" font-family="{FONT_FAMILY}">+ buildPdf(parcel, history): byte[]</text>')

    c.append(line_arrow(670, 120, 760, 120, text="renders via"))

    c.append('<rect x="420" y="400" width="250" height="80" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="545" y="425" font-size="10" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">«repository»</text>')
    c.append(f'<text x="545" y="445" font-size="13" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">ParcelRepository</text>')
    c.append(line_arrow(545, 330, 545, 400, text="loads entity"))

    return svg_wrap("\n".join(c), w, h)

# Run all generator functions and write files
generators = [
    ("fig1-usecases.svg", gen_fig1),
    ("fig2-datamodel.svg", gen_fig2),
    ("fig3-architecture.svg", gen_fig3),
    ("fig4-integration.svg", gen_fig4),
    ("fig5-sequence-notification.svg", gen_fig5),
    ("fig6-sequence-receipt.svg", gen_fig6),
    ("fig7-sequence-preferences.svg", gen_fig7),
    ("fig8-sequence-bell.svg", gen_fig8),
    ("fig9-sequence-sms-inbox.svg", gen_fig9),
    ("fig10-activity-dispatch.svg", gen_fig10),
    ("fig11-patterns-notification.svg", gen_fig11),
    ("fig12-patterns-receipt.svg", gen_fig12)
]

for filename, func in generators:
    svg_path = os.path.join(OUT_DIR, filename)
    with open(svg_path, "w", encoding="utf-8") as f:
        f.write(func())
    print(f"Generated {filename}")

    # Render matching PNG at 150 DPI for design PDF
    png_name = filename.replace(".svg", ".png")
    png_path = os.path.join(OUT_DIR, png_name)
    doc = fitz.open(svg_path)
    pix = doc[0].get_pixmap(dpi=150)
    pix.save(png_path)
    print(f"Rendered {png_name}")

print("All 12 SVG and PNG diagrams generated successfully in doc/diagrams/m9/")
