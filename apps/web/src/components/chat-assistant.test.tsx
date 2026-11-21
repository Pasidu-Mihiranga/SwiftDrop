import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ChatAssistant } from "./chat-assistant";

function sse(...events: [string, unknown][]): string {
  return events.map(([name, data]) => `event: ${name}\ndata: ${JSON.stringify(data)}\n\n`).join("");
}

/** A streamed response; `hold` keeps the stream open until the returned release() is called. */
function streaming(chunks: string[], hold = false) {
  let release: () => void = () => {};
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    async start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
      if (hold) await new Promise<void>((resolve) => (release = resolve));
      controller.close();
    },
  });
  return { response: new Response(body, { status: 200 }), release: () => release() };
}

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => vi.unstubAllGlobals());

async function openWidget(signedIn = false) {
  const user = userEvent.setup();
  render(<ChatAssistant signedIn={signedIn} />);
  await user.click(screen.getByRole("button", { name: "Ask the delivery assistant" }));
  return user;
}

function sentBodies() {
  return fetchMock.mock.calls.map(([, init]) => JSON.parse(String(init?.body)));
}

describe("ChatAssistant", () => {
  it("opens from the floating button, offers guest suggestions and closes with Escape", async () => {
    const user = await openWidget();
    const dialog = screen.getByRole("dialog", { name: "Delivery assistant" });
    expect(within(dialog).getByRole("link", { name: "Sign in" })).toHaveAttribute("href", "/login");
    expect(within(dialog).getByRole("button", { name: "How much to send 2 kg to Kandy?" })).toBeInTheDocument();
    expect(within(dialog).getByRole("textbox", { name: "Message" })).toHaveFocus();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Ask the delivery assistant" })).toHaveFocus();
  });

  it("streams an answer with the lookups it made, and keeps the conversation going", async () => {
    fetchMock
      .mockResolvedValueOnce(
        streaming([
          sse(["tool", { name: "estimate_delivery_fee", label: "Estimated the delivery fee", ok: true }]),
          sse(["token", { text: "Sending 2 kg to **Kandy** costs " }]),
          sse(["token", { text: "LKR 650." }], ["done", { conversationId: "conv-1" }]),
        ]).response,
      )
      .mockResolvedValueOnce(streaming([sse(["token", { text: "Track SD261020K7Q2M9X." }], ["done", { conversationId: "conv-1" }])]).response);

    const user = await openWidget();
    await user.click(screen.getByRole("button", { name: "How much to send 2 kg to Kandy?" }));

    const log = screen.getByRole("log", { name: "Conversation" });
    await within(log).findByText("LKR 650.", { exact: false });
    expect(within(log).getByText("Estimated the delivery fee")).toBeInTheDocument();
    expect(within(log).getByText("Kandy").tagName).toBe("STRONG");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/bff/api/v1/assistant/chat");

    await user.type(screen.getByRole("textbox", { name: "Message" }), "And to Galle?{Enter}");
    expect(await within(log).findByRole("link", { name: "SD261020K7Q2M9X" })).toHaveAttribute("href", "/track/SD261020K7Q2M9X");
    expect(sentBodies()).toEqual([
      { message: "How much to send 2 kg to Kandy?" },
      { message: "And to Galle?", conversationId: "conv-1" },
    ]);
  });

  it("offers the signed-in suggestions and starts over on New chat", async () => {
    fetchMock.mockImplementation(async () => streaming([sse(["token", { text: "You have 2 parcels." }], ["done", { conversationId: "c-7" }])]).response);
    const user = await openWidget(true);
    expect(screen.queryByRole("link", { name: "Sign in" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Where is my parcel?" }));
    await screen.findByText("You have 2 parcels.");

    await user.click(screen.getByRole("button", { name: "New chat" }));
    expect(screen.getByRole("button", { name: "Where is my parcel?" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Where is my parcel?" }));
    await screen.findByText("You have 2 parcels.");
    expect(sentBodies()[1]).toEqual({ message: "Where is my parcel?" });
  });

  it("explains a refusal and an assistant that is not configured", async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: "RATE_LIMITED" }), { status: 429 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: "ASSISTANT_NOT_CONFIGURED" }), { status: 503 }));
    const user = await openWidget();
    const box = screen.getByRole("textbox", { name: "Message" });
    await user.type(box, "hello{Enter}");
    expect(await screen.findByText(/sending messages quickly/)).toBeInTheDocument();
    await user.type(box, "hello again{Enter}");
    expect(await screen.findByText(/not switched on here yet/)).toBeInTheDocument();
  });

  it("shows the assistant's own error event and an empty reply as failures", async () => {
    fetchMock
      .mockResolvedValueOnce(streaming([sse(["error", { code: "ASSISTANT_UNAVAILABLE", message: "Unavailable right now." }], ["done", { conversationId: "c" }])]).response)
      .mockResolvedValueOnce(streaming([sse(["done", { conversationId: "c" }])]).response)
      .mockRejectedValueOnce(new Error("boom"));
    const user = await openWidget();
    const box = screen.getByRole("textbox", { name: "Message" });
    await user.type(box, "one{Enter}");
    expect(await screen.findByText("Unavailable right now.")).toBeInTheDocument();
    await user.type(box, "two{Enter}");
    expect(await screen.findByText(/having trouble right now/)).toBeInTheDocument();
    await user.type(box, "three{Enter}");
    await waitFor(() => expect(screen.getAllByText(/having trouble right now|could not reach/)).toHaveLength(2));
  });

  it("lets the reader stop an answer that is still streaming", async () => {
    let signal: AbortSignal | undefined;
    fetchMock.mockImplementation(async (_url, init) => {
      signal = init?.signal ?? undefined;
      return new Promise<Response>((_resolve, reject) => {
        signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
      });
    });
    const user = await openWidget();
    await user.type(screen.getByRole("textbox", { name: "Message" }), "Where is SD261020K7Q2M9X?");
    await user.keyboard("{Shift>}{Enter}{/Shift}");
    expect(screen.getByRole("textbox", { name: "Message" })).toHaveValue("Where is SD261020K7Q2M9X?\n");
    await user.click(screen.getByRole("button", { name: "Send" }));
    expect(screen.getByLabelText("Thinking")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Stop" }));
    expect(await screen.findByText("Stopped.")).toBeInTheDocument();
    expect(signal?.aborted).toBe(true);
  });

  it("marks a lookup that failed and closes from its own button", async () => {
    const { response, release } = streaming([sse(["tool", { name: "list_my_parcels", label: "Could not use list my parcels", ok: false }])], true);
    fetchMock.mockResolvedValueOnce(response);
    const user = await openWidget();
    await user.click(screen.getByRole("button", { name: "Which areas do you deliver to?" }));
    expect(await screen.findByText("Could not use list my parcels")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Stop" })).toBeInTheDocument();
    release();
    await screen.findByText(/having trouble right now/);
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Close assistant" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
