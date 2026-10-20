# syntax=docker/dockerfile:1.7
# Build context = repository root (needs the pnpm workspace, libs/node and contracts):
#   docker build -f services/webhook-service/Dockerfile -t swiftdrop/webhook-service .

# ---------- build ----------
FROM node:22.20.0-alpine AS build
RUN corepack enable
WORKDIR /repo
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.base.json ./
COPY contracts contracts
COPY libs/node libs/node
COPY services/webhook-service services/webhook-service
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter "@swiftdrop/webhook-service..."
RUN pnpm --filter "@swiftdrop/webhook-service..." build
# Self-contained folder with production dependencies only
RUN pnpm --filter "@swiftdrop/webhook-service" deploy --prod --legacy /out

# ---------- runtime ----------
FROM node:22.20.0-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY --from=build --chown=node:node /out ./
USER node
EXPOSE 8092
CMD ["node", "dist/main.js"]
