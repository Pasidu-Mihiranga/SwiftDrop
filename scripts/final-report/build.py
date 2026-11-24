"""Builds doc/SwiftDrop-Final-Report.pdf in the layout of the team's design report.

Two passes: the first PDF tells where each heading landed, the second fills the contents page.
Run from the repository root after collecting the test reports:

    python scripts/final-report/build.py

Needs PyMuPDF and Pillow, the workspace's playwright-core (pnpm install) and Google Chrome
(CHROME_PATH overrides the default location).
"""
import html
import pathlib
import re
import subprocess

import fitz
from PIL import Image

R = pathlib.Path(__file__).resolve().parent
REPO = R.parents[1]
BUILD = REPO / "build" / "final-report"          # gitignored work directory
SCREENS = REPO / "doc" / "screenshots"
OUT = REPO / "doc" / "SwiftDrop-Final-Report.pdf"
REPO_URL = "https://github.com/Pasidu-Mihiranga/SwiftDrop"
e = html.escape

MEMBERS = [
    # id, feature (as on the design report cover), member, github, index
    ("M1", "Identity and Access", "T.M. Warsavithana", "ThisukaMW", "234219R"),
    ("M2", "Pricing and Zones", "S.J. Wijewardana", "sasindu345", "234241C"),
    ("M3", "Customer Booking", "M.A.M. Pahasara", "Malshipahasara", "234151B"),
    ("M4", "Courier Operations and Status Life Cycle", "W.P.P. Welikada", "pamindu-w", "234224D"),
    ("M5", "Dispatch and Workload", "W.M.C.S. Wijekoon", "Chaniths", "234232B"),
    ("M6", "Real-Time Tracking", "K.W.A. Imalsha", "ashmax77", "234086E"),
    ("M7", "Merchant Onboarding", "K.K.G. Saputhanthri", "KavinuS", "234189X"),
    ("M8", "Webhooks and External Integration", "W.C.G.H. Fernando", "geemalfernando", "234063G"),
    ("M9", "Notification, Delivery History and Receipts", "K.P.S.M. Pathirana", "sapuni-pathirana", "234152E"),
    ("M10", "Reporting and AI Assistant", "P.M. Ilamperuma", "Pasidu-Mihiranga", "234083R"),
]
COVER_ORDER = ["M1", "M2", "M6", "M3", "M9", "M10", "M7", "M5", "M8", "M4"]  # the order of the design report's cover


def md_inline(text):
    """The few Markdown marks used in the contribution table, as HTML."""
    t = e(text.replace("**", ""))
    return re.sub(r"`([^`]+)`", r"<code>\1</code>", t)


def split_rows():
    """Backend, frontend, platform share and diagrams per member, from doc/03-team-contribution.md."""
    rows = {}
    for line in (REPO / "doc" / "03-team-contribution.md").read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\| (M\d+) \| \*\*([^*]+)\*\* \| (.*)$", line)
        if m and m.group(1) not in rows:
            cells = [c.strip() for c in m.group(3).rstrip("|").split(" | ")]
            if len(cells) >= 4:
                rows[m.group(1)] = cells[:4]
    return rows


def coverage():
    """Module rows and the end-to-end line from doc/test-reports/SUMMARY.md."""
    text = (REPO / "doc" / "test-reports" / "SUMMARY.md").read_text(encoding="utf-8")
    collected = re.search(r"Collected ([^ ]+ [^ ]+ UTC)", text)
    mods = []
    for line in text.splitlines():
        m = re.match(r"^\| ([^|]+) \| ([^|]+) \| ([^|]+) \| `([^`]+)` \|$", line)
        if m and m.group(1).strip() != "Module":
            mods.append((m.group(1).strip(), m.group(2).strip(), m.group(3).strip(), m.group(4)))
    e2e = re.search(r"Run (.+? UTC): \*\*(\d+) passed\*\*, (\d+) failed, (\d+) flaky, (\d+) skipped", text)
    skipped = re.findall(r"^- (.+)$", text.split("Skipped, with the reason")[-1], re.M) if "Skipped, with the reason" in text else []
    skipped = [re.sub(r"^(\d+)\. ", r"Demo step \1, ", s) for s in skipped]
    return (collected.group(1) if collected else "n/a"), mods, e2e, skipped


