import os
import re

FONT_FAMILY = "Segoe UI, Helvetica Neue, Helvetica, Arial, sans-serif"

def test_fig3():
    w, h = 1100, 560
    c = []
    
    # Outer container
    c.append('<rect x="20" y="20" width="1060" height="520" rx="4" fill="#ffffff" stroke="#d4d4d4" stroke-width="1.2"/>')
    
    # Four horizontal tiers
    tiers = [
        (30, 75, "CLIENT TIER"),
        (115, 70, "TRANSPORT"),
        (195, 155, "APPLICATION"),
        (360, 165, "DATA AND INFRASTRUCTURE")
    ]
    for y, th, name in tiers:
        c.append(f'<rect x="30" y="{y}" width="780" height="{th}" rx="3" fill="#fcfcfc" stroke="#e8e8e8" stroke-width="1"/>')
        c.append(f'<text x="45" y="{y + 18}" font-size="9.5" font-weight="700" fill="#888888" font-family="{FONT_FAMILY}" letter-spacing="1.5">{name}</text>')
        
    # External Services column on right
    c.append('<rect x="825" y="30" width="245" height="495" rx="3" fill="#f9f9f9" stroke="#e0e0e0" stroke-width="1"/>')
    c.append(f'<text x="840" y="48" font-size="9.5" font-weight="700" fill="#888888" font-family="{FONT_FAMILY}" letter-spacing="1.5">EXTERNAL SERVICES</text>')
    
    # ---------------- CLIENT TIER ----------------
    # Customer web app
    c.append('<rect x="140" y="45" width="300" height="48" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="290" y="65" font-size="11.5" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Customer Web UI (Next.js)</text>')
    c.append(f'<text x="290" y="81" font-size="9.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">delivery history · PDF receipt · notification bell</text>')
    
    # Admin web app
    c.append('<rect x="480" y="45" width="280" height="48" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="620" y="65" font-size="11.5" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Admin Web UI (Next.js)</text>')
    c.append(f'<text x="620" y="81" font-size="9.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">demo SMS outbox inbox · dispatch overview</text>')
    
    # ---------------- TRANSPORT TIER ----------------
    # Next.js BFF proxy
    c.append('<rect x="140" y="130" width="300" height="44" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="290" y="148" font-size="11.5" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Next.js BFF Proxy</text>')
    c.append(f'<text x="290" y="163" font-size="9.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">server-side token injection · SSR · CSRF check</text>')
    
    # Spring Cloud Gateway
    c.append('<rect x="480" y="130" width="310" height="44" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="635" y="148" font-size="11.5" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Spring Cloud Gateway (:8080)</text>')
    c.append(f'<text x="635" y="163" font-size="9.5" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">single entry point · JWT validation · rate limiting</text>')
    
    # Arrow Customer -> BFF
    c.append('<line x1="290" y1="93" x2="290" y2="130" stroke="#111111" stroke-width="1.2" marker-end="url(#a)"/>')
    # Arrow Admin -> Gateway
    c.append('<line x1="620" y1="93" x2="620" y2="130" stroke="#111111" stroke-width="1.2" marker-end="url(#a)"/>')
    # Arrow BFF -> Gateway
    c.append('<line x1="440" y1="152" x2="480" y2="152" stroke="#111111" stroke-width="1.2" marker-end="url(#a)"/>')
    c.append(f'<text x="460" y="146" font-size="8.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">Bearer JWT</text>')

    # ---------------- APPLICATION TIER ----------------
    # Left: parcel-service (M3/M4/M9)
    c.append('<rect x="60" y="215" width="230" height="120" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append('<rect x="60" y="215" width="230" height="26" fill="#111111"/>')
    c.append(f'<text x="175" y="232" font-size="11.5" font-weight="700" fill="#ffffff" text-anchor="middle" font-family="{FONT_FAMILY}">parcel-service (M9 Receipt)</text>')
    c.append(f'<text x="175" y="258" font-size="10" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Spring Boot 4 · Java 21</text>')
    c.append(f'<text x="175" y="276" font-size="9.5" fill="#444444" text-anchor="middle" font-family="{FONT_FAMILY}">GET /api/v1/parcels/id/receipt</text>')
    c.append(f'<text x="175" y="293" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">OpenPDF Document Builder</text>')
    c.append(f'<text x="175" y="309" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">ParcelAccessPolicy security</text>')
    c.append(f'<text x="175" y="325" font-size="9" fill="#888888" text-anchor="middle" font-family="{FONT_FAMILY}">Transactional Outbox Publisher</text>')

    # Center: Other microservices fleet
    c.append('<rect x="315" y="230" width="160" height="90" rx="2" fill="#ffffff" stroke="#888888" stroke-width="1.1" stroke-dasharray="3 3"/>')
    c.append(f'<text x="395" y="250" font-size="11" font-weight="700" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">Other Microservices</text>')
    c.append(f'<text x="395" y="268" font-size="9.5" fill="#777777" text-anchor="middle" font-family="{FONT_FAMILY}">user-service (profiles)</text>')
    c.append(f'<text x="395" y="284" font-size="9.5" fill="#777777" text-anchor="middle" font-family="{FONT_FAMILY}">dispatch-service (runs)</text>')
    c.append(f'<text x="395" y="300" font-size="9.5" fill="#777777" text-anchor="middle" font-family="{FONT_FAMILY}">tracking (Socket.IO)</text>')

    # Right: notification-service (M9)
    c.append('<rect x="500" y="215" width="290" height="120" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append('<rect x="500" y="215" width="290" height="26" fill="#111111"/>')
    c.append(f'<text x="645" y="232" font-size="11.5" font-weight="700" fill="#ffffff" text-anchor="middle" font-family="{FONT_FAMILY}">notification-service (M9 Core)</text>')
    c.append(f'<text x="645" y="258" font-size="10" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">NestJS 11 · TypeScript</text>')
    c.append(f'<text x="645" y="276" font-size="9.5" fill="#444444" text-anchor="middle" font-family="{FONT_FAMILY}">4 Kafka Consumers (status, delivered, failed)</text>')
    c.append(f'<text x="645" y="293" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">Channel Strategy: Email &amp; Mock SMS</text>')
    c.append(f'<text x="645" y="309" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">Preference evaluation &amp; Handlebars render</text>')
    c.append(f'<text x="645" y="325" font-size="9" fill="#888888" text-anchor="middle" font-family="{FONT_FAMILY}">In-app notification center REST API</text>')

    # Routing from Gateway to Services
    # Gateway to parcel-service (left)
    c.append('<path d="M520 174 V195 H175 V215" fill="none" stroke="#111111" stroke-width="1.2" marker-end="url(#a)"/>')
    c.append(f'<text x="320" y="191" font-size="8.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">GET /parcels/id/receipt</text>')
    
    # Gateway to notification-service (right)
    c.append('<path d="M680 174 V215" fill="none" stroke="#111111" stroke-width="1.2" marker-end="url(#a)"/>')
    c.append(f'<text x="735" y="195" font-size="8.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">/api/v1/notifications/*</text>')

    # ---------------- DATA AND INFRASTRUCTURE TIER ----------------
    # Left: PostgreSQL 16 (parcels_db)
    c.append('<rect x="60" y="380" width="230" height="130" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="175" y="402" font-size="11.5" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">PostgreSQL 16 (parcels_db)</text>')
    c.append(f'<text x="175" y="420" font-size="9.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">PARCEL (status, addresses, fee)</text>')
    c.append(f'<text x="175" y="437" font-size="9.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">PARCEL_STATUS_HISTORY</text>')
    c.append(f'<text x="175" y="454" font-size="9.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">DELIVERY_PROOF (name, photo)</text>')
    c.append(f'<text x="175" y="471" font-size="9.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">outbox_event (reliable relay)</text>')
    c.append(f'<text x="175" y="492" font-size="8.5" fill="#888888" text-anchor="middle" font-family="{FONT_FAMILY}">Receipt reads delivered parcel facts</text>')

    # Vertical Arrow: parcel-service -> PostgreSQL
    c.append('<line x1="175" y1="335" x2="175" y2="380" stroke="#111111" stroke-width="1.2" marker-end="url(#a)"/>')
    c.append(f'<text x="178" y="360" font-size="8.5" fill="#555555" text-anchor="start" font-family="{FONT_FAMILY}">SELECT facts</text>')

    # Center: Apache Kafka (KRaft)
    c.append('<rect x="315" y="380" width="160" height="130" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="395" y="402" font-size="11.5" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Apache Kafka (KRaft)</text>')
    c.append(f'<text x="395" y="422" font-size="9.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">Topics: parcel.*</text>')
    c.append(f'<text x="395" y="439" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">parcel.status-changed</text>')
    c.append(f'<text x="395" y="455" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">parcel.delivered</text>')
    c.append(f'<text x="395" y="473" font-size="8.5" fill="#888888" text-anchor="middle" font-family="{FONT_FAMILY}">key = parcelId</text>')
    c.append(f'<text x="395" y="492" font-size="8.5" fill="#888888" text-anchor="middle" font-family="{FONT_FAMILY}">Consumer group partition</text>')

    # Arrow: parcel-service -> Kafka (outbox publish)
    c.append('<line x1="290" y1="430" x2="315" y2="430" stroke="#111111" stroke-width="1.2" marker-end="url(#a)"/>')
    c.append(f'<text x="302" y="424" font-size="8" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">publish</text>')

    # Arrow: Kafka -> notification-service (consume)
    c.append('<path d="M475 430 H540 V335" fill="none" stroke="#111111" stroke-width="1.2" marker-end="url(#a)"/>')
    c.append(f'<text x="500" y="424" font-size="8" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">consume</text>')

    # Right: MongoDB 7 (notifications db)
    c.append('<rect x="500" y="380" width="290" height="130" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append(f'<text x="645" y="402" font-size="11.5" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">MongoDB 7 (notifications db)</text>')
    c.append(f'<text x="645" y="420" font-size="9.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">notifications.logs (in-app history)</text>')
    c.append(f'<text x="645" y="437" font-size="9.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">notifications.preferences (email, sms flags)</text>')
    c.append(f'<text x="645" y="454" font-size="9.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">notifications.sms_outbox (demo review)</text>')
    c.append(f'<text x="645" y="471" font-size="9.5" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">processed_event (idempotency, TTL 7d)</text>')
    c.append(f'<text x="645" y="492" font-size="8.5" fill="#888888" text-anchor="middle" font-family="{FONT_FAMILY}">Document model with index on eventId &amp; userId</text>')

    # Vertical Arrow: notification-service -> MongoDB
    c.append('<line x1="645" y1="335" x2="645" y2="380" stroke="#111111" stroke-width="1.2" marker-end="url(#a)"/>')
    c.append(f'<text x="648" y="360" font-size="8.5" fill="#555555" text-anchor="start" font-family="{FONT_FAMILY}">read / write</text>')

    # ---------------- EXTERNAL SERVICES & OBSERVABILITY ----------------
    # Box 1: Keycloak 26
    c.append('<rect x="840" y="60" width="215" height="70" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.2"/>')
    c.append(f'<text x="947" y="80" font-size="11" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Keycloak 26 (Identity)</text>')
    c.append(f'<text x="947" y="97" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">OIDC / OAuth 2.0 · Google login</text>')
    c.append(f'<text x="947" y="112" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">JWKS public keys for token check</text>')

    # Arrow Keycloak -> Gateway
    c.append('<line x1="840" y1="145" x2="790" y2="145" stroke="#111111" stroke-width="1.2" marker-end="url(#a)"/>')
    c.append(f'<text x="815" y="139" font-size="8" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">JWKS</text>')

    # Box 2: Mailpit SMTP
    c.append('<rect x="840" y="215" width="215" height="120" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.3"/>')
    c.append('<rect x="840" y="215" width="215" height="24" fill="#111111"/>')
    c.append(f'<text x="947" y="231" font-size="11" font-weight="700" fill="#ffffff" text-anchor="middle" font-family="{FONT_FAMILY}">Mailpit SMTP (:1025)</text>')
    c.append(f'<text x="947" y="258" font-size="9.5" fill="#444444" text-anchor="middle" font-family="{FONT_FAMILY}">Development SMTP Host</text>')
    c.append(f'<text x="947" y="275" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">Webmail verification UI (:8026)</text>')
    c.append(f'<text x="947" y="292" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">HTML &amp; text email preview</text>')
    c.append(f'<text x="947" y="310" font-size="8.5" fill="#888888" text-anchor="middle" font-family="{FONT_FAMILY}">Simulated inbox for automated tests</text>')

    # Arrow notification-service -> Mailpit SMTP (clean horizontal arrow!)
    c.append('<line x1="790" y1="260" x2="840" y2="260" stroke="#111111" stroke-width="1.3" marker-end="url(#a)"/>')
    c.append(f'<text x="815" y="253" font-size="8" font-weight="600" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">SMTP:1025</text>')

    # Box 3: Grafana LGTM Observability
    c.append('<rect x="840" y="380" width="215" height="130" rx="2" fill="#ffffff" stroke="#111111" stroke-width="1.2"/>')
    c.append(f'<text x="947" y="402" font-size="11" font-weight="700" fill="#111111" text-anchor="middle" font-family="{FONT_FAMILY}">Observability (LGTM Stack)</text>')
    c.append(f'<text x="947" y="422" font-size="9" fill="#555555" text-anchor="middle" font-family="{FONT_FAMILY}">Grafana Alloy collector</text>')
    c.append(f'<text x="947" y="439" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">Loki (JSON logs) · Tempo (traces)</text>')
    c.append(f'<text x="947" y="456" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">Prometheus (Kafka &amp; HTTP metrics)</text>')
    c.append(f'<text x="947" y="473" font-size="9" fill="#666666" text-anchor="middle" font-family="{FONT_FAMILY}">Grafana Dashboards (:3000)</text>')
    c.append(f'<text x="947" y="492" font-size="8.5" fill="#888888" text-anchor="middle" font-family="{FONT_FAMILY}">Unified correlation across services</text>')

    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1100 560" width="1100" height="560">\n<defs><marker id="a" viewBox="0 0 10 10" refX="9.2" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 z" fill="#111111"/></marker></defs>\n' + '\n'.join(c) + '\n</svg>'

with open('doc/diagrams/m9/fig3-architecture.svg', 'w', encoding='utf-8') as f:
    f.write(test_fig3())
print('Wrote new fig3-architecture.svg')

