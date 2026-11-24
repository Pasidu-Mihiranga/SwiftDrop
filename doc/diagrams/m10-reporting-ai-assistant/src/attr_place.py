"""Automatic placement of Chen attribute ovals around an entity, without overlaps.

All entities, relationships, lines and labels are drawn (and registered in draw.REG) first. Then,
for each attribute, candidate positions are tried within the angles the entity leaves free; the
first candidate is kept whose oval touches nothing and whose straight line crosses no other line
and passes through no other shape. Accepted ovals are registered, so later ones avoid them too.
"""

import math

import draw
from check import cross_point, inside, sample
from eer import Shape, attribute, radial

CLEAR = 6          # minimum gap between an oval and anything else, px


def _boxes_hit(a, b, pad=0.0):
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax - pad < bx + bw and bx - pad < ax + aw and ay - pad < by + bh and by - pad < ay + ah


def _ellipse_points(cx, cy, rx, ry, n=24):
    return [(cx + rx * math.cos(2 * math.pi * k / n), cy + ry * math.sin(2 * math.pi * k / n)) for k in range(n)]


def _fits(owner, name, cx, cy):
    w = draw.text_width(name, 11) + 26
    rx, ry = w / 2 + CLEAR, 15 + CLEAR
    obox = (cx - rx, cy - ry, 2 * rx, 2 * ry)
    canvas = next((r["box"] for r in reversed(draw.REG) if r["kind"] == "canvas"), None)
    # ray from the owner's edge to the oval's edge
    probe = Shape("ellipse", cx, cy, w, 30)
    dx, dy = cx - owner.cx, cy - owner.cy
    t1, t2 = owner.exit_t(dx, dy), 1 - probe.exit_t(-dx, -dy)
    if t2 <= t1 + 0.02:
        return False
    p1 = (owner.cx + dx * t1, owner.cy + dy * t1)
    p2 = (owner.cx + dx * t2, owner.cy + dy * t2)
    rbox = (min(p1[0], p2[0]) - 2, min(p1[1], p2[1]) - 2, abs(p2[0] - p1[0]) + 4, abs(p2[1] - p1[1]) + 4)
    ray_pts = sample((p1, p2), 3.0)[1:-1]
    oval_pts = _ellipse_points(cx, cy, rx, ry) + [(cx, cy)]
    for r in draw.REG:
        kind = r["kind"]
        if kind == "shape":
            if r["box"] == (owner.cx - owner.w / 2, owner.cy - owner.h / 2, owner.w, owner.h):
                continue
            if r["type"] == "ellipse" and _boxes_hit(obox, r["box"]):
                return False                          # ovals keep their bounding boxes apart, as the checker does
            if _boxes_hit(obox, r["box"]) and (any(inside(r, q, 0) for q in oval_pts)
                                              or inside(dict(type="ellipse", box=obox), (r["box"][0] + r["box"][2] / 2,
                                                                                            r["box"][1] + r["box"][3] / 2), 0)):
                return False
            if _boxes_hit(rbox, r["box"]) and any(inside(r, q, 1) for q in ray_pts):
                return False
        elif kind in ("label", "text"):
            if _boxes_hit(obox, r["box"]) or (_boxes_hit(rbox, r["box"]) and
                                              any(r["box"][0] < q[0] < r["box"][0] + r["box"][2] and
                                                  r["box"][1] < q[1] < r["box"][1] + r["box"][3] for q in ray_pts)):
                return False
        elif kind == "path":
            for seg in zip(r["pts"], r["pts"][1:]):
                sbox = (min(seg[0][0], seg[1][0]) - 1, min(seg[0][1], seg[1][1]) - 1,
                        abs(seg[1][0] - seg[0][0]) + 2, abs(seg[1][1] - seg[0][1]) + 2)
                if _boxes_hit(obox, sbox) and any(((q[0] - cx) / rx) ** 2 + ((q[1] - cy) / ry) ** 2 < 1
                                                  for q in sample(seg, 3.0)):
                    return False
                if _boxes_hit(rbox, sbox) and cross_point((p1, p2), seg):
                    return False
    if canvas and not (canvas[0] + 10 < obox[0] and obox[0] + obox[2] < canvas[2] - 10 and
                       canvas[1] + 10 < obox[1] and obox[1] + obox[3] < canvas[3] - 10):
        return False
    return True


def place(owner, items, ranges, rmin=None, rmax=900, flat=0.62):
    """Place attribute ovals for `owner`.

    items: [(name, kind)]; ranges: [(from_deg, to_deg)] the free directions (0 = right, 90 = down).
    Ovals are spread evenly over the free angles, then pushed outwards until they fit.
    """
    rmin = rmin or (max(owner.w, owner.h) / 2 + 70)
    span = [(a, b) for a, b in ranges]
    total = sum(b - a for a, b in span)
    targets = []
    for i in range(len(items)):                         # even spread over the union of the ranges
        pos = total * (i + 0.5) / len(items)
        for a, b in span:
            if pos <= b - a:
                targets.append(a + pos)
                break
            pos -= b - a
    out = []
    angles = [a + k * 3 for a, b in span for k in range(int((b - a) / 3) + 1)]
    for i, ((name, kind), target) in enumerate(zip(items, targets)):
        placed = False
        start = rmin + (i % 2) * 80                     # neighbours stagger on two rings
        for r in list(range(int(start), int(rmax), 18)) + list(range(int(rmin), int(start), 18)):
            for ang in sorted(angles, key=lambda x: abs(x - target)):
                a = math.radians(ang)
                cx, cy = owner.cx + r * math.cos(a), owner.cy + r * math.sin(a) * flat
                if _fits(owner, name, cx, cy):
                    svg, shp = attribute(cx, cy, name, kind)
                    out.append(radial(owner, shp) + svg)
                    placed = True
                    break
            if placed:
                break
        if not placed:
            raise RuntimeError(f"no room for attribute '{name}' around {owner.cx:.0f},{owner.cy:.0f}")
    return "".join(out)
