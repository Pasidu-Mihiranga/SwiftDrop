"""Distributed tracing (M9): incoming requests and the calls to the gateway join the caller's trace.

FastAPI requests continue the trace in their ``traceparent`` header, and every httpx call to the gateway
carries it on, so a chat turn shows up in Tempo next to the pricing, parcel and tracking calls it made.
Nothing starts without ``OTEL_EXPORTER_OTLP_ENDPOINT``, so tests and local runs are unaffected.
"""

import os

from fastapi import FastAPI
from httpx import AsyncClient

_provider = None


def setup_tracing(app: FastAPI, client: AsyncClient, endpoint: str | None = None,
                  service_name: str = "ai-assistant-service") -> bool:
    """Instruments the app and the gateway client; returns False when no endpoint is configured."""
    global _provider
    endpoint = endpoint if endpoint is not None else os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT")
    if not endpoint:
        return False
    from opentelemetry import trace
    from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
    from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
    from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
    from opentelemetry.sdk.resources import Resource
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import BatchSpanProcessor

    if _provider is None:
        _provider = TracerProvider(resource=Resource.create({"service.name": service_name}))
        _provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter(endpoint=f"{endpoint.rstrip('/')}/v1/traces")))
        trace.set_tracer_provider(_provider)
    FastAPIInstrumentor.instrument_app(app, tracer_provider=_provider, excluded_urls="health,metrics")
    HTTPXClientInstrumentor.instrument_client(client, tracer_provider=_provider)
    return True
