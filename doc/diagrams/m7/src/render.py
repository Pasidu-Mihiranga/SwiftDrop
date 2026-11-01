"""Rebuilds everything: figures, geometry check, PNGs, and doc/m7-design.pdf. Needs Google Chrome,
Pillow and websocket-client. Run it from any folder: python render.py"""

import base64
import json
import shutil
import subprocess
import sys
import time
import urllib.request

import paths
import build_figures
import check
import draw
import make_pdf
import m7_page
import websocket
from PIL import Image, ImageChops

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
PORT = 9334
BUILD = paths.HERE / "build"
OUT = paths.HERE.parent
PDF = OUT.parent.parent / "m7-design.pdf"

FIT = {
    "01-use-cases": "with-intro",
    "02-er-diagram": "with-intro",
    "03-architecture": "alone",
    "04-integration-design": "with-heading",
    "05-seq-register": "with-intro",
    "06-seq-approve": "alone",
    "07-seq-merchant-api-order": "alone",
    "08-seq-rotate-secret": "alone",
    "09-seq-orders-dashboard": "alone",
    "10-activity-order-request": "alone",
    "11-state-merchant": "alone",
    "12-class-merchant-service": "with-heading",
    "13-class-merchant-orders": "alone",
}


class TextFile:
    """make_pdf.print_html passes newline= to write_text, which Python below 3.10 does not accept."""

    def __init__(self, path):
        self.path = path

    def __str__(self):
        return str(self.path)

    def write_text(self, body, encoding, newline):
        with open(self.path, "w", encoding=encoding, newline=newline) as fh:
            fh.write(body)


def trim(raw, out):
    im = Image.open(raw).convert("RGB")
    mask = ImageChops.difference(im, Image.new("RGB", im.size, (255, 255, 255))).convert("L")
    box = mask.point(lambda v: 255 if v > 12 else 0).getbbox()
    assert box[2] < im.width - 2 and box[3] < im.height - 2, f"{raw.name}: clipped, enlarge the window"
    pad = 32
    im.crop((max(box[0] - pad, 0), max(box[1] - pad, 0), min(box[2] + pad, im.width),
             min(box[3] + pad, im.height))).save(out, optimize=True)


def rasterise(fid):
    source = BUILD / f"{fid}.svg"
    page = BUILD / f"{fid}.html"
    page.write_text(f'<!doctype html><meta charset="utf-8"><style>html,body{{margin:0;background:#fff}}'
                    f'img{{display:block}}</style><img src="{fid}.svg">', encoding="utf-8")
    raw = BUILD / f"{fid}.raw.png"
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=2",
                    "--window-size=1900,1700", "--virtual-time-budget=4000", f"--user-data-dir={BUILD / 'p'}",
                    f"--screenshot={raw}", page.as_uri()], check=True, stdout=subprocess.DEVNULL,
                   stderr=subprocess.DEVNULL, timeout=60)
    trim(raw, OUT / f"{fid}.png")
    shutil.copy(source, OUT / f"{fid}.svg")


def print_pdf(src, out):
    proc = subprocess.Popen([CHROME, "--headless=new", "--disable-gpu", f"--remote-debugging-port={PORT}",
                             "--remote-allow-origins=*", f"--user-data-dir={BUILD / 'p'}", "about:blank"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        page = None
        for _ in range(100):
            try:
                targets = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list"))
                page = next(t for t in targets if t["type"] == "page")
                break
            except Exception:
                time.sleep(0.2)
        ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=60, suppress_origin=True)
        ids = iter(range(1, 10_000))

        def call(method, **params):
            mid = next(ids)
            ws.send(json.dumps({"id": mid, "method": method, "params": params}))
            while True:
                msg = json.loads(ws.recv())
                if msg.get("id") == mid:
                    assert "error" not in msg, msg["error"]
                    return msg.get("result", {})

        call("Page.enable")
        call("Page.navigate", url=src.as_uri())
        call("Runtime.evaluate", expression="new Promise(r => { if (document.readyState === 'complete') r(); "
                                            "else addEventListener('load', () => r()); })", awaitPromise=True)
        call("Runtime.evaluate", expression="document.fonts.ready.then(() => true)", awaitPromise=True)
        pdf = call("Page.printToPDF", preferCSSPageSize=True, printBackground=True, displayHeaderFooter=False,
                   marginTop=0, marginBottom=0, marginLeft=0, marginRight=0)
        out.write_bytes(base64.b64decode(pdf["data"]))
        ws.close()
    finally:
        proc.terminate()
        proc.wait(timeout=10)


def main():
    BUILD.mkdir(exist_ok=True)
    problems = check.run(list(build_figures.FIGURES))
    if problems:
        sys.exit(f"{problems} geometry problem(s): fix them first")
    build_figures.main()
    for fid in build_figures.FIGURES:
        rasterise(fid)
    shutil.rmtree(BUILD / "p", ignore_errors=True)
    html_path = BUILD / "m7-print.html"
    make_pdf.print_html(m7_page.page(), FIT, [], TextFile(html_path))
    print_pdf(html_path, PDF)
    shutil.rmtree(BUILD / "p", ignore_errors=True)
    print("wrote", PDF, f"({PDF.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    draw.reset()
    main()
