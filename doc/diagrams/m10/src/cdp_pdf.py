"""Print an HTML file to PDF through the DevTools protocol, honouring the CSS @page sizes.

The --print-to-pdf command-line flag does not set preferCSSPageSize, so Edge lays the page out
for its default paper and scales it; Page.printToPDF with preferCSSPageSize=true does not.

usage: python cdp_pdf.py input.html output.pdf [--probe]
  --probe   also report any element wider than the printable width (the cause of shrinking)
"""

import base64
import json
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

import websocket

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
PORT = 9333


def main():
    src, out = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
    profile = out.parent / "cdp-profile"
    proc = subprocess.Popen([EDGE, "--headless=new", "--disable-gpu", f"--remote-debugging-port={PORT}",
                             f"--user-data-dir={profile}", "about:blank"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(50):                                  # wait for the debugger endpoint
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
                    if "error" in msg:
                        raise RuntimeError(msg["error"])
                    return msg.get("result", {})

        call("Page.enable")
        call("Page.navigate", url=src.as_uri())
        call("Runtime.evaluate", expression="new Promise(r => { if (document.readyState === 'complete') r(); "
                                            "else addEventListener('load', () => r()); })", awaitPromise=True)
        call("Runtime.evaluate", expression="document.fonts.ready.then(() => true)", awaitPromise=True)

        if "--probe" in sys.argv:
            call("Emulation.setEmulatedMedia", media="print")
            call("Emulation.setDeviceMetricsOverride", width=1017, height=700, deviceScaleFactor=1, mobile=False)
            probe = call("Runtime.evaluate", returnByValue=True, expression="""
                [...document.querySelectorAll('body *')]
                  .map(e => [e, e.getBoundingClientRect()])
                  .filter(([e, r]) => r.right > 1018)
                  .slice(0, 15)
                  .map(([e, r]) => `${e.tagName.toLowerCase()} in "${(e.closest('svg') || {getAttribute: () => ''}).getAttribute('aria-label')}" `
                       + `x=${e.getAttribute('x')} w=${e.getAttribute('width')} right=${Math.round(r.right)}`)
                  .concat([`documentElement.scrollWidth=${document.documentElement.scrollWidth}`,
                           `section p font-size=${getComputedStyle(document.querySelector('section p')).fontSize}`,
                           `html font-size=${getComputedStyle(document.documentElement).fontSize}`,
                           `fonts loaded=${[...document.fonts].filter(f => f.status === 'loaded').map(f => f.family).join(',')}`])
            """)
            print("wider than the printable width (1017px):", probe["result"].get("value") or "nothing")
            call("Emulation.clearDeviceMetricsOverride")
            call("Emulation.setEmulatedMedia", media="")

        pdf = call("Page.printToPDF", preferCSSPageSize=True, printBackground=True,
                   displayHeaderFooter=False, marginTop=0, marginBottom=0, marginLeft=0, marginRight=0)
        out.write_bytes(base64.b64decode(pdf["data"]))
        print("wrote", out, f"({out.stat().st_size // 1024} KB)")
        ws.close()
    finally:
        proc.terminate()
        proc.wait(timeout=10)
        subprocess.run(["cmd", "/c", "rmdir", "/s", "/q", str(profile)], stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    main()
