# CI 基础镜像：ubuntu:24.04 + Node 24 + pnpm 12.16.1 + Playwright Chromium + JDK 21 + mysql client。
# 仅 linux/amd64（与 CI、生产一致）；apt 走中科大镜像，Node 走 npmmirror CDN，pnpm 走 npmmirror registry。
# 版本必须与 frontend/package.json（node engines、packageManager）和 pnpm-lock.yaml（playwright 1.65.0）
# 对齐，否则浏览器 revision 不命中，CI 里会重新下载。
#
# tag 语义：不可变自描述 24.04_pw<playwright>_node<node>。
# lockfile 升级 Playwright 或 Node 时必须重建镜像、换新 tag，并同步 .github/workflows/ci.yml。
#
# 构建（本机 Docker VM 直连 443 会被重置，需经宿主机代理；网络可直连的构建机可去掉 --build-arg）：
#   docker buildx build --platform linux/amd64 --load \
#     --build-arg HTTP_PROXY=http://host.docker.internal:7897 \
#     --build-arg HTTPS_PROXY=http://host.docker.internal:7897 \
#     -f deploy/ci-base/ubuntu-v1.Dockerfile \
#     -t danche1013/ubuntu:24.04_pw1.65.0_node24.22.3 deploy/ci-base
# 推送（公开仓库，需先 docker login；推送前确认前端 lockfile 版本与 tag 一致）：
#   docker push danche1013/ubuntu:24.04_pw1.65.0_node24.22.3
FROM ubuntu:24.04

ARG NODE_VERSION=24.22.3
ARG PNPM_VERSION=12.16.1
ARG TARGETARCH

# 镜像内尚无 ca-certificates，首次 apt 只能走 http（InRelease 有 GPG 签名）。
# 官方源实测不足 50kB/s，中科大镜像 120–180kB/s，故切 USTC；deb822 的 archive/security 两段一并替换。
# 重试与超时写进 apt 配置，playwright --with-deps 内部的 apt 调用同样生效。
# 不要给 apt 目录加 --mount=type=cache：实测会让 apt 吞吐从 ~530kB/s 掉到 ~25kB/s 并整层失败。
RUN printf 'Acquire::Retries "20";\nAcquire::http::Timeout "30";\n' > /etc/apt/apt.conf.d/99retries \
    && sed -i 's|^URIs:.*|URIs: http://mirrors.ustc.edu.cn/ubuntu/|' /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       ca-certificates curl xz-utils git openjdk-21-jre-headless default-mysql-client \
    && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
    case "${TARGETARCH}" in \
      amd64) node_arch=x64 ;; \
      arm64) node_arch=arm64 ;; \
      *) echo "unsupported arch: ${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    curl -fsSL --retry 5 --retry-all-errors -o /tmp/node.tar.xz \
      "https://cdn.npmmirror.com/binaries/node/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${node_arch}.tar.xz"; \
    mkdir -p /usr/local/node; \
    tar -xJf /tmp/node.tar.xz -C /usr/local/node --strip-components=1; \
    rm /tmp/node.tar.xz
ENV PATH=/usr/local/node/bin:${PATH}

RUN npm install -g "pnpm@${PNPM_VERSION}" --registry=https://registry.npmmirror.com \
    && npm cache clean --force

# 浏览器统一落到 /ms-playwright，与运行用户 HOME 解耦。
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

WORKDIR /opt/ci-base
COPY package.json ./
RUN pnpm install --registry=https://registry.npmmirror.com

# 与上一步拆开：浏览器下载失败时重跑不必再装一遍 npm 依赖。
RUN pnpm exec playwright install --with-deps chromium \
    && rm -rf /var/lib/apt/lists/* /root/.npm /root/.cache/pnpm
