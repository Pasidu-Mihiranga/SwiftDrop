"""Geometry checker for the M10 figures.

Rebuilds a figure with draw.REG recording every primitive, then measures:
  ORTHO     a connector segment that is not horizontal or vertical
  LOOSE     a connector end that touches no shape (and is not a junction on another line)
  THROUGH   a connector passing through the inside of a shape
  OVERLAP   two connectors running along the same line
  CROSS     two connectors crossing each other
  COVERS    a label or free text lying on top of a connector
  CLASH     a label, free text or shape overlapping another one
  STRADDLE  a label, free text or shape cut by the edge of a band or boundary
  LONG      a vertical band label longer than the band it names
  CLIPPED   anything drawn outside the canvas

Sequence diagrams: a message end may sit on a lifeline, messages may cross lifelines, but no
label may cover a lifeline.

Run:  python check.py [figure-id ...]
"""

import math
import sys

import draw

TOL = 2.0


# ---------------------------------------------------------------- shape geometry

def inside(shape, p, margin):
    """True if p lies inside the shape shrunk by margin (a negative margin grows it)."""
    x, y = p
    bx, by, bw, bh = shape["box"]
    kind = shape["type"]
    if kind == "rect":
        return bx + margin < x < bx + bw - margin and by + margin < y < by + bh - margin
    if kind == "cylinder":
        ry, rx = shape["ry"], bw / 2
        if bx + margin < x < bx + bw - margin and by + ry < y < by + bh - ry:
            return True
        a, b = rx - margin, ry - margin
        return a > 0 and b > 0 and any(((x - bx - rx) / a) ** 2 + ((y - ey) / b) ** 2 < 1
                                       for ey in (by + ry, by + bh - ry))
    if kind == "cloud":
        if any(math.hypot(x - cx, y - cy) < r - margin for cx, cy, r in shape["circles"]):
            return True
        rx, ry, rw, rh = shape["base"]
        return rx + margin < x < rx + rw - margin and ry + margin < y < ry + rh - margin
    if kind == "ellipse":
        rx, ry = bw / 2 - margin, bh / 2 - margin
        return rx > 0 and ry > 0 and ((x - bx - bw / 2) / rx) ** 2 + ((y - by - bh / 2) / ry) ** 2 < 1
    if kind == "actor":                       # stick figure plus its caption underneath
        return any(bx_ + margin < x < bx_ + bw_ - margin and by_ + margin < y < by_ + bh_ - margin
                   for bx_, by_, bw_, bh_ in (shape["box"], shape["caption"]))
    if kind == "diamond":
        cx, cy = bx + bw / 2, by + bh / 2
        return abs(x - cx) / (bw / 2) + abs(y - cy) / (bh / 2) < 1 - margin / (min(bw, bh) / 2)
    raise ValueError(kind)


def on_outline(shape, p):
    return inside(shape, p, -(TOL + 0.01)) and not inside(shape, p, TOL)


def in_box(box, p, margin=0.0):
    x, y = p
    bx, by, bw, bh = box
    return bx + margin < x < bx + bw - margin and by + margin < y < by + bh - margin


def boxes_overlap(a, b, slack=0.5):
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax + slack < bx + bw and bx + slack < ax + aw and ay + slack < by + bh and by + slack < ay + ah


def contains(outer, inner, slack=1.0):
    ox, oy, ow, oh = outer
    ix, iy, iw, ih = inner
    return ix >= ox - slack and iy >= oy - slack and ix + iw <= ox + ow + slack and iy + ih <= oy + oh + slack


def box_points(box, step=3.0):
    bx, by, bw, bh = box
    nx, ny = max(2, int(bw / step) + 1), max(2, int(bh / step) + 1)
    for i in range(nx):
        for j in range(ny):
            yield bx + bw * i / (nx - 1), by + bh * j / (ny - 1)


# ---------------------------------------------------------------- connector geometry

def segments(path):
    pts = path["pts"]
    return list(zip(pts, pts[1:]))


def sample(seg, step=1.0):
    (x1, y1), (x2, y2) = seg
    n = max(1, int(math.hypot(x2 - x1, y2 - y1) / step))
    return [(x1 + (x2 - x1) * k / n, y1 + (y2 - y1) * k / n) for k in range(n + 1)]


def horizontal(seg):
    return abs(seg[0][1] - seg[1][1]) < 0.5


