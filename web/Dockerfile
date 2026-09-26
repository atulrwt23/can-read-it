# syntax=docker/dockerfile:1
# CanReadIt web (Next.js standalone). Built natively per architecture (linux/amd64, linux/arm64).

FROM node:24-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile
COPY . .
# Rewrites are fixed at build time: /api/* is proxied to the api container on the compose network.
# No API is reachable during the build; pages that fetch at build time render a placeholder.
ENV API_INTERNAL_URL=http://api:8080 \
    NEXT_TELEMETRY_DISABLED=1
ARG ALLOW_INDEXING=false
ENV ALLOW_INDEXING=${ALLOW_INDEXING}
RUN pnpm build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000 \
    API_INTERNAL_URL=http://api:8080
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
USER node
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=4 \
  CMD wget -q -O /dev/null http://127.0.0.1:3000/healthz || exit 1
CMD ["node", "server.js"]
