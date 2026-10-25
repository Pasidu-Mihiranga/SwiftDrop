/**
 * Typed client for the backend-for-frontend proxy.
 *
 * Browser code never talks to the gateway directly and never sees an access token: it calls
 * `/api/bff/<api/v1/...>` and the route handler attaches the token server-side. That also means no
 * API hostname is ever compiled into the bundle.
 */

/** RFC 9457 problem+json, as returned by every SwiftDrop service. */
export interface Problem {
  status: number;
  code: string;
  title?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
}

/** A failed API call, carrying the problem body when the service sent one. */
export class BffError extends Error {
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`);
    this.name = "BffError";
    this.problem = problem;
  }

  get code(): string {
    return this.problem.code;
  }
}

const NETWORK_PROBLEM: Problem = {
  status: 0,
  code: "SERVICE_UNREACHABLE",
  detail: "We could not reach the pricing service. Please try again.",
};

/** The path after the proxy prefix must be a versioned API path; the handler rejects anything else. */
function requireApiPath(path: string): string {
  const normalised = path.replace(/^\/+/, "");
  if (!normalised.startsWith("api/v1/")) {
    throw new Error(`BFF paths must start with "api/v1/", got "${path}"`);
  }
  return normalised;
}

export interface BffRequest {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
}

/**
 * Calls the BFF and returns the parsed body, or throws {@link BffError}.
 *
 * @param fetchImpl injected so this can be unit tested without a browser or a running gateway
 */
export async function bffFetch<T>(
  path: string,
  request: BffRequest = {},
  fetchImpl: typeof fetch = fetch,
): Promise<T> {
  const target = `/api/bff/${requireApiPath(path)}`;
  const method = request.method ?? "GET";
  const hasBody = request.body !== undefined;

  let response: Response;
  try {
    response = await fetchImpl(target, {
      method,
      headers: hasBody ? { "content-type": "application/json" } : {},
      body: hasBody ? JSON.stringify(request.body) : undefined,
      signal: request.signal,
    });
  } catch (cause) {
    // An aborted request is the caller replacing it, not a failure worth reporting.
    if (cause instanceof DOMException && cause.name === "AbortError") throw cause;
    throw new BffError(NETWORK_PROBLEM);
  }

  if (!response.ok) throw new BffError(await readProblem(response));
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/** Reads a problem+json body, falling back to the status when the body is missing or not JSON. */
async function readProblem(response: Response): Promise<Problem> {
  try {
    const body = (await response.json()) as Partial<Problem>;
    return {
      status: body.status ?? response.status,
      code: body.code ?? "UNEXPECTED_ERROR",
      title: body.title,
      detail: body.detail,
      errors: body.errors,
    };
  } catch {
    return { status: response.status, code: "UNEXPECTED_ERROR" };
  }
}

const MESSAGES: Record<string, string> = {
  DISTRICT_NOT_SERVICED: "We do not deliver between those districts yet.",
  NO_RATE_CARD: "We have no published rate for that route yet. Please try another district.",
  AMBIGUOUS_RATE_CARD: "Pricing for that route needs attention. Please try again later.",
  SIZE_CLASS_WEIGHT_EXCEEDED: "That parcel is too heavy for the size you picked. Choose a larger size.",
  QUOTE_NOT_FOUND: "This estimate has expired. Please get a new one.",
  VALIDATION_FAILED: "Please check the details and try again.",
  SERVICE_UNREACHABLE: NETWORK_PROBLEM.detail as string,
  UNAUTHORIZED: "Please sign in and try again.",
  FORBIDDEN: "You do not have permission to do that.",
};

/**
 * A message safe to show a customer. Service details are mapped to plain wording and anything
 * unrecognised falls back to a neutral sentence rather than leaking internals.
 */
export function problemMessage(error: unknown): string {
  if (error instanceof BffError) {
    const known = MESSAGES[error.problem.code];
    if (known) return known;
    if (error.problem.status >= 500) return "The pricing service is having trouble. Please try again shortly.";
    return error.problem.detail ?? "Something went wrong. Please try again.";
  }
  return "Something went wrong. Please try again.";
}

/** Field-level messages from a validation problem, keyed by field name. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof BffError) || !error.problem.errors) return {};
  return Object.fromEntries(error.problem.errors.map((entry) => [entry.field, entry.message]));
}
