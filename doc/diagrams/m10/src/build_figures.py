"""Writes every M10 figure to bw/<id>.svg."""

from pathlib import Path

import draw

import fig_activity
import fig_er
import fig_system_er
import fig_system_architecture
import fig_system_eer
import fig_integration
import fig_architecture
import fig_usecases
import figures_flows as flows

FIGURES = {
    "01-use-cases": fig_usecases.build,
    "02-er-diagram": fig_er.build,
    "03-architecture": fig_architecture.build,
    "04-integration-design": fig_integration.build,
    "05-seq-report-projection": flows.fig_seq_projection,
    "06-seq-view-reports": flows.fig_seq_reports,
    "07-seq-where-is-my-parcel": flows.fig_seq_where,
    "08-seq-fee-estimate": flows.fig_seq_fee,
    "09-seq-mcp-client": flows.fig_seq_mcp,
    "10-activity-agent-loop": fig_activity.build,
    "11-class-assistant": flows.fig_class_assistant,
    "12-class-reporting": flows.fig_class_reporting,
    "s1-system-er-diagram": fig_system_er.build,
    "s2-system-architecture": fig_system_architecture.build,
    "s3-system-eer-diagram": fig_system_eer.build,
}

TITLES = {
    "01-use-cases": "Use cases: reporting and the delivery assistant",
    "02-er-diagram": "Data model: reporting projections and assistant memory",
    "03-architecture": "Architecture: layered microservices with a BFF",
    "04-integration-design": "Integration design: the patterns M10 relies on",
    "05-seq-report-projection": "Sequence: a courier status update becomes report data",
    "06-seq-view-reports": "Sequence: an admin opens the reports dashboard",
    "07-seq-where-is-my-parcel": "Sequence: \"Where is my parcel?\" for a signed-in customer",
    "08-seq-fee-estimate": "Sequence: a guest asks for a delivery fee",
    "09-seq-mcp-client": "Sequence: an external MCP client uses SwiftDrop tools",
    "10-activity-agent-loop": "Activity: how the assistant answers one message",
    "11-class-assistant": "Design patterns in the AI assistant",
    "12-class-reporting": "Design patterns in reporting-service",
    "s1-system-er-diagram": "Data model: every database of every service",
    "s2-system-architecture": "Architecture: every service, store and integration",
    "s3-system-eer-diagram": "EER diagram: the whole SwiftDrop domain in Chen notation",
}

CAPTIONS = {
    "01-use-cases": "Customers, merchants and guests use the assistant; dispatchers and admins read reports. Couriers "
                    "have no direct use case here: their status updates are the data the reports count. "
                    "«include» points to the use case that is always included; «extend» points to the use case it extends.",
    "02-er-diagram": "Two fact tables written from Kafka events feed daily rollups, rebuilt per business day. Redis "
                     "keeps chat memory for 24 hours as pseudonymized text; real values sit in the session's encrypted "
                     "PII vault. Crow's foot: a real relationship. Dashed arrow: derived rows, no foreign key.",
    "03-architecture": "Four tiers. The M10 services sit in the application tier; reports are read models fed by "
                       "Kafka, and the assistant reaches every other service only through the gateway with the user's token.",
    "04-integration-design": "Numbers match the pattern table. Asynchronous patterns (1-5) build the reports; "
                             "synchronous ones (6-11) serve users and the assistant. Pattern 11 sits on the link to "
                             "the model: personal data is replaced by placeholders before any request leaves SwiftDrop.",
    "05-seq-report-projection": "The event that triggers notifications and webhooks is consumed independently by "
                                "reporting-service. A duplicate delivery is detected and counted only once. Days are "
                                "rebuilt from the facts rather than incremented, so the result does not depend on the "
                                "order in which events arrive.",
    "06-seq-view-reports": "The browser never holds the access token: the BFF adds it server-side. Gateway and "
                           "service both validate it, and the service enforces the DISPATCHER or ADMIN role.",
    "07-seq-where-is-my-parcel": "The assistant calls the same APIs as the web app with the customer's own token, so "
                                 "it can only see that customer's parcels. Names, phone numbers, emails and addresses "
                                 "are swapped for placeholders before anything reaches the model and swapped back only "
                                 "in the answer streamed to this customer.",
    "08-seq-fee-estimate": "Guests get public tools only. With the pickup city missing, the model asks for it before "
                           "calling the pricing tool. The fee shown is an example value.",
    "09-seq-mcp-client": "The same tool implementations serve the built-in chat and any MCP client. The client signs "
                         "in through Keycloak and the token travels with every tool call. MCP clients are third-party AI "
                         "apps, so personal data in tool results is masked irreversibly by default.",
    "10-activity-agent-loop": "Every guardrail is a decision in this loop: input limits, rate limiting, tool scope by "
                              "sign-in state, an iteration cap, and a scope check before anything is streamed back. "
                              "Personal data is pseudonymized on the way in and restored only for the user on the way out.",
    "11-class-assistant": "Strategy for the LLM provider, a factory that picks it from configuration, an adapter over "
                          "the gateway, one tool registry shared by the chat agent and the MCP server, and a PII redactor "
                          "that pseudonymizes for the model and masks for MCP clients. Dashed open arrow: depends on. Solid "
                          "open arrow: holds a field of that type. Dashed hollow triangle: implements.",
    "12-class-reporting": "Layered and hexagonal: the Kafka listeners and the REST controller are adapters around two "
                          "application services, which reach the database only through ports implemented by JDBC "
                          "adapters. The idempotent consumer comes from the shared library and the business calendar is "
                          "plain domain code. Dashed open arrow: depends on. Dashed hollow triangle: implements.",
    "s1-system-er-diagram": "Database per service: six PostgreSQL databases, three MongoDB databases and Redis keys. "
                            "Foreign keys exist only inside one database (solid crow's-foot lines); ids that belong to "
                            "another service are marked ref and are never foreign keys. Outbox and processed-event "
                            "tables come from the shared library.",
    "s2-system-architecture": "Every request enters through the BFF and the API gateway; services own their stores and "
                              "integrate through Kafka (arrow into Kafka = publish via the outbox, arrow out = consume). "
                              "Outbound calls leave from three services only: email and SMS, signed merchant webhooks, "
                              "and the LLM provider.",
    "s3-system-eer-diagram": "Conceptual model of the whole platform in Chen notation. A user can hold several roles "
                             "(overlapping specialization); a parcel is either booked by a customer or placed as a "
                             "merchant order (disjoint). Weak entities depend on their owner: status changes on a "
                             "parcel, messages on a chat session, daily statistics on a zone or a courier.",
}


def main() -> None:
    out = Path("bw")
    out.mkdir(exist_ok=True)
    for name, fn in FIGURES.items():
        draw.reset()                      # each figure starts with an empty geometry record
        (out / f"{name}.svg").write_text(fn(), encoding="utf-8", newline="\n")
    print(f"built {len(FIGURES)} figures")


if __name__ == "__main__":
    main()
