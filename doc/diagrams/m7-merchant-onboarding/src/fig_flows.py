"""Figures 5-9 and 11-13: sequence diagrams, the merchant state diagram and two class diagrams."""

import paths  # noqa: F401
from draw import BLACK, FILL, NL, Sequence, label, path, rounded, shape_rect, svg
from figures_flows import _realizes, _uml_class, _uses

BFF = "Next.js BFF" + NL + "and gateway"
PORTAL = "Merchant portal" + NL + "and BFF"
MS = "merchant-service"


def fig_seq_register() -> str:
    s = Sequence([("Merchant user", 90), (BFF, 360), (MS, 650), ("merchants_db", 930), ("Kafka", 1180)], width=1290)
    s.heads(actor_names={"Merchant user"})
    s.msg("Merchant user", BFF, "submit the /merchant/register form")
    s.msg(BFF, MS, "POST /api/v1/merchants/register  (bearer JWT)")
    s.msg(MS, MS, "Bean Validation on every field")
    s.msg(MS, "merchants_db", "is this BR number already registered?")
    s.msg("merchants_db", MS, "result", dashed=True)
    s.alt("BR number already registered")
    s.msg(MS, BFF, "409 problem+json", dashed=True)
    s.msg(BFF, "Merchant user", "show the conflict on the form", dashed=True)
    s.orelse("new application")
    s.msg(MS, "merchants_db", "one transaction: merchant (PENDING) + outbox row")
    s.msg("merchants_db", MS, "committed", dashed=True)
    s.msg(MS, BFF, "201 Created, status PENDING", dashed=True)
    s.msg(BFF, "Merchant user", "show the pending state", dashed=True)
    s.msg(MS, "Kafka", "outbox relay publishes MerchantLifecycleChanged")
    s.end()
    s.note("Kafka", "Kafka", "notification-service emails the applicant")
    return s.render("Sequence: a user registers a business")


def fig_seq_approve() -> str:
    s = Sequence([("Admin", 90), (BFF, 340), (MS, 620), ("Keycloak", 880), ("merchants_db", 1110),
                  ("Kafka", 1330)], width=1440)
    s.heads(actor_names={"Admin"})
    s.msg("Admin", BFF, "approve in the approval queue")
    s.msg(BFF, MS, "POST /api/v1/admin/merchants/{id}/approve")
    s.msg(MS, MS, "role is ADMIN and the merchant is PENDING")
    s.msg(MS, "Keycloak", "create client merchant-<id>: client credentials, MERCHANT_API, merchant_id claim, audience")
    s.msg("Keycloak", MS, "client created, or found on a retry", dashed=True)
    s.msg(MS, "Keycloak", "owner: set attribute merchant_id, grant role MERCHANT")
    s.msg("Keycloak", MS, "done", dashed=True)
    s.alt("Keycloak unavailable")
    s.msg(MS, BFF, "503 problem+json, merchant stays PENDING", dashed=True)
    s.msg(BFF, "Admin", "show the error, retrying is safe", dashed=True)
    s.orelse("all Keycloak calls succeeded")
    s.msg(MS, "merchants_db", "one transaction: APPROVED + api_credential + outbox row")
    s.msg("merchants_db", MS, "committed", dashed=True)
    s.msg(MS, BFF, "200 OK, status APPROVED", dashed=True)
    s.msg(BFF, "Admin", "remove from the queue", dashed=True)
    s.msg(MS, "Kafka", "outbox relay publishes MerchantLifecycleChanged")
    s.end()
    s.note("Keycloak", "Kafka", "the owner gets the new role and merchant_id claim at the next sign-in")
    return s.render("Sequence: an admin approves a merchant")


