"""Black-and-white SVG primitives for the M10 diagrams.

House style (follows the reference the user gave):
  - no colour: white fill, black strokes, grey band backgrounds, grey annotation text
  - every connector is orthogonal: vertical or horizontal segments with 90-degree corners
  - nothing overlaps: routes are placed by hand on a coordinate grid
"""

from html import escape

BLACK = "#111111"
GREY = "#6b6b6b"
BAND = "#ececec"
BAND_EDGE = "#d4d4d4"
FILL = "#ffffff"
HEAD = "#111111"          # solid header strip, white text (as in the reference)
FONT = "Segoe UI, Helvetica Neue, Helvetica, Arial, sans-serif"
NL = "|"                  # line separator inside labels

# ---------------------------------------------------------------- geometry registry
# Every primitive records what it drew. check.py rebuilds a figure and measures this record
# (loose arrow ends, lines through boxes, crossings, labels covering things), so faults are
# found by geometry instead of by eye.

REG = []
_mute = [0]               # >0 while a helper draws text it registers itself


def reset():
    REG.clear()


def _add(kind, **geom):
    REG.append({"kind": kind, **geom})


def text_width(body, size, spacing=None):
    return len(body) * size * 0.55 + (float(spacing) * len(body) if spacing else 0.0)


def shape_rect(x, y, w, h, name=""):
    """Register a box drawn with raw SVG (for example an ER table) so the checker sees it."""
    _add("shape", type="rect", box=(x, y, w, h), name=name)


def container(x, y, w, h, name=""):
    """Register a boundary drawn with raw SVG (for example a use-case system boundary)."""
    _add("band", box=(x, y, w, h), name=name)


# ---------------------------------------------------------------- text


def text(x, y, body, size=13, color=BLACK, weight=400, anchor="middle", italic=False, spacing=None):
    if not _mute[0]:
        w = text_width(body, size, spacing)
        x0 = x if anchor == "start" else (x - w / 2 if anchor == "middle" else x - w)
        _add("text", box=(x0, y - size * 0.6, w, size * 1.2), body=body)
    style = f' font-style="italic"' if italic else ""
    track = f' letter-spacing="{spacing}"' if spacing else ""
    return (f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size}" font-weight="{weight}" fill="{color}" '
            f'text-anchor="{anchor}" dominant-baseline="middle" font-family="{FONT}"{style}{track}>{escape(body)}</text>')


def text_block(x, y, lines, size=13, color=BLACK, weight=400, anchor="middle", italic=False, lead=1.35):
    step = size * lead
    top = y - (len(lines) - 1) * step / 2
    return "".join(text(x, top + i * step, line, size, color, weight, anchor, italic) for i, line in enumerate(lines))


# ---------------------------------------------------------------- shapes


def band(x, y, w, h, label=None, vertical_label=False):
    _add("band", box=(x, y, w, h), name=label or "")
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="4" fill="{BAND}" stroke="{BAND_EDGE}" stroke-width="1"/>']
    if label and vertical_label:
        cx, cy = x - 16, y + h / 2
        _mute[0] += 1
        out.append(f'<g transform="rotate(-90 {cx} {cy})">'
                   + text(cx, cy, label.upper(), 11, GREY, 500, spacing="1.6") + "</g>")
        _mute[0] -= 1
        length = text_width(label.upper(), 11, "1.6")       # rotated, so it runs vertically
        _add("text", box=(cx - 7, cy - length / 2, 14, length), body=label, band=(x, y, w, h))
    elif label:
        out.append(text(x + 14, y + 16, label.upper(), 11, GREY, 500, anchor="start", spacing="1.6"))
    return "".join(out)


