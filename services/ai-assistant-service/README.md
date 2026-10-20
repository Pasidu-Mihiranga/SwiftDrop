# ai-assistant-service

AI delivery assistant (bonus feature): answers "Where is my parcel?" and "How much to send 2 kg to Kandy?"
by calling SwiftDrop's own APIs. The same capabilities are exposed as MCP tools.

| | |
|---|---|
| Port | `8095` |
| API base | `/api/v1/assistant` (through the gateway), MCP at `/mcp` |
| LLM | `LLM_PROVIDER=gemini\|openai`, `LLM_MODEL`, `GEMINI_API_KEY` / `OPENAI_API_KEY` |
| Owner | M10 — see [doc/03-team-contribution.md](../../doc/03-team-contribution.md) |

## Layout

```
app/
├── main.py              create_app(): logging, JWT verifier, problem+json, metrics, routes
├── core/config.py       pydantic-settings (env vars; reads the root .env locally)
├── api/v1/routes.py     PRESENTATION — /meta/whoami, /meta/provider, /chat (Phase 4)
├── agent/               APPLICATION — tool-calling loop, prompts, guardrails (Phase 4)
├── llm/                 Strategy: LLMProvider protocol + OpenAI-compatible provider factory
├── mcp_server/          MCP tools (Phase 4)
└── clients/             SwiftDropApi — gateway client that forwards the user's token
```

## Run & test

```bash
uv sync
uv run uvicorn app.main:app --reload --port 8095
uv run pytest            # coverage gate ≥70%
```
