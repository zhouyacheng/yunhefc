# 后端基础镜像：eclipse-temurin:21-jre + 运维工具（curl/nc/netstat/vim），apt 源切阿里云。
# 仅 linux/amd64（与 CI、生产一致）；阿里云 /ubuntu/ 只镜像 amd64 存档源。
# ACR 中无 eclipse-temurin:21-jre（仅 -v1），源镜像取自同仓库的 java:21-jre（官方 temurin 镜像）。
#
# 构建与推送：
#   docker buildx build --platform linux/amd64 --load \
#     -f deploy/temurin-jre-v1.Dockerfile \
#     -t registry.cn-shanghai.aliyuncs.com/zyc1013/eclipse-temurin:21-jre-v1 deploy
#   docker push registry.cn-shanghai.aliyuncs.com/zyc1013/eclipse-temurin:21-jre-v1
FROM registry.cn-shanghai.aliyuncs.com/zyc1013/java:21-jre

# Temurin 的 apt 源为 deb822 格式，两段 URI（archive + security）一并替换。
RUN sed -i 's|^URIs:.*|URIs: https://mirrors.aliyun.com/ubuntu/|' /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl netcat-openbsd net-tools vim \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/logs \
    && chown -R 10001:10001 /app
