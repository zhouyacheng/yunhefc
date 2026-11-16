# 构建上下文为仓库根目录：
#   docker build -f deploy/backend.Dockerfile -t yunhefc-backend:sha-<7> .
# CI 先执行 ./mvnw verify，target/ 下得到 boot jar。
# 基础镜像见 deploy/temurin-jre-v1.Dockerfile：预装 curl/nc/netstat/vim，apt 源已切阿里云。
FROM registry.cn-shanghai.aliyuncs.com/zyc1013/eclipse-temurin:21-jre-v1 AS runtime

COPY --chown=10001:10001 backend/target/*.jar /app/
# 只允许一个 boot jar；出现 classifier/sources jar 时构建直接失败，避免拷错产物。
RUN set -- /app/*.jar; [ "$#" -eq 1 ] || { echo "expected exactly one jar, got $#"; exit 1; }; mv "$1" /app/app.jar

WORKDIR /app
USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