def node(x, y, w, h, title, lines=(), header=False, dashed=False, italic_title=False, size=13):
    """Plain box. header=True draws the black title strip from the reference."""
    _add("shape", type="rect", box=(x, y, w, h), name=title)
    dash = ' stroke-dasharray="5 4"' if dashed else ""
    out = [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"{dash}/>']
    if header:
        hh = 34
        out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{hh}" fill="{HEAD}"/>')
        out.append(text(x + w / 2, y + hh / 2 + 1, title, 14, "#ffffff", 700))
        out.append(f'<line x1="{x}" y1="{y + hh}" x2="{x + w}" y2="{y + hh}" stroke="{BLACK}" stroke-width="1.4"/>')
        if lines:
            out.append(text_block(x + w / 2, y + hh + (h - hh) / 2, list(lines), 12, GREY))
    else:
        if lines:
            out.append(text(x + w / 2, y + 22, title, size, BLACK, 700, italic=italic_title))
            out.append(text_block(x + w / 2, y + 22 + 10 + len(lines) * 7, list(lines), 11.5, GREY))
        else:
            out.append(text_block(x + w / 2, y + h / 2, [title], size, BLACK, 700, italic=italic_title))
    return "".join(out)


def rounded(x, y, w, h, lines, size=13):
    r = min(h / 2, 18)
    _add("shape", type="rect", box=(x, y, w, h), name=lines[0])
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>'
            + text_block(x + w / 2, y + h / 2, list(lines), size, BLACK, 600))


def cylinder(x, y, w, h, title, lines=()):
    ry = 12
    _add("shape", type="cylinder", box=(x, y, w, h), ry=ry, name=title)
    out = [f'<path d="M{x} {y + ry} v{h - 2 * ry} a{w / 2} {ry} 0 0 0 {w} 0 v{-(h - 2 * ry)}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>',
           f'<ellipse cx="{x + w / 2}" cy="{y + ry}" rx="{w / 2}" ry="{ry}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>']
    body = [title] if not lines else [title, *lines]
    cy = y + ry + (h - ry) / 2
    out.append(text(x + w / 2, cy - (len(body) - 1) * 8, title, 13, BLACK, 700))
    for i, line in enumerate(lines):
        out.append(text(x + w / 2, cy - (len(body) - 1) * 8 + 17 + i * 15, line, 11.5, GREY))
    return "".join(out)


def _cloud_parts(cx, cy, w, h):
    """The cloud is a union of four circles standing on a flat base rectangle."""
    left, top = cx - w / 2, cy - h / 2
    r1, r2, r3, r4 = 0.30 * h, 0.36 * h, 0.42 * h, 0.32 * h
    circles = [(left + r1, top + h - r1, r1),             # left bump, touches the left edge
               (left + 0.38 * w, top + 0.10 * h + r2, r2),
               (left + 0.62 * w, top + r3, r3),             # tallest crest, touches the top
               (left + w - r4, top + h - r4, r4)]           # right bump, touches the right edge
    base = (left + r1, top + 0.55 * h, w - r1 - r4, 0.45 * h)
    return circles, base


def cloud_cy(left_anchor_y, h):
    """Centre y that puts the cloud's left (or right) attach point on the given row."""
    return left_anchor_y - 0.2 * h


def cloud_anchor(cx, cy, w, h, side, at=None):
    """Exact point on the cloud outline for a connector.

    left / right: the outermost point of the side bump, where the outline is vertical, so a
    horizontal arrow meets it square on. bottom: the flat base (at = x on the base). top: the crest.
    """
    circles, (bx, _, bw, _) = _cloud_parts(cx, cy, w, h)
    (x1, y1, r1), _, (x3, y3, r3), (x4, y4, r4) = circles
    if side == "left":
        return x1 - r1, y1
    if side == "right":
        return x4 + r4, y4
    if side == "bottom":
        x = cx if at is None else at
        if not bx <= x <= bx + bw:
            raise ValueError(f"x={x} is not on the flat base {bx:.0f}..{bx + bw:.0f}")
        return x, cy + h / 2
    if side == "top":
        return x3, y3 - r3
    raise ValueError(side)


def cloud(cx, cy, w, h, title, lines=()):
    """Standard cloud symbol: four bumps on a flat base.

    Drawn with the outline trick: every part is stroked at double width, then every part is
    filled again on top without a stroke, which leaves exactly the outer contour.
    """
    circles, (bx, by, bw, bh) = _cloud_parts(cx, cy, w, h)
    _add("shape", type="cloud", box=(cx - w / 2, cy - h / 2, w, h), circles=circles,
         base=(bx, by, bw, bh), name=title)
    parts = [f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.1f}"' for x, y, r in circles]
    parts.append(f'<rect x="{bx:.1f}" y="{by:.1f}" width="{bw:.1f}" height="{bh:.1f}"')
    out = [p + f' fill="{FILL}" stroke="{BLACK}" stroke-width="2.8"/>' for p in parts]
    out += [p + f' fill="{FILL}"/>' for p in parts]
    mid = cy - h / 2 + h * 0.60
    out.append(text(cx, mid - (7 if lines else 0), title, 13, BLACK, 700))
    for i, line in enumerate(lines):
        out.append(text(cx, mid + 10 + i * 14, line, 11, GREY))
    return "".join(out)


