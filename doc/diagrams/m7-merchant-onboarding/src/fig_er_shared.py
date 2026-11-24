"""The ER table box: black header strip, one row per column, kind on the right."""

import paths  # noqa: F401
from draw import BLACK, FILL, shape_rect, text, GREY

ROW = 26


def table(x, y, w, title, rows):
    h = 32 + len(rows) * ROW
    shape_rect(x, y, w, h, title)
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>',
           f'<rect x="{x}" y="{y}" width="{w}" height="32" fill="{BLACK}"/>',
           text(x + w / 2, y + 16, title, 12.5, "#ffffff", 700)]
    for i, (name, kind) in enumerate(rows):
        ry = y + 32 + i * ROW
        if i:
            out.append(f'<line x1="{x}" y1="{ry}" x2="{x + w}" y2="{ry}" stroke="#d8d8d8" stroke-width="1"/>')
        out.append(text(x + 12, ry + ROW / 2, name, 11.5, BLACK, anchor="start"))
        if kind:
            out.append(text(x + w - 12, ry + ROW / 2, kind, 10.5, GREY, anchor="end"))
    return "".join(out), h
