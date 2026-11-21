# ai-assistant-service

AI delivery assistant (bonus feature): answers "Where is my parcel?" and "How much to send 2 kg to Kandy?"
by calling SwiftDrop's own APIs. The same tools are served over MCP for external AI clients.

| | |
|---|---|
| Port | `8095` |
| API base | `/api/v1/assistant` (through the gateway), MCP at `/mcp` |
| Contract | [contracts/openapi/ai-assistant-service.v1.yaml](../../contracts/openapi/ai-assistant-service.v1.yaml) |
| Design | `doc/diagrams/m10/` figures 07-11 and `doc/m10-design.pdf` |
| Owner | M10, see [doc/03-team-contribution.md](../../doc/03-team-contribution.md) |

## How a message is answered

`POST /api/v1/assistant/chat` streams server-sent events: `tool` (a chip such as "Checked tracking
SD..."), `token` (answer text), `error`, and finally `done` with the `conversationId`.

1. **Rate limit** per signed-in user, or per client address for guests (429 `RATE_LIMITED`).
2. **Memory** is loaded from Redis. A conversation belongs to whoever started it; any other caller gets a new one.
3. **Pseudonymize** the message: personal data becomes placeholders such as `[PHONE_1]`.
4. **Model loop** (at most `ASSISTANT_MAX_TOOL_ROUNDS` rounds). The model sees the public tools, or all five
   for a signed-in user. Tool arguments are restored before a tool runs; the user's token is forwarded
   to the gateway; results are trimmed to what the answer needs and pseudonymized before the model sees them.
5. **Stream** the answer with the placeholders restored for this caller only. The first characters are
   checked for the off-topic sentinel, which turns into a fixed reply.
6. **Save** the pseudonymized turn and the encrypted vault (24 hours).

| Tool | Who | Calls |
|---|---|---|
| `track_parcel` | everyone | `GET /api/v1/tracking/public/{n}` |
| `estimate_delivery_fee` | everyone | `POST /api/v1/pricing/quotes` |
| `list_service_zones` | everyone | `GET /api/v1/pricing/zones` |
| `list_my_parcels` | signed in | `GET /api/v1/parcels/mine` |
| `get_parcel_details` | signed in | `GET /api/v1/parcels/{id}` |

Until pricing-, parcel- and tracking-service are deployed, these tools return an "unavailable" or "not
found" error, which the assistant explains to the user.

## Personal data

| Where | What happens |
|---|---|
| To the LLM | Names, phones, emails, NIC numbers, payment cards (Luhn-checked) and addresses become placeholders (`app/privacy`). A value the vault already knows, such as a recipient's name from a tool result, is also replaced when the user types it. |
| To the user | Placeholders are restored, even when one is split across streamed chunks. |
| Redis | Messages hold placeholders only; the vault (placeholder to value) is Fernet-encrypted with a key derived from `ASSISTANT_VAULT_SECRET` and expires with the conversation. |
| MCP clients | Results are masked irreversibly (`K*** P.`, `+94*******67`, `***`) unless `MCP_MASK_PII=false`. |
| Logs | Only metadata (conversation id, tools, outcome, duration); the shared JSON logger masks anything else. |

Known limit: a name typed by the user that SwiftDrop has never returned cannot be recognised by a pattern,
so it reaches the model as typed. Phones, emails, NIC and card numbers are always caught.

## Configuration

| Variable | Default | |
|---|---|---|
| `LLM_PROVIDER`, `LLM_MODEL` | `gemini`, `gemini-2.5-flash` | `openai` also works; one OpenAI-compatible client |
| `GEMINI_API_KEY` / `OPENAI_API_KEY` | empty | Without a key `/chat` answers 503; MCP still works |
| `REDIS_URL` | built from `REDIS_HOST_PORT` and `REDIS_PASSWORD` | Memory, vault and rate limits |
| `ASSISTANT_VAULT_SECRET` | random per process | Set it everywhere except quick local runs |
| `ASSISTANT_RATE_LIMIT_PER_MIN` | `20` | Messages per caller per minute |
| `ASSISTANT_MAX_TOOL_ROUNDS` | `5` | Tool rounds per message |
| `MCP_MASK_PII` | `true` | `false` only for trusted MCP clients |

## Metrics (`/metrics`)

`assistant_turns_total{outcome}`, `assistant_tool_calls_total{tool,result}` and
`assistant_pii_redactions_total{kind}`, next to the HTTP request histogram.

## Layout

```
app/
├── main.py              create_app(): wiring, MCP mount, lifespan
├── api/v1/routes.py     /chat (SSE), /meta/whoami, /meta/provider
├── agent/               orchestrator (one turn), prompts, guardrails (rate limit, scope gate)
├── privacy/             detectors, PseudonymVault + VaultCipher, PiiRedactor + StreamingRestorer
├── tools/               ToolRegistry (sign-in based access) and the five SwiftDrop tools
├── llm/                 Strategy: LLMProvider protocol, OpenAI-compatible provider, factory
├── memory/              ConversationStore (Redis)
├── mcp_server/          MCP server over the same registry
└── clients/             SwiftDropApi: gateway client that forwards the user's token
```

## Run and test

```bash
uv sync
uv run uvicorn app.main:app --reload --port 8095   # needs Redis from `pnpm infra:up`
uv run pytest                                        # coverage gate ≥70%
```

Tests run without network access: a scripted LLM, in-memory Redis and a mock gateway. The key privacy
test (`tests/test_agent.py`) asserts that no name, phone, email or address ever reaches the model while
the user's answer contains them.
