"""Trims the white margin from each rendered PNG and reports whether anything was clipped."""

import sys
from pathlib import Path

from PIL import Image, ImageChops

raw, out = Path("bw-raw"), Path("bw-png")
out.mkdir(exist_ok=True)
ids = sys.argv[1:] or [p.stem for p in sorted(raw.glob("*.png"))]

for name in ids:
    path = raw / f"{name}.png"
    if not path.exists():
        print(f"{name}: MISSING")
        continue
    im = Image.open(path).convert("RGB")
    mask = ImageChops.difference(im, Image.new("RGB", im.size, (255, 255, 255))).convert("L")
    box = mask.point(lambda v: 255 if v > 12 else 0).getbbox()
    clipped = box[2] >= im.width - 2 or box[3] >= im.height - 2
    pad = 32
    box = (max(box[0] - pad, 0), max(box[1] - pad, 0), min(box[2] + pad, im.width), min(box[3] + pad, im.height))
    im.crop(box).save(out / f"{name}.png", optimize=True)
    print(f"{name}: {box[2] - box[0]}x{box[3] - box[1]}" + ("  CLIPPED - enlarge the window" if clipped else ""))