def fig_seq_order() -> str:
    M, GW, P, Q, W = "Merchant system", "API gateway", "parcel-service", "pricing-service", "webhook-service"
    s = Sequence([(M, 100), ("Keycloak", 330), (GW, 560), (P, 800), (Q, 1030), ("Kafka", 1240), (W, 1450)],
                 width=1720)
    s.heads()
    s.msg(M, "Keycloak", "POST /token  grant_type=client_credentials")
    s.msg("Keycloak", M, "access token: merchant_id, MERCHANT_API, audience swiftdrop-api", dashed=True)
    s.msg(M, GW, "POST /api/v1/merchant/orders  (bearer, Idempotency-Key)")
    s.msg(GW, GW, "verify the JWT, apply the per-client rate limit")
    s.msg(GW, P, "forward with X-Correlation-Id")
    s.msg(P, P, "role MERCHANT_API, merchant_id taken from the token only")
    s.msg(P, P, "status cache says APPROVED")
    s.msg(P, P, "no idempotency record for this merchant and key")
    s.msg(P, Q, "POST /api/v1/pricing/quotes")
    s.msg(Q, P, "fee and quote id", dashed=True)
    s.msg(P, P, "one transaction: parcel + idempotency_record + outbox row")
    s.msg(P, M, "201 Created: tracking number, fee", dashed=True)
    s.msg(P, "Kafka", "outbox relay publishes ParcelCreated")
    s.msg("Kafka", W, "deliver to group webhook-service")
    s.msg(W, W, "sign with HMAC-SHA256, merchant's own secret")
    s.msg(W, M, "POST parcel.created webhook  (X-SwiftDrop-Signature)")
    s.msg(M, W, "200 OK", dashed=True)
    s.note(M, GW, "non-2xx or timeout: M8's retry policy takes over, with backoff and a dead-letter queue")
    return s.render("Sequence: a merchant system creates an order through the API")


def fig_seq_rotate() -> str:
    s = Sequence([("Merchant user", 90), (BFF, 380), (MS, 700), ("Keycloak", 980), ("merchants_db", 1240)], width=1340)
    s.heads(actor_names={"Merchant user"})
    s.msg("Merchant user", BFF, "open credentials, choose Rotate secret, confirm")
    s.msg(BFF, MS, "POST /api/v1/merchants/me/credentials/rotate")
    s.msg(MS, MS, "merchant_id from the token, status APPROVED")
    s.msg(MS, "Keycloak", "regenerate the secret of client merchant-<id>")
    s.msg("Keycloak", MS, "new secret", dashed=True)
    s.msg(MS, "merchants_db", "api_credential: set rotated_at")
    s.msg("merchants_db", MS, "committed", dashed=True)
    s.msg(MS, BFF, "200 OK: clientId, clientSecret, tokenUrl", dashed=True)
    s.msg(BFF, "Merchant user", "show once, with a copy button and a curl example", dashed=True)
    s.note(MS, "merchants_db", "the secret is never stored in merchants_db or logged")
    s.note("Merchant user", BFF, "leaving the page loses the secret: it can only be rotated again")
    return s.render("Sequence: a merchant rotates the API secret")


def fig_seq_dashboard() -> str:
    s = Sequence([("Merchant user", 90), (PORTAL, 390), ("API gateway", 680), ("parcel-service", 950),
                  ("parcels_db", 1210)], width=1320)
    s.heads(actor_names={"Merchant user"})
    s.msg("Merchant user", PORTAL, "open /merchant/orders, set status and date filters")
    s.msg(PORTAL, "API gateway", "GET /api/v1/merchant/orders?status&from&to&page")
    s.msg("API gateway", "API gateway", "verify the JWT")
    s.msg("API gateway", "parcel-service", "forward with X-Correlation-Id")
    s.msg("parcel-service", "parcel-service", "role MERCHANT or MERCHANT_API, merchant_id from the token")
    s.msg("parcel-service", "parcels_db", "select orders where merchant_id = claim")
    s.msg("parcels_db", "parcel-service", "page of orders", dashed=True)
    s.msg("parcel-service", PORTAL, "200 page of orders", dashed=True)
    s.msg(PORTAL, "Merchant user", "table with status chips and search by order ref", dashed=True)
    s.msg("Merchant user", PORTAL, "Export to CSV")
    s.msg(PORTAL, PORTAL, "build the file from the same filtered results")
    s.msg(PORTAL, "Merchant user", "download orders.csv", dashed=True)
    s.note("API gateway", "parcels_db", "no merchant_id parameter exists, so merchant A cannot ask for merchant B's orders")
    return s.render("Sequence: a merchant views and exports orders")


