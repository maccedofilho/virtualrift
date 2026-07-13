# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

FROM node:22-alpine@sha256:16e22a550f3863206a3f701448c45f7912c6896a62de43add43bb9c86130c3e2 AS build

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

FROM nginx:1.31.2-alpine@sha256:54f2a904c251d5a34adf545a72d32515a15e08418dae0266e23be2e18c66fefa

RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 virtualrift \
    && adduser -S -D -H -u 10001 -G virtualrift virtualrift \
    && rm -rf /usr/share/nginx/html/* \
    && chown -R 10001:10001 /usr/share/nginx/html

COPY --from=build --chown=10001:10001 /workspace/apps/virtualrift-dashboard/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY --chown=10001:10001 docker/entrypoint.sh /usr/local/bin/virtualrift-entrypoint

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/virtualrift-entrypoint"]
