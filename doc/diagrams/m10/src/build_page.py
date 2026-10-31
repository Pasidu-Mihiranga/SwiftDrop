"""Builds the M10 design page, embedding the hand-laid SVG figures inline."""

import html
import re
import sys
from pathlib import Path

from build_figures import CAPTIONS, FIGURES, TITLES

HERE = Path(__file__).parent

SECTIONS = [
    ("use-cases", "Use cases", ["01-use-cases"], """
<p>M10 owns two capabilities. <strong>Reporting</strong> gives dispatchers and admins delivery volumes and
success rates. The <strong>delivery assistant</strong> answers questions such as <em>"Where is my parcel?"</em> and
<em>"How much to send 2 kg to Kandy?"</em> for guests, customers and merchants, and exposes the same tools to
external MCP clients.</p>"""),
    ("data-model", "Data model", ["02-er-diagram"], """
<p>Reports are <strong>read models</strong>: reporting-service never queries another service's database. Kafka
events become two fact tables, <code>PARCEL_FACT</code> and <code>STATUS_TRANSITION</code>; the daily tables the
dashboard reads are rebuilt from them for each day an event touches, so late or out-of-order events still count
correctly. <code>PROCESSED_EVENT</code> makes event handling idempotent.</p>"""),
    ("architecture", "Architecture", ["03-architecture"], """
<p><strong>Proposal:</strong> microservices behind an API gateway, with the Next.js app acting as
backend-for-frontend (BFF). Each service owns its data; services integrate through Kafka events or through the
gateway.</p>
<div class="two-col">
  <div>
    <h4>Why this style for M10</h4>
    <ul>
      <li><strong>Independent scaling:</strong> report reads and LLM-bound chat have very different load
      profiles from parcel writes.</li>
      <li><strong>Different runtimes:</strong> Java suits transactional projections; Python has the strongest LLM
      and MCP libraries. Microservices let each use the right one.</li>
      <li><strong>Loose coupling:</strong> reporting subscribes to events, so producers never know it exists and
      a reporting outage never blocks bookings or deliveries.</li>
      <li><strong>Security by construction:</strong> the assistant has no database access of its own; it can only
      do what the user's token allows through the gateway.</li>
    </ul>
  </div>
  <div>
    <h4>Tiers</h4>
    <dl class="layers">
      <dt>Client</dt><dd>Next.js chat widget and reports dashboard</dd>
      <dt>Transport</dt><dd>Next.js BFF and Spring Cloud Gateway: one entry point, JWT validation</dd>
      <dt>Application</dt><dd>reporting-service (api → application → domain → infrastructure), ai-assistant-service (api → agent → tools)</dd>
      <dt>Data</dt><dd>PostgreSQL <code>reporting_db</code>, Redis chat memory, Kafka topics</dd>
    </dl>
    <p class="small">Trade-off accepted: reports are eventually consistent (typically under a second behind).</p>
  </div>
</div>"""),
    ("integration", "Integration design", ["04-integration-design"], """
<div class="table-wrap"><table>
<thead><tr><th>#</th><th>Pattern</th><th>Where in M10</th><th>Why</th></tr></thead>
<tbody>
<tr><td>1</td><td>Transactional outbox</td><td>parcel-service writes the event with the status change</td><td>Event and data commit together, so reports never miss or invent a delivery.</td></tr>
<tr><td>2</td><td>Publish/subscribe (broker)</td><td>Kafka topics <code>swiftdrop.parcel.*.v1</code></td><td>One status change feeds reports, notifications, webhooks and tracking without the producer knowing any of them.</td></tr>
<tr><td>3</td><td>Competing consumers</td><td>Consumer group <code>reporting-service</code></td><td>Replicas share partitions; key = parcelId keeps each parcel's events in order.</td></tr>
<tr><td>4</td><td>Idempotent consumer</td><td><code>processed_event</code> table</td><td>Kafka delivers at least once; duplicates must not inflate the numbers.</td></tr>
<tr><td>5</td><td>CQRS read model</td><td>Daily and courier stats tables</td><td>Dashboards read pre-aggregated rows instead of joining across services.</td></tr>
<tr><td>6</td><td>Request/reply (REST)</td><td><code>/api/v1/reports/**</code> via gateway</td><td>The dashboard needs an immediate answer for a chosen date range.</td></tr>
<tr><td>7</td><td>Streaming (Server-Sent Events)</td><td><code>POST /api/v1/assistant/chat</code></td><td>Users see the answer and tool activity as it is produced.</td></tr>
<tr><td>8</td><td>Request/reply with function calling</td><td>Assistant to OpenAI or Gemini</td><td>The model decides which SwiftDrop tool to call; answers stay grounded in real data.</td></tr>
<tr><td>9</td><td>Gateway + token relay</td><td>Tool calls carry the user's JWT</td><td>The assistant gets exactly the user's permissions, never more.</td></tr>
<tr><td>10</td><td>MCP (JSON-RPC over HTTP)</td><td><code>/mcp</code> server</td><td>Standard protocol so external AI clients can reuse the same tools.</td></tr>
<tr><td>11</td><td>Pseudonymization at the model boundary</td><td><code>PiiRedactor</code> between the agent and the LLM</td><td>Names, phones, emails, NIC numbers and addresses become placeholders such as <code>[PHONE_1]</code> before a request leaves SwiftDrop; the real values are restored only for the signed-in user.</td></tr>
</tbody></table></div>"""),
    ("flows", "Key flows", ["05-seq-report-projection", "06-seq-view-reports", "07-seq-where-is-my-parcel",
                            "08-seq-fee-estimate", "09-seq-mcp-client", "10-activity-agent-loop"], """
<p>Flow 5 is M10's part of the shared <em>courier status update</em> flow. Flows 7 to 10 cover the assistant,
including how a user's login (OAuth 2.0 / OIDC through Keycloak) carries through to every tool call.</p>"""),
    ("patterns", "Design patterns and principles", ["11-class-assistant", "12-class-reporting"], """
<div class="table-wrap"><table>
<thead><tr><th>Pattern</th><th>Applied to</th></tr></thead>
<tbody>
<tr><td>Strategy</td><td><code>LLMProvider</code>: OpenAI and Gemini are interchangeable; <code>LLM_PROVIDER</code> selects one at start-up.</td></tr>
<tr><td>Factory</td><td><code>provider_config(settings)</code> builds the provider from environment variables.</td></tr>
<tr><td>Adapter</td><td><code>SwiftDropApi</code> wraps gateway HTTP calls as typed tool functions; Kafka consumer and REST controller adapt the outside world to application services.</td></tr>
<tr><td>Facade</td><td><code>AgentOrchestrator</code> hides the model loop, tools, memory and guardrails behind one <code>chat()</code> call.</td></tr>
<tr><td>Observer / pub-sub</td><td>reporting-service reacts to parcel events it subscribes to.</td></tr>
<tr><td>Repository and ports</td><td><code>ProjectionStore</code> and <code>ReportStore</code> ports with JDBC adapters for reports; <code>ConversationStore</code> for Redis chat memory and the PII vault.</td></tr>
<tr><td>Proxy (privacy)</td><td><code>PiiRedactor</code> stands between the agent and the model: it pseudonymizes every outgoing message and tool result and restores the placeholders in the answer; for MCP clients it masks irreversibly.</td></tr>
<tr><td>CQRS and idempotent consumer</td><td>Write side lives in parcel-service; reporting keeps its own read model and skips duplicates.</td></tr>
<tr><td>Template method</td><td>Shared consumer pipeline: dedupe, handle, commit (libs/java and libs/node).</td></tr>
</tbody></table></div>
<h4>Principles</h4>
<ul class="principles">
  <li><strong>Single responsibility:</strong> listeners only parse and validate events; the projection rules live
  in <code>ProjectionService</code>; controllers only validate and delegate; <code>PiiRedactor</code> alone decides what
  counts as personal data.</li>
  <li><strong>Open/closed:</strong> a new LLM provider or a new MCP tool is an added class, not an edit to the agent.</li>
  <li><strong>Dependency inversion:</strong> the agent depends on <code>LLMProvider</code> and <code>ToolRegistry</code>
  interfaces; ArchUnit enforces the layer rules in reporting-service.</li>
  <li><strong>DRY:</strong> one tool registry serves both the chat and the MCP server; JWT, logging and error
  handling come from the shared libraries.</li>
  <li><strong>Least privilege:</strong> token relay means the assistant inherits the caller's rights; guests get
  public tools only.</li>
  <li><strong>Privacy by design and data minimization:</strong> tools return only the fields an answer needs, the model
  never receives raw personal data, chat memory stores placeholders only, the vault that maps them back is encrypted
  and expires with the conversation, and logs are masked.</li>
  <li><strong>Twelve-factor configuration:</strong> provider, model and keys come from environment variables; no
  secret is stored in code.</li>
</ul>"""),
]