def fig_state() -> str:
    """Left to right along the happy path; the only side exit is the rejection."""
    W, H = 1340, 520
    b = []

    def state(x, y, name):
        return rounded(x, y, 180, 64, [name])

    y = 120
    mid = y + 32
    pending_x, approved_x, suspended_x = 230, 650, 1070
    start_x = 70
    shape_rect(start_x - 10, mid - 10, 20, 20, "initial node")
    b.append(f'<circle cx="{start_x}" cy="{mid}" r="10" fill="{BLACK}"/>')
    b += [state(pending_x, y, "PENDING"), state(approved_x, y, "APPROVED"), state(suspended_x, y, "SUSPENDED"),
          state(pending_x, 340, "REJECTED")]
    b.append(path([(start_x + 10, mid), (pending_x, mid)], end="open"))
    b.append(label((start_x + 10 + pending_x) / 2, mid - 28, "register|duplicate BR: 409", anchor="middle", size=11))
    b.append(path([(pending_x + 180, mid), (approved_x, mid)], end="open"))
    b.append(label((pending_x + 180 + approved_x) / 2, mid - 40,
                   "approve [ADMIN]|/ provision Keycloak client,|grant MERCHANT role", anchor="middle", size=11))
    b.append(path([(approved_x + 180, mid), (suspended_x, mid)], end="open"))
    b.append(label((approved_x + 180 + suspended_x) / 2, mid - 40,
                   "suspend [ADMIN]|/ API orders refused with 403,|webhooks paused", anchor="middle", size=11))
    px = pending_x + 90
    b.append(path([(px, y + 64), (px, 340)], end="open"))
    b.append(label(px + 12, 262, "reject [ADMIN] / store the reason", size=11))

    fy = 372
    fx = 700
    shape_rect(fx - 13, fy - 13, 26, 26, "final node")
    b.append(f'<circle cx="{fx}" cy="{fy}" r="13" fill="{FILL}" stroke="{BLACK}" stroke-width="1.4"/>'
             f'<circle cx="{fx}" cy="{fy}" r="8" fill="{BLACK}"/>')
    b.append(path([(pending_x + 180, fy), (fx - 13, fy)], end="open"))
    return svg(W, H, "".join(b), "State diagram: merchant lifecycle")


