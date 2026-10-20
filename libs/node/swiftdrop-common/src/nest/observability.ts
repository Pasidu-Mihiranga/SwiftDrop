import { Controller, Get, Header, Injectable, type NestMiddleware } from "@nestjs/common";
import { collectDefaultMetrics, Histogram, Registry } from "prom-client";
import { CORRELATION_HEADER, resolveCorrelationId } from "../http/problem";
import { Public } from "./auth";

export const metricsRegistry = new Registry();
collectDefaultMetrics({ register: metricsRegistry });

/** Same metric name and labels as Spring Boot, so one Grafana dashboard covers every service. */
const httpRequests = new Histogram({
  name: "http_server_requests_seconds",
  help: "HTTP server request duration",
  labelNames: ["method", "uri", "status"],
  buckets: [0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10],
  registers: [metricsRegistry],
});

interface Req {
  method: string;
  originalUrl?: string;
  url: string;
  route?: { path?: string };
  baseUrl?: string;
  headers: Record<string, string | string[] | undefined>;
  correlationId?: string;
}
interface Res {
  statusCode: number;
  setHeader(name: string, value: string): unknown;
  on(event: "finish", listener: () => void): unknown;
}

/** Assigns/propagates X-Correlation-Id and records request metrics. */
@Injectable()
export class RequestContextMiddleware implements NestMiddleware<Req, Res> {
  use(req: Req, res: Res, next: () => void): void {
    req.correlationId = resolveCorrelationId(req.headers[CORRELATION_HEADER]);
    res.setHeader("X-Correlation-Id", req.correlationId);
    const stop = httpRequests.startTimer({ method: req.method });
    res.on("finish", () => {
      // Use the route template (/parcels/:id), never the raw URL, to keep label cardinality low.
      const uri = req.route?.path ? `${req.baseUrl ?? ""}${req.route.path}` : "UNKNOWN";
      stop({ uri, status: String(res.statusCode) });
    });
    next();
  }
}

@Controller()
export class OpsController {
  @Get("metrics")
  @Public()
  @Header("Content-Type", "text/plain; version=0.0.4")
  metrics(): Promise<string> {
    return metricsRegistry.metrics();
  }

  @Get("health/live")
  @Public()
  live(): { status: string } {
    return { status: "UP" };
  }
}
