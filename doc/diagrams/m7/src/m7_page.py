"""The M7 design page. The page skeleton and print styles come from doc/diagrams/m10/src."""

import html
import re

import paths
from build_figures import CAPTIONS, TITLES
from build_page import document

AUTHOR = "Kavinu Saputhanthri"
BUILD = paths.HERE / "build"

SECTIONS = [
    ("use-cases", "Use cases", ["01-use-cases"], """
<p>{author} owns merchant onboarding and the merchant API. An online store <strong>registers</strong>, an admin
<strong>approves</strong> it, it receives <strong>OAuth 2.0 client credentials</strong>, creates delivery orders from
its own system, and sees every order in a dashboard.</p>"""),
    ("data-model", "Data model", ["02-er-diagram"], """
<p>Each service owns its data. <code>merchants_db</code> holds the application and a record of the API client, while the
client secret stays in Keycloak. <code>parcels_db</code> gains <code>IDEMPOTENCY_RECORD</code>, plus
<code>MERCHANT_STATUS_CACHE</code>, built from lifecycle events so an order never needs a call to merchant-service.</p>"""),
    ("architecture", "Architecture", ["03-architecture"], """
<p><strong>Proposal:</strong> microservices behind an API gateway, with the Next.js app acting as
backend-for-frontend (BFF). Each service owns its data; services integrate through Kafka events or through the
gateway.</p>
<div class="two-col">
  <div>
    <h4>Why this style for merchant onboarding</h4>
    <ul>
      <li><strong>Separate failure domains:</strong> if merchant-service or Keycloak's Admin API is down, approved
      merchants keep placing orders, because parcel-service reads its own status cache.</li>
      <li><strong>Independent scaling:</strong> order traffic from online stores can burst far above the occasional
      onboarding request.</li>
      <li><strong>Clear ownership:</strong> merchant-service owns the merchant's lifecycle; parcel-service owns the
      parcel. A merchant order is a parcel, so the order API belongs with the parcel aggregate.</li>
      <li><strong>Security by construction:</strong> only merchant-service holds the Keycloak admin account, and machine
      clients receive no more than the <code>MERCHANT_API</code> role.</li>
    </ul>
  </div>
  <div>
    <h4>Tiers</h4>
    <dl class="layers">
      <dt>Client</dt><dd>Merchant portal, admin approvals (Next.js), and the merchant's own system</dd>
      <dt>Transport</dt><dd>Next.js BFF and Spring Cloud Gateway: one entry point, JWT validation</dd>
      <dt>Application</dt><dd>merchant-service and parcel-service (api → application → domain → infrastructure)</dd>
      <dt>Data</dt><dd>PostgreSQL <code>merchants_db</code> and <code>parcels_db</code>, Kafka topics</dd>
    </dl>
    <p class="small">Trade-off accepted: the status cache is eventually consistent, so a suspension reaches the order API
    moments after it is applied.</p>
  </div>
</div>"""),
    ("integration", "Integration design", ["04-integration-design"], """
<div class="table-wrap"><table>
<thead><tr><th>#</th><th>Pattern</th><th>Where used</th><th>Why</th></tr></thead>
<tbody>
<tr><td>1</td><td>Transactional outbox</td><td>merchant-service and parcel-service</td><td>The row and its event commit together, so no status change or order is lost or invented.</td></tr>
<tr><td>2</td><td>Publish/subscribe (broker)</td><td>Kafka <code>swiftdrop.merchant.lifecycle.v1</code>, <code>swiftdrop.parcel.created.v1</code></td><td>One change feeds several consumers without the producer knowing them.</td></tr>
<tr><td>3</td><td>Idempotent consumer</td><td><code>processed_event</code> table</td><td>Kafka delivers at least once; a repeated event must change nothing.</td></tr>
<tr><td>4</td><td>Event-carried state transfer</td><td><code>merchant_status_cache</code> in parcel-service</td><td>The order API can check APPROVED without a synchronous call.</td></tr>
<tr><td>5</td><td>Webhook callback</td><td>webhook-service to the merchant (M8)</td><td>Merchants learn of changes without polling; retries absorb their outages.</td></tr>
<tr><td>6</td><td>OAuth 2.0 client credentials</td><td>Merchant system to Keycloak</td><td>A machine has no user to log in, and Keycloak can rotate or revoke its access centrally.</td></tr>
<tr><td>7</td><td>API gateway with JWT</td><td>Spring Cloud Gateway</td><td>One protected entry point with per-client rate limits; each service validates the token again.</td></tr>
<tr><td>8</td><td>Idempotent receiver</td><td><code>Idempotency-Key</code> on order creation</td><td>A store can safely retry after a timeout without creating a second parcel.</td></tr>
<tr><td>9</td><td>Request/reply with circuit breaker</td><td>parcel-service to pricing-service</td><td>The order needs a fee before it is accepted, and a pricing outage must fail fast.</td></tr>
<tr><td>10</td><td>Adapter over request/reply</td><td><code>KeycloakAdminAdapter</code></td><td>Provisioning needs an immediate answer, and the application layer stays independent of Keycloak.</td></tr>
</tbody></table></div>"""),
    ("flows", "Key flows", ["05-seq-register", "06-seq-approve", "07-seq-merchant-api-order", "08-seq-rotate-secret",
                            "09-seq-orders-dashboard", "10-activity-order-request", "11-state-merchant"], """
<p>Figures 5 and 6 take a merchant from application to approval. Figure 7 is the required merchant API order flow, and
figure 10 shows every decision made on that request. The outbox relay runs after the response, so publishing never
delays the caller.</p>"""),
    ("api-design", "API design note", [], """
<div class="two-col">
  <div>
    <h4>Versioning</h4>
    <p>Every route starts with <code>/api/v1</code>. Compatible additions, such as a new optional field, stay in v1; a
    breaking change gets <code>/api/v2</code>, and v1 runs alongside it while merchants migrate. Their integrations ship
    on their own schedule, so a version can never change under them. Events are versioned the same way, in the topic
    name and the <code>eventVersion</code> field.</p>
    <h4>Idempotency</h4>
    <p>Order creation requires an <code>Idempotency-Key</code> header. The record is keyed on merchant and key, so two
    merchants can never collide, and it stores a SHA-256 hash of the body. The same key and body return the original
    parcel with 200; the same key with a different body returns 409. The record commits with the parcel, and its
    primary key settles a race between two identical requests.</p>
  </div>
  <div>
    <h4>Client credentials</h4>
    <p>Each approved merchant gets one confidential Keycloak client, <code>merchant-&lt;id&gt;</code>. Its token carries
    the audience <code>swiftdrop-api</code>, the role <code>MERCHANT_API</code> and a hard-coded <code>merchant_id</code>
    claim, so a client cannot act for another merchant. The secret is shown once and exists only in Keycloak. Rotating
    replaces it at once, which also ends a leaked secret.</p>
    <h4>Errors and validation</h4>
    <p>Errors use RFC 9457 <code>application/problem+json</code> with a <code>traceId</code>, and never contain stack
    traces or personal data. Bean Validation checks every body. A row owned by another merchant returns 404, so its
    existence is not revealed. Phone numbers and addresses are encrypted at rest, and logs are masked.</p>
  </div>
</div>
<div class="table-wrap"><table>
<thead><tr><th>Endpoint</th><th>Caller</th><th>Main responses</th></tr></thead>
<tbody>
<tr><td>POST /api/v1/merchants/register</td><td>Any signed-in user</td><td>201 PENDING · 409 duplicate BR number</td></tr>
<tr><td>GET /api/v1/merchants/me</td><td>Owner</td><td>200 profile and status · 404</td></tr>
<tr><td>POST /api/v1/merchants/me/credentials/rotate</td><td>MERCHANT</td><td>200 new secret, shown once</td></tr>
<tr><td>GET /api/v1/admin/merchants?status=</td><td>ADMIN</td><td>200 applications</td></tr>
<tr><td>POST /api/v1/admin/merchants/{{id}}/approve · reject · suspend</td><td>ADMIN</td><td>200 · 403 non-admin · 422 wrong state</td></tr>
<tr><td>POST /api/v1/merchant/orders</td><td>MERCHANT_API</td><td>201 created · 200 replay · 403 · 409 · 422</td></tr>
<tr><td>GET /api/v1/merchant/orders</td><td>MERCHANT or MERCHANT_API</td><td>200 page of the caller's own orders</td></tr>
</tbody></table></div>"""),
    ("patterns", "Design patterns and principles", ["12-class-merchant-service", "13-class-merchant-orders"], """
<div class="table-wrap" style="break-before: page"><table>
<thead><tr><th>Pattern</th><th>Applied to</th></tr></thead>
<tbody>
<tr><td>Adapter</td><td><code>KeycloakAdminAdapter</code> and <code>PricingServiceClient</code> put external systems behind application ports.</td></tr>
<tr><td>State</td><td>The <code>Merchant</code> class permits only the lifecycle moves in figure 11 and refuses all others.</td></tr>
<tr><td>Repository and ports</td><td><code>MerchantRepository</code> and <code>IdempotencyStore</code> hide persistence from the application layer.</td></tr>
<tr><td>Strategy-style policy</td><td><code>IdempotencyPolicy</code> holds the NEW, REPLAY or CONFLICT rule as one pure decision.</td></tr>
<tr><td>Observer / pub-sub</td><td>Notification, webhook and parcel consumers react to <code>MerchantLifecycleChanged</code>.</td></tr>
<tr><td>Outbox and idempotent consumer</td><td>Shared library <code>swiftdrop-outbox</code>: reliable publishing and safe re-delivery.</td></tr>
<tr><td>Circuit breaker</td><td>Resilience4j on the pricing call, so an outage returns 503 quickly.</td></tr>
<tr><td>Facade / BFF</td><td>Next.js route handlers keep tokens out of the browser for the portal and approval queue.</td></tr>
</tbody></table></div>
<h4>Principles</h4>
<ul class="principles">
  <li><strong>Single responsibility:</strong> controllers only validate and delegate; state rules live in
  <code>Merchant</code>; Keycloak details live in one adapter.</li>
  <li><strong>Open/closed:</strong> a new consumer of lifecycle events needs no change to merchant-service.</li>
  <li><strong>Dependency inversion:</strong> application services depend on ports, and ArchUnit enforces the layers.</li>
  <li><strong>DRY:</strong> JWT handling, errors, logging and the outbox come from the shared libraries.</li>
  <li><strong>Least privilege:</strong> machine clients hold only <code>MERCHANT_API</code>, and only merchant-service can
  manage Keycloak clients.</li>
  <li><strong>Twelve-factor configuration:</strong> Keycloak URLs and client secrets come from environment variables; no
  secret is stored in code.</li>
</ul>"""),
]


