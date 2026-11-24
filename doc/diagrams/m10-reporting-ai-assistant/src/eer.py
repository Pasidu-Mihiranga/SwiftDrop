"""Chen / EER notation primitives, drawn in the same black-and-white house style.

  entity      rectangle; weak entity = double rectangle
  relation    diamond;   identifying relationship = double diamond
  attribute   oval: key = underlined, partial key = dashed underline, derived = dashed oval,
              multivalued = double oval
  isa         specialization circle with d (disjoint) or o (overlapping)
  link        orthogonal relationship line; total participation = double line
  card        cardinality label (1, N, M)

Attribute lines are short straight radial lines from the entity toward the oval, as in classic
Chen diagrams; they are registered as "free" paths so the checker tests them for crossings with a
general segment test instead of the orthogonal one.
"""

import math

from draw import BLACK, FILL, GREY, _add, label, text, text_width

ATTR_H = 30


class Shape:
    """Geometry handle for an entity, relationship or attribute: centre and how to clip a ray."""

    def __init__(self, kind, cx, cy, w, h):
        self.kind, self.cx, self.cy, self.w, self.h = kind, cx, cy, w, h

    @property
    def left(self):
        return self.cx - self.w / 2

    @property
    def right(self):
        return self.cx + self.w / 2

    @property
    def top(self):
        return self.cy - self.h / 2

    @property
    def bottom(self):
        return self.cy + self.h / 2

    def exit_t(self, dx, dy):
        """Fraction t along (dx, dy) from the centre where the ray leaves this shape."""
        hw, hh = self.w / 2, self.h / 2
        if self.kind == "rect":
            return min(hw / abs(dx) if dx else math.inf, hh / abs(dy) if dy else math.inf)
        if self.kind == "diamond":
            return 1 / (abs(dx) / hw + abs(dy) / hh)
        return 1 / math.sqrt((dx / hw) ** 2 + (dy / hh) ** 2)          # ellipse


def entity(cx, cy, name, w=None, h=44, weak=False):
    w = w or max(150, text_width(name, 13) + 36)
    shp = Shape("rect", cx, cy, w, h)
    _add("shape", type="rect", box=(cx - w / 2, cy - h / 2, w, h), name=name)
    out = f'<rect x="{cx - w / 2:.1f}" y="{cy - h / 2:.1f}" width="{w:.1f}" height="{h}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>'
    if weak:
        out += (f'<rect x="{cx - w / 2 + 4:.1f}" y="{cy - h / 2 + 4:.1f}" width="{w - 8:.1f}" height="{h - 8}" '
                f'fill="none" stroke="{BLACK}" stroke-width="1.2"/>')
    return out + text(cx, cy, name, 13, BLACK, 700), shp


def relation(cx, cy, name, identifying=False, w=None, h=56):
    w = w or max(96, text_width(name, 11.5) + 52)
    shp = Shape("diamond", cx, cy, w, h)
    _add("shape", type="diamond", box=(cx - w / 2, cy - h / 2, w, h), name=f"<{name}>")
    pts = f"{cx},{cy - h / 2} {cx + w / 2},{cy} {cx},{cy + h / 2} {cx - w / 2},{cy}"
    out = f'<polygon points="{pts}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>'
    if identifying:
        iw, ih = w - 14, h - 9
        inner = f"{cx},{cy - ih / 2} {cx + iw / 2},{cy} {cx},{cy + ih / 2} {cx - iw / 2},{cy}"
        out += f'<polygon points="{inner}" fill="none" stroke="{BLACK}" stroke-width="1.2"/>'
    return out + text(cx, cy, name, 11.5, BLACK, 600), shp


def attribute(cx, cy, name, kind="plain"):
    """kind: plain | key | partial | derived | multi"""
    w = text_width(name, 11) + 26
    rx, ry = w / 2, ATTR_H / 2
    shp = Shape("ellipse", cx, cy, w, ATTR_H)
    _add("shape", type="ellipse", box=(cx - rx, cy - ry, w, ATTR_H), name=name)
    dash = ' stroke-dasharray="4 3"' if kind == "derived" else ""
    out = f'<ellipse cx="{cx:.1f}" cy="{cy:.1f}" rx="{rx:.1f}" ry="{ry}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.2"{dash}/>'
    if kind == "multi":
        out += f'<ellipse cx="{cx:.1f}" cy="{cy:.1f}" rx="{rx - 4:.1f}" ry="{ry - 4}" fill="none" stroke="{BLACK}" stroke-width="1"/>'
    out += text(cx, cy, name, 11, BLACK)
    if kind in ("key", "partial"):
        tw = text_width(name, 11) * 0.94
        style = ' stroke-dasharray="3 2"' if kind == "partial" else ""
        out += (f'<line x1="{cx - tw / 2:.1f}" y1="{cy + 7:.1f}" x2="{cx + tw / 2:.1f}" y2="{cy + 7:.1f}" '
                f'stroke="{BLACK}" stroke-width="1"{style}/>')
    return out, shp


def radial(a, b):
    """Straight line between two shapes along the line joining their centres, clipped to both."""
    dx, dy = b.cx - a.cx, b.cy - a.cy
    t1 = a.exit_t(dx, dy)
    t2 = 1 - b.exit_t(-dx, -dy)
    p1 = (a.cx + dx * t1, a.cy + dy * t1)
    p2 = (a.cx + dx * t2, a.cy + dy * t2)
    _add("path", pts=[p1, p2], head=False, back=False, dashed=False, free=True)
    return (f'<line x1="{p1[0]:.1f}" y1="{p1[1]:.1f}" x2="{p2[0]:.1f}" y2="{p2[1]:.1f}" '
            f'stroke="{BLACK}" stroke-width="1.1"/>')


def fan(owner, items, angles, radius):
    """Attribute ovals around `owner`, one per angle (degrees, 0 = right, 90 = down).

    radius may be a number or a list (one per item), so long fans can stagger in and out.
    """
    out = []
    radii = radius if isinstance(radius, (list, tuple)) else [radius] * len(items)
    for (name, kind), ang, r in zip(items, angles, radii):
        a = math.radians(ang)
        cx = owner.cx + r * math.cos(a) * 1.0
        cy = owner.cy + r * math.sin(a) * 0.62            # flatter fan: diagrams are wider than tall
        svg, shp = attribute(cx, cy, name, kind)
        out.append(radial(owner, shp) + svg)
    return "".join(out)


def link(points, total=False):
    """Orthogonal relationship line; a double line marks total participation."""
    _add("path", pts=[(float(x), float(y)) for x, y in points], head=False, back=False, dashed=False)
    d = "M" + " L".join(f"{x:.1f} {y:.1f}" for x, y in points)
    if total:
        return (f'<path d="{d}" fill="none" stroke="{BLACK}" stroke-width="4.4"/>'
                f'<path d="{d}" fill="none" stroke="{FILL}" stroke-width="1.6"/>')
    return f'<path d="{d}" fill="none" stroke="{BLACK}" stroke-width="1.3"/>'


def isa(cx, cy, letter, r=16):
    shp = Shape("ellipse", cx, cy, 2 * r, 2 * r)
    _add("shape", type="ellipse", box=(cx - r, cy - r, 2 * r, 2 * r), name=f"isa-{letter}")
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>'
            + text(cx, cy, letter, 14, BLACK, 700)), shp


def card(x, y, value):
    return label(x, y, value, anchor="middle", size=11.5, color=BLACK)
