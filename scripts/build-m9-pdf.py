"""
Builds the complete 16-page M9 Design Documentation matching Pasidu Mihiranga's PDF style.
Generates HTML and compiles to PDF via Chrome.
"""
import os
import subprocess

DOC_DIR = os.path.join(os.path.dirname(__file__), "..", "doc")
HTML_PATH = os.path.join(DOC_DIR, "m9-design-documentation.html")
PDF_PATH = os.path.join(DOC_DIR, "M9-Design-Documentation.pdf")
DIAGRAMS_DIR = os.path.join(DOC_DIR, "diagrams", "m9")

FONT_STACK = "'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Helvetica, Arial, sans-serif"

CSS = f"""
@page {{
  size: 297mm 210mm; /* A4 Landscape */
  margin: 0;
}}
* {{
  box-sizing: border-box;
}}
body {{
  margin: 0;
  padding: 0;
  font-family: {FONT_STACK};
  background: #f0f0f0;
  color: #111111;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
}}
.page {{
  width: 297mm;
  height: 210mm;
  padding: 16mm 22mm 14mm 22mm;
  margin: 0 auto;
  background: #ffffff;
  position: relative;
  page-break-after: always;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}}
@media screen {{
  .page {{
    margin-bottom: 20px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  }}
}}
.header-meta {{
  font-size: 11px;
  font-weight: 600;
  color: #555555;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  margin-bottom: 12px;
}}
.doc-footer {{
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 10px;
  color: #777777;
  padding-top: 10px;
  border-top: 1px solid #eaeaea;
}}
.doc-footer .note {{
  max-width: 85%;
  line-height: 1.3;
}}
.doc-footer .page-num {{
  font-weight: 500;
  white-space: nowrap;
}}

/* Cover styles */
.cover-title {{
  font-size: 40px;
  font-weight: 800;
  color: #111111;
  margin: 20px 0 16px 0;
  letter-spacing: -0.5px;
}}
.cover-desc {{
  font-size: 15px;
  line-height: 1.55;
  color: #444444;
  max-width: 880px;
  margin-bottom: 24px;
}}
.meta-bar {{
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  font-size: 12px;
  color: #444444;
  padding: 12px 16px;
  background: #f7f7f7;
  border-left: 3px solid #111111;
  border-radius: 2px;
  margin-bottom: 40px;
}}
.meta-bar b {{
  color: #111111;
}}
.toc-list {{
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  font-size: 15px;
  font-weight: 600;
  color: #222222;
  margin-top: 10px;
}}
.toc-item span {{
  display: inline-block;
  width: 28px;
  color: #777777;
  font-weight: 500;
}}

/* Section content */
.section-title {{
  font-size: 20px;
  font-weight: 700;
  color: #111111;
  margin: 0 0 8px 0;
}}
.section-intro {{
  font-size: 12.5px;
  line-height: 1.5;
  color: #333333;
  margin-bottom: 12px;
  max-width: 1000px;
}}

/* Card container for figures */
.figure-card {{
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  background: #ffffff;
  padding: 14px 18px 12px 18px;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}}
.figure-heading {{
  font-size: 13px;
  font-weight: 700;
  color: #111111;
  margin-bottom: 8px;
}}
.figure-heading .label {{
  font-weight: 700;
  color: #777777;
  letter-spacing: 1.2px;
  margin-right: 6px;
}}
.figure-img-wrap {{
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
}}
.figure-img {{
  max-width: 100%;
  max-height: 400px;
  object-fit: contain;
}}
.figure-caption {{
  font-size: 10.5px;
  color: #666666;
  line-height: 1.45;
  margin-top: 8px;
  border-top: 1px solid #f0f0f0;
  padding-top: 6px;
}}

/* Tables */
table.clean-table {{
  width: 100%;
  border-collapse: collapse;
  font-size: 11.5px;
  margin-top: 10px;
}}
table.clean-table th {{
  text-align: left;
  padding: 8px 12px;
  border-bottom: 1.5px solid #111111;
  font-weight: 700;
  color: #111111;
  font-size: 11px;
  letter-spacing: 0.8px;
}}
table.clean-table td {{
  padding: 7px 12px;
  border-bottom: 1px solid #eaeaea;
  color: #333333;
  line-height: 1.35;
}}
table.clean-table tr:last-child td {{
  border-bottom: none;
}}
"""