def diamond(cx, cy, w, h, lines):
    _add("shape", type="diamond", box=(cx - w / 2, cy - h / 2, w, h), name=" ".join(lines))
    pts = f"{cx},{cy - h / 2} {cx + w / 2},{cy} {cx},{cy + h / 2} {cx - w / 2},{cy}"
    return (f'<polygon points="{pts}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>'
            + text_block(cx, cy, list(lines), 12, BLACK, 600, lead=1.2))


ACTOR_H = 82                         # head top to the bottom of the caption


def stickman(cx, y, label):
    """UML actor: head, body down to the waist, arms, then exactly two legs from the waist."""
    head_r = 11
    cap_w = text_width(label, 11.5)
    _add("shape", type="actor", box=(cx - 16, y, 32, 64),             # the stick figure itself
         caption=(cx - cap_w / 2, y + 66, cap_w, ACTOR_H - 66), name=label)
    top = y + head_r                 # centre of the head
    neck = top + head_r              # body starts here
    waist = neck + 24                # body ends and both legs start here
    return (f'<circle cx="{cx}" cy="{top}" r="{head_r}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>'
            f'<path d="M{cx} {neck} V{waist} M{cx - 16} {neck + 8} H{cx + 16} '
            f'M{cx} {waist} l-13 18 M{cx} {waist} l13 18" '
            f'fill="none" stroke="{BLACK}" stroke-width="1.4" stroke-linecap="round"/>'
            + text(cx, y + 74, label, 11.5, GREY))


def terminator(cx, cy, w, h, label):
    _add("shape", type="rect", box=(cx - w / 2, cy - h / 2, w, h), name=label)
    return (f'<rect x="{cx - w / 2}" y="{cy - h / 2}" width="{w}" height="{h}" rx="{h / 2}" fill="{FILL}" '
            f'stroke="{BLACK}" stroke-width="1.4"/>' + text(cx, cy, label, 12.5, BLACK, 600))


# ---------------------------------------------------------------- connectors


def _marker_defs():
    """Arrowheads. filled: message / flow. hollow: UML realization (triangle). open: UML
    dependency or association (V). one / many: ER crow's-foot ends (exactly one, zero or many).
    aggregation / composition: UML hollow and filled diamonds, used at the whole's end."""
    v = 'orient="auto-start-reverse"'
    return (f'<defs>'
            f'<marker id="a" viewBox="0 0 10 10" refX="9.2" refY="5" markerWidth="7" markerHeight="7" {v}>'
            f'<path d="M0 0 L10 5 L0 10 z" fill="{BLACK}"/></marker>'
            f'<marker id="o" viewBox="0 0 10 10" refX="9.2" refY="5" markerWidth="8" markerHeight="8" {v}>'
            f'<path d="M0.5 0.5 L9.5 5 L0.5 9.5 z" fill="{FILL}" stroke="{BLACK}" stroke-width="1.2"/></marker>'
            f'<marker id="v" viewBox="0 0 10 10" refX="9.4" refY="5" markerWidth="8" markerHeight="8" {v}>'
            f'<path d="M1 1 L9.4 5 L1 9" fill="none" stroke="{BLACK}" stroke-width="1.2"/></marker>'
            f'<marker id="one" viewBox="0 0 16 16" refX="16" refY="8" markerWidth="16" markerHeight="16" '
            f'markerUnits="userSpaceOnUse" {v}>'
            f'<path d="M8 2 V14 M12 2 V14" fill="none" stroke="{BLACK}" stroke-width="1.3"/></marker>'
            f'<marker id="many" viewBox="0 0 16 16" refX="16" refY="8" markerWidth="16" markerHeight="16" '
            f'markerUnits="userSpaceOnUse" {v}>'
            f'<path d="M8 8 L16 1 M8 8 L16 8 M8 8 L16 15" fill="none" stroke="{BLACK}" stroke-width="1.3"/>'
            f'<circle cx="4" cy="8" r="3" fill="{FILL}" stroke="{BLACK}" stroke-width="1.3"/></marker>'
            f'<marker id="zeroone" viewBox="0 0 16 16" refX="16" refY="8" markerWidth="16" markerHeight="16" '
            f'markerUnits="userSpaceOnUse" {v}>'
            f'<path d="M12 2 V14" fill="none" stroke="{BLACK}" stroke-width="1.3"/>'
            f'<circle cx="5" cy="8" r="3" fill="{FILL}" stroke="{BLACK}" stroke-width="1.3"/></marker>'
            f'<marker id="od" viewBox="0 0 22 12" refX="21.5" refY="6" markerWidth="22" markerHeight="12" '
            f'markerUnits="userSpaceOnUse" {v}>'
            f'<path d="M0.5 6 L11 0.7 L21.5 6 L11 11.3 z" fill="{FILL}" stroke="{BLACK}" stroke-width="1.2"/></marker>'
            f'<marker id="fd" viewBox="0 0 22 12" refX="21.5" refY="6" markerWidth="22" markerHeight="12" '
            f'markerUnits="userSpaceOnUse" {v}>'
            f'<path d="M0.5 6 L11 0.7 L21.5 6 L11 11.3 z" fill="{BLACK}" stroke="{BLACK}" stroke-width="1.2"/></marker>'
            f'</defs>')


