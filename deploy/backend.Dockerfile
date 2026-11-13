# 构建上下文为仓库根目录：
#   docker build -f deploy/backend.Dockerfile -t yunhefc-backend:sha-<7> .
# CI 先执行 ./mvnw verify，target/ 下得到 boot jar。
FROM eclipse-temurin:21-jre AS runtime

# curl 仅用于容器内 readiness healthcheck。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/logs \
    && chown -R 10001:10001 /app

COPY --chown=10001:10001 backend/target/*.jar /app/
# 只允许一个 boot jar；出现 classifier/sources jar 时构建直接失败，避免拷错产物。
RUN set -- /app/*.jar; [ "$#" -eq 1 ] || { echo "expected exactly one jar, got $#"; exit 1; }; mv "$1" /app/app.jar

WORKDIR /app
USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