def page_shell(page_num, content_html, is_cover=False):
    footer = f"""
    <div class="doc-footer">
      <div class="note">Diagram sources: doc/diagrams/m9/*.svg in the SwiftDrop repository, with matching PNG exports for the design PDF. Example values (fees, tracking numbers) are illustrative.</div>
      <div class="page-num">Page {page_num} of 16</div>
    </div>
    """
    return f"""
    <div class="page">
      <div class="doc-content">
        <div class="header-meta">SWIFTDROP · DESIGN DOCUMENTATION · SAPUNI MADHUSHIKA</div>
        {content_html}
      </div>
      {footer}
    </div>
    """

pages = []

# PAGE 1: COVER
p1 = """
<div class="cover-title">Notifications, Delivery History and Receipts</div>
<div class="cover-desc">
Design of Sapuni Madhushika's services: event-driven notification dispatch (email and mock SMS)
via pluggable channels, customer delivery history and OpenPDF receipt generation in parcel-service,
in-app notification centre with unread badge, and the complete LGTM observability stack.
</div>
<div class="meta-bar">
  <div><b>Services</b> notification-service (NestJS 11) · parcel-service (Spring Boot 4, receipt endpoint)</div>
  <div><b>Data</b> MongoDB notifications · PostgreSQL parcels_db · Kafka</div>
  <div><b>Branch</b> feat/m9-notifications</div>
</div>
<div class="toc-list">
  <div class="toc-item"><span>1</span> Use cases</div>
  <div class="toc-item"><span>2</span> Data model</div>
  <div class="toc-item"><span>3</span> Architecture</div>
  <div class="toc-item"><span>4</span> Integration design</div>
  <div class="toc-item"><span>5</span> Key flows</div>
  <div class="toc-item"><span>6</span> Design patterns and principles</div>
</div>
"""
pages.append(page_shell(1, p1, is_cover=True))

# PAGE 2: USE CASES
p2 = """
<div class="section-title">1 Use cases</div>
<div class="section-intro">
Sapuni Madhushika owns three capabilities. <b>Notification service</b> provides reliable, multi-channel customer communications
(email via SMTP/Mailpit and mock SMS) triggered by status-change events. <b>Delivery history and receipts</b> let customers browse
their historical orders and download cryptographically sound PDF delivery receipts. <b>Observability</b> gives the engineering team complete visibility across the platform.
</div>
<div class="figure-card">
  <div class="figure-heading"><span class="label">FIGURE 1</span> Use cases: notifications, delivery history and receipts</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig1-usecases.svg" class="figure-img"/></div>
  <div class="figure-caption">
    Customers view delivery history, download receipts, and configure preferences; couriers trigger notifications through status updates; admins review demo SMS records.
    «include» points to the use case that is always included; «extend» points to the use case it extends.
  </div>
</div>
"""
pages.append(page_shell(2, p2))

# PAGE 3: DATA MODEL
p3 = """
<div class="section-title">2 Data model</div>
<div class="section-intro">
Notifications are stored in MongoDB: notification-service maintains <code>notification_log</code> (in-app history), <code>user_preferences</code>, and <code>sms_outbox</code>.
<code>PROCESSED_EVENT</code> makes event handling idempotent across Kafka consumers. Receipt generation reads delivered parcel facts directly from PostgreSQL (<code>parcels_db</code>) without cross-network joins.
</div>
<div class="figure-card">
  <div class="figure-heading"><span class="label">FIGURE 2</span> Data model: notifications MongoDB and receipt relational source</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig2-datamodel.svg" class="figure-img"/></div>
  <div class="figure-caption">
    notifications db holds document collections in MongoDB with a 7-day TTL on processed events. Receipts are projected in memory from parcel, status history, and delivery proof in parcels_db.
    Solid line with crow's foot: a real one-to-many relationship. Dashed arrow: records referenced by ID or derived by projection.
  </div>
</div>
"""
pages.append(page_shell(3, p3))