def inline(svg: str, diagram_id: str) -> str:
    """Drop the fixed size of an exported SVG so CSS sizes it from its viewBox."""
    svg, n = re.subn(r'^(<svg\b[^>]*?)\swidth="[\d.]+"\sheight="[\d.]+"', r'\1 width="100%"', svg, count=1)
    assert n == 1, f"{diagram_id}: <svg> size attributes not found"
    return svg


def figure(number: int, diagram_id: str, title: str = None, caption: str = None, svg: str = None,
           element_id: str = None) -> str:
    svg = inline(svg or (HERE / "bw" / f"{diagram_id}.svg").read_text(encoding="utf-8"), diagram_id)
    title = TITLES[diagram_id] if title is None else title
    caption = CAPTIONS[diagram_id] if caption is None else caption
    cap = f'\n  <p class="caption">{html.escape(caption)}</p>' if caption else ""
    return f"""
<figure class="fig" id="{element_id or 'fig-' + diagram_id}">
  <figcaption><span class="fig-no">Figure {number}</span> {html.escape(title)}</figcaption>
  <div class="diagram">{svg}</div>{cap}
</figure>"""


def page() -> str:
    body, nav, n = [], [], 0
    for index, (anchor, heading, figure_ids, intro) in enumerate(SECTIONS, start=1):
        nav.append(f'<li><a href="#{anchor}">{html.escape(heading)}</a></li>')
        figs = []
        for fid in figure_ids:
            n += 1
            figs.append(figure(n, fid))
        after = anchor in ("integration", "patterns")
        content = ("".join(figs) + intro) if after else (intro + "".join(figs))
        body.append(f"""
<section id="{anchor}">
  <h2><span class="sec-no">{index}</span>{html.escape(heading)}</h2>
  {content}
</section>""")

    return document(
        "M10 Reporting and AI Design", "SwiftDrop · Design documentation · Member M10", "Reporting and AI Assistant",
        """Design of the two M10 services: event-driven delivery reports for dispatchers and admins, and a
    delivery assistant that answers customers by calling SwiftDrop's own APIs, also available as MCP tools.""",
        [("Services", "reporting-service (Spring Boot 4) · ai-assistant-service (FastAPI)"),
         ("Data", "PostgreSQL reporting_db · Redis · Kafka"), ("Issue", "#23")],
        nav, body,
        """Diagram sources: <code>doc/diagrams/m10/*.svg</code> in the SwiftDrop repository, with matching PNG
  exports for the design PDF. Example values (fees, tracking numbers) are illustrative.""")


