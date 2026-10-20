import { type ArgumentsHost, Catch, type ExceptionFilter, HttpException, Inject, Optional } from "@nestjs/common";
import type { Logger } from "pino";
import { ApiError, problem, type ProblemDetail } from "../http/problem";
import { LOGGER } from "./tokens";

interface HttpRequest {
  url: string;
  correlationId?: string;
}
interface HttpResponse {
  status(code: number): HttpResponse;
  setHeader(name: string, value: string | string[]): unknown;
  getHeader(name: string): unknown;
  json(body: unknown): unknown;
  end(body?: string): unknown;
}

/**
 * Turns every exception into an RFC 9457 problem+json response with the same shape as the Java
 * services. Unexpected errors are logged and reported as a generic 500 — no stack traces leak.
 */
@Catch()
export class ProblemDetailsFilter implements ExceptionFilter {
  constructor(@Optional() @Inject(LOGGER) private readonly logger?: Logger) {}

  catch(exception: unknown, host: ArgumentsHost): void {
    const http = host.switchToHttp();
    const request = http.getRequest<HttpRequest>();
    const response = http.getResponse<HttpResponse>();
    const body = this.toProblem(exception, request);

    if (body.status >= 500) {
      this.logger?.error({ err: exception, path: request.url }, "Unhandled exception");
    }
    if (body.status === 401 && !response.getHeader("WWW-Authenticate")) {
      response.setHeader("WWW-Authenticate", "Bearer");
    }
    response.setHeader("Content-Type", "application/problem+json");
    response.status(body.status).end(JSON.stringify(body));
  }

  private toProblem(exception: unknown, request: HttpRequest): ProblemDetail {
    const extra = { instance: request.url, traceId: request.correlationId };
    if (exception instanceof ApiError) {
      return problem(exception.status, exception.code, exception.message, extra);
    }
    if (exception instanceof HttpException) {
      const status = exception.getStatus();
      const code = status === 404 ? "NOT_FOUND" : status === 400 ? "BAD_REQUEST" : `HTTP_${status}`;
      // Nest's built-in messages are generic (route not found, validation failed) and safe to return.
      const detail = status < 500 ? exception.message : "An unexpected error occurred.";
      return problem(status, code, detail, extra);
    }
    return problem(500, "INTERNAL_ERROR", "An unexpected error occurred. Quote the traceId when contacting support.", extra);
  }
}