_MARKER_ID = {"filled": "a", "hollow": "o", "open": "v", "one": "one", "many": "many", "zeroone": "zeroone",
              "aggregation": "od", "composition": "fd"}


def path(points, dashed=False, head=True, back=False, hollow=False, end=None, start=None):
    """Orthogonal connector through the given points (caller supplies the corners).

    end / start choose the marker at each end: "filled", "hollow", "open", "one" or "many".
    Without them, head / back / hollow keep their old meaning (filled or hollow arrowheads).
    """
    end = end or (("hollow" if hollow else "filled") if head else None)
    start = start or (("hollow" if hollow else "filled") if back else None)
    _add("path", pts=[(float(px), float(py)) for px, py in points], head=end is not None,
         back=start is not None, dashed=dashed)
    d = "M" + " L".join(f"{px:.1f} {py:.1f}" for px, py in points)
    dash = ' stroke-dasharray="5 4"' if dashed else ""
    m_end = f' marker-end="url(#{_MARKER_ID[end]})"' if end else ""
    m_start = f' marker-start="url(#{_MARKER_ID[start]})"' if start else ""
    return f'<path d="{d}" fill="none" stroke="{BLACK}" stroke-width="1.3"{dash}{m_end}{m_start}/>'


def label_size(items, size=11.5):
    """Width and height of a label's white backing box (the same formula label() draws)."""
    return max(len(i) for i in items) * size * 0.52 + 12, len(items) * size * 1.35 + 6


def label(x, y, lines, anchor="start", size=11.5, color=GREY, box=True):
    items = lines.split(NL) if isinstance(lines, str) else list(lines)
    out = []
    width, height = label_size(items, size)
    bx = x - 6 if anchor == "start" else (x - width / 2 if anchor == "middle" else x - width + 6)
    _add("label", box=(bx, y - height / 2, width, height), body=" / ".join(items))
    _mute[0] += 1                    # the label registers its own box, not each text line
    if box:
        out.append(f'<rect x="{bx:.1f}" y="{y - height / 2:.1f}" width="{width:.1f}" height="{height:.1f}" fill="{FILL}"/>')
    step = size * 1.35
    top = y - (len(items) - 1) * step / 2
    out += [text(x, top + i * step, line, size, color, anchor=anchor) for i, line in enumerate(items)]
    _mute[0] -= 1
    return "".join(out)


def _tokens(body):
    """Words, with a bracketed group such as '(customer JWT)' kept together as one token."""
    out, depth = [], 0
    for word in body.split():
        if depth > 0:
            out[-1] += " " + word
        else:
            out.append(word)
        depth += word.count("(") - word.count(")")
    return out


def _greedy(tokens, max_chars):
    lines, cur = [], ""
    for word in tokens:
        if cur and len(cur) + 1 + len(word) > max_chars:
            lines.append(cur)
            cur = word
        else:
            cur = f"{cur} {word}" if cur else word
    return lines + [cur]


