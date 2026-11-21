"""Figure 10: activity diagram with swimlanes for one merchant order request.

Lanes: the merchant system, the API gateway, parcel-service and pricing-service.

Layout rules:
  - the checks run down one column of the parcel-service lane; each refusal leaves to the right and
    ends at its own response and final node, so no edge crosses another
  - a replay and a conflict share the idempotency decision: the replay continues below it
  - pricing runs in its own lane and comes back into the transaction from the right
"""

import paths  # noqa: F401
from activity import Activity

MAIN, RESP, END = 720, 1000, 1120          # columns inside the parcel-service lane
SPLIT = 880                                # the replay or conflict decision


def build() -> str:
    a = Activity("Create a delivery order by API", [
        ("Merchant system", 260), ("API gateway", 260), ("parcel-service", 620), ("pricing-service", 240)])
    system, gateway, pricing = a.lane(0), a.lane(1), a.lane(3)

    def refuse(y, body):
        resp = a.action(RESP, y, body, w=180)
        end = a.final(END, y)
        a.flow([resp.r, end.l])
        return resp

    s = a.start(system, 125)
    send = a.action(system, 200, "Send the order|token, Idempotency-Key", w=220)
    a.flow([s.b, send.t])
    verify = a.action(gateway, 200, "Check the request|JWT and rate limit", w=210)
    a.flow([send.r, verify.l])
    d0 = a.decision(gateway, 290, "token valid?")
    a.flow([verify.b, d0.t])
    err = a.action(system, 370, "Receive 401 or 429", w=200)
    a.flow([d0.l, (system, 290), err.t], guard="invalid, or|over the limit", at=(225, 262), anchor="middle")
    a.flow([err.b, a.final(system, 450).t])

    claims = a.action(MAIN, 290, "Read the token claims|role and merchant_id", w=270)
    a.flow([d0.r, claims.l], guard="valid", at=(490, 276), anchor="middle")
    d1 = a.decision(MAIN, 380, "may order?")
    a.flow([claims.b, d1.t])
    a.flow([d1.r, refuse(380, "Respond 403|problem+json").l], guard="not MERCHANT_API,|or not APPROVED",
           at=(835, 356), anchor="middle")
    body = a.action(MAIN, 470, "Validate the body|Bean Validation", w=270)
    a.flow([d1.b, body.t], guard="allowed", at=(MAIN + 10, 424))
    d2 = a.decision(MAIN, 560, "body valid?")
    a.flow([body.b, d2.t])
    a.flow([d2.r, refuse(560, "Respond 422|problem+json").l], guard="invalid", at=(835, 546), anchor="middle")
    find = a.action(MAIN, 650, "Find the idempotency record|merchant_id + key, hash the body", w=300)
    a.flow([d2.b, find.t], guard="valid", at=(MAIN + 10, 604))

    d3 = a.decision(MAIN, 740, "record exists?")
    a.flow([find.b, d3.t])
    d4 = a.decision(SPLIT, 740, "same hash?")
    a.flow([d3.r, d4.l], guard="yes", at=(800, 726), anchor="middle")
    a.flow([d4.r, refuse(740, "Respond 409|problem+json").l], guard="different body", at=(941, 700), anchor="middle")
    replay = refuse(830, "Respond 200|the original parcel")
    a.flow([d4.b, (SPLIT, 830), replay.l], guard="same body", at=(SPLIT - 8, 790), anchor="end")

    quote = a.action(MAIN, 920, "Request a fee quote|inline, with a circuit breaker", w=280)
    a.flow([d3.b, quote.t], guard="no record", at=(MAIN + 10, 830))
    price = a.action(pricing, 920, "Price the order|zone, weight, size", w=210)
    a.flow([quote.r, price.l])
    d5 = a.decision(pricing, 1010, "quoted?")
    a.flow([price.b, d5.t])
    busy = a.action(RESP, 1010, "Respond 503|retry with the same key", w=210)
    a.flow([d5.l, busy.r], guard="unavailable", at=(1210, 996), anchor="middle")
    busy_end = a.final(MAIN + 60, 1010)
    a.flow([busy.l, busy_end.r])

    tx = a.action(MAIN, 1100, "One transaction|parcel + idempotency_record + outbox", w=340)
    a.flow([d5.b, (pricing, 1100), tx.r], guard="fee returned", at=(pricing + 10, 1050))
    d6 = a.decision(MAIN, 1190, "key already taken?")
    a.flow([tx.b, d6.t])
    a.flow([d6.r, refuse(1190, "Respond 200|the winner's parcel").l], guard="lost a race",
           at=(848, 1176), anchor="middle")
    ok = a.action(MAIN, 1280, "Respond 201|tracking number and fee", w=250)
    a.flow([d6.b, ok.t], guard="committed", at=(MAIN + 10, 1234))
    done = a.action(system, 1280, "Store the tracking number", w=220)
    a.flow([ok.l, done.r])
    a.flow([done.b, a.final(system, 1360).t])

    a.note(860, 1262, 270, "the outbox relay publishes ParcelCreated, which starts the webhook to the merchant",
           lambda n: [(ok.right, ok.cy), (n.left, ok.cy)])
    return a.render(1420, "Activity: how one merchant order request is decided")
