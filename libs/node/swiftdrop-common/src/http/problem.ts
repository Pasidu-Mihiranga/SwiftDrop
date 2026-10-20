import { randomUUID } from "node:crypto";

export const CORRELATION_HEADER = "x-correlation-id";
const VALID_CORRELATION_ID = /^[A-Za-z0-9-]{8,64}$/;

/** Keeps a well-formed incoming correlation id, otherwise creates one (prevents log injection). */
export function resolveCorrelationId(incoming: string | string[] | undefined): string {
  const value = Array.isArray(incoming) ? incoming[0] : incoming;
  return value && VALID_CORRELATION_ID.test(value) ? value : randomUUID();
}

/** RFC 9457 problem document with SwiftDrop's extra members (same shape as the Java services). */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  code: string;
  traceId?: string;
  timestamp: string;
  errors?: { field: string; message: string }[];
}

const TITLES: Record<number, string> = {
  400: "Bad Request",
  401: "Unauthorized",
  403: "Forbidden",
  404: "Not Found",
  409: "Conflict",
  422: "Unprocessable Content",
  429: "Too Many Requests",
  500: "Internal Server Error",
  503: "Service Unavailable",
};

export function problem(status: number, code: string, detail: string, extra: Partial<ProblemDetail> = {}): ProblemDetail {
  return {
    type: `https://api.swiftdrop.lk/problems/${code.toLowerCase().replaceAll("_", "-")}`,
    title: TITLES[status] ?? "Error",
    status,
    detail,
    code,
    timestamp: new Date().toISOString(),
    ...extra,
  };
}

/** Business error that maps to an HTTP status. The message is shown to clients: no personal data. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }

  static notFound(resource: string, id: string): ApiError {
    return new ApiError(404, `${resource.toUpperCase().replaceAll(" ", "_")}_NOT_FOUND`, `${resource} ${id} was not found`);
  }
}