def figure(path, caption, width=None):
    style = f' style="max-width:{width}"' if width else ""
    return f'<figure><img src="{(REPO / path).as_uri()}"{style} alt="{e(caption)}"><figcaption>{e(caption)}</figcaption></figure>'


def appendix_shots():
    """Crops tall full-page screenshots to their first screen and pairs the two phone screens."""
    out = BUILD / "shots"
    out.mkdir(parents=True, exist_ok=True)
    first_screen = {"00-landing-desktop.png": 900, "07-public-quote-result.png": 1150, "15-customer-parcel-detail.png": 1096,
                    "17-ai-assistant-guest-price.png": 900, "18-ai-assistant-customer-tracking.png": 900,
                    "40-dispatcher-dispatch-board.png": 1000, "60-admin-reports.png": 1100,
                    "80-grafana-parcels-webhooks.png": 1135}
    for name, height in first_screen.items():
        im = Image.open(SCREENS / name).convert("RGB")
        im.crop((0, 0, im.width, min(height, im.height))).save(out / name)
    a = Image.open(SCREENS / "30-courier-run-sheet.png").convert("RGB")
    b = Image.open(SCREENS / "31-courier-parcel.png").convert("RGB")
    pair = Image.new("RGB", (a.width + b.width + 40, max(a.height, b.height)), "white")
    pair.paste(a, (0, 0))
    pair.paste(b, (a.width + 40, 0))
    pair.save(out / "30-31-courier.png")
    return out