def _split_long(token, max_chars):
    """Break a token longer than a line after ( . / = , so code like findById(quoteId) can wrap."""
    parts = []
    while len(token) > max_chars:
        ok = lambda c: (3 <= c <= max_chars and len(token) - c >= 3
                        and not (token[c - 1] == "." and (token[c:c + 1] == "." or token[c - 2:c - 1] == ".")))
        spaces = [i + 1 for i, ch in enumerate(token[:max_chars]) if ch == " "]          # best: between words
        after = [i + 1 for i, ch in enumerate(token[:max_chars]) if ch in "(./=,&?_" and i > 0]
        camel = [i for i in range(1, max_chars + 1) if i < len(token) and token[i].isupper() and token[i - 1].islower()]
        cuts = [c for c in spaces if ok(c)] or [c for c in after + camel if ok(c)]
        cut = max(cuts) if cuts else max_chars
        parts.append(token[:cut].rstrip())
        token = token[cut:].lstrip()
    return parts + [token]


def wrap(body, max_chars):
    """Split text into lines of at most max_chars characters, breaking only between words and
    never inside a bracketed group (a group longer than a line is broken after ( . / = ,).
    Lines are balanced, so no single word is left on its own."""
    tokens = [p for t in _tokens(body) for p in _split_long(t, max_chars)]
    lines = _greedy(tokens, max_chars)
    for width in range(max(len(t) for t in tokens), max_chars):    # narrowest width, same line count
        balanced = _greedy(tokens, width)
        if len(balanced) == len(lines):
            return balanced
    return lines


