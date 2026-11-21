"""Prints a Markdown design document to a PDF beside it, in the layout of the team's design report.

    python scripts/final-report/md2pdf.py doc/design/m1-identity-access-design.md [more.md ...]

Uses the same stylesheet and printer as build.py (report.css, printkit.mjs). Relative image links
resolve against the Markdown file's folder.
"""
import html
import pathlib
import re
import subprocess
import sys

import markdown

R = pathlib.Path(__file__).resolve().parent
REPO = R.parents[1]
BUILD = REPO / "build" / "final-report"


def to_html(md_path):
    text = md_path.read_text(encoding="utf-8")
    body = markdown.markdown(text, extensions=["tables", "fenced_code", "sane_lists", "attr_list"])
    base = md_path.parent.resolve()

    def absolute(m):
        src = m.group(2)
        if re.match(r"^[a-z]+:", src):
            return m.group(0)
        return f'{m.group(1)}{(base / src).resolve().as_uri()}"'

    body = re.sub(r'(<img[^>]*?src=")([^"]+)"', absolute, body)
    # Wide figures in a design document read better as figures with a caption from the alt text.
    body = re.sub(r'<p>(<img[^>]*alt="([^"]*)"[^>]*>)</p>', r"<figure>\1<figcaption>\2</figcaption></figure>", body)
    title = re.search(r"^# (.+)$", text, re.M)
    css = (R / "report.css").read_text(encoding="utf-8")
    return (f'<!doctype html><html><head><meta charset="utf-8"><title>{html.escape(title.group(1) if title else md_path.stem)}'
            f"</title><style>{css}</style></head><body>{body}</body></html>")


def main(paths):
    BUILD.mkdir(parents=True, exist_ok=True)
    for arg in paths:
        md_path = pathlib.Path(arg).resolve()
        tmp = BUILD / (md_path.stem + ".html")
        tmp.write_text(to_html(md_path), encoding="utf-8")
        out = md_path.with_suffix(".pdf")
        subprocess.run(["node", str(R / "printkit.mjs"), str(tmp), str(out)], check=True, capture_output=True)
        print(out.relative_to(REPO))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
