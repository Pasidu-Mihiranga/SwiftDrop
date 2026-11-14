"""The M7 figure registry: ids, titles and captions. Writes every figure to build/<id>.svg."""

from pathlib import Path

import paths  # noqa: F401
import draw
import fig_activity
import fig_architecture
import fig_er
import fig_flows
import fig_integration
import fig_usecases

FIGURES = {
    "01-use-cases": fig_usecases.build,
    "02-er-diagram": fig_er.build,
    "03-architecture": fig_architecture.build,
    "04-integration-design": fig_integration.build,
    "05-seq-register": fig_flows.fig_seq_register,
    "06-seq-approve": fig_flows.fig_seq_approve,
    "07-seq-merchant-api-order": fig_flows.fig_seq_order,
    "08-seq-rotate-secret": fig_flows.fig_seq_rotate,
    "09-seq-orders-dashboard": fig_flows.fig_seq_dashboard,
    "10-activity-order-request": fig_activity.build,
    "11-state-merchant": fig_flows.fig_state,
    "12-class-merchant-service": fig_flows.fig_class_merchant,
    "13-class-merchant-orders": fig_flows.fig_class_orders,
}

TITLES = {
    "01-use-cases": "Use cases: merchant onboarding and the merchant API",
    "02-er-diagram": "Data model: merchants, credentials and idempotency records",
    "03-architecture": "Architecture: layered microservices with a BFF",
    "04-integration-design": "Integration design: the patterns behind onboarding and orders",
    "05-seq-register": "Sequence: a user registers a business",
    "06-seq-approve": "Sequence: an admin approves a merchant",
    "07-seq-merchant-api-order": "Sequence: a merchant system creates an order through the API",
    "08-seq-rotate-secret": "Sequence: a merchant rotates the API secret",
    "09-seq-orders-dashboard": "Sequence: a merchant views and exports orders",
    "10-activity-order-request": "Activity: how one merchant order request is decided",
    "11-state-merchant": "State diagram: merchant lifecycle",
    "12-class-merchant-service": "Design patterns in merchant-service",
    "13-class-merchant-orders": "Design patterns in the merchant order API",
}

CAPTIONS = {
    "01-use-cases": "A merchant user works through the portal, a merchant system through the API, and an admin decides "
                    "every application. Keycloak is the external system behind provisioning, rotation and tokens. "
                    "«include» points to the use case that is always included; «extend» points to the use case it extends.",
    "02-er-diagram": "Solid crow's-foot lines are foreign keys inside one database. Dashed lines cross a service boundary "
                     "by id or by event, never by foreign key. No client secret is stored in either database. PARCEL shows "
                     "only the columns the merchant API uses; M3 owns the full table.",
    "03-architecture": "Four tiers. People reach the services through the BFF; the merchant's own system calls the gateway "
                       "directly with a client-credentials token. merchant-service is the only service that calls the "
                       "Keycloak Admin API.",
    "04-integration-design": "Numbers match the pattern table. Patterns 1 to 5 carry events; patterns 6 to 10 serve "
                             "requests that need an immediate answer.",
    "05-seq-register": "Any signed-in user can apply. The application and its event commit together, and the applicant is "
                       "told about a duplicate business registration number at once.",
    "06-seq-approve": "Keycloak is called before the commit and every call is safe to repeat, so a failure leaves the "
                      "merchant PENDING and the admin simply approves again.",
    "07-seq-merchant-api-order": "merchant_id always comes from the signed token, never from the request body. The "
                                 "webhook is signed by webhook-service, owned by M8.",
    "08-seq-rotate-secret": "The first secret is issued the same way, after approval, because the approving admin must "
                            "never see it. Only Keycloak keeps the secret.",
    "09-seq-orders-dashboard": "Portal users and API clients read the same endpoint. The merchant_id claim scopes every "
                               "query, so isolation does not depend on the caller's input.",
    "10-activity-order-request": "Each check that fails ends at its own response. Retrying with the same key can never "
                                 "create a second parcel, even when two requests race.",
    "11-state-merchant": "Only an admin moves a merchant between states, and each change publishes "
                         "MerchantLifecycleChanged. Reinstating a suspended merchant is outside issue #20.",
    "12-class-merchant-service": "Ports keep the application layer free of Keycloak and JPA; the Merchant class holds the "
                                 "state rules as plain Java. Dashed open arrow: depends on. Dashed hollow triangle: implements.",
    "13-class-merchant-orders": "The order service depends on ports for quotes, idempotency records and merchant status; "
                                "the idempotency decision is a pure domain policy that is simple to unit test.",
}


def main() -> None:
    out = paths.HERE / "build"
    out.mkdir(exist_ok=True)
    for name, fn in FIGURES.items():
        draw.reset()
        (out / f"{name}.svg").write_text(fn(), encoding="utf-8")
    print(f"built {len(FIGURES)} figures")


if __name__ == "__main__":
    main()
