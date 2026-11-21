"use client";

import { type FormEvent, useState, useRef, useEffect } from "react";

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
  tools?: string[];
}

export function AiAssistantWidget() {
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: "assistant",
      content:
        "Hello! I am the SwiftDrop AI Delivery Assistant (M10). Ask me to estimate a delivery fee, check zone coverage, or track a parcel.",
    },
  ]);
  const [conversationId, setConversationId] = useState<string | undefined>(undefined);
  const [streaming, setStreaming] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, open]);

  async function handleSend(e: FormEvent) {
    e.preventDefault();
    const prompt = input.trim();
    if (!prompt || streaming) return;

    setInput("");
    setMessages((prev) => [...prev, { role: "user", content: prompt }, { role: "assistant", content: "", tools: [] }]);
    setStreaming(true);

    try {
      const res = await fetch("/api/bff/api/v1/assistant/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: prompt, conversationId }),
      });

      if (!res.ok || !res.body) {
        const problem = await res.json().catch(() => ({}));
        const detail = problem.detail || `Assistant unavailable (HTTP ${res.status}).`;
        setMessages((prev) => {
          const copy = [...prev];
          copy[copy.length - 1] = { role: "assistant", content: detail };
          return copy;
        });
        return;
      }

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const frames = buffer.split("\n\n");
        buffer = frames.pop() ?? "";

        for (const frame of frames) {
          let eventName = "message";
          let dataLine = "";
          for (const line of frame.split("\n")) {
            if (line.startsWith("event:")) eventName = line.slice(6).trim();
            else if (line.startsWith("data:")) dataLine = line.slice(5).trim();
          }
          if (!dataLine) continue;
          try {
            const payload = JSON.parse(dataLine);
            if (eventName === "token" && typeof payload.text === "string") {
              setMessages((prev) => {
                const copy = [...prev];
                const last = copy[copy.length - 1];
                copy[copy.length - 1] = { ...last, content: last.content + payload.text };
                return copy;
              });
            } else if (eventName === "tool" && typeof payload.tool === "string") {
              setMessages((prev) => {
                const copy = [...prev];
                const last = copy[copy.length - 1];
                const tools = [...(last.tools ?? []), payload.tool];
                copy[copy.length - 1] = { ...last, tools };
                return copy;
              });
            } else if (eventName === "done" && typeof payload.conversationId === "string") {
              setConversationId(payload.conversationId);
            } else if (eventName === "error" && typeof payload.detail === "string") {
              setMessages((prev) => {
                const copy = [...prev];
                copy[copy.length - 1] = { role: "assistant", content: payload.detail };
                return copy;
              });
            }
          } catch {
            // Ignore malformed SSE frame
          }
        }
      }
    } catch {
      setMessages((prev) => {
        const copy = [...prev];
        copy[copy.length - 1] = {
          role: "assistant",
          content: "Could not connect to ai-assistant-service. Ensure the assistant service is running.",
        };
        return copy;
      });
    } finally {
      setStreaming(false);
    }
  }

  return (
    <div className="fixed bottom-5 right-5 z-40">
      {open ? (
        <section
          aria-label="SwiftDrop AI Delivery Assistant"
          className="flex h-[460px] w-80 flex-col overflow-hidden rounded-2xl bg-white shadow-xl ring-1 ring-slate-200 sm:w-96"
        >
          <header className="flex items-center justify-between border-b border-slate-100 bg-brand-600 px-4 py-3 text-white">
            <div>
              <p className="text-sm font-semibold">SwiftDrop AI Assistant</p>
              <p className="text-[11px] text-brand-100">MCP tools &middot; PII vault protected</p>
            </div>
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label="Close assistant"
              className="rounded-lg p-1 text-brand-100 hover:bg-brand-700 hover:text-white"
            >
              <svg viewBox="0 0 16 16" fill="none" className="h-4 w-4" aria-hidden="true">
                <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
            </button>
          </header>

          <div ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto bg-slate-50/70 p-4 text-xs">
            {messages.map((m, idx) => (
              <div key={idx} className={`flex flex-col ${m.role === "user" ? "items-end" : "items-start"}`}>
                {m.tools && m.tools.length > 0 && (
                  <div className="mb-1 flex flex-wrap gap-1">
                    {m.tools.map((t, i) => (
                      <span
                        key={`${t}-${i}`}
                        className="rounded-full bg-brand-50 px-2 py-0.5 font-mono text-[10px] font-medium text-brand-700 ring-1 ring-brand-200"
                      >
                        Tool: {t}
                      </span>
                    ))}
                  </div>
                )}
                <div
                  className={`max-w-[85%] rounded-2xl px-3.5 py-2.5 leading-relaxed ${
                    m.role === "user"
                      ? "bg-brand-600 text-white"
                      : "bg-white text-slate-800 shadow-xs ring-1 ring-slate-200"
                  }`}
                >
                  {m.content || (streaming && idx === messages.length - 1 ? "Thinking..." : "")}
                </div>
              </div>
            ))}
          </div>

          <form onSubmit={handleSend} className="flex items-center gap-2 border-t border-slate-200 bg-white p-3">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask about quotes, zones, or tracking..."
              disabled={streaming}
              className="input py-1.5 text-xs"
            />
            <button type="submit" disabled={streaming || !input.trim()} className="btn-primary px-3 py-1.5 text-xs">
              Send
            </button>
          </form>
        </section>
      ) : (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="flex items-center gap-2 rounded-full bg-brand-600 px-4 py-2.5 text-xs font-semibold text-white shadow-lg ring-1 ring-brand-700 transition hover:bg-brand-700"
        >
          <svg viewBox="0 0 20 20" fill="none" className="h-4 w-4" aria-hidden="true">
            <path
              d="M3 5.5A2.5 2.5 0 0 1 5.5 3h9A2.5 2.5 0 0 1 17 5.5v6a2.5 2.5 0 0 1-2.5 2.5H8l-4 3v-3H5.5A2.5 2.5 0 0 1 3 11.5v-6Z"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinejoin="round"
            />
          </svg>
          AI Assistant
        </button>
      )}
    </div>
  );
}
