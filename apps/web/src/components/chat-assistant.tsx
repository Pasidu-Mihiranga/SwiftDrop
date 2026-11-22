"use client";

import Link from "next/link";
import { type FormEvent, type KeyboardEvent, useEffect, useId, useRef, useState } from "react";
import {
  AssistantError,
  type Inline,
  MAX_MESSAGE_LENGTH,
  type ToolChip,
  failureMessage,
  formatAnswer,
  streamChat,
} from "@/lib/assistant";
import { FEEDBACK_MOTION, PRESS } from "@/lib/motion";

interface Message {
  id: number;
  role: "user" | "assistant";
  text: string;
  tools: ToolChip[];
  status: "streaming" | "done" | "error";
}

const GUEST_SUGGESTIONS = ["How much to send 2 kg to Kandy?", "Which areas do you deliver to?", "Where is parcel SD261020K7Q2M9X?"];
const SIGNED_IN_SUGGESTIONS = ["Where is my parcel?", "Which of my parcels are still on the way?", "How much to send 2 kg to Kandy?"];

/**
 * The delivery assistant, available on every page: a floating button that opens a chat panel.
 * Answers stream in as they are written; each lookup the assistant makes shows as a chip, so the
 * reader can see where an answer came from. Works signed in (the user's own parcels) and as a guest
 * (tracking, prices and zones only).
 */
