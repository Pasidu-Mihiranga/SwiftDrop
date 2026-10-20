import pino, { type DestinationStream, type Logger, type LoggerOptions } from "pino";
import { maskPii } from "./pii";

export interface LoggerConfig {
  service: string;
  level?: string;
  /** Extra fields added to every line. */
  base?: Record<string, unknown>;
}

/**
 * JSON logger with the same field names as the Java services
 * (@timestamp, level, service, message, traceId, correlationId) and PII masking on every message.
 */
export function createLogger(config: LoggerConfig, destination?: DestinationStream): Logger {
  const options: LoggerOptions = {
    level: config.level ?? process.env.LOG_LEVEL ?? "info",
    messageKey: "message",
    timestamp: () => `,"@timestamp":"${new Date().toISOString()}"`,
    base: { service: config.service, ...config.base },
    formatters: {
      level: (label) => ({ level: label.toUpperCase() }),
      log: (object) => maskObject(object) as Record<string, unknown>,
    },
    hooks: {
      logMethod(args, method) {
        const masked = args.map((arg) => (typeof arg === "string" ? maskPii(arg) : arg));
        return method.apply(this, masked as Parameters<typeof method>);
      },
    },
    redact: {
      paths: ["password", "*.password", "authorization", "*.authorization", "headers.authorization", "secret", "*.secret"],
      censor: "***",
    },
  };
  return destination ? pino(options, destination) : pino(options);
}

function maskObject(value: unknown, depth = 0): unknown {
  if (depth > 5 || value === null || value === undefined) return value;
  if (typeof value === "string") return maskPii(value);
  if (Array.isArray(value)) return value.map((v) => maskObject(v, depth + 1));
  if (value instanceof Error) return { type: value.name, message: maskPii(value.message), stack: maskPii(value.stack) };
  if (typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, maskObject(v, depth + 1)]));
  }
  return value;
}
