import { describe, expect, it, vi } from "vitest";
import { AssistantError, type AssistantEvent, CHAT_PATH, SseParser, failureMessage, formatAnswer, streamChat } from "./assistant";

/** A streamed response whose body arrives in exactly these chunks. */
function sseResponse(chunks: string[], init: ResponseInit = {}): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
      controller.close();
    },
  });
  return new Response(body, { status: 200, headers: { "content-type": "text/event-stream" }, ...init });
}

describe("SseParser", () => {
  it("reassembles events split anywhere, with CRLF, comments and multi-line data", () => {
    const parser = new SseParser();
    expect(parser.feed('event: tool\r\ndata: {"name":"track_parcel",')).toEqual([]);
    expect(parser.feed('"label":"Checked tracking SD1234567890","ok":true}\r\n\r\n: keep-alive\n\nevent: token\ndata: {"text":')).toEqual([
      { type: "tool", tool: { name: "track_parcel", label: "Checked tracking SD1234567890", ok: true } },
    ]);
    expect(parser.feed('\ndata: "Out for delivery"}\n\nevent: done\ndata: {"conversationId":"c-1"}\n\n')).toEqual([
      { type: "token", text: "Out for delivery" },
      { type: "done", conversationId: "c-1" },
    ]);
  });

  it("drops what it cannot understand rather than guessing", () => {
    const parser = new SseParser();
    const events = parser.feed(
      [
        "event: token\ndata: not json",
        "event: token\ndata: {\"other\":1}",
        "event: done\ndata: {}",
        "event: mystery\ndata: {}",
        "data",
        "event: tool\ndata: {}",
        "event: error\ndata: {\"code\":\"ASSISTANT_UNAVAILABLE\",\"message\":\"Try later\"}",
        "event: error\ndata: {}",
      ].join("\n\n") + "\n\n",
    );
    expect(events).toEqual([
      { type: "tool", tool: { name: "", label: "", ok: false } },
      { type: "error", code: "ASSISTANT_UNAVAILABLE", message: "Try later" },
      { type: "error", code: "UNEXPECTED_ERROR", message: failureMessage("UNEXPECTED_ERROR") },
    ]);
  });
});

describe("streamChat", () => {
  it("posts through the BFF and reports each event as it streams", async () => {
    const fetchImpl = vi.fn(async () =>
      sseResponse(['event: token\ndata: {"text":"Rs. 6', '50"}\n\nevent: done\ndata: {"conversationId":"c-9"}']),
    );
    const events: AssistantEvent[] = [];
    await streamChat({ message: "How much to Kandy?", conversationId: "c-8" }, (e) => events.push(e), { fetchImpl });

    expect(fetchImpl).toHaveBeenCalledWith(CHAT_PATH, expect.objectContaining({ method: "POST" }));
    const [, init] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit];
    expect(JSON.parse(init.body as string)).toEqual({ message: "How much to Kandy?", conversationId: "c-8" });
    expect(events).toEqual([
      { type: "token", text: "Rs. 650" },
      { type: "done", conversationId: "c-9" },
    ]);
  });

  it.each([
    [429, { code: "RATE_LIMITED" }, "RATE_LIMITED"],
    [429, "slow down", "RATE_LIMITED"],
    [503, { code: "ASSISTANT_NOT_CONFIGURED" }, "ASSISTANT_NOT_CONFIGURED"],
    [400, { code: "SOMETHING_NEW" }, "VALIDATION_FAILED"],
    [502, { code: "UPSTREAM_ERROR" }, "UNEXPECTED_ERROR"],
  ])("words a %i refusal for the reader", async (status, body, code) => {
    const fetchImpl = vi.fn(async () => new Response(typeof body === "string" ? body : JSON.stringify(body), { status }));
    const error = await streamChat({ message: "hi" }, () => {}, { fetchImpl }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(AssistantError);
    expect((error as AssistantError).code).toBe(code);
    expect((error as AssistantError).message).toBe(failureMessage(code));
  });

  it("separates a cancelled request from an unreachable assistant", async () => {
    const aborted = vi.fn(async () => {
      throw new DOMException("aborted", "AbortError");
    });
    await expect(streamChat({ message: "hi" }, () => {}, { fetchImpl: aborted })).rejects.toMatchObject({ name: "AbortError" });

    const offline = vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    });
    await expect(streamChat({ message: "hi" }, () => {}, { fetchImpl: offline })).rejects.toMatchObject({ code: "UNREACHABLE" });
  });
});

describe("formatAnswer", () => {
  it("keeps paragraphs, lists, bold and links tracking numbers", () => {
    expect(formatAnswer("Your parcels:\n\n- **SD261020K7Q2M9X** is out for delivery\n- One more\n\nThanks!\nBye")).toEqual([
      { kind: "paragraph", parts: [{ kind: "text", text: "Your parcels:" }] },
      {
        kind: "list",
        items: [
          [
            { kind: "strong", text: "SD261020K7Q2M9X" },
            { kind: "text", text: " is out for delivery" },
          ],
          [{ kind: "text", text: "One more" }],
        ],
      },
      { kind: "paragraph", parts: [{ kind: "text", text: "Thanks!\nBye" }] },
    ]);
    expect(formatAnswer("Track SD261020K7Q2M9X now.")[0]).toEqual({
      kind: "paragraph",
      parts: [
        { kind: "text", text: "Track " },
        { kind: "tracking", text: "SD261020K7Q2M9X", href: "/track/SD261020K7Q2M9X" },
        { kind: "text", text: " now." },
      ],
    });
  });

  it("leaves markup as text and skips empty blocks", () => {
    expect(formatAnswer("<b>hi</b> ****\n\n\n\n")).toEqual([{ kind: "paragraph", parts: [{ kind: "text", text: "<b>hi</b> ****" }] }]);
  });
});
