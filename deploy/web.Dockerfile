# 构建上下文为仓库根目录：
#   docker build -f deploy/web.Dockerfile \
#     --build-arg VITE_WEB_URL=https://www.example.com \
#     --build-arg VITE_ADMIN_URL=https://admin.example.com \
#     -t yunhefc-nginx:sha-<7> .
FROM node:24-bookworm-slim AS build

ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH
RUN corepack enable && corepack prepare pnpm@12.16.1 --activate

WORKDIR /src

COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml frontend/tsconfig.base.json ./
COPY frontend/apps ./apps
COPY frontend/packages ./packages
RUN pnpm install --frozen-lockfile

# 生产 admin 子域挂在根路径；dev/E2E 仍是 /admin/。
ARG ADMIN_BASE_URL=/
ARG VITE_WEB_URL=
ARG VITE_ADMIN_URL=
RUN ADMIN_BASE_URL="$ADMIN_BASE_URL" VITE_WEB_URL="$VITE_WEB_URL" VITE_ADMIN_URL="$VITE_ADMIN_URL" \
        pnpm --filter @yunhefc/web build \
    && ADMIN_BASE_URL="$ADMIN_BASE_URL" VITE_WEB_URL="$VITE_WEB_URL" VITE_ADMIN_URL="$VITE_ADMIN_URL" \
        pnpm --filter @yunhefc/admin build

FROM nginx:1.27-alpine

COPY deploy/nginx/nginx.conf /etc/nginx/nginx.conf
COPY deploy/nginx/snippets /etc/nginx/snippets
COPY deploy/nginx/templates /etc/nginx/templates
COPY --from=build /src/apps/web/dist /usr/share/nginx/html/www
COPY --from=build /src/apps/admin/dist /usr/share/nginx/html/admin

EXPOSE 80 443