# PAGE 4: ARCHITECTURE (TEXT & TABLE)
p4 = """
<div class="section-title">3 Architecture</div>
<div class="section-intro">
Proposal: microservices behind an API gateway, with the Next.js app acting as backend-for-frontend (BFF). Each service owns its data; services integrate through Kafka events or through the gateway.
</div>

<div style="display: flex; gap: 30px; margin-top: 15px;">
  <div style="flex: 1.2;">
    <h3 style="font-size: 14px; font-weight: 700; margin-bottom: 12px;">Why this style for Sapuni Madhushika's services</h3>
    <ul style="font-size: 12px; line-height: 1.6; padding-left: 18px; color: #333;">
      <li style="margin-bottom: 8px;"><b>Independent scaling:</b> notification fan-out and history browsing have completely different scaling profiles from write-heavy parcel booking.</li>
      <li style="margin-bottom: 8px;"><b>Pluggable channels:</b> NestJS handles asynchronous I/O and template compilation efficiently, while Java OpenPDF ensures enterprise-grade PDF rendering in parcel-service.</li>
      <li style="margin-bottom: 8px;"><b>Loose coupling:</b> notification-service subscribes to events, so order producers never know notification logic exists and notification downtime never blocks order booking.</li>
      <li style="margin-bottom: 8px;"><b>Security by construction:</b> receipt downloads enforce strict tenant isolation via domain AccessPolicy (404 for foreign parcels; 409 for non-delivered parcels).</li>
    </ul>
    <div style="font-size: 11.5px; color: #555; margin-top: 16px; background: #fafafa; padding: 10px; border-radius: 4px; border: 1px solid #e0e0e0;">
      <b>Trade-off accepted:</b> notification delivery is eventually consistent (typically arriving within 1 to 2 seconds following status transitions).
    </div>
  </div>

  <div style="flex: 1.1;">
    <h3 style="font-size: 14px; font-weight: 700; margin-bottom: 8px;">Tiers</h3>
    <table class="clean-table">
      <tr><th style="width: 25%;">TIER</th><th>RESPONSIBILITIES</th></tr>
      <tr><td><b>Client</b></td><td>Next.js delivery history page, notification bell, preferences form, admin demo SMS inbox</td></tr>
      <tr><td><b>Transport</b></td><td>Next.js BFF (token stays server-side) and Spring Cloud Gateway: routing, JWT check, rate limits</td></tr>
      <tr><td><b>Application</b></td><td>notification-service (consumers → channel strategy → templates), parcel-service (receipts)</td></tr>
      <tr><td><b>Data</b></td><td>MongoDB <code>notifications</code>, PostgreSQL <code>parcels_db</code>, Kafka status topics</td></tr>
      <tr><td><b>Platform</b></td><td>Full LGTM observability stack: Alloy collector, Loki logs, Tempo traces, Prometheus metrics, Grafana</td></tr>
    </table>
  </div>
</div>
"""
pages.append(page_shell(4, p4))

# PAGE 5: ARCHITECTURE DIAGRAM
p5 = """
<div class="figure-card" style="height: 100%;">
  <div class="figure-heading"><span class="label">FIGURE 3</span> Architecture: layered microservices with a BFF</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig3-architecture.svg" class="figure-img" style="max-height: 480px;"/></div>
  <div class="figure-caption">
    Four tiers with unified observability. Sapuni Madhushika's notification-service sits in the application tier consuming Kafka events, while receipt generation is embedded in parcel-service; all services export telemetry to Grafana Alloy.
  </div>
</div>
"""
pages.append(page_shell(5, p5))

# PAGE 6: INTEGRATION DESIGN (DIAGRAM)
p6 = """
<div class="section-title">4 Integration design</div>
<div class="figure-card">
  <div class="figure-heading"><span class="label">FIGURE 4</span> Integration design: the patterns Sapuni Madhushika's services rely on</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig4-integration.svg" class="figure-img"/></div>
  <div class="figure-caption">
    Numbers match the pattern table. Asynchronous patterns (1-5) ingest events and dispatch notifications; synchronous ones (6-10) serve users browsing history and downloading receipts.
  </div>
</div>
"""
pages.append(page_shell(6, p6))