export function ChatAssistant({ signedIn }: { signedIn: boolean }) {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const conversationId = useRef<string | undefined>(undefined);
  const nextId = useRef(1);
  const abort = useRef<AbortController | null>(null);
  const launcher = useRef<HTMLButtonElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const log = useRef<HTMLDivElement>(null);
  const panelId = useId();

  useEffect(() => {
    if (open) input.current?.focus();
  }, [open]);

  useEffect(() => {
    const element = log.current;
    if (element) element.scrollTop = element.scrollHeight;
  }, [messages]);

  // A conversation in flight is cancelled if the widget goes away (sign-out, navigation away).
  useEffect(() => () => abort.current?.abort(), []);

  function close() {
    setOpen(false);
    launcher.current?.focus();
  }

  function update(id: number, change: (message: Message) => Message) {
    setMessages((all) => all.map((message) => (message.id === id ? change(message) : message)));
  }

  async function send(text: string) {
    const message = text.trim();
    if (!message || busy) return;
    const replyId = nextId.current + 1;
    nextId.current += 2;
    setMessages((all) => [
      ...all,
      { id: replyId - 1, role: "user", text: message, tools: [], status: "done" },
      { id: replyId, role: "assistant", text: "", tools: [], status: "streaming" },
    ]);
    setDraft("");
    setBusy(true);
    const controller = new AbortController();
    abort.current = controller;

    try {
      await streamChat(
        { message, conversationId: conversationId.current },
        (event) => {
          if (event.type === "token") update(replyId, (m) => ({ ...m, text: m.text + event.text }));
          else if (event.type === "tool") update(replyId, (m) => ({ ...m, tools: [...m.tools, event.tool] }));
          else if (event.type === "error") update(replyId, (m) => ({ ...m, text: event.message, status: "error" }));
          else conversationId.current = event.conversationId;
        },
        { signal: controller.signal },
      );
      update(replyId, (m) =>
        m.status === "error"
          ? m
          : m.text
            ? { ...m, status: "done" }
            : { ...m, text: failureMessage("UNEXPECTED_ERROR"), status: "error" },
      );
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        update(replyId, (m) => ({ ...m, text: m.text || "Stopped.", status: "done" }));
      } else {
        const text = error instanceof AssistantError ? error.message : failureMessage("UNEXPECTED_ERROR");
        update(replyId, (m) => ({ ...m, text, status: "error" }));
      }
    } finally {
      abort.current = null;
      setBusy(false);
      input.current?.focus();
    }
  }

  function startOver() {
    abort.current?.abort();
    conversationId.current = undefined;
    setMessages([]);
    input.current?.focus();
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    void send(draft);
  }

  function onInputKey(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      void send(draft);
    }
  }

  const suggestions = signedIn ? SIGNED_IN_SUGGESTIONS : GUEST_SUGGESTIONS;

  return (
    <div className="print:hidden">
      {open && (
        <section
          id={panelId}
          role="dialog"
          aria-label="Delivery assistant"
          onKeyDown={(event) => event.key === "Escape" && close()}
          className={`fixed inset-x-2 bottom-20 z-50 flex max-h-[min(640px,calc(100dvh-6rem))] flex-col overflow-hidden rounded-2xl bg-white shadow-2xl ring-1 ring-slate-200 sm:inset-x-auto sm:right-4 sm:w-[400px] ${FEEDBACK_MOTION}`}
        >
          <header className="flex items-start gap-3 border-b border-slate-100 px-4 py-3">
            <span aria-hidden="true" className="mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-brand-600 text-white">
              <ChatIcon />
            </span>
            <div className="min-w-0 flex-1">
              <h2 className="text-sm font-semibold text-slate-900">Delivery assistant</h2>
              <p className="text-xs text-slate-500">
                {signedIn ? "Ask about your parcels, prices and tracking." : "Ask about tracking, prices and areas we serve."}
              </p>
            </div>
            {messages.length > 0 && (
              <button type="button" onClick={startOver} className="rounded-md px-2 py-1 text-xs font-medium text-slate-500 hover:bg-slate-100 hover:text-slate-800">
                New chat
              </button>
            )}
            <button
              type="button"
              onClick={close}
              aria-label="Close assistant"
              className="rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
            >
              <svg aria-hidden="true" viewBox="0 0 16 16" className="h-4 w-4" fill="none">
                <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
            </button>
          </header>

          <div ref={log} role="log" aria-live="polite" aria-label="Conversation" className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
            {messages.length === 0 ? (
              <div className="space-y-3">
                <p className="text-sm text-slate-600">
                  Hi! I can check where a parcel is, estimate a delivery fee, and tell you which areas we serve.
                </p>
                {!signedIn && (
                  <p className="text-xs text-slate-500">
                    <Link href="/login" className="font-medium text-brand-700 hover:underline">Sign in</Link> to ask about your own parcels.
                  </p>
                )}
                <ul className="flex flex-col items-start gap-2" aria-label="Suggestions">
                  {suggestions.map((suggestion) => (
                    <li key={suggestion}>
                      <button
                        type="button"
                        onClick={() => void send(suggestion)}
                        className={`rounded-full border border-brand-100 bg-brand-50 px-3 py-1.5 text-left text-xs font-medium text-brand-700 hover:border-brand-500 ${PRESS}`}
                      >
                        {suggestion}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              messages.map((message) => <Bubble key={message.id} message={message} />)
            )}
          </div>

          <form onSubmit={onSubmit} className="border-t border-slate-100 p-3">
            <div className="flex items-end gap-2">
              <label htmlFor={`${panelId}-input`} className="sr-only">
                Message
              </label>
              <textarea
                id={`${panelId}-input`}
                ref={input}
                rows={1}
                value={draft}
                maxLength={MAX_MESSAGE_LENGTH}
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={onInputKey}
                placeholder="Ask about a parcel or a price"
                className="input max-h-32 min-h-10 flex-1 resize-none py-2"
              />
              {busy ? (
                <button type="button" onClick={() => abort.current?.abort()} className="btn-secondary px-3 py-2">
                  Stop
                </button>
              ) : (
                <button type="submit" disabled={!draft.trim()} className={`btn-primary px-3 py-2 ${PRESS}`}>
                  Send
                </button>
              )}
            </div>
            <p className="mt-2 text-[11px] text-slate-400">Answers come from SwiftDrop&apos;s own systems. Do not share card numbers.</p>
          </form>
        </section>
      )}

      <button
        ref={launcher}
        type="button"
        onClick={() => (open ? close() : setOpen(true))}
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        aria-label={open ? "Close assistant" : "Ask the delivery assistant"}
        className={`fixed bottom-4 right-4 z-50 inline-flex h-12 items-center gap-2 rounded-full bg-brand-600 px-4 text-sm font-semibold text-white shadow-lg hover:bg-brand-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 ${PRESS}`}
      >
        <ChatIcon />
        <span className="hidden sm:inline">{open ? "Close" : "Ask SwiftDrop"}</span>
      </button>
    </div>
  );
}

function Bubble({ message }: { message: Message }) {
  if (message.role === "user") {
    return (
      <div className="flex justify-end">
        <p className="max-w-[85%] whitespace-pre-wrap rounded-2xl rounded-br-md bg-brand-600 px-3 py-2 text-sm text-white">{message.text}</p>
      </div>
    );
  }
  const waiting = message.status === "streaming" && !message.text;
  return (
    <div className="flex max-w-[92%] flex-col items-start gap-1.5">
      {message.tools.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label="Lookups">
          {message.tools.map((tool, index) => (
            <li
              key={`${tool.name}-${index}`}
              className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset ${
                tool.ok ? "bg-emerald-50 text-emerald-800 ring-emerald-600/20" : "bg-amber-50 text-amber-900 ring-amber-600/20"
              }`}
            >
              <svg aria-hidden="true" viewBox="0 0 16 16" fill="none" className="h-3 w-3">
                <path d={tool.ok ? "M3.5 8.5l3 3 6-7" : "M8 4v5M8 11.5v.1"} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              {tool.label}
            </li>
          ))}
        </ul>
      )}
      <div
        className={`rounded-2xl rounded-bl-md px-3 py-2 text-sm ${
          message.status === "error" ? "bg-red-50 text-red-800" : "bg-slate-100 text-slate-800"
        }`}
        aria-busy={message.status === "streaming"}
      >
        {waiting ? (
          <span className="inline-flex items-center gap-1 py-1" aria-label="Thinking">
            {[0, 150, 300].map((delay) => (
              <span key={delay} className="h-1.5 w-1.5 animate-pulse rounded-full bg-slate-400" style={{ animationDelay: `${delay}ms` }} />
            ))}
          </span>
        ) : (
          <Answer text={message.text} />
        )}
      </div>
    </div>
  );
}

function Answer({ text }: { text: string }) {
  return (
    <div className="space-y-2">
      {formatAnswer(text).map((block, index) =>
        block.kind === "list" ? (
          <ul key={index} className="list-disc space-y-0.5 pl-4">
            {block.items.map((item, i) => (
              <li key={i}>
                <Parts parts={item} />
              </li>
            ))}
          </ul>
        ) : (
          <p key={index} className="whitespace-pre-wrap">
            <Parts parts={block.parts} />
          </p>
        ),
      )}
    </div>
  );
}

function Parts({ parts }: { parts: Inline[] }) {
  return (
    <>
      {parts.map((part, index) =>
        part.kind === "strong" ? (
          <strong key={index} className="font-semibold">{part.text}</strong>
        ) : part.kind === "tracking" ? (
          <Link key={index} href={part.href} className="font-medium text-brand-700 underline decoration-brand-500/40 underline-offset-2">
            {part.text}
          </Link>
        ) : (
          <span key={index}>{part.text}</span>
        ),
      )}
    </>
  );
}

function ChatIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" fill="none" className="h-5 w-5">
      <path
        d="M4 4.5h12a1.5 1.5 0 0 1 1.5 1.5v6.5A1.5 1.5 0 0 1 16 14H9l-3.5 3v-3H4A1.5 1.5 0 0 1 2.5 12.5V6A1.5 1.5 0 0 1 4 4.5z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path d="M6.5 9.25h.01M10 9.25h.01M13.5 9.25h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}
