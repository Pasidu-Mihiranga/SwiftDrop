# syntax=docker/dockerfile:1.7
# Build context = repository root (needs libs/python):
#   docker build -f services/ai-assistant-service/Dockerfile -t swiftdrop/ai-assistant-service .

# ---------- build ----------
FROM python:3.12-slim AS build
COPY --from=ghcr.io/astral-sh/uv:0.11.26 /uv /usr/local/bin/uv
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never
WORKDIR /repo
COPY libs/python libs/python
COPY services/ai-assistant-service/pyproject.toml services/ai-assistant-service/uv.lock services/ai-assistant-service/
WORKDIR /repo/services/ai-assistant-service
RUN --mount=type=cache,target=/root/.cache/uv uv sync --frozen --no-dev
COPY services/ai-assistant-service/app app

# ---------- runtime ----------
FROM python:3.12-slim
RUN useradd --system --uid 10001 swiftdrop
COPY --from=build /repo /repo
WORKDIR /repo/services/ai-assistant-service
ENV PATH="/repo/services/ai-assistant-service/.venv/bin:$PATH" PYTHONUNBUFFERED=1
USER 10001
EXPOSE 8095
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8095", "--no-access-log"]