def figure(number: int, fid: str) -> str:
    svg = (BUILD / f"{fid}.svg").read_text(encoding="utf-8")
    svg, n = re.subn(r'^(<svg\b[^>]*?)\swidth="[\d.]+"\sheight="[\d.]+"', r'\1 width="100%"', svg, count=1)
    assert n == 1, f"{fid}: <svg> size attributes not found"
    return f"""
<figure class="fig" id="fig-{fid}">
  <figcaption><span class="fig-no">Figure {number}</span> {html.escape(TITLES[fid])}</figcaption>
  <div class="diagram">{svg}</div>
  <p class="caption">{html.escape(CAPTIONS[fid])}</p>
</figure>"""


def page() -> str:
    body, nav, n = [], [], 0
    for index, (anchor, heading, figure_ids, intro) in enumerate(SECTIONS, start=1):
        nav.append(f'<li><a href="#{anchor}">{html.escape(heading)}</a></li>')
        figs = []
        for fid in figure_ids:
            n += 1
            figs.append(figure(n, fid))
        intro = intro.format(author=AUTHOR) if "{author}" in intro else intro.replace("{{", "{").replace("}}", "}")
        after = anchor in ("integration", "patterns")
        content = ("".join(figs) + intro) if after else (intro + "".join(figs))
        body.append(f"""
<section id="{anchor}">
  <h2><span class="sec-no">{index}</span>{html.escape(heading)}</h2>
  {content}
</section>""")

    return document(
        f"Merchant Onboarding and API Design · {AUTHOR}", f"SwiftDrop · Design documentation · {AUTHOR}",
        "Merchant Onboarding and API",
        f"""Design of {AUTHOR}'s part: online stores register, an admin approves them, and they create delivery
    orders from their own systems with OAuth 2.0 client credentials.""",
        [("Services", "merchant-service (Spring Boot 4) · merchant orders in parcel-service"),
         ("Data", "PostgreSQL merchants_db · parcels_db · Kafka"), ("Issue", "#20")],
        nav, body,
        """Diagram sources: <code>doc/diagrams/m7/</code> in the SwiftDrop repository, with matching PNG exports. This
  document is the design for issue #20; it describes the planned behaviour. Example values are illustrative.""")