def fig_class_merchant() -> str:
    """Columns: api (60-340), application (440-760), domain and ports (860-1180)."""
    W, H = 1240, 680
    b = []
    register, _ = _uml_class(60, 60, 280, "MerchantController", "«api.v1»",
                             ["+ register(request)", "+ me()", "+ rotateCredentials()"])
    admin, _ = _uml_class(60, 270, 280, "AdminMerchantController", "«api.v1»",
                          ["+ list(status)", "+ approve · reject · suspend"])
    service, service_h = _uml_class(440, 60, 320, "MerchantOnboardingService", "«application»",
                                    ["+ register(command)", "+ approve(id, admin)", "+ reject(id, reason, admin)",
                                     "+ suspend(id, admin)", "+ rotate(owner)"])
    outbox, _ = _uml_class(440, 340, 320, "OutboxWriter", "«shared library»", ["+ publish(event)"])
    merchant, _ = _uml_class(860, 60, 320, "Merchant", "«domain»",
                             ["- status: MerchantStatus", "+ approve(admin)", "+ reject(admin, reason)",
                              "+ suspend(admin)"])
    repo, _ = _uml_class(860, 270, 320, "MerchantRepository", "«port»", ["+ save(merchant)", "+ findByOwner(id)"])
    provisioner, _ = _uml_class(440, 470, 320, "IdentityProvisioner", "«port»",
                                ["+ provisionClient(merchant)", "+ grantMerchantRole(owner, id)",
                                 "+ rotateSecret(clientId)"])
    keycloak, _ = _uml_class(860, 480, 320, "KeycloakAdminAdapter", "«adapter»", ["Keycloak Admin REST API"])
    b += [register, admin, service, outbox, merchant, repo, provisioner, keycloak]

    b.append(_uses([(340, 110), (440, 110)]))
    b.append(_uses([(340, 300), (400, 300), (400, 170), (440, 170)]))
    b.append(_uses([(760, 110), (860, 110)]))
    b.append(label(810, 94, "state rules", anchor="middle", size=11))
    b.append(_uses([(760, 150), (810, 150), (810, 300), (860, 300)]))
    b.append(_uses([(600, 60 + service_h), (600, 340)]))
    b.append(label(610, (60 + service_h + 340) / 2, "same transaction", size=11))
    b.append(_uses([(760, 180), (790, 180), (790, 495), (760, 495)]))
    b.append(label(800, 430, "calls Keycloak|before it commits", size=11))
    b.append(_realizes([(860, 540), (760, 540)]))
    b.append(label(810, 524, "implements", anchor="middle", size=11))
    return svg(W, H, "".join(b), "Design patterns in merchant-service")


def fig_class_orders() -> str:
    """Columns: api and consumer (60-340), application (440-760), ports (860-1180)."""
    W, H = 1240, 640
    b = []
    controller, _ = _uml_class(60, 60, 280, "MerchantOrderController", "«api.v1»",
                               ["+ create(key, request, token)", "+ list(filters, token)"])
    listener, _ = _uml_class(60, 420, 280, "MerchantLifecycleListener", "«infrastructure»",
                             ["+ onLifecycleChanged(value)"])
    service, service_h = _uml_class(440, 60, 320, "MerchantOrderService", "«application»",
                                    ["+ create(merchantId, key, order)", "+ list(merchantId, filters)"])
    policy, _ = _uml_class(440, 260, 320, "IdempotencyPolicy", "«domain»",
                           ["+ decide(record, requestHash)", "NEW · REPLAY · CONFLICT"])
    cache, _ = _uml_class(440, 420, 320, "MerchantStatusCache", "«port»", ["+ statusOf(merchantId)", "+ update(event)"])
    quotes, quotes_h = _uml_class(860, 60, 320, "QuoteClient", "«port»", ["+ quote(order)"])
    pricing, _ = _uml_class(860, 210, 320, "PricingServiceClient", "«adapter»", ["timeout · retry · circuit breaker"])
    records, _ = _uml_class(860, 360, 320, "IdempotencyStore", "«port»", ["+ find(merchantId, key)", "+ save(record)"])
    b += [controller, listener, service, policy, cache, quotes, pricing, records]

    b.append(_uses([(340, 110), (440, 110)]))
    b.append(_uses([(340, 470), (440, 470)]))
    b.append(label(370, 488, "updates", anchor="middle", size=11))
    b.append(_uses([(760, 110), (860, 110)]))
    b.append(_realizes([(1020, 210), (1020, 60 + quotes_h)]))
    b.append(label(1030, (60 + quotes_h + 210) / 2, "implements", size=11))
    b.append(_uses([(600, 60 + service_h), (600, 260)]))
    b.append(label(610, (60 + service_h + 260) / 2, "replay or conflict?", size=11))
    b.append(_uses([(760, 140), (810, 140), (810, 400), (860, 400)]))
    b.append(_uses([(440, 140), (400, 140), (400, 450), (440, 450)]))
    b.append(label(390, 320, "APPROVED?", anchor="end", size=11))
    return svg(W, H, "".join(b), "Design patterns in the merchant order API")
