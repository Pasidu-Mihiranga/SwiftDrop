"""Figure 1: use cases.

Two columns of use cases inside the boundary. The merchant's own use cases sit on the left, next
to the merchant user and the merchant system; the admin's sit on the right, next to the admin.
Keycloak sits level with the two use cases that reach it, so each joins it with one straight line.
"""

import paths  # noqa: F401
from draw import BLACK, FILL, GREY, container, label, node, path, rounded, stickman, svg, text

W, H = 1580, 1020
UC_W, UC_H = 310, 54
LEFT_X, RIGHT_X = 380, 930
L_R, R_R = LEFT_X + UC_W, RIGHT_X + UC_W
L_CX, R_CX = LEFT_X + UC_W / 2, RIGHT_X + UC_W / 2
BOUNDARY = (340, 50, 930, 930)
BUS_L, BUS_R = 270, 1330
ARM = 30

LEFT = {"register": 100, "status": 190, "filter": 280, "dashboard": 380, "export": 480, "credentials": 580,
        "rotate": 680, "create": 800, "list": 890}
RIGHT = {"review": 120, "reject": 240, "suspend": 360, "approve": 560, "client": 680, "token": 800}
CAPTIONS = {"register": "Register as a merchant", "status": "View application status",
            "filter": "Filter and search orders", "dashboard": "View the orders dashboard",
            "export": "Export orders to CSV", "credentials": "View API credentials", "rotate": "Rotate the client secret",
            "create": "Create a delivery order by API", "list": "List own orders by API",
            "review": "Review merchant applications", "reject": "Reject with a reason",
            "suspend": "Suspend a merchant", "approve": "Approve a merchant",
            "client": "Manage the API client in Keycloak", "token": "Obtain an access token"}


def mid(rows, key):
    return rows[key] + UC_H / 2


def build() -> str:
    b = []
    bx, by, bw, bh = BOUNDARY
    container(bx, by, bw, bh, "system boundary")
    b.append(f'<rect x="{bx}" y="{by}" width="{bw}" height="{bh}" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>')
    b.append(text(bx + bw / 2, by + 24, "SwiftDrop  ·  system boundary of Kavinu Saputhanthri's part", 12.5, GREY, 600))
    for key, y in LEFT.items():
        b.append(rounded(LEFT_X, y, UC_W, UC_H, [CAPTIONS[key]]))
    for key, y in RIGHT.items():
        b.append(rounded(RIGHT_X, y, UC_W, UC_H, [CAPTIONS[key]]))

    # ---- merchant user: the arm meets a bus, one arrow enters each primary use case
    keys = ["register", "status", "dashboard", "credentials"]
    arm = mid(LEFT, "dashboard")
    b.append(stickman(110, arm - ARM, "Merchant user"))
    b.append(path([(126, arm), (BUS_L, arm)], head=False))
    b.append(path([(BUS_L, mid(LEFT, keys[0])), (BUS_L, mid(LEFT, keys[-1]))], head=False))
    for key in keys:
        b.append(path([(BUS_L, mid(LEFT, key)), (LEFT_X, mid(LEFT, key))]))

    # ---- merchant system: the online store, authenticated by its own client
    arm = (mid(LEFT, "create") + mid(LEFT, "list")) / 2
    b.append(stickman(110, arm - ARM, "Merchant system"))
    b.append(path([(126, arm), (BUS_L, arm)], head=False))
    b.append(path([(BUS_L, mid(LEFT, "create")), (BUS_L, mid(LEFT, "list"))], head=False))
    for key in ("create", "list"):
        b.append(path([(BUS_L, mid(LEFT, key)), (LEFT_X, mid(LEFT, key))]))

    # ---- admin
    keys = ["review", "reject", "suspend", "approve"]
    arm = mid(RIGHT, "suspend")
    b.append(stickman(1470, arm - ARM, "Admin"))
    b.append(path([(1454, arm), (BUS_R, arm)], head=False))
    b.append(path([(BUS_R, mid(RIGHT, keys[0])), (BUS_R, mid(RIGHT, keys[-1]))], head=False))
    for key in keys:
        b.append(path([(BUS_R, mid(RIGHT, key)), (R_R, mid(RIGHT, key))]))

    # ---- «extend»: the extension sits next to its base and points at it
    def relation(x, y1, y2, caption):
        return path([(x, y1), (x, y2)], dashed=True, end="open") + label(x + 8, (y1 + y2) / 2, caption, size=11)

    b.append(relation(L_CX, LEFT["filter"] + UC_H, LEFT["dashboard"], "«extend»"))
    b.append(relation(L_CX, LEFT["export"], LEFT["dashboard"] + UC_H, "«extend»"))
    b.append(relation(L_CX, LEFT["rotate"], LEFT["credentials"] + UC_H, "«extend»"))

    # ---- «include»: approval and rotation both reach Keycloak; every API call needs a token
    b.append(relation(R_CX, RIGHT["approve"] + UC_H, RIGHT["client"], "«include»"))
    y = mid(RIGHT, "client")
    b.append(path([(L_R, y), (RIGHT_X, y)], dashed=True, end="open"))
    b.append(label((L_R + RIGHT_X) / 2, y - 16, "«include»", anchor="middle", size=11))
    y = mid(RIGHT, "token")
    b.append(path([(L_R, y), (RIGHT_X, y)], dashed=True, end="open"))
    b.append(label((L_R + RIGHT_X) / 2, y - 16, "«include»", anchor="middle", size=11))

    # ---- Keycloak: level with both use cases that depend on it
    kc_top = mid(RIGHT, "client") - 50
    kc_h = mid(RIGHT, "token") - mid(RIGHT, "client") + 100
    b.append(node(1340, kc_top, 200, kc_h, "Keycloak", ["identity provider", "Admin API · /token"], dashed=True))
    for key in ("client", "token"):
        b.append(path([(1340, mid(RIGHT, key)), (R_R, mid(RIGHT, key))]))
    return svg(W, H, "".join(b), "Use cases for merchant onboarding and the merchant API")