def vertical(seg):
    return abs(seg[0][0] - seg[1][0]) < 0.5


def near_segment(p, seg):
    (x1, y1), (x2, y2) = seg
    x, y = p
    if horizontal(seg):
        return abs(y - y1) <= TOL and min(x1, x2) - TOL <= x <= max(x1, x2) + TOL
    if vertical(seg):
        return abs(x - x1) <= TOL and min(y1, y2) - TOL <= y <= max(y1, y2) + TOL
    return False


def cross_point(a, b):
    """Intersection point of two segments (None if they do not meet)."""
    (x1, y1), (x2, y2) = a
    (x3, y3), (x4, y4) = b
    den = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    if abs(den) < 1e-9:
        return None
    t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / den
    u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / den
    if -1e-6 <= t <= 1 + 1e-6 and -1e-6 <= u <= 1 + 1e-6:
        return x1 + t * (x2 - x1), y1 + t * (y2 - y1)
    return None


def same_point(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1]) <= TOL


# ---------------------------------------------------------------- naming, for readable reports

def shape_at(shapes, p):
    for s in shapes:
        if on_outline(s, p):
            return s["name"] or s["type"]
    return None


def describe(path, shapes):
    a, z = path["pts"][0], path["pts"][-1]
    src = shape_at(shapes, a) or f"({a[0]:.0f},{a[1]:.0f})"
    dst = shape_at(shapes, z) or f"({z[0]:.0f},{z[1]:.0f})"
    return f"line {src} -> {dst}"


# ---------------------------------------------------------------- the checks