def svg(width, height, body, title):
    width, height = int(-(-width // 1)), int(-(-height // 1))      # whole pixels, rounded up
    _add("canvas", box=(0, 0, width, height))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
            f'role="img" aria-label="{escape(title)}">' + _marker_defs()
            + f'<rect width="{width}" height="{height}" fill="{FILL}"/>' + body + "</svg>")


# ---------------------------------------------------------------- sequence helper


def uml_note(x, y, w, h, lines, size=11):
    """UML note: a white box with a folded top-right corner (lines is a list of strings)."""
    fold = 12
    _add("shape", type="rect", box=(x, y, w, h), name=f"note: {lines[0]}")
    body = (f'<path d="M{x:.1f} {y:.1f} H{x + w - fold:.1f} L{x + w:.1f} {y + fold:.1f} V{y + h:.1f} H{x:.1f} Z" '
            f'fill="{FILL}" stroke="{BLACK}" stroke-width="1.2"/>'
            f'<path d="M{x + w - fold:.1f} {y:.1f} V{y + fold:.1f} H{x + w:.1f}" fill="none" stroke="{BLACK}" '
            f'stroke-width="1.2"/>')
    return body + text_block(x + w / 2, y + h / 2, lines, size, BLACK, lead=1.35)


class Sequence:
    """UML sequence diagram.

    Notation: actors as stick figures, other participants as boxes with underlined names; dashed
    lifelines; activation bars while a participant is busy; synchronous calls are solid lines with a
    filled arrowhead, replies are dashed lines with an open arrowhead; alt fragments with guards.

    Activation follows the calls: a call opens the receiver's bar and its reply closes it; a
    participant that starts an exchange is active until the reply it waits for comes back. Message
    lines are therefore drawn at render time, from bar edge to bar edge.

    Layout rules: a label or note sits in a gap between two lifelines and is wrapped to fit it, so
    it never covers a lifeline or a bar; each row is as tall as its label.
    """

    MARGIN = 12                          # clear space between a label box and a lifeline
    SIZE = 11.5                          # message label font size
    FRAME_PAD = 72                       # frame edge to the first lifeline inside it (room for the tag)
    BAR = 6                              # half width of an activation bar
    GUARD_CHARS = 30                     # guards wrap to this width, which fits any lifeline gap

    def __init__(self, actors, width, top=96, row=46, head_h=46, head_w=168, autonumber=False):
        self.actors = {name: x for name, x in actors}
        self.width = width
        self.top = top
        self.row = row
        self.head_h = head_h
        self.head_w = head_w
        self.items = []
        self.y = top + 40
        self.floor = top                 # lowest y drawn so far; the next row starts below it
        self.autonumber = autonumber
        self.n = 0
        self.frames = []                 # open combined fragments, outermost first
        self.msgs = []                   # (y, src, dst, dashed), drawn at render time
        self.events = []                 # messages and fragment edges in order, for the activations

    # ---------------------------------------------------------------- heads and labels

    def _head(self, name, x, is_actor):
        if is_actor:                     # caption ends exactly where the lifeline starts
            return stickman(x, self.top - ACTOR_H, name)
        _add("shape", type="rect", box=(x - self.head_w / 2, self.top - self.head_h, self.head_w, self.head_h),
             name=name.replace(NL, " "))
        lines = name.split(NL)
        cy = self.top - self.head_h / 2
        step = 12.5 * 1.35
        under = "".join(
            f'<line x1="{x - text_width(t, 12.5) * 0.47:.1f}" y1="{cy - (len(lines) - 1) * step / 2 + i * step + 8:.1f}" '
            f'x2="{x + text_width(t, 12.5) * 0.47:.1f}" y2="{cy - (len(lines) - 1) * step / 2 + i * step + 8:.1f}" '
            f'stroke="{BLACK}" stroke-width="1"/>' for i, t in enumerate(lines))
        return (f'<rect x="{x - self.head_w / 2}" y="{self.top - self.head_h}" width="{self.head_w}" height="{self.head_h}" '
                f'fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>'
                + text_block(x, cy, lines, 12.5, BLACK, 600) + under)

    def heads(self, actor_names=()):
        for name, x in self.actors.items():
            self.items.append(self._head(name, x, name in actor_names))
        return self

    def _fit(self, body, room, prefix=""):
        """Wrap body so its label box is at most `room` pixels wide; `prefix` (the message number)
        always leads the first line instead of standing alone."""
        chars = max(4, int((room - 12) / (self.SIZE * 0.52)) - len(prefix))
        lines = wrap(body, chars)
        lines[0] = prefix + lines[0]
        return lines

    def _touch(self, left, right):
        """Every open frame must enclose this horizontal extent."""
        for f in self.frames:
            f["lo"] = min(f["lo"], left)
            f["hi"] = max(f["hi"], right)

    def _gaps(self, lo, hi):
        xs = sorted(self.actors.values())
        stops = [lo] + [x for x in xs if lo < x < hi] + [hi]
        return list(zip(stops, stops[1:]))

    # ---------------------------------------------------------------- messages and notes

    def msg(self, src, dst, body, dashed=False, note=False):
        x1, x2 = self.actors[src], self.actors[dst]
        prefix = ""
        if self.autonumber:
            self.n += 1
            prefix = f"{self.n}. "
        xs = sorted(self.actors.values())
        if src == dst:                   # self-call: a small loop to the right of the bar
            right = next((x for x in xs if x > x1), self.width)
            lx = x1 + 58
            lines = self._fit(body, right - lx - self.MARGIN + 6, prefix)
            w, h = label_size(lines, self.SIZE)
            y = max(self.y, self.floor + max(10, h / 2 - 11 + 8))
            self.msgs.append((y, src, dst, dashed))
            self.events.append(("msg", y, src, dst, dashed))
            self.items.append(label(lx, y + 11, lines, size=self.SIZE, color=BLACK))
            self._touch(x1, lx - 6 + w)
            self.floor = max(y + 22, y + 11 + h / 2)
            self.y = self.floor + self.row - 22
            return self
        a, b = max(self._gaps(min(x1, x2), max(x1, x2)),
                   key=lambda g: (g[1] - g[0], -abs((g[0] + g[1]) / 2 - x1)))
        lines = self._fit(body, (b - a) - 2 * self.MARGIN, prefix)
        _, h = label_size(lines, self.SIZE)
        y = max(self.y, self.floor + h + 14)
        self.msgs.append((y, src, dst, dashed))
        self.events.append(("msg", y, src, dst, dashed))
        self.items.append(label((a + b) / 2, y - 6 - h / 2, lines, anchor="middle", size=self.SIZE,
                                color=BLACK if not note else GREY))
        self._touch(min(x1, x2), max(x1, x2))
        self.floor = y
        self.y = y + self.row
        return self

    def gap(self, px=14):
        self.y += px
        return self

    def note(self, x_from, x_to, body, pad=10):
        """UML note in the widest gap between lifelines over the given participants (| splits lines)."""
        x1, x2 = sorted((self.actors[x_from], self.actors[x_to]))
        xs = sorted(self.actors.values())
        if x1 == x2:                                 # one participant: a gap beside it, between lifelines
            left = max([x for x in xs if x < x1], default=None)
            right = min([x for x in xs if x > x1], default=None)
            cands = [g for g in ((left, x1), (x1, right)) if None not in g] or [(x1, min(x1 + 240, self.width - 16))]
        else:
            cands = self._gaps(x1, x2)
        a, b = max(cands, key=lambda g: g[1] - g[0])
        room = (b - a) - 2 * self.MARGIN
        lines = wrap(" ".join(body.split(NL)), max(8, int((room - 24) / 6.05)))
        w = min(room, max(text_width(t, 11) for t in lines) + 34)
        h = 14 + len(lines) * 15
        y = max(self.y - h / 2, self.floor + 12)
        self.items.append(uml_note((a + b) / 2 - w / 2, y, w, h, lines))
        self._touch((a + b) / 2 - w / 2, (a + b) / 2 + w / 2)
        self.floor = y + h
        self.y = self.floor + self.row - 6
        return self

    # ---------------------------------------------------------------- combined fragments

    def _guard(self, guard):
        lines = wrap(f"[{guard}]", self.GUARD_CHARS)
        return lines, label_size(lines, 11)[1]

    def alt(self, guard):
        """Open an alt fragment; its first operand runs while `guard` holds."""
        top = self.floor + 16
        lines, h = self._guard(guard)
        self.frames.append({"top": top, "guards": [(top + 24 + h / 2, lines)], "lo": 1e9, "hi": -1e9,
                            "depth": len(self.frames)})
        self.events.append(("alt", top))
        self.floor = top + 24 + h + 6
        self.y = max(self.y, self.floor + 20)
        return self

    def orelse(self, guard):
        """Start the next operand of the innermost open alt."""
        f = self.frames[-1]
        div = self.floor + 16
        lines, h = self._guard(guard)
        f.setdefault("dividers", []).append(div)
        self.events.append(("else", div))
        f["guards"].append((div + 8 + h / 2, lines))
        self.floor = div + 8 + h + 6
        self.y = max(self.y, self.floor + 20)
        return self

    def end(self):
        """Close the innermost fragment and draw it."""
        f = self.frames.pop()
        d = f["depth"]
        left = f["lo"] - self.FRAME_PAD + 12 * d
        right = f["hi"] + 36                          # clear of the rightmost bar at every depth
        bottom = self.floor + 16
        self.events.append(("end", bottom))
        for parent in self.frames:                    # a parent's edges stay 12px outside its child's
            parent["lo"] = min(parent["lo"], left - 12 + self.FRAME_PAD - 12 * parent["depth"])
            parent["hi"] = max(parent["hi"], right + 12 - 36)
        _add("band", box=(left, f["top"], right - left, bottom - f["top"]), name="alt")
        out = [f'<rect x="{left:.1f}" y="{f["top"]:.1f}" width="{right - left:.1f}" height="{bottom - f["top"]:.1f}" '
               f'fill="none" stroke="{BLACK}" stroke-width="1.2"/>',
               f'<path d="M{left:.1f} {f["top"] + 20:.1f} H{left + 34:.1f} L{left + 44:.1f} {f["top"] + 10:.1f} '
               f'V{f["top"]:.1f}" fill="none" stroke="{BLACK}" stroke-width="1.2"/>',
               text(left + 18, f["top"] + 10, "alt", 11.5, BLACK, 700)]
        for div in f.get("dividers", []):
            out.append(f'<line x1="{left:.1f}" y1="{div:.1f}" x2="{right:.1f}" y2="{div:.1f}" stroke="{BLACK}" '
                       f'stroke-width="1" stroke-dasharray="6 4"/>')
        # each guard sits in the widest free gap inside the frame, never over the "alt" tag
        stops = [left + 50] + sorted(x for x in self.actors.values() if left + 50 < x < right) + [right - 6]
        a, b = max(zip(stops, stops[1:]), key=lambda g: g[1] - g[0])
        for gy, lines in f["guards"]:
            out.append(label((a + b) / 2, gy, lines, anchor="middle", size=11, color=BLACK))
        self.items.extend(out)
        self.floor = bottom
        self.y = max(self.y, self.floor + 30)
        return self

    # ---------------------------------------------------------------- activations and rendering

    def _activations(self):
        """Busy intervals per participant, from a call stack replayed over the messages.

        A call pushes (caller, callee). A reply pops frames until it reaches the call its receiver
        made, so participants it skips (an error answered straight to the client) are finished just
        before the reply passes them. When a participant acts again, calls it made and nobody
        answered are taken as returned. Each operand of an alt starts from the state the fragment
        opened with, since only one of them runs.
        """
        names = list(self.actors)
        stack, saved = [], []
        start = dict.fromkeys(names)
        last = dict.fromkeys(names, 0)
        spans = {n: [] for n in names}

        def busy_set():
            return {p for frame in stack for p in frame}

        def close(p, y):
            spans[p].append((start[p], max(y, start[p] + 16)))
            start[p] = None

        def settle(before, y, ends=()):
            after = busy_set()
            for p in before - after:                 # endpoints finish on the line, others just above it
                close(p, y if p in ends else max(last[p], y - 12))
            for p in after - before:
                start[p] = y

        for ev in self.events:
            kind, y = ev[0], ev[1]
            before = busy_set()
            if kind == "alt":
                saved.append(list(stack))
                continue
            if kind == "else":
                stack[:] = saved[-1]
                settle(before, y)
                continue
            if kind == "end":
                saved.pop()
                continue
            _, y, src, dst, dashed = ev
            ends = {src, dst}
            if src in before:                        # unanswered calls of an acting participant are done
                own = max((i for i, f in enumerate(stack) if f[1] == src), default=-1)
                del stack[own + 1:]
            if src == dst:                           # self-call: busy for the loop at least
                settle(before, y, ends)
                if start[src] is None:
                    start[src] = y
                last[src] = y + 22
                if src not in busy_set():
                    close(src, y + 22)
                continue
            if dashed:
                hit = max((i for i, f in enumerate(stack) if f[0] == dst), default=None)
                if hit is not None:
                    del stack[hit:]
            else:
                stack.append((src, dst))
            settle(before, y, ends)
            for p in ends:
                last[p] = y
        for p in names:                              # still busy at the end: stop at the last event
            if start[p] is not None:
                close(p, max(last[p], start[p]))
        return spans

    def render(self, title):
        assert not self.frames, "an alt fragment was left open"
        bottom = self.floor + 30
        for name, x in self.actors.items():
            _add("lifeline", x=x, y1=self.top, y2=bottom, name=name.replace(NL, " "))
        out = [f'<line x1="{x}" y1="{self.top}" x2="{x}" y2="{bottom}" stroke="{BLACK}" stroke-width="1" '
               f'stroke-dasharray="4 5"/>' for x in self.actors.values()]
        spans = self._activations()
        for p, intervals in spans.items():
            x = self.actors[p]
            for a, b in intervals:
                if b - a < 16:
                    a, b = a - 8, a + 8
                _add("shape", type="rect", box=(x - self.BAR, a, 2 * self.BAR, b - a), name=f"activation {p}")
                out.append(f'<rect x="{x - self.BAR}" y="{a:.1f}" width="{2 * self.BAR}" height="{b - a:.1f}" '
                           f'fill="{FILL}" stroke="{BLACK}" stroke-width="1.2"/>')

        def busy(p, y):
            return any(a - 0.5 <= y <= b + 0.5 for a, b in spans[p])

        for y, src, dst, dashed in self.msgs:
            x1, x2 = self.actors[src], self.actors[dst]
            if src == dst:
                x0 = x1 + (self.BAR if busy(src, y) else 0)
                out.append(path([(x0, y), (x1 + 46, y), (x1 + 46, y + 22), (x0, y + 22)], dashed=dashed,
                                end="open" if dashed else "filled"))
                continue
            d = 1 if x2 > x1 else -1
            a = x1 + d * (self.BAR if busy(src, y) else 0)
            b = x2 - d * (self.BAR if busy(dst, y) else 0)
            out.append(path([(a, y), (b, y)], dashed=dashed, end="open" if dashed else "filled"))
        return svg(self.width, bottom + 24, "".join(out) + "".join(self.items), title)