# PAGE 7: INTEGRATION PATTERNS TABLE
p7 = """
<div class="section-title">4 Integration design (continued)</div>
<div class="section-intro">
Integration patterns applied across asynchronous event consumption and synchronous receipt generation:
</div>
<table class="clean-table" style="font-size: 11px;">
  <tr><th style="width: 4%;">#</th><th style="width: 22%;">PATTERN</th><th style="width: 32%;">WHERE USED</th><th>WHY</th></tr>
  <tr><td>1</td><td><b>Transactional outbox</b></td><td><code>parcel-service</code> outbox_event table</td><td>Event and state change commit together, so notifications never miss a real status transition.</td></tr>
  <tr><td>2</td><td><b>Publish/subscribe (broker)</b></td><td>Kafka topic <code>swiftdrop.parcel.status-changed.v1</code></td><td>One status update feeds notifications, tracking, webhooks, and dispatch without producers knowing any of them.</td></tr>
  <tr><td>3</td><td><b>Competing consumers</b></td><td>Consumer group <code>notification-service</code></td><td>Replicas share Kafka partitions; key = parcelId keeps events in order per shipment.</td></tr>
  <tr><td>4</td><td><b>Idempotent consumer</b></td><td>MongoDB <code>processed_event</code> collection</td><td>Kafka delivers at least once; duplicate deliveries are detected and rejected before sending duplicate emails.</td></tr>
  <tr><td>5</td><td><b>Strategy channel dispatch</b></td><td><code>NotificationChannel</code> (Email, Mock SMS)</td><td>Allows adding new delivery channels (e.g. real SMS provider) without altering consumer orchestration.</td></tr>
  <tr><td>6</td><td><b>Request/reply (REST)</b></td><td><code>/api/v1/parcels/{id}/receipt</code> via gateway</td><td>The browser needs an immediate binary PDF stream when a customer requests a proof receipt.</td></tr>
  <tr><td>7</td><td><b>Streaming response</b></td><td>Spring MVC <code>ResponseEntity&lt;Resource&gt;</code></td><td>Receipt PDF is generated and streamed in chunks without loading large binary files entirely in memory.</td></tr>
  <tr><td>8</td><td><b>Backend-For-Frontend (BFF)</b></td><td>Next.js 16 App Router route handlers</td><td>Access tokens remain server-side in encrypted session cookies; client JS never touches bearer tokens.</td></tr>
  <tr><td>9</td><td><b>Gateway + token relay</b></td><td>Spring Cloud Gateway to parcel-service</td><td>Validates Keycloak JWT signature once at the edge, relaying correlation headers and claims.</td></tr>
  <tr><td>10</td><td><b>Dead-letter queue (DLQ)</b></td><td>Topic <code>swiftdrop.notification.dead-letter.v1</code></td><td>Failed dispatches after 3 exponential backoff retries are preserved for administrative diagnosis.</td></tr>
</table>
"""
pages.append(page_shell(7, p7))

# PAGE 8: KEY FLOWS - NOTIFICATION SEQUENCE
p8 = """
<div class="section-title">5 Key flows</div>
<div class="section-intro">
Flow 5 is Sapuni Madhushika's part of the shared courier status update flow. Flows 6 to 10 cover receipt download, preference handling, live notification bell updates, admin SMS inspection, and channel dispatch.
</div>
<div class="figure-card">
  <div class="figure-heading"><span class="label">FIGURE 5</span> Sequence: a courier status update triggers email and SMS notifications</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig5-sequence-notification.svg" class="figure-img"/></div>
  <div class="figure-caption">
    The event that triggers live tracking and webhooks is consumed independently by notification-service. A duplicate delivery is detected via processed_event and rejected without producing duplicate emails.
  </div>
</div>
"""
pages.append(page_shell(8, p8))

