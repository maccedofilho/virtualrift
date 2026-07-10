# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS build

WORKDIR /workspace

RUN corepack enable && corepack prepare pnpm@10.33.0 --activate

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/virtualrift-dashboard/package.json apps/virtualrift-dashboard/package.json
COPY packages/virtualrift-api-client/package.json packages/virtualrift-api-client/package.json
COPY packages/virtualrift-types/package.json packages/virtualrift-types/package.json
COPY packages/virtualrift-ui/package.json packages/virtualrift-ui/package.json

RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

COPY . .

RUN pnpm build

FROM nginx:1.28-alpine

RUN addgroup -S -g 10001 virtualrift \
    && adduser -S -D -H -u 10001 -G virtualrift virtualrift \
    && rm -rf /usr/share/nginx/html/* \
    && chown -R 10001:10001 /usr/share/nginx/html

COPY --from=build --chown=10001:10001 /workspace/apps/virtualrift-dashboard/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY --chown=10001:10001 docker/entrypoint.sh /usr/local/bin/virtualrift-entrypoint

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/virtualrift-entrypoint"]
