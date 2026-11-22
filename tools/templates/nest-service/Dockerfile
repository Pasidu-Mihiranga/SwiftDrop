# syntax=docker/dockerfile:1.7
# Build context = repository root (needs the pnpm workspace, libs/node and contracts):
#   docker build -f services/__service__/Dockerfile -t swiftdrop/__service__ .

# ---------- build ----------
FROM node:22.20.0-alpine AS build
RUN corepack enable
WORKDIR /repo
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml tsconfig.base.json ./
COPY contracts contracts
COPY libs/node libs/node
COPY services/__service__ services/__service__
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter "@swiftdrop/__service__..."
RUN pnpm --filter "@swiftdrop/__service__..." build
# Self-contained folder with production dependencies only
RUN pnpm --filter "@swiftdrop/__service__" deploy --prod --legacy /out

# ---------- runtime ----------
FROM node:22.20.0-alpine
# Security: take Alpine fixes released after this base image (e.g. OpenSSL) and remove npm/corepack,
# which the app never runs but which ship their own dependencies (e.g. tar) into the image.
RUN apk upgrade --no-cache \
 && rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
          /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack
ENV NODE_ENV=production
WORKDIR /app
COPY --from=build --chown=node:node /out ./
USER node
EXPOSE __port__
CMD ["node", "dist/main.js"]