def check(reg):
    shapes = [r for r in reg if r["kind"] == "shape"]
    paths = [r for r in reg if r["kind"] == "path"]
    labels = [r for r in reg if r["kind"] == "label"]
    bands = [r for r in reg if r["kind"] == "band"]
    lifelines = [r for r in reg if r["kind"] == "lifeline"]
    canvas = next((r["box"] for r in reg if r["kind"] == "canvas"), None)
    # free text = text not drawn inside a shape or a label (those belong to their owner)
    owners = ([s["box"] for s in shapes] + [s["caption"] for s in shapes if "caption" in s]
              + [lb["box"] for lb in labels])
    texts = [t for t in reg if t["kind"] == "text" and not any(contains(o, t["box"], 2) for o in owners)]
    issues = []

    def add(code, msg):
        issues.append((code, msg))

    for p in paths:
        name = describe(p, shapes)
        for seg in segments(p):
            if not p.get("free") and not (horizontal(seg) or vertical(seg)):
                add("ORTHO", f"{name}: segment {seg} is diagonal")

        # ends: on a shape outline, or (only if it has no arrowhead) a junction on another line
        for end, has_head in ((p["pts"][0], p["back"]), (p["pts"][-1], p["head"])):
            if any(on_outline(s, end) for s in shapes):
                continue
            if any(abs(end[0] - ln["x"]) <= TOL and ln["y1"] - TOL <= end[1] <= ln["y2"] + TOL for ln in lifelines):
                continue
            junction = any(near_segment(end, seg) for q in paths if q is not p for seg in segments(q))
            if junction and not has_head:
                continue
            what = "arrowhead" if has_head else "end"
            add("LOOSE", f"{name}: {what} at ({end[0]:.0f},{end[1]:.0f}) touches no shape")

        for seg in segments(p):
            for s in shapes:
                if any(inside(s, q, TOL) for q in sample(seg)[1:-1]):
                    add("THROUGH", f"{name}: passes through '{s['name']}'")

    for i, p in enumerate(paths):
        for q in paths[i + 1:]:
            ends = [p["pts"][0], p["pts"][-1], q["pts"][0], q["pts"][-1]]
            for a in segments(p):
                for b in segments(q):
                    if horizontal(a) and horizontal(b) and abs(a[0][1] - b[0][1]) < 0.5 or \
                       vertical(a) and vertical(b) and abs(a[0][0] - b[0][0]) < 0.5:
                        k = 0 if horizontal(a) else 1
                        lo = max(min(a[0][k], a[1][k]), min(b[0][k], b[1][k]))
                        hi = min(max(a[0][k], a[1][k]), max(b[0][k], b[1][k]))
                        if hi - lo > 1:
                            shared = same_point(p["pts"][0], q["pts"][0])
                            code = "TRUNK" if shared else "OVERLAP"
                            add(code, f"{describe(p, shapes)} and {describe(q, shapes)} run along the same line")
                    elif not ((horizontal(a) or vertical(a)) and (horizontal(b) or vertical(b))):
                        pt = cross_point(a, b)
                        if pt and not any(same_point(pt, e) for e in ends):
                            add("CROSS", f"{describe(p, shapes)} crosses {describe(q, shapes)} "
                                         f"at ({pt[0]:.0f},{pt[1]:.0f})")
                    elif (horizontal(a) and vertical(b)) or (vertical(a) and horizontal(b)):
                        h, v = (a, b) if horizontal(a) else (b, a)
                        x, y = v[0][0], h[0][1]
                        if min(h[0][0], h[1][0]) - 0.5 <= x <= max(h[0][0], h[1][0]) + 0.5 and \
                           min(v[0][1], v[1][1]) - 0.5 <= y <= max(v[0][1], v[1][1]) + 0.5:
                            if any(same_point((x, y), e) for e in ends):
                                continue          # a T-junction or a shared end point
                            add("CROSS", f"{describe(p, shapes)} crosses {describe(q, shapes)} at ({x:.0f},{y:.0f})")

    tagged = [("label", lb["body"], lb["box"]) for lb in labels] + [("text", t["body"], t["box"]) for t in texts]
    for kind, body, box in tagged:
        for p in paths:
            for seg in segments(p):
                if any(in_box(box, q, 0.5) for q in sample(seg)):
                    add("COVERS", f"{kind} '{body}' lies on {describe(p, shapes)}")
                    break
        for ln in lifelines:
            bx, by, bw, bh = box
            if bx + 0.5 < ln["x"] < bx + bw - 0.5 and by < ln["y2"] and ln["y1"] < by + bh:
                add("COVERS", f"{kind} '{body}' cuts the lifeline of '{ln['name']}'")
        for s in shapes:
            if any(inside(s, q, 0) for q in box_points(box)):
                add("CLASH", f"{kind} '{body}' overlaps shape '{s['name']}'")
    for i, (k1, b1, x1) in enumerate(tagged):
        for k2, b2, x2 in tagged[i + 1:]:
            if boxes_overlap(x1, x2):
                add("CLASH", f"{k1} '{b1}' overlaps {k2} '{b2}'")
    for i, s in enumerate(shapes):
        for t in shapes[i + 1:]:
            if boxes_overlap(s["box"], t["box"]):
                add("CLASH", f"shape '{s['name']}' overlaps shape '{t['name']}'")

    for t in reg:
        if t["kind"] == "text" and "band" in t and t["box"][3] > t["band"][3]:
            add("LONG", f"band label '{t['body']}' ({t['box'][3]:.0f}px) is longer than its band ({t['band'][3]:.0f}px)")

    things = [(k, b, x) for k, b, x in tagged] + [("shape", s["name"], s["box"]) for s in shapes]
    for band in bands:
        for kind, body, box in things:
            if band.get("name") == "alt" and str(body).startswith("activation"):
                continue                     # a participant may stay busy across a fragment edge
            if boxes_overlap(band["box"], box, 0) and not contains(band["box"], box, 0.5):
                add("STRADDLE", f"{kind} '{body}' is cut by the edge of band '{band['name']}'")
    if canvas:
        for kind, body, box in things:
            if not contains(canvas, box, 0.5):
                add("CLIPPED", f"{kind} '{body}' is outside the canvas")
        for p in paths:
            if any(not in_box(canvas, q, -0.5) for q in p["pts"]):
                add("CLIPPED", f"{describe(p, shapes)} runs outside the canvas")
    return issues


def run(ids):
    from build_figures import FIGURES      # imported here: figures may import this module
    ids = ids or list(FIGURES)
    total = 0
    for fid in ids:
        draw.reset()
        FIGURES[fid]()
        found = check(list(draw.REG))
        errors = [i for i in found if i[0] != "TRUNK"]
        total += len(errors)
        print(f"{fid}: {len(errors)} problem(s)" + (f", {len(found) - len(errors)} shared trunk(s)" if
                                                    len(found) > len(errors) else ""))
        for code, msg in found:
            print(f"  {code:8} {msg}")
    return total


if __name__ == "__main__":
    sys.exit(1 if run(sys.argv[1:]) else 0)
