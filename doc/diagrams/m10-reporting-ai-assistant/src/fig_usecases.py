"""Figure 1: use cases.

UML notation: include / extend are dashed lines with an open arrowhead and a guillemet label.
«include» points from the base use case to the included one; «extend» points from the
extension to the base use case it extends.

Layout rules, so no two lines touch:
  - a use case that is included sits directly below or above its base, so the «include» is a
    short vertical arrow between neighbours (Ask -> Track <- MCP, Reports -> Filter)
  - the two relations that need a side lane are nested: the inner lane serves the nearer use
    case and attaches higher on the shared box, so the lanes can never cross
  - every external actor or system sits level with its use case and joins it with one
    straight line that enters below or above the lanes, never across them
"""

from draw import BLACK, FILL, GREY, container, label, node, path, rounded, stickman, svg, text

W, H = 1580, 980
UC_X, UC_W, UC_H = 430, 390, 56
UC_R = UC_X + UC_W                            # right edge of every use case
UC_CX = UC_X + UC_W / 2
BOUNDARY = (400, 60, 700, 890)                # x, y, w, h
LANE_IN, LANE_OUT = 880, 1000                 # side lanes inside the boundary
EXT_X = 1300                                  # left edge of the external boxes
BUS_X = 330                                   # where each actor group's lines meet
ARM = 30                                      # stickman: arm height below the head top

ROWS = {"fee": 110, "mine": 200, "assistant": 300, "track": 410, "mcp": 520,
        "reports": 660, "filter": 770, "couriers": 860}
CAPTIONS = {"fee": "Estimate a delivery fee", "mine": "List my parcels and orders",
            "assistant": "Ask the delivery assistant", "track": "Track a parcel by number",
            "mcp": "Use SwiftDrop tools over MCP", "reports": "View delivery reports",
            "filter": "Filter by date range and zone", "couriers": "Compare courier performance"}


def top(key):
    return ROWS[key]


def bottom(key):
    return ROWS[key] + UC_H


def mid(key):
    return ROWS[key] + UC_H / 2


def build() -> str:
    b = []
    bx, by, bw, bh = BOUNDARY
    container(bx, by, bw, bh, "system boundary")
    b.append(f'<rect x="{bx}" y="{by}" width="{bw}" height="{bh}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>')
    b.append(text(bx + bw / 2, by + 26, "SwiftDrop  ·  M10 system boundary", 12.5, GREY, 600))
    for key, y in ROWS.items():
        b.append(rounded(UC_X, y, UC_W, UC_H, [CAPTIONS[key]]))

    # ---- primary actors: each arm line meets the group's bus, one arrow enters the use case
    def actor_group(actors, target):
        out = []
        arms = []
        for name, arm_y in actors:
            out.append(stickman(120, arm_y - ARM, name))
            out.append(path([(120 + 16, arm_y), (BUS_X, arm_y)], head=False))
            arms.append(arm_y)
        out.append(path([(BUS_X, min(arms)), (BUS_X, max(arms))], head=False))
        out.append(path([(BUS_X, mid(target)), (UC_X, mid(target))]))
        return "".join(out)

    m = mid("assistant")
    b.append(actor_group([("Guest visitor", m - 130), ("Customer", m), ("Merchant", m + 130)], "assistant"))
    r = mid("reports")
    b.append(actor_group([("Dispatcher", r - 65), ("Admin", r + 65)], "reports"))

    # ---- «include»: short vertical arrows between neighbouring use cases
    def include_down(src, dst):
        return (path([(UC_CX, bottom(src)), (UC_CX, top(dst))], dashed=True, end="open")
                + label(UC_CX + 8, (bottom(src) + top(dst)) / 2, "«include»", size=11))

    def include_up(src, dst):
        return (path([(UC_CX, top(src)), (UC_CX, bottom(dst))], dashed=True, end="open")
                + label(UC_CX + 8, (top(src) + bottom(dst)) / 2, "«include»", size=11))

    b.append(include_down("assistant", "track"))
    b.append(include_up("mcp", "track"))
    b.append(include_down("reports", "filter"))

    # ---- side lanes, nested: the inner lane attaches higher on "Ask", the outer one lower
    a_top = top("assistant")
    b.append(path([(UC_R, mid("mine")), (LANE_IN, mid("mine")), (LANE_IN, a_top + 14), (UC_R, a_top + 14)],
                  dashed=True, end="open"))                                      # extension -> base
    b.append(label(LANE_IN + 8, (mid("mine") + a_top + 14) / 2, "«extend»|(signed in)", size=11))
    b.append(path([(UC_R, a_top + 28), (LANE_OUT, a_top + 28), (LANE_OUT, mid("fee")), (UC_R, mid("fee"))],
                  dashed=True, end="open"))                                      # base -> included
    b.append(label(LANE_OUT + 8, (mid("fee") + a_top + 28) / 2, "«include»", size=11))
    r_top = top("reports")
    b.append(path([(UC_R, mid("couriers")), (LANE_IN, mid("couriers")), (LANE_IN, r_top + 40), (UC_R, r_top + 40)],
                  dashed=True, end="open"))
    b.append(label(LANE_IN + 8, (mid("couriers") + r_top + 40) / 2, "«extend»", size=11))

    # ---- external systems and the courier: each level with its use case, one straight line
    llm_y = a_top + 42                         # below both lanes' attach points on "Ask"
    b.append(node(EXT_X, llm_y - 37, 240, 74, "LLM provider", ["OpenAI or Gemini"], dashed=True))
    b.append(path([(EXT_X, llm_y), (UC_R, llm_y)], dashed=True))
    b.append(label(BOUNDARY[0] + BOUNDARY[2] + 12, llm_y - 26, "answers|generated by", size=11))

    mcp_y = mid("mcp")
    b.append(node(EXT_X, mcp_y - 37, 240, 74, "External MCP client", ["MCP Inspector, IDE agents"], dashed=True))
    b.append(path([(EXT_X, mcp_y), (UC_R, mcp_y)]))

    courier_y = r_top + 16                     # above the «extend» attach point on "View reports"
    b.append(stickman(1420, courier_y - ARM, "Courier"))
    b.append(path([(1420 - 16, courier_y), (UC_R, courier_y)], dashed=True))
    b.append(label(BOUNDARY[0] + BOUNDARY[2] + 12, courier_y - 26, "status updates|become report data", size=11))
    return svg(W, H, "".join(b), "Use cases for reporting and the delivery assistant")
