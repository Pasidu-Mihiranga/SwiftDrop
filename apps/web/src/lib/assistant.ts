/**
 * Client for the delivery assistant (ai-assistant-service, POST /api/v1/assistant/chat).
 *
 * The answer streams back as server-sent events. EventSource only does GET, so the stream is read
 * from fetch and parsed here. Like every browser call it goes through the BFF, which attaches the
 * user's token when there is one: signed-in users get their own parcels, guests the public tools.
 * Event shapes mirror contracts/openapi/ai-assistant-service.v1.yaml.
 */

export const CHAT_PATH = "/api/bff/api/v1/assistant/chat";
export const MAX_MESSAGE_LENGTH = 2000;

export interface ToolChip {
  name: string;
  label: string;
  ok: boolean;
}

export type AssistantEvent =
  | { type: "token"; text: string }
  | { type: "tool"; tool: ToolChip }
  | { type: "error"; code: string; message: string }
  | { type: "done"; conversationId: string };

/** A failure before any answer arrived, already worded for the person using the widget. */
export class AssistantError extends Error {
  constructor(
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "AssistantError";
  }
}

const FAILURES: Record<string, string> = {
  RATE_LIMITED: "You are sending messages quickly. Please wait a minute and try again.",
  ASSISTANT_NOT_CONFIGURED:
    "The assistant is not switched on here yet. You can still track a parcel or get a price from the menu.",
  VALIDATION_FAILED: "That message could not be sent. Please shorten it and try again.",
  UNREACHABLE: "We could not reach the assistant. Please check your connection and try again.",
  UNEXPECTED_ERROR: "The assistant is having trouble right now. Please try again shortly.",
};

export function failureMessage(code: string): string {
  return FAILURES[code] ?? FAILURES.UNEXPECTED_ERROR;
}

/**
 * Splits a text/event-stream into events. Feed it chunks as they arrive, in any size; it holds back
 * an incomplete event until the rest of it comes.
 */
export class SseParser {
  private buffer = "";

  feed(chunk: string): AssistantEvent[] {
    this.buffer += chunk.replace(/\r\n?/g, "\n");
    const events: AssistantEvent[] = [];
    let end: number;
    while ((end = this.buffer.indexOf("\n\n")) >= 0) {
      const block = this.buffer.slice(0, end);
      this.buffer = this.buffer.slice(end + 2);
      const event = parseBlock(block);
      if (event) events.push(event);
    }
    return events;
  }
}

function parseBlock(block: string): AssistantEvent | null {
  let name = "message";
  const data: string[] = [];
  for (const line of block.split("\n")) {
    if (!line || line.startsWith(":")) continue;
    const colon = line.indexOf(":");
    const field = colon < 0 ? line : line.slice(0, colon);
    const value = colon < 0 ? "" : line.slice(colon + 1).replace(/^ /, "");
    if (field === "event") name = value;
    else if (field === "data") data.push(value);
  }
  let payload: Record<string, unknown>;
  try {
    payload = JSON.parse(data.join("\n")) as Record<string, unknown>;
  } catch {
    return null;
  }
  switch (name) {
    case "token":
      return typeof payload.text === "string" ? { type: "token", text: payload.text } : null;
    case "tool":
      return {
        type: "tool",
        tool: { name: String(payload.name ?? ""), label: String(payload.label ?? ""), ok: payload.ok === true },
      };
    case "error":
      return {
        type: "error",
        code: String(payload.code ?? "UNEXPECTED_ERROR"),
        message: typeof payload.message === "string" ? payload.message : failureMessage("UNEXPECTED_ERROR"),
      };
    case "done":
      return typeof payload.conversationId === "string" ? { type: "done", conversationId: payload.conversationId } : null;
    default:
      return null;
  }
}

/**
 * Sends one message and calls `onEvent` for each event as it streams in.
 *
 * Throws {@link AssistantError} when the request is refused or the assistant cannot be reached, and
 * rethrows an AbortError when the caller cancels.
 */
export async function streamChat(
  request: { message: string; conversationId?: string },
  onEvent: (event: AssistantEvent) => void,
  options: { signal?: AbortSignal; fetchImpl?: typeof fetch } = {},
): Promise<void> {
  const fetchImpl = options.fetchImpl ?? fetch;
  let response: Response;
  try {
    response = await fetchImpl(CHAT_PATH, {
      method: "POST",
      headers: { "content-type": "application/json", accept: "text/event-stream" },
      body: JSON.stringify(request),
      signal: options.signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === "AbortError") throw cause;
    throw new AssistantError("UNREACHABLE", failureMessage("UNREACHABLE"));
  }

  if (!response.ok || !response.body) {
    const code = await problemCode(response);
    throw new AssistantError(code, failureMessage(code));
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const parser = new SseParser();
  for (;;) {
    const { done, value } = await reader.read();
    // stream: true keeps a multi-byte character that is split across chunks intact.
    const text = done ? decoder.decode() : decoder.decode(value, { stream: true });
    for (const event of parser.feed(done ? `${text}\n\n` : text)) onEvent(event);
    if (done) break;
  }
}

async function problemCode(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { code?: unknown };
    if (typeof body.code === "string" && body.code in FAILURES) return body.code;
  } catch {
    // not problem+json; fall through to the status
  }
  if (response.status === 429) return "RATE_LIMITED";
  if (response.status === 400) return "VALIDATION_FAILED";
  return "UNEXPECTED_ERROR";
}

// ---- answer formatting -----------------------------------------------------------------------

export type Inline = { kind: "text" | "strong"; text: string } | { kind: "tracking"; text: string; href: string };
export type Block = { kind: "paragraph"; parts: Inline[] } | { kind: "list"; items: Inline[][] };

const TRACKING_NUMBER = /\bSD[0-9A-Z]{8,20}\b/g;

/**
 * Turns an answer into paragraphs and bullet lists, with **bold** and tracking numbers linked to the
 * tracking page. Only this small subset is understood; everything else stays plain text, and React
 * escapes it, so a model reply can never inject markup.
 */
export function formatAnswer(text: string): Block[] {
  const blocks: Block[] = [];
  for (const chunk of text.split(/\n{2,}/)) {
    const lines = chunk.split("\n").filter((line) => line.trim());
    if (lines.length === 0) continue;
    const bullet = /^\s*(?:[-*•]|\d+[.)])\s+/;
    if (lines.every((line) => bullet.test(line))) {
      blocks.push({ kind: "list", items: lines.map((line) => inline(line.replace(bullet, ""))) });
    } else {
      blocks.push({ kind: "paragraph", parts: inline(lines.join("\n")) });
    }
  }
  return blocks;
}

function inline(text: string): Inline[] {
  const parts: Inline[] = [];
  text.split(/(\*\*[^*]+\*\*)/).forEach((segment) => {
    if (!segment) return;
    if (segment.startsWith("**") && segment.endsWith("**") && segment.length > 4) {
      parts.push({ kind: "strong", text: segment.slice(2, -2) });
      return;
    }
    let last = 0;
    for (const match of segment.matchAll(TRACKING_NUMBER)) {
      if (match.index > last) parts.push({ kind: "text", text: segment.slice(last, match.index) });
      parts.push({ kind: "tracking", text: match[0], href: `/track/${match[0]}` });
      last = match.index + match[0].length;
    }
    if (last < segment.length) parts.push({ kind: "text", text: segment.slice(last) });
  });
  return parts;
}
