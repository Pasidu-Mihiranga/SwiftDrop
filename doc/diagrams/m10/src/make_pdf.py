"""Builds m10-print.html: the design page plus a print stylesheet, ready for print-to-PDF.

Print rules:
  - A4 landscape throughout, because most diagrams are wider than tall. (A portrait page for the
    tall activity diagram was tried: Edge then scales the whole document down by about 31%, so
    every page stays landscape and that diagram gets a full page instead.)
  - light theme forced; every section starts on a new page; no figure is split across pages
  - each figure is sized to the space it really has on its page, so it is as large as possible
    without being pushed onto the next page:
        with-intro    shares the page with the section heading and a short intro
        with-heading  shares the page with the section heading only
        alone         has the whole page
  - table headers repeat on every page and no row is split
"""

import re
from pathlib import Path

import build_page

HERE = Path(__file__).parent

FIT = {
    "01-use-cases": "with-intro",
    "02-er-diagram": "with-intro",
    "03-architecture": "alone",               # the architecture text fills the page before it
    "04-integration-design": "with-heading",
    "05-seq-report-projection": "with-intro",
    "06-seq-view-reports": "alone",
    "07-seq-where-is-my-parcel": "alone",
    "08-seq-fee-estimate": "alone",
    "09-seq-mcp-client": "alone",
    "10-activity-agent-loop": "alone",
    "11-class-assistant": "with-heading",
    "12-class-reporting": "alone",
}

PRINT_CSS = """
<style>
@page { size: A4 landscape; margin: 12mm 14mm 14mm;
        @bottom-right { content: "Page " counter(page) " of " counter(pages);
                        font: 9pt "Segoe UI", sans-serif; color: #5b6372; } }
* { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
html, body { background: #ffffff !important; }
body { padding: 0 !important; font-size: 10.5pt; line-height: 1.5; }
.page { max-width: none; gap: 0; }

header.hero { padding-block: 18mm 0; border-bottom: 0; gap: 5mm; }
h1 { font-size: 30pt; }
.lede { max-width: 150mm; }
nav.toc { margin-top: 6mm; }
nav.toc ol { display: grid; grid-template-columns: max-content; gap: 2.5mm; }
nav.toc a { border: 0; border-radius: 0; padding: 0; font-size: 12pt; background: none; gap: 4mm; }

section { break-before: page; gap: 4mm; }
section > p, section > ul { max-width: 175mm; }
h2 { font-size: 17pt; break-after: avoid; }
h4 { break-after: avoid; }

.fig { break-inside: avoid; padding: 4mm; border-radius: 6px; gap: 3mm; }
.fig figcaption { font-size: 12pt; break-after: avoid; }
.diagram { overflow: visible; padding: 0; }
.diagram svg { min-width: 0 !important; width: 100%; }
.fig.with-intro .diagram svg { max-height: 108mm; }
.fig.with-heading .diagram svg { max-height: 130mm; }
.fig.alone { break-before: page; }
.fig.alone .diagram svg { max-height: 148mm; }
.caption { font-size: 9.5pt; max-width: none; }

.table-wrap { overflow: visible; border: 0; border-radius: 0; background: none; }
table { font-size: 9.5pt; border: 1px solid #d9dde4; }
thead { display: table-header-group; }
tr, li, dl.layers, .two-col > div { break-inside: avoid; }
p, li { orphans: 3; widows: 3; }
header.hero footer { margin-top: 10mm; padding-top: 3mm; font-size: 9pt; max-width: 175mm; }
</style>
"""


# In the PDF the member code "M10" is replaced by the author's name. Each phrase is listed
# exactly (a blind replace would also hit SVG path commands such as "M1010 34").
AUTHOR = "Pasidu Mihiranga"
NAME_IN_PDF = [
    ("<title>M10 Reporting and AI Design</title>", f"<title>Reporting and AI Design · {AUTHOR}</title>"),
    ("Design documentation · Member M10", f"Design documentation · {AUTHOR}"),
    ("Design of the two M10 services", f"Design of {AUTHOR}'s two services"),
    ("<p>M10 owns two capabilities.", f"<p>{AUTHOR} owns two capabilities."),
    ("SwiftDrop  ·  M10 system boundary", f"SwiftDrop  ·  system boundary of {AUTHOR}'s part"),
    ("Why this style for M10", f"Why this style for {AUTHOR}'s services"),
    ("Four tiers. The M10 services sit", f"Four tiers. {AUTHOR}'s services sit"),
    ("the patterns M10 relies on", f"the patterns {AUTHOR}'s services rely on"),
    ("<th>Where in M10</th>", "<th>Where used</th>"),
    ("Flow 5 is M10's part", f"Flow 5 is {AUTHOR}'s part"),
]


def main():
    print_html(build_page.page(), FIT, NAME_IN_PDF, HERE / "m10-print.html")


def print_html(body, fit_by_id, replacements, out):
    """The design page as a print document: names swapped in, figures sized to their pages."""
    for old, new in replacements:
        assert old in body, f"phrase not found: {old}"
        body = body.replace(old, new)
    for fid, fit in fit_by_id.items():               # tag each figure with the space it has
        body, n = re.subn(rf'<figure class="fig" id="fig-{fid}">', f'<figure class="fig {fit}" id="fig-{fid}">', body)
        assert n == 1, fid
    # the closing source note would sit alone on the last page; on paper it belongs on the cover
    footer = re.search(r"<footer>.*?</footer>", body, flags=re.S).group(0)
    body = body.replace(footer, "").replace("</header>", f"  {footer}\n  </header>", 1)
    head, rest = body.split('<div class="page">', 1)
    html = ('<!doctype html>\n<html lang="en" data-theme="light">\n<head>\n<meta charset="utf-8">\n'
            '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
            + head + PRINT_CSS + '</head>\n<body>\n<div class="page">' + rest + "\n</body>\n</html>\n")
    out.write_text(html, encoding="utf-8", newline="\n")
    print("wrote", out)


if __name__ == "__main__":
    main()
