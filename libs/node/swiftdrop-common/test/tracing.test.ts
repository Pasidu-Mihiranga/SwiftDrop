import { startTracing, tracingStarted } from "../src/tracing";

describe("tracing", () => {
  it("stays off without an OTLP endpoint", () => {
    // The module starts itself from the environment; tests run without OTEL_EXPORTER_OTLP_ENDPOINT.
    expect(tracingStarted()).toBe(false);
    expect(startTracing({ serviceName: "test" })).toBe(false);
    expect(tracingStarted()).toBe(false);
  });

  it("starts once with an endpoint and is idempotent", () => {
    expect(startTracing({ serviceName: "test", endpoint: "http://127.0.0.1:1/" })).toBe(true);
    expect(tracingStarted()).toBe(true);
    expect(startTracing({ serviceName: "other", endpoint: "http://127.0.0.1:1" })).toBe(true);
  });
});
