"""Figure 10: activity diagram with swimlanes for one assistant turn.

Lanes: the customer's chat widget, ai-assistant-service, the SwiftDrop APIs (reached through the
gateway with the user's token) and the LLM provider.

Layout rules:
  - the assistant's main path runs down the centre of its lane; side branches use a column on
    either side of it, so no edge crosses another
  - the tool loop returns into the loop merge from the left (tool result) and from below (tool
    not available); the model's final answer comes back over the loop into the scope check
  - a rejected message ends at its own final node in the customer's lane
"""

from activity import Activity

MAIN, LEFT, RIGHT = 600, 460, 760          # columns inside the assistant lane


def build() -> str:
    a = Activity("Ask the delivery assistant", [
        ("Customer (chat widget)", 300), ("ai-assistant-service", 560),
        ("SwiftDrop APIs via the gateway", 280), ("LLM provider", 300)])
    user, apis, llm = a.lane(0), a.lane(2), a.lane(3)

    # --- the customer sends a message; length and rate limit are checked first
    s = a.start(user, 125)
    send = a.action(user, 190, "Send a question", w=220)
    a.flow([s.b, send.t])
    check = a.action(MAIN, 190, "Check the message|1 to 2000 characters, rate limit", w=300)
    a.flow([send.r, check.l])
    d1 = a.decision(MAIN, 270, "accepted?")
    a.flow([check.b, d1.t])
    err = a.action(user, 330, "Show the error", w=200)
    a.flow([d1.l, (user, 270), err.t], guard="rejected: 400 or 429", at=(455, 256), anchor="middle")
    end_err = a.final(user, 410)
    a.flow([err.b, end_err.t])

    # --- tool scope follows the sign-in state
    d2 = a.decision(MAIN, 340, "signed in?")
    a.flow([d1.b, d2.t], guard="accepted", at=(MAIN + 10, 305))
    public = a.action(LEFT, 410, "Offer public tools only", w=190)
    a.flow([d2.l, (LEFT, 340), public.t], guard="guest", at=(525, 326), anchor="middle")
    private = a.action(RIGHT, 410, "Offer all five tools|forward the user's JWT", w=200)
    a.flow([d2.r, (RIGHT, 340), private.t], guard="signed in", at=(682, 326), anchor="middle")
    m1 = a.merge(MAIN, 470)
    a.flow([public.b, (LEFT, 470), m1.l])
    a.flow([private.b, (RIGHT, 470), m1.r])
    prompt = a.action(MAIN, 535, "Pseudonymize the message|then build the prompt: rules, history, tools", w=340)
    a.flow([m1.b, prompt.t])

    # --- the model loop: the merge collects every return into the next model call
    loop = a.merge(MAIN, 610, "loop merge")
    a.flow([prompt.b, loop.t])
    think = a.action(llm, 680, "Choose the next step|answer, or call a tool", w=240)
    a.flow([loop.r, (llm, 610), think.t])
    d3 = a.decision(llm, 780, "tool call?")
    a.flow([think.b, d3.t])
    d4 = a.decision(RIGHT, 780, "allowed?")
    a.flow([d3.l, d4.r], guard="tool call", at=(1020, 766), anchor="middle")
    refuse = a.action(RIGHT, 690, "Tell the model|the tool is not available", w=200)
    a.flow([d4.t, refuse.b], guard="not allowed or|5 rounds reached", at=(RIGHT - 8, 736), anchor="end")
    a.flow([refuse.l, (MAIN, refuse.cy), loop.b])
    run = a.action(apis, 860, "Run the tool|with the user's token", w=220)
    a.flow([d4.b, (RIGHT, 860), run.l], guard="allowed and|under 5 rounds", at=(RIGHT - 8, 825), anchor="end")
    result = a.action(RIGHT, 945, "Pseudonymize the result|add it, stream a tool chip", w=210)
    a.flow([run.b, (apis, 945), result.r])
    a.flow([result.l, (LEFT, 945), (LEFT, 610), loop.l])

    # --- the final answer is checked for scope before it is streamed
    d5 = a.decision(MAIN, 1060, "in scope?")
    a.flow([d3.b, (llm, 1015), (MAIN, 1015), d5.t], guard="final answer", at=(llm + 10, 900))
    stream = a.action(RIGHT, 1130, "Restore placeholders|stream tokens over SSE", w=210)
    a.flow([d5.r, (RIGHT, 1060), stream.t], guard="in scope", at=(690, 1046), anchor="middle")
    fallback = a.action(LEFT, 1130, "Use the safe|fallback answer", w=170)
    a.flow([d5.l, (LEFT, 1060), fallback.t], guard="off topic", at=(520, 1046), anchor="middle")
    m3 = a.merge(MAIN, 1200)
    a.flow([fallback.b, (LEFT, 1200), m3.l])
    a.flow([stream.b, (RIGHT, 1200), m3.r])
    save = a.action(MAIN, 1270, "Save the pseudonymized turn|encrypted vault, TTL 24h; logs masked", w=320)
    a.flow([m3.b, save.t])
    show = a.action(user, 1270, "Read the answer", w=200)
    a.flow([save.l, show.r])
    end = a.final(user, 1350)
    a.flow([show.b, end.t])

    # --- notes
    a.note(1185, 470, 250, "OpenAI or Gemini, chosen by LLM_PROVIDER; it sees placeholders, never raw personal data",
           lambda n: [(1400, n.bottom), (1400, think.top)])
    a.note(895, 660, 245, "the model is told, so it can answer without the tool",
           lambda n: [(refuse.right, refuse.cy), (n.left, refuse.cy)])
    return a.render(1400, "Activity: how the assistant answers one message")