def build_html(pages):
    """pages maps heading ids to page numbers ({} on the first pass)."""
    toc_items = []

    def h(level, hid, title):
        toc_items.append((level, hid, title))
        tag = {1: "h1", 2: "h2", 3: "h3"}[level]
        return f'<{tag} id="{hid}">{e(title)}</{tag}>'

    split = split_rows()
    collected, mods, e2e, skipped = coverage()
    body = []

    # ------------------------------------------------------------ 1 introduction
    body.append('<div class="page">')
    body.append(h(1, "s1", "1. Introduction"))
    body.append("<p>SwiftDrop is a parcel delivery and courier management system for Sri Lanka, built by Group 02 "
                "(Avengers) as a set of ten microservices behind an API gateway, with a Next.js web application, "
                "Keycloak identity, Kafka event integration and an AI delivery assistant. This final report collects "
                "what the submission requires in one place:</p>")
    body.append("<ol><li>the source code repository link, with the README that holds the setup instructions "
                "(Section 2);</li><li>the design documentation, as a PDF, with the overall system design summarised "
                "here (Section 3);</li><li>the test reports and the coverage summary (Section 4);</li><li>the "
                "contribution sheet listing each member's responsibilities (Section 5).</li></ol>")
    body.append("<p>Key screens of the running system are shown in the appendix. Every figure in this report is "
                "taken from the repository, so it matches the code that was submitted.</p>")

    # ------------------------------------------------------------ 2 repository
    body.append(h(1, "s2", "2. Source Code Repository"))
    body.append(h(2, "s2-1", "2.1 Repository link"))
    body.append(f'<table><tr><th style="width:32%">Repository</th><td><a href="{REPO_URL}">{REPO_URL}</a></td></tr>'
                f'<tr><th>Setup instructions</th><td><a href="{REPO_URL}#getting-started">README.md, section '
                f'"Getting started"</a></td></tr>'
                '<tr><th>Branches</th><td><code>main</code> (protected, reviewed releases) and <code>dev</code> '
                '(integration). Feature branches follow <code>feat/m&lt;N&gt;-&lt;topic&gt;</code> and merge through '
                'pull requests with green CI.</td></tr>'
                '<tr><th>Licensing of third-party material</th><td>District boundaries from geoBoundaries (ODbL, '
                'see <code>services/pricing-service/src/main/resources/geo/NOTICE.md</code>); technology marks from '
                'Simple Icons (CC0).</td></tr></table>')
    body.append('<div class="keep">' + h(2, "s2-2", "2.2 Repository structure"))
    body.append("<table><tr><th style='width:32%'>Path</th><th>Contents</th></tr>" + "".join(
        f"<tr><td><code>{a}</code></td><td>{b}</td></tr>" for a, b in [
            ("apps/web", "Next.js 16 web application for every role, and the BFF that keeps tokens on the server"),
            ("services/*", "user, merchant, pricing, parcel, dispatch, reporting (Spring Boot 4); tracking, notification, "
                           "webhook (NestJS); ai-assistant (FastAPI + MCP); api-gateway (Spring Cloud Gateway)"),
            ("libs/java, libs/node, libs/python", "Shared libraries: outbox, events, security, logging, PII encryption"),
            ("contracts", "OpenAPI contract per service and Kafka event JSON Schemas"),
            ("infra", "Keycloak realm and theme, database init, Kafka topics, observability, Helm charts"),
            ("tests/e2e", "Playwright end-to-end demo suite"),
            ("doc", "Design documentation, diagrams, screenshots, test reports, this report"),
        ]) + "</table></div>")
    body.append(h(2, "s2-3", "2.3 Setup instructions"))
    body.append("<p>The README gives the full instructions; the essentials are reproduced here.</p>")
    body.append("<table><tr><th>Tool</th><th>Version</th><th>Used for</th></tr>" + "".join(
        f"<tr><td>{a}</td><td>{b}</td><td>{c}</td></tr>" for a, b, c in [
            ("Docker Desktop", "4.x, 8 GB RAM or more", "infrastructure and containers"), ("Java (Temurin)", "21", "Spring Boot services"),
            ("Maven", "3.9+", "Java build"), ("Node.js", "22.12+", "NestJS services and the web app"),
            ("pnpm", "10 (corepack enable)", "Node workspace"), ("uv", "0.11+", "Python AI service")]) + "</table>")
    body.append("<pre>git clone " + REPO_URL + ".git && cd SwiftDrop\n"
                "pnpm install            # Node workspace\n"
                "pnpm env:init           # creates .env with freshly generated secrets\n"
                "pnpm infra:up           # Postgres, Keycloak, Kafka (+topics), Mongo, Redis, RustFS, Mailpit\n"
                "pnpm stack:up           # everything in containers: infra + observability + apps\n"
                "open http://localhost:3000</pre>")
    body.append("<table><tr><th>What</th><th>Address</th></tr>" + "".join(
        f"<tr><td>{a}</td><td>{b}</td></tr>" for a, b in [
            ("Web application", "http://localhost:3000"), ("API gateway", "http://localhost:8080 (configurable)"),
            ("Keycloak", "http://localhost:8180"), ("Grafana", "http://localhost:3001"), ("Mailpit", "http://localhost:8026"),
            ("Merchant simulator", "http://localhost:4010")]) + "</table>")
    body.append("<p>Six demo accounts are seeded in the Keycloak realm (admin, dispatcher, two couriers, customer, "
                "merchant), all with the password in <code>KC_SEED_USER_PASSWORD</code> (default "
                "<code>Swift@2026</code>). Optional integrations, each switched on by one setting in <code>.env</code>: "
                "an LLM key for the assistant, Google sign-in, OSRM road routing and a geocoder for address search.</p>")
    body.append(h(2, "s2-4", "2.4 Deployment"))
    body.append("<p>Docker Compose profiles run the whole system locally. For Kubernetes, <code>infra/helm</code> "
                "holds a library chart, one chart per service and an umbrella chart with datastores and an NGINX "
                "ingress; <code>SWIFTDROP_PROFILE=full ./scripts/kind-deploy.sh</code> installs every service on kind, "
                "and the <code>helm-e2e</code> workflow does the same in CI. <code>release.yml</code> builds every "
                "image, scans it with Trivy and publishes it to the GitHub Container Registry.</p>")

    # ------------------------------------------------------------ 3 design
    body.append('<div class="page"></div>')
    body.append(h(1, "s3", "3. Design Documentation"))
    body.append("<p>The design documentation is submitted as a PDF, <code>doc/design/SwiftDrop-Design-Documentation.pdf"
                "</code> (150 pages): the overall system design followed by each of the ten features, each with use "
                "cases, data model, architecture, integration design, key flows, and design patterns and principles. "
                "The members' own design documents and every diagram are kept in the repository as listed below.</p>")
    body.append(h(2, "s3-1", "3.1 Documents"))
    docs = [
        ("Team design report", "doc/design/SwiftDrop-Design-Documentation.pdf", "whole system and all features"),
        ("M1 Identity and Access", "doc/design/m1-identity-access-design.pdf", "design section"),
        ("M2 Pricing and Zones", "doc/05-m2-design-notes.md", "design notes and tracker"),
        ("M3 Customer Booking", "doc/design/m3-customer-booking-design.pdf", "design document"),
        ("M5 Dispatch and Workload", "doc/design/m5-dispatch-workload-design.pdf", "design document"),
        ("M7 Merchant Onboarding", "doc/design/m7-merchant-onboarding-design.pdf", "design document and monitoring note"),
        ("M9 Notifications and Receipts", "doc/design/m9-notifications-receipts-design.pdf", "design document"),
        ("M10 Reporting and AI Assistant", "doc/design/m10-reporting-ai-assistant-design.pdf", "design document"),
        ("Diagrams", "doc/diagrams/README.md", "every figure, system and per feature, in one template"),
        ("Contracts", "contracts/openapi, contracts/events", "REST contracts and event schemas"),
    ]
    body.append("<table><tr><th>Document</th><th>Location</th><th>Contents</th></tr>" + "".join(
        f"<tr><td>{a}</td><td><code>{b}</code></td><td>{c}</td></tr>" for a, b, c in docs) + "</table>")
    body.append(h(2, "s3-2", "3.2 System architecture"))
    body.append("<p>Requests enter through the Next.js BFF, which keeps the access token on the server, and the API "
                "gateway, which validates the Keycloak JWT, routes, rate-limits per caller and upgrades WebSockets. "
                "Each service owns its store and the services integrate through Kafka. Only three services call "
                "outside systems: e-mail and SMS, signed merchant webhooks, and the LLM provider.</p>")
    body.append(figure("doc/diagrams/system/system-architecture.png", "Figure 1. System architecture: every service, store and integration"))
    body.append(h(2, "s3-3", "3.3 Event-driven integration"))
    body.append("<p>Seven Kafka topics connect the services. A change and its event are written in one transaction "
                "(transactional outbox), relayed in key order, and applied by idempotent consumers; each consumer "
                "has its own dead-letter topic.</p>")
    body.append(figure("doc/diagrams/system/system-event-flow.png", "Figure 2. Kafka topics, producers and consumers"))
    body.append(h(2, "s3-4", "3.4 Deployment"))
    body.append(figure("doc/diagrams/system/system-deployment.png", "Figure 3. Build, release and run"))
    body.append(h(2, "s3-5", "3.5 Feature designs"))
    folders = {"M1": "m1-identity-access", "M2": "m2-pricing-zones", "M3": "m3-customer-booking", "M4": "m4-courier-operations",
               "M5": "m5-dispatch-workload", "M6": "m6-real-time-tracking", "M7": "m7-merchant-onboarding",
               "M8": "m8-webhooks-integration", "M9": "m9-notifications-receipts", "M10": "m10-reporting-ai-assistant"}
    rows = []
    for mid, feat, *_ in MEMBERS:
        n = len(list((REPO / "doc" / "diagrams" / folders[mid]).glob("*.png")))
        rows.append(f"<tr><td>{mid}</td><td>{e(feat)}</td><td><code>doc/diagrams/{folders[mid]}</code></td><td class='num'>{n}</td></tr>")
    body.append("<table><tr><th>#</th><th>Feature</th><th>Diagrams</th><th class='num'>Figures</th></tr>" + "".join(rows) + "</table>")
    body.append(h(2, "s3-6", "3.6 Design patterns and principles"))
    pats = [
        ("Backend for frontend", "the web server holds the session and calls the gateway; no token reaches the browser"),
        ("API gateway", "one entry point for JWT validation, routing, rate limits and 503 handling"),
        ("Database per service", "six PostgreSQL databases, three MongoDB databases, Redis; foreign keys never cross services"),
        ("Transactional outbox and idempotent consumer", "events are never lost or applied twice"),
        ("CQRS read models", "dispatch, tracking, reporting and webhooks keep projections built from events"),
        ("Strategy and decorator", "the pricing fee pipeline and the notification channels"),
        ("State machine", "the parcel lifecycle; invalid transitions are refused with 422"),
        ("Circuit breaker", "parcel-service to pricing-service, and the routing and geocoding clients"),
        ("Layered and hexagonal services", "api, application, domain, infrastructure, enforced by ArchUnit tests"),
        ("Security by default", "Keycloak roles at the gateway and in each service, PII encrypted at rest and masked in logs"),
    ]
    body.append("<table><tr><th style='width:34%'>Pattern</th><th>Where and why</th></tr>" + "".join(
        f"<tr><td>{a}</td><td>{b}</td></tr>" for a, b in pats) + "</table>")

    # ------------------------------------------------------------ 4 tests
    body.append('<div class="page"></div>')
    body.append(h(1, "s4", "4. Test Reports and Coverage Summary"))
    body.append(h(2, "s4-1", "4.1 Test strategy"))
    body.append("<ul><li><b>Unit tests</b> for domain rules, application services, controllers and UI logic (JUnit 5, "
                "Jest, Vitest with Testing Library, pytest).</li><li><b>Integration tests</b> against real "
                "PostgreSQL, MongoDB, Redis, Kafka, Keycloak, Mailpit and RustFS in Testcontainers.</li><li><b>Contract "
                "and architecture tests</b>: consumer contract tests between services, ArchUnit layering rules.</li>"
                "<li><b>End-to-end tests</b>: a Playwright suite drives the running system through the browser and the "
                "API, and the Helm workflow installs the whole system on kind and runs a scripted demo.</li></ul>")
    body.append(h(2, "s4-2", "4.2 Coverage by module"))
    body.append(f"<p>Collected {e(collected)} by <code>scripts/collect-test-reports.sh</code> after running every suite. "
                "The gate is 70% line coverage per module, enforced by JaCoCo in the Java build and by the coverage "
                "thresholds of the Node and Python suites.</p>")
    body.append("<table><tr><th>Module</th><th class='num'>Lines</th><th class='num'>Branches</th><th>HTML report</th></tr>" + "".join(
        f"<tr><td>{e(m)}</td><td class='num'>{e(l)}</td><td class='num'>{e(b)}</td><td class='small'><code>{e(p)}</code></td></tr>"
        for m, l, b, p in mods) + "</table>")
    body.append(h(2, "s4-3", "4.3 End-to-end suite"))
    if e2e:
        body.append(f"<p>Run {e(e2e.group(1))}: <b>{e2e.group(2)} passed</b>, {e2e.group(3)} failed, {e2e.group(4)} flaky, "
                    f"{e2e.group(5)} skipped. HTML report: <code>doc/test-reports/e2e/html/index.html</code>; JUnit: "
                    "<code>doc/test-reports/e2e/junit.xml</code>.</p>")
        if skipped:
            body.append("<p>Skipped tests, with the reason the suite gave:</p><ul>" + "".join(f"<li>{md_inline(s)}</li>" for s in skipped) + "</ul>")
    body.append(h(2, "s4-4", "4.4 Quality gates in CI"))
    body.append("<table><tr><th style='width:34%'>Workflow</th><th>Checks</th></tr>" + "".join(
        f"<tr><td><code>{a}</code></td><td>{b}</td></tr>" for a, b in [
            ("ci-java.yml", "mvn verify: unit and Testcontainers tests, JaCoCo gate"),
            ("ci-node.yml", "typecheck, tests with coverage and the production build of every Node package"),
            ("ci-python.yml", "pytest with coverage for the shared library and the assistant"),
            ("ci-platform.yml", "compose configuration, Keycloak realm and contract files"),
            ("helm.yml, helm-e2e.yml", "chart lint and render; install on kind and run the scripted demo"),
            ("release.yml", "image build, Trivy scan, publication with provenance and SBOM"),
            ("no-emoji.yml", "repository policy check")]) + "</table>")

    # ------------------------------------------------------------ 5 contribution
    body.append('<div class="page"></div>')
    body.append(h(1, "s5", "5. Contribution Sheet"))
    body.append("<p>Each member owned one feature end to end: the backend service or module, its web pages, its tests, "
                "its deployment settings, its Grafana panels and its diagrams, plus one share of the platform work. "
                "Index numbers are those on the cover of the design report.</p>")
    body.append("<table><tr><th>#</th><th>Feature</th><th>Member</th><th>GitHub</th><th>Index number</th></tr>" + "".join(
        f"<tr><td>{mid}</td><td>{e(feat)}</td><td>{e(name) or '-'}</td><td>{e(gh)}</td><td>{idx}</td></tr>"
        for mid, feat, name, gh, idx in MEMBERS) + "</table>")
    body.append(h(2, "s5-1", "5.1 Responsibilities"))
    for mid, feat, name, gh, idx in MEMBERS:
        backend, frontend, platform, diagrams = split.get(mid, ["", "", "", ""])
        who = f"{name} ({gh})" if name else gh
        body.append(f'<h3>{mid} &middot; {e(feat)} &middot; {e(who)} &middot; {idx}</h3>')
        body.append("<table>"
                    f"<tr><th style='width:22%'>Backend</th><td>{md_inline(backend)}</td></tr>"
                    f"<tr><th>Frontend</th><td>{md_inline(frontend)}</td></tr>"
                    f"<tr><th>Platform share</th><td>{md_inline(platform)}</td></tr>"
                    f"<tr><th>Diagrams</th><td>{md_inline(diagrams)} (<code>doc/diagrams/{folders[mid]}</code>)</td></tr>"
                    "</table>")
    body.append("<p class='small'>Shared rules for every member: unit and integration tests for their own code (70% "
                "or more), their own Kafka topics in <code>contracts/events</code> and OpenAPI contract, feature "
                "branches named <code>feat/m&lt;N&gt;-&lt;topic&gt;</code>, and their own section in the design "
                "report. The detailed record of what each member delivered is <code>doc/03-team-contribution.md</code>.</p>")

    # ------------------------------------------------------------ appendix
    body.append('<div class="page"></div>')
    body.append(h(1, "s6", "6. Appendix: Key Screens"))
    SH = appendix_shots()
    shots = [("00-landing-desktop.png", "Landing page"), ("07-public-quote-result.png", "Price calculator with a live quote"),
             ("17-ai-assistant-guest-price.png", "AI delivery assistant answering a guest price question"),
             ("18-ai-assistant-customer-tracking.png", "AI delivery assistant tracking a customer's parcel"),
             ("15-customer-parcel-detail.png", "Parcel detail with live timeline"),
             ("30-31-courier.png", "Courier run sheet and parcel screen on a phone"),
             ("40-dispatcher-dispatch-board.png", "Dispatch board"), ("60-admin-reports.png", "Delivery reports"),
             ("80-grafana-parcels-webhooks.png", "Grafana dashboard")]
    body.append("".join(
        f'<figure class="shot"><img src="{(SH / f).as_uri()}" alt="{e(c)}"><figcaption>Figure {i + 4}. {e(c)}</figcaption></figure>'
        for i, (f, c) in enumerate(shots)))
    body.append("<p class='small'>All 52 screenshots: <code>doc/screenshots</code>.</p>")
    body.append("</div>")

    # ------------------------------------------------------------ cover and contents
    by_id = {m[0]: m for m in MEMBERS}
    cover = ['<div class="cover"><div class="title">SWIFTDROP</div>',
             '<div class="subtitle">Parcel Delivery &amp; Courier Management System</div>',
             '<div class="doc">FINAL REPORT</div><div class="group">Group 02 - Avengers</div>',
             "<table><tr><th>Feature</th><th>Index Number</th></tr>"]
    for mid in COVER_ORDER:
        _, feat, _, _, idx = by_id[mid]
        cover.append(f"<tr><td>{e(feat)}</td><td>{idx}</td></tr>")
    cover.append('</table><div class="faculty">Faculty of Information Technology<br>University of Moratuwa</div></div>')
    toc = ['<div class="page toc"><h1>Table of Contents</h1><ol>']
    for level, hid, title in toc_items:
        p = pages.get(hid, "")
        toc.append(f'<li class="l{level}"><span class="t">{e(title)}</span><span class="dots"></span><span>{p}</span></li>')
    toc.append("</ol></div>")
    css = (R / "report.css").read_text(encoding="utf-8")
    return (f'<!doctype html><html><head><meta charset="utf-8"><title>SwiftDrop Final Report</title><style>{css}</style></head>'
            f'<body>{"".join(cover)}{"".join(toc)}{"".join(body)}</body></html>'), toc_items


def render(pages):
    doc_html, items = build_html(pages)
    BUILD.mkdir(parents=True, exist_ok=True)
    tmp = BUILD / "final-report.html"
    tmp.write_text(doc_html, encoding="utf-8")
    subprocess.run(["node", str(R / "printkit.mjs"), str(tmp), str(OUT)], check=True, capture_output=True)
    return items


items = render({})
pdf = fitz.open(OUT)
pages = {}
texts = [pdf[i].get_text() for i in range(pdf.page_count)]
for level, hid, title in items:
    for i in range(2, len(texts)):            # skip the cover and the contents page
        if title.upper() in texts[i].upper().replace("\n", " "):
            pages[hid] = i + 1
            break
pdf.close()
render(pages)
print("pages", fitz.open(OUT).page_count, "headings placed", len(pages), "of", len(items))