# PAGE 9: RECEIPT DOWNLOAD SEQUENCE
p9 = """
<div class="figure-card" style="height: 100%;">
  <div class="figure-heading"><span class="label">FIGURE 6</span> Sequence: a customer downloads a delivery receipt (PDF)</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig6-sequence-receipt.svg" class="figure-img" style="max-height: 480px;"/></div>
  <div class="figure-caption">
    The browser never holds the access token: the BFF adds it server-side. ParcelAccessPolicy ensures customers cannot download receipts for other customers' parcels; non-delivered parcels return 409 Conflict.
  </div>
</div>
"""
pages.append(page_shell(9, p9))

# PAGE 10: PREFERENCES SEQUENCE
p10 = """
<div class="figure-card" style="height: 100%;">
  <div class="figure-heading"><span class="label">FIGURE 7</span> Sequence: a user configures notification preferences</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig7-sequence-preferences.svg" class="figure-img" style="max-height: 480px;"/></div>
  <div class="figure-caption">
    User preferences are queried and updated through the Next.js BFF. The notification-service updates MongoDB with upsert semantics, returning an immediate acknowledgment.
  </div>
</div>
"""
pages.append(page_shell(10, p10))

# PAGE 11: IN-APP BELL SEQUENCE
p11 = """
<div class="figure-card" style="height: 100%;">
  <div class="figure-heading"><span class="label">FIGURE 8</span> Sequence: in-app notification & live bell update</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig8-sequence-bell.svg" class="figure-img" style="max-height: 480px;"/></div>
  <div class="figure-caption">
    In addition to SMTP email and SMS outbox records, in-app notifications are persisted to MongoDB and broadcast via Redis pub/sub to tracking-service, updating the browser notification bell live.
  </div>
</div>
"""
pages.append(page_shell(11, p11))

# PAGE 12: ADMIN SMS INBOX SEQUENCE
p12 = """
<div class="figure-card" style="height: 100%;">
  <div class="figure-heading"><span class="label">FIGURE 9</span> Sequence: admin reviews the demo SMS inbox</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig9-sequence-sms-inbox.svg" class="figure-img" style="max-height: 480px;"/></div>
  <div class="figure-caption">
    The demo SMS inbox gives evaluators and administrators instant visual confirmation of outgoing SMS dispatches. Gateway enforces the ADMIN role; non-admin users receive 403 problem+json.
  </div>
</div>
"""
pages.append(page_shell(12, p12))

# PAGE 13: ACTIVITY: CHANNEL DISPATCH LOOP
p13 = """
<div class="figure-card" style="height: 100%;">
  <div class="figure-heading"><span class="label">FIGURE 10</span> Activity: notification dispatch and channel selection loop</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig10-activity-dispatch.svg" class="figure-img" style="max-height: 480px;"/></div>
  <div class="figure-caption">
    Every guardrail is a decision in this loop: deduplication check, preference resolution, parallel channel dispatch, audit logging, and dead-letter queue routing on persistent failure.
  </div>
</div>
"""
pages.append(page_shell(13, p13))

# PAGE 14: PATTERNS IN NOTIFICATION-SERVICE
p14 = """
<div class="section-title">6 Design patterns and principles</div>
<div class="figure-card">
  <div class="figure-heading"><span class="label">FIGURE 11</span> Design patterns in notification-service</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig11-patterns-notification.svg" class="figure-img"/></div>
  <div class="figure-caption">
    Strategy for notification channels (Email, Mock SMS, future providers), Facade for channel dispatch, Adapter for SMTP, and Repository for MongoDB access.
    Dashed open arrow: depends on. Solid open arrow: composition. Dashed hollow triangle: implements.
  </div>
</div>
"""
pages.append(page_shell(14, p14))

