# 01 — prod profile 与可观测性

Type: implementation
Status: resolved
Blocked by: 无

**What to build:** 应用以生产 profile 启动时，关键配置齐全且不安全配置会让启动立即失败；生产日志是结构化 JSON、按天与大小滚动并保留 14 天；readiness 同时覆盖数据库与 Redis，供发布脚本判定实例可用。

## Scope

- `application-prod.yml`：RDS 私网连接与 Hikari（`maximum-pool-size=10`、`connection-timeout=5s`、`max-lifetime≤30m`）、Redis 密码、Kafka bootstrap、`server.forward-headers-strategy=framework`、`server.shutdown=graceful`、host-only + Secure Cookie、CORS 白名单、`springdoc` 关闭、`server.error.include-*=never`、`spring.flyway.enabled=false`、Actuator probes。
- prod 启动自检（fail-fast）：`SESSION_COOKIE_SECURE=true`；`SESSION_COOKIE_DOMAIN` 为空；CORS 只能是 `https://www.<domain>` 与 `https://admin.<domain>`（拒绝 localhost 与通配符）；`DB_PASSWORD` 不得为默认值；`REDIS_PASSWORD` 非空。短信、OSS、微信的 fail-fast 已在各自适配器实现，本票只保证 profile 提供配置注入口径。
- `logback-spring.xml`：ECS JSON 行格式，含 requestId/userId/level/logger/message/exception；按天 + 100MB 滚动；保留 14 天且总量上限 2GB；dev/test 保持原有可读格式。
- Actuator：仅暴露 `health,info`；开启 liveness/readiness；readiness 覆盖 DB 与 Redis；`show-details=never`。
- dev/test 行为不变：无 prod profile 时不触发自检、日志格式不变。

## Owned files

- `backend/src/main/resources/application-prod.yml`
- `backend/src/main/resources/logback-spring.xml`
- prod 自检实现与配置绑定（`common` 或 `infra` 下的配置类）
- 对应单元/上下文测试

## Test obligations

- prod profile 在完整合法变量下可加载，dev/test 不受影响。
- 反例逐项覆盖：Secure=false、Cookie Domain 非空、CORS 含 localhost/`*`、默认 DB 密码、空 Redis 密码，均启动失败并给出可定位原因。
- 日志配置可被 Spring 加载，prod 输出 JSON、dev 保持原格式（至少断言 appender/encoder 选择）。
- `mvn verify` 全绿（含 ArchUnit）。
- 不改 Flyway、schema、Quartz DDL；不新增业务接口。

## Result

- 交付：`application-prod.yml`（RDS 私有地址、Hikari 10/5s/30m、Redis 密码、Kafka bootstrap、forward-headers、graceful shutdown 30s、host-only+Secure Cookie、CORS 白名单、springdoc 关闭、error `include-*=never`、`flyway.enabled=false`、Actuator probes/readiness=DB+Redis）；`logback-spring.xml`（prod 用 Boot 内建 `StructuredLogEncoder` ECS，控制台+文件，按天/100MB、14 天、2GB；dev/test 保持原可读 console）。
- 启动自检：`common/config/ProdValidationConfig`（`@Profile("prod")`）注册 `ProdStartupValidator`，校验 `SESSION_COOKIE_SECURE=true`、`SESSION_COOKIE_DOMAIN` 为空、CORS 恰为同一域名的 `https://www.<domain>`/`https://admin.<domain>`（拒绝 localhost/通配符/http/端口/路径）、`DB_PASSWORD` 非空且非 `yunhefc`、`REDIS_PASSWORD` 非空。
- 改动：`SecurityConfig` 放行 `/actuator/health/**`（readiness/liveness 可被判读）；`SessionAuthFilter` 写入并清理 MDC `userId`。
- 测试：新增 `ProdStartupValidatorTest`（12）、`ProdProfileContextTest`（3，prod 合法启动、prod 反例失败、dev 不受影响）、`ProdProfileYamlTest`（1，真实 `application-prod.yml` 绑定 `ServerProperties`/session/CORS 与关键键）、`LogbackConfigTest`（3，appender/encoder/滚动参数 + ECS 字段含 requestId/userId/异常）。
- 验证：`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B -ntp verify` BUILD SUCCESS（surefire 211、failsafe 306，0 失败/0 错误）；以 prod profile 启动 boot jar 冒烟：控制台与文件均为 ECS JSON，`SESSION_COOKIE_SECURE=false` 时以 `生产环境 SESSION_COOKIE_SECURE 必须为 true` 退出（exit 1）；`grep -c '^CREATE TABLE' scripts/sql/schema.sql` = 27，无 schema/Flyway/Quartz DDL 改动。
- 修正：`server.error.include-exception` 是布尔项（非 `never`），冒烟发现后改为 `false`，并由 `ProdProfileYamlTest` 回归覆盖。
- 未执行：未用真实 prod 凭证连接 RDS/Redis 启动（外部前置归票 02）；未 push/提交；springdoc 未引入依赖，仅预置关闭键。
