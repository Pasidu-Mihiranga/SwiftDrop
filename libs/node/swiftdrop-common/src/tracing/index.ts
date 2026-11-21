/**
 * Distributed tracing for the Node services (M9). Import "@swiftdrop/common/tracing" as the very first
 * line of main.ts: OpenTelemetry patches http and kafkajs when they are loaded, so it must start
 * before them.
 *
 * Incoming HTTP requests and Kafka messages continue the trace in their `traceparent` header (the
 * outbox relay of the Java services sets it on every event), so one trace runs from the gateway
 * through parcel-service and Kafka into tracking, notification and webhook-service. Spans go to the
 * OTLP endpoint (Alloy, then Tempo). Without OTEL_EXPORTER_OTLP_ENDPOINT nothing is started, so tests
 * and local runs are unaffected.
 */
import { NodeSDK } from "@opentelemetry/sdk-node";
import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-http";
import { HttpInstrumentation } from "@opentelemetry/instrumentation-http";
import { KafkaJsInstrumentation } from "@opentelemetry/instrumentation-kafkajs";
import { resourceFromAttributes } from "@opentelemetry/resources";
import { ATTR_SERVICE_NAME } from "@opentelemetry/semantic-conventions";

export interface TracingOptions {
  serviceName: string;
  /** Base OTLP/HTTP endpoint, e.g. http://alloy:4318; traces go to <endpoint>/v1/traces. */
  endpoint?: string;
}

let sdk: NodeSDK | null = null;

/** Starts tracing once; returns false when no endpoint is configured. */
export function startTracing(options: TracingOptions): boolean {
  if (sdk || !options.endpoint) return sdk !== null;
  sdk = new NodeSDK({
    resource: resourceFromAttributes({ [ATTR_SERVICE_NAME]: options.serviceName }),
    traceExporter: new OTLPTraceExporter({ url: `${options.endpoint.replace(/\/+$/, "")}/v1/traces` }),
    instrumentations: [
      // Health and metrics scrapes would otherwise fill Tempo with noise.
      new HttpInstrumentation({ ignoreIncomingRequestHook: (request) => /^\/(health|metrics)/.test(request.url ?? "") }),
      new KafkaJsInstrumentation(),
    ],
  });
  sdk.start();
  process.once("SIGTERM", () => void sdk?.shutdown());
  return true;
}

/** For tests: whether tracing was started in this process. */
export function tracingStarted(): boolean {
  return sdk !== null;
}

startTracing({
  serviceName: process.env.OTEL_SERVICE_NAME ?? process.env.SERVICE_NAME ?? "swiftdrop-node-service",
  endpoint: process.env.OTEL_EXPORTER_OTLP_ENDPOINT,
});