# PAGE 15: PATTERNS IN PARCEL-SERVICE RECEIPT
p15 = """
<div class="figure-card" style="height: 100%;">
  <div class="figure-heading"><span class="label">FIGURE 12</span> Design patterns in parcel-service receipt generation</div>
  <div class="figure-img-wrap"><img src="diagrams/m9/fig12-patterns-receipt.svg" class="figure-img" style="max-height: 480px;"/></div>
  <div class="figure-caption">
    Layered and hexagonal: controller and OpenPDF generator are adapters around domain policies and application services; access policy ensures strict tenant isolation.
  </div>
</div>
"""
pages.append(page_shell(15, p15))

# PAGE 16: PATTERNS & PRINCIPLES SUMMARY
p16 = """
<div class="section-title">6 Design patterns and principles (continued)</div>
<div class="section-intro">
Summary of design patterns and engineering principles applied across Sapuni Madhushika's vertical:
</div>

<table class="clean-table" style="font-size: 11px; margin-bottom: 20px;">
  <tr><th style="width: 25%;">PATTERN</th><th>APPLIED TO</th></tr>
  <tr><td><b>Strategy</b></td><td><code>NotificationChannel</code>: EmailChannel and MockSmsChannel are interchangeable strategies.</td></tr>
  <tr><td><b>Template Method</b></td><td>Shared consumer pipeline in <code>@swiftdrop/common</code>: dedupe, handle, commit.</td></tr>
  <tr><td><b>Adapter</b></td><td><code>EmailChannel</code> wraps nodemailer SMTP; <code>PdfReceiptGenerator</code> wraps OpenPDF document rendering.</td></tr>
  <tr><td><b>Facade</b></td><td><code>ChannelDispatcherService</code> hides preference querying, template compilation, and transport invocation.</td></tr>
  <tr><td><b>Observer / Pub-Sub</b></td><td><code>notification-service</code> reacts to Kafka parcel events it subscribes to.</td></tr>
  <tr><td><b>Repository</b></td><td>Mongoose models for MongoDB collections; Spring Data repositories for parcel facts.</td></tr>
  <tr><td><b>CQRS and Idempotent Consumer</b></td><td>Transactional writes commit in parcel-service; notification-service reads events and skips duplicate deliveries.</td></tr>
</table>

<h3 style="font-size: 13.5px; font-weight: 700; margin: 12px 0 8px 0;">Principles</h3>
<ul style="font-size: 11.5px; line-height: 1.55; color: #333; padding-left: 18px; margin: 0;">
  <li><b>Single responsibility:</b> Consumers translate events; dispatcher coordinates channels; templates handle presentation; access policy validates security.</li>
  <li><b>Open/closed:</b> A new notification channel (e.g., Twilio SMS or push notifications) is an added class implementing <code>NotificationChannel</code>, not an edit to consumers.</li>
  <li><b>Dependency inversion:</b> Consumers and dispatcher depend on the <code>NotificationChannel</code> interface; persistence depends on repositories.</li>
  <li><b>DRY:</b> Common layout headers and footers are encapsulated in Handlebars partials; Kafka consumer logic is shared via <code>@swiftdrop/common</code>.</li>
  <li><b>Least privilege:</b> Receipt downloads verify caller ownership (customer can only access their own parcel); non-privileged calls return 404.</li>
  <li><b>Twelve-factor configuration:</b> SMTP hosts, MongoDB URIs, and Kafka brokers are injected via environment variables and validated at startup with Zod.</li>
</ul>
"""
pages.append(page_shell(16, p16))

# ASSEMBLE FULL HTML
full_html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<title>SwiftDrop — Design Documentation — Sapuni Madhushika</title>
<style>
{CSS}
</style>
</head>
<body>
{"".join(pages)}
</body>
</html>"""

with open(HTML_PATH, "w", encoding="utf-8") as f:
    f.write(full_html)
print(f"Wrote HTML to {HTML_PATH}")

# COMPILE TO PDF VIA CHROME HEADLESS
chrome = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"
print("Compiling PDF with Chrome...")
cmd = [
    chrome,
    "--headless=new",
    "--disable-gpu",
    "--no-pdf-header-footer",
    f"--print-to-pdf={PDF_PATH}",
    f"file:///{HTML_PATH}"
]
subprocess.run(cmd, check=True)
print(f"Generated PDF at {PDF_PATH}")
