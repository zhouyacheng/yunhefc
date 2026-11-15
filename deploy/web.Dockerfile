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

# 先只拷 workspace 清单：lockfile 未变时 install 层可命中构建缓存，
# 源码改动不再使整次 pnpm install 失效。
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml frontend/tsconfig.base.json ./
COPY frontend/apps/web/package.json ./apps/web/package.json
COPY frontend/apps/admin/package.json ./apps/admin/package.json
COPY frontend/packages/api-client/package.json ./packages/api-client/package.json
COPY frontend/packages/permissions/package.json ./packages/permissions/package.json
COPY frontend/packages/shared/package.json ./packages/shared/package.json
RUN pnpm install --frozen-lockfile

COPY frontend/apps ./apps
COPY frontend/packages ./packages

# 生产 admin 子域挂在根路径；dev/E2E 仍是 /admin/。
ARG ADMIN_BASE_URL=/
ARG VITE_WEB_URL=
ARG VITE_ADMIN_URL=
RUN ADMIN_BASE_URL="$ADMIN_BASE_URL" VITE_WEB_URL="$VITE_WEB_URL" VITE_ADMIN_URL="$VITE_ADMIN_URL" \
        pnpm --filter @yunhefc/web build \
    && ADMIN_BASE_URL="$ADMIN_BASE_URL" VITE_WEB_URL="$VITE_WEB_URL" VITE_ADMIN_URL="$VITE_ADMIN_URL" \
        pnpm --filter @yunhefc/admin build

# 1.27.3+ 才支持 upstream 的 resolve 参数；钉住 patch 避免浮动 tag 回退。
FROM nginx:1.27.5-alpine

COPY deploy/nginx/nginx.conf /etc/nginx/nginx.conf
COPY deploy/nginx/snippets /etc/nginx/snippets
COPY deploy/nginx/templates /etc/nginx/templates
COPY --from=build /src/apps/web/dist /usr/share/nginx/html/www
COPY --from=build /src/apps/admin/dist /usr/share/nginx/html/admin

EXPOSE 80 443
