# syntax=docker/dockerfile:1.7
# Build context = repository root (pnpm workspace):
#   docker build -f apps/web/Dockerfile -t swiftdrop/web .

# ---------- build ----------
FROM node:22.20.0-alpine AS build
RUN corepack enable
WORKDIR /repo
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.base.json ./
COPY apps/web apps/web
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter "@swiftdrop/web..."
RUN pnpm --filter @swiftdrop/web build

# ---------- runtime (Next.js standalone server) ----------
FROM node:22.20.0-alpine
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
WORKDIR /app
COPY --from=build --chown=node:node /repo/apps/web/.next/standalone ./
COPY --from=build --chown=node:node /repo/apps/web/.next/static ./apps/web/.next/static
COPY --from=build --chown=node:node /repo/apps/web/public ./apps/web/public
USER node
EXPOSE 3000
CMD ["node", "apps/web/server.js"]
