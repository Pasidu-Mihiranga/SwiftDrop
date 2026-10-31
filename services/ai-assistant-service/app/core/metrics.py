"""Assistant meters, registered on the service's /metrics registry. Labels are closed sets: no ids, no text."""

from prometheus_client import CollectorRegistry, Counter

from app.privacy import PiiKind


class AssistantMetrics:
    def __init__(self, registry: CollectorRegistry | None = None) -> None:
        # registry=None keeps the counters unregistered, which is what unit tests want
        self._turns = Counter("assistant_turns", "Chat turns by outcome", ["outcome"], registry=registry)
        self._tools = Counter("assistant_tool_calls", "Tool calls by tool and result", ["tool", "result"],
                              registry=registry)
        self._redactions = Counter("assistant_pii_redactions", "Personal data replaced or masked, by kind",
                                   ["kind"], registry=registry)

    def turn(self, outcome: str) -> None:
        self._turns.labels(outcome).inc()

    def tool_call(self, tool: str, ok: bool) -> None:
        self._tools.labels(tool, "ok" if ok else "error").inc()

    def redaction(self, kind: PiiKind) -> None:
        self._redactions.labels(kind.value).inc()
