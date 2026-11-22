# syntax=docker/dockerfile:1.7
# Build context = repository root:
#   docker build -f tools/merchant-simulator/Dockerfile -t swiftdrop/merchant-simulator .
# The simulator has no dependencies beyond Node itself, so there is no install step.
FROM node:22.20.0-alpine
# Security: take Alpine fixes released after this base image and remove npm/corepack, which the
# simulator never runs.
RUN apk upgrade --no-cache \
 && rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
          /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack
ENV NODE_ENV=production
WORKDIR /app
COPY --chown=node:node tools/merchant-simulator/package.json ./
COPY --chown=node:node tools/merchant-simulator/src ./src
USER node
EXPOSE 4000
CMD ["node", "src/main.js"]
