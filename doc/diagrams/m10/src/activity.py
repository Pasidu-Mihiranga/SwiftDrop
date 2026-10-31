"""UML activity diagram with swimlanes, in the house style.

Notation (follows the reference the user gave):
  - a frame whose tag reads "Use Case: ...", a header row naming each lane, one lane per actor
    or system, separated by vertical lines
  - initial node: a filled circle; final node: a bullseye
  - actions: rounded rectangles, a bold first line and grey detail lines
  - decision and merge nodes: small empty diamonds; each outgoing edge carries its [guard]
  - notes: folded-corner boxes joined to what they explain by a dotted line
  - edges: orthogonal lines with an open arrowhead

The caller places every node and routes every edge (corners given explicitly), as in the other
figures; nodes expose their attachment points (t, b, l, r) so edges meet them exactly.
"""

from draw import (BLACK, FILL, GREY, NL, _add, container, label, path, svg, text, text_block,
                  text_width, uml_note, wrap)

DIAMOND = 34
TITLE, DETAIL = 12.5, 11.5


class Node:
    def __init__(self, cx, cy, w, h):
        self.cx, self.cy, self.w, self.h = cx, cy, w, h

    @property
    def t(self):
        return (self.cx, self.cy - self.h / 2)

    @property
    def b(self):
        return (self.cx, self.cy + self.h / 2)

    @property
    def l(self):
        return (self.cx - self.w / 2, self.cy)

    @property
    def r(self):
        return (self.cx + self.w / 2, self.cy)

    @property
    def top(self):
        return self.cy - self.h / 2

    @property
    def bottom(self):
        return self.cy + self.h / 2

    @property
    def left(self):
        return self.cx - self.w / 2

    @property
    def right(self):
        return self.cx + self.w / 2


class Activity:
    FRAME = 20
    TAG_H = 34
    HEAD_H = 40

    def __init__(self, use_case, lanes):
        """lanes: [(name, width)] from left to right."""
        self.use_case = use_case
        self.lanes = []
        x = self.FRAME
        for name, w in lanes:
            self.lanes.append((name, x, w))
            x += w
        self.right = x
        self.items = []
        self.top = self.FRAME + self.TAG_H + self.HEAD_H      # first y below the lane headers

    def lane(self, i, dx=0):
        """Centre x of lane i, shifted by dx."""
        _, x, w = self.lanes[i]
        return x + w / 2 + dx

    # ------------------------------------------------------------------ nodes

    def start(self, x, y):
        r = 10
        _add("shape", type="ellipse", box=(x - r, y - r, 2 * r, 2 * r), name="initial node")
        self.items.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{BLACK}"/>')
        return Node(x, y, 2 * r, 2 * r)

    def final(self, x, y):
        r = 13
        _add("shape", type="ellipse", box=(x - r, y - r, 2 * r, 2 * r), name="final node")
        self.items.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>'
                          f'<circle cx="{x}" cy="{y}" r="8" fill="{BLACK}"/>')
        return Node(x, y, 2 * r, 2 * r)

    def action(self, x, y, body, w=None):
        """Rounded action box centred on (x, y); body lines are split by | (first line bold)."""
        lines = body.split(NL)
        title, details = lines[0], lines[1:]
        need = max([text_width(title, TITLE)] + [text_width(d, DETAIL) for d in details]) + 30
        w = w or need
        assert w >= need - 0.5, f"action '{title}' needs {need:.0f}px"
        h = 26 + TITLE + len(details) * DETAIL * 1.4
        _add("shape", type="rect", box=(x - w / 2, y - h / 2, w, h), name=title)
        out = [f'<rect x="{x - w / 2:.1f}" y="{y - h / 2:.1f}" width="{w:.1f}" height="{h:.1f}" rx="14" '
               f'fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>']
        ty = y - (len(details) * DETAIL * 1.4) / 2
        out.append(text(x, ty, title, TITLE, BLACK, 600))
        if details:
            out.append(text_block(x, ty + TITLE * 0.6 + 4 + (len(details) * DETAIL * 1.4) / 2, details, DETAIL, GREY,
                                  lead=1.4))
        self.items.append("".join(out))
        return Node(x, y, w, h)

    def decision(self, x, y, name="decision"):
        """Decision or merge node: a small empty diamond; the guards go on its edges."""
        s = DIAMOND / 2
        _add("shape", type="diamond", box=(x - s, y - s, DIAMOND, DIAMOND), name=name)
        self.items.append(f'<polygon points="{x},{y - s} {x + s},{y} {x},{y + s} {x - s},{y}" fill="{FILL}" '
                          f'stroke="{BLACK}" stroke-width="1.4"/>')
        return Node(x, y, DIAMOND, DIAMOND)

    merge = decision

    # ------------------------------------------------------------------ edges and notes

    def flow(self, points, guard=None, at=None, anchor="start"):
        """Control flow through the given corners; guard is drawn as [guard] at `at`."""
        self.items.append(path(points, end="open"))
        if guard:
            lines = [f"[{line}" if i == 0 else line for i, line in enumerate(guard.split(NL))]
            lines[-1] += "]"
            self.items.append(label(at[0], at[1], lines, anchor=anchor, size=11, color=BLACK))

    def note(self, x, y, w, body, link):
        """Note with its top-left corner at (x, y); `link(note)` returns the dotted connector's
        corners, from the note's edge to the element it explains."""
        lines = wrap(body, max(8, int((w - 24) / (11 * 0.55))))
        h = 16 + len(lines) * 15
        n = Node(x + w / 2, y + h / 2, w, h)
        self.items.append(uml_note(x, y, w, h, lines))
        self.items.append(path(link(n), dashed=True, head=False).replace('stroke-dasharray="5 4"',
                                                                          'stroke-dasharray="2 3"'))
        return n

    # ------------------------------------------------------------------ frame

    def render(self, height, title):
        f, width = self.FRAME, self.right + self.FRAME
        bottom = height - f
        head_top = f + self.TAG_H
        container(f, f, self.right - f, bottom - f, "frame")
        out = [f'<rect x="{f}" y="{f}" width="{self.right - f}" height="{bottom - f}" fill="{FILL}" '
               f'stroke="{BLACK}" stroke-width="1.4"/>']
        tag = f"Use Case: {self.use_case}"
        tw = text_width(tag, 12.5) + 24
        out.append(f'<path d="M{f} {head_top} H{f + tw} L{f + tw + 12} {head_top - 12} V{f}" fill="none" '
                   f'stroke="{BLACK}" stroke-width="1.2"/>')
        out.append(text(f + 12, f + self.TAG_H / 2, tag, 12.5, BLACK, 700, anchor="start"))
        out.append(f'<line x1="{f}" y1="{head_top}" x2="{self.right}" y2="{head_top}" stroke="{BLACK}" '
                   f'stroke-width="1.2"/>')
        out.append(f'<line x1="{f}" y1="{self.top}" x2="{self.right}" y2="{self.top}" stroke="{BLACK}" '
                   f'stroke-width="1.2"/>')
        for i, (name, x, w) in enumerate(self.lanes):
            container(x, head_top, w, bottom - head_top, name)
            assert text_width(name, 12.5) < w - 16, f"lane header '{name}' is wider than its lane"
            out.append(text(x + w / 2, head_top + self.HEAD_H / 2, name, 12.5, BLACK, 700))
            if i:
                out.append(f'<line x1="{x}" y1="{head_top}" x2="{x}" y2="{bottom}" stroke="{BLACK}" '
                           f'stroke-width="1.2"/>')
        return svg(width, height, "".join(out) + "".join(self.items), title)