def document(title, eyebrow, h1, lede, meta, nav, body, footer) -> str:
    """The page skeleton shared by every member's design page."""
    meta_html = "\n      ".join(f"<span><b>{k}</b> {v}</span>" for k, v in meta)
    return f"""<title>{title}</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Archivo:wght@500;700;800&family=Source+Sans+3:ital,wght@0,400;0,600;1,400&family=JetBrains+Mono:wght@400;500&display=swap">
<style>
/* Layout: one reading column; figures break out wider. Diagrams are black and white, so the page
   stays neutral and lets the drawings carry the structure. */
:root {{
  --bg: #f5f6f8;
  --surface: #ffffff;
  --ink: #15181d;
  --muted: #5b6372;
  --line: #d9dde4;
  --accent: #15181d;
  --accent-soft: #eceef2;
  --font-display: "Archivo", "Segoe UI", system-ui, sans-serif;
  --font-body: "Source Sans 3", "Segoe UI", system-ui, sans-serif;
  --font-mono: "JetBrains Mono", ui-monospace, Consolas, monospace;
}}
@media (prefers-color-scheme: dark) {{
  :root:not([data-theme="light"]) {{
    --bg: #0e1116; --surface: #161a21; --ink: #e8ecf2; --muted: #9aa3b2; --line: #272d38;
    --accent: #e8ecf2; --accent-soft: #1d222b; color-scheme: dark;
  }}
}}
:root[data-theme="dark"] {{
  --bg: #0e1116; --surface: #161a21; --ink: #e8ecf2; --muted: #9aa3b2; --line: #272d38;
  --accent: #e8ecf2; --accent-soft: #1d222b; color-scheme: dark;
}}
* {{ box-sizing: border-box; }}
body {{
  background: var(--bg); color: var(--ink); font: 400 1.0625rem/1.6 var(--font-body);
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px));
  padding-block: 0 4rem;
}}
.page {{ max-width: 1180px; margin: 0 auto; display: grid; gap: 3.5rem; }}
header.hero {{ padding-block: 3rem 1rem; display: grid; gap: 1rem; border-bottom: 1px solid var(--line); }}
.eyebrow {{ font: 600 0.78rem/1 var(--font-mono); letter-spacing: 0.08em; text-transform: uppercase; color: var(--muted); }}
h1 {{ font: 800 clamp(2rem, 5vw, 3.2rem)/1.05 var(--font-display); letter-spacing: -0.02em; margin: 0; text-wrap: balance; }}
.lede {{ max-width: 68ch; color: var(--muted); margin: 0; font-size: 1.15rem; }}
.meta {{ display: flex; flex-wrap: wrap; gap: 0.5rem 1.5rem; font-size: 0.95rem; color: var(--muted); }}
.meta b {{ color: var(--ink); font-weight: 600; }}
nav.toc ol {{ list-style: none; padding: 0; margin: 0; display: flex; flex-wrap: wrap; gap: 0.5rem; counter-reset: toc; }}
nav.toc li {{ counter-increment: toc; }}
nav.toc a {{ display: inline-flex; gap: 0.5rem; padding: 0.35rem 0.8rem; border: 1px solid var(--line); border-radius: 999px;
  color: var(--ink); text-decoration: none; font-size: 0.95rem; background: var(--surface); }}
nav.toc a::before {{ content: counter(toc); font-family: var(--font-mono); color: var(--muted); }}
nav.toc a:hover, nav.toc a:focus-visible {{ border-color: var(--ink); outline: none; }}
section {{ display: grid; gap: 1.25rem; scroll-margin-top: 1rem; }}
section > p, section > ul {{ max-width: 72ch; margin: 0; }}
h2 {{ font: 700 1.7rem/1.2 var(--font-display); margin: 0; display: flex; align-items: baseline; gap: 0.75rem; text-wrap: balance; }}
.sec-no {{ font: 500 1rem/1 var(--font-mono); color: var(--muted); }}
h4 {{ font: 700 1.05rem/1.3 var(--font-display); margin: 0 0 0.5rem; }}
code {{ font: 0.88em var(--font-mono); background: var(--accent-soft); padding: 0.05em 0.35em; border-radius: 4px; }}
.fig {{ margin: 0; display: grid; gap: 0.75rem; background: var(--surface); border: 1px solid var(--line); border-radius: 10px; padding: 1.25rem; min-width: 0; }}
.fig figcaption {{ font: 700 1.1rem/1.3 var(--font-display); display: flex; gap: 0.6rem; align-items: baseline; flex-wrap: wrap; }}
.fig-no {{ font: 500 0.8rem/1 var(--font-mono); letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); }}
.diagram {{ overflow-x: auto; background: #ffffff; border-radius: 6px; padding: 0.75rem; }}
.diagram svg {{ display: block; min-width: 680px; height: auto; }}
.caption {{ margin: 0; color: var(--muted); font-size: 0.97rem; max-width: 80ch; }}
.two-col {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1.5rem; }}
.two-col > div {{ min-width: 0; }}
.two-col ul, .principles {{ margin: 0; padding-left: 1.2rem; display: grid; gap: 0.45rem; }}
dl.layers {{ display: grid; grid-template-columns: max-content 1fr; gap: 0.4rem 1rem; margin: 0 0 0.75rem; }}
dl.layers dt {{ font-weight: 600; }}
dl.layers dd {{ margin: 0; color: var(--muted); }}
.small {{ font-size: 0.93rem; color: var(--muted); margin: 0; }}
.table-wrap {{ overflow-x: auto; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); }}
table {{ border-collapse: collapse; width: 100%; font-size: 0.97rem; }}
th, td {{ text-align: left; padding: 0.65rem 0.9rem; border-bottom: 1px solid var(--line); vertical-align: top; }}
th {{ font: 600 0.78rem/1.2 var(--font-mono); text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }}
tbody tr:last-child td {{ border-bottom: 0; }}
td:first-child {{ font-family: var(--font-mono); color: var(--muted); white-space: nowrap; }}
footer {{ color: var(--muted); font-size: 0.9rem; border-top: 1px solid var(--line); padding-top: 1rem; }}
@media (prefers-reduced-motion: reduce) {{ html {{ scroll-behavior: auto; }} }}
</style>

<div class="page">
  <header class="hero">
    <span class="eyebrow">{eyebrow}</span>
    <h1>{h1}</h1>
    <p class="lede">{lede}</p>
    <div class="meta">
      {meta_html}
    </div>
    <nav class="toc" aria-label="Contents"><ol>{"".join(nav)}</ol></nav>
  </header>
  {"".join(body)}
  <footer>{footer}</footer>
</div>
"""


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "m10-design.html"
    out.write_text(page(), encoding="utf-8", newline="\n")
    print("wrote", out, f"({out.stat().st_size // 1024} KB)")
