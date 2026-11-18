# 云河FC 后端

Spring Boot 3.5.x / Java 21 单部署单元。当前已实现 M01–M03 接口。

## 本地运行

```bash
./mvnw test          # 单元测试（不起容器）
./mvnw verify        # 单元 + 集成测试（推荐）
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run   # dev 显式开启短信/微信 mock
```

默认连接本机 MySQL/Redis/Kafka；可通过环境变量覆盖：

| 变量 | 默认值 |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/yunhefc?...` |
| `DB_USERNAME` / `DB_PASSWORD` | `yunhefc` / `yunhefc` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `AUTH_LOGIN_RATE_LIMIT_MAX_ATTEMPTS` / `AUTH_LOGIN_RATE_LIMIT_WINDOW` | `50` / `1m`；登录限流（按手机号 + IP） |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `SERVER_PORT` | `8080` |
| `SMS_MOCK_ENABLED` | `false`；本地开发需显式设为 `true`。prod 下必须为 `false`，为 `true` 则启动失败；未开启 mock 时必须配置短信凭证，否则启动失败 |
| `SMS_ENDPOINT` | `https://gyytz.market.alicloudapi.com/sms/smsSend` |
| `SMS_APPCODE` / `SMS_SIGN_ID` / `SMS_TEMPLATE_ID` | 空；非 mock 模式下必须配置，prod 缺失则启动失败 |

### 查看操作日志（需本地 Kafka）

dev profile 默认关闭 Outbox 中继与 Kafka 消费（`application-dev.yml`），业务写入只落
`outbox_event`，不会产生 `audit_log`，B10 操作日志页因此看不到新记录。需要验证操作日志时显式开启：

1. 创建 topic（Kafka 禁止自动建 topic）：

```bash
KAFKA_BOOTSTRAP=localhost:19092   # 换成实际映射到宿主机 9092 的端口
for topic in yunhefc.notification.events yunhefc.notification.events.DLT \
             yunhefc.audit.events yunhefc.audit.events.DLT; do
  docker run --rm --network host apache/kafka:3.7.2 \
    /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" \
    --create --if-not-exists --topic "$topic" --partitions 1 --replication-factor 1
done
```

2. 启动时开启链路，缩短中继周期便于排空积压：

```bash
SPRING_PROFILES_ACTIVE=dev \
OUTBOX_ENABLED=true KAFKA_LISTENER_ENABLED=true OUTBOX_RELAY_INTERVAL_MS=5000 \
./mvnw spring-boot:run
```

日志为异步落库，正常延迟不超过一个中继周期。首次开启会补投历史 PENDING 事件（每批 20 条），
积压较多时需要等待排空；期间通知消费者也会同步消费，dev 下短信/微信为 mock，不会外发。

E2E 仍显式注入 `OUTBOX_ENABLED=false` / `KAFKA_LISTENER_ENABLED=false`，不受上述开关影响；
prod 两者默认开启，若线上同样没有日志，先查 `outbox_event` 的 `status` 与 `last_error`。

## 测试

前置：Docker 正在运行；首次运行会拉取 `mysql:8.4` 与 `redis:6.2.24`。

- `./mvnw test` 只跑 `*Test`（含 ArchUnit）；`./mvnw verify` 额外跑 `*IT`（Failsafe）。
- 整个 Failsafe JVM 共享一套 MySQL/Redis 单例容器；每个测试方法前清空业务表、用 Flyway V3 恢复种子数据并 `FLUSHDB`。
- Failsafe 默认设置 `TESTCONTAINERS_REUSE_ENABLE=true`：容器跨运行复用，Flyway 首次全量迁移，之后只校验/应用新增迁移。IDE 直接运行测试时需自行设置该环境变量才能跨运行复用。
- 复用容器不会在 `mvnw verify` 结束后删除。需要强制全新数据库时：

```bash
docker ps -a --filter ancestor=mysql:8.4
docker ps -a --filter ancestor=redis:6.2.24
docker rm -f <容器ID>
```

- 修改已应用的 Flyway 迁移会导致 checksum 校验失败（Flyway 11 已移除 `cleanOnValidationError`）；删除复用容器后重跑即可。`FlywayMigrationIT` 始终使用独立的全新 MySQL。

## Flyway

`src/main/resources/db/migration/`：

- `V1__baseline_schema.sql`：27 张业务表
- `V2__quartz_schema.sql`：11 张 Quartz 表
- `V3__seed_team_roles_permissions_settings.sql`：球队、3 个默认角色、28 个权限、9 项配置
- `V4__seed_formation_templates.sql`：8 套预置阵型、64 个槽位
- `V5__add_table_comments.sql`：27 张业务表的表级中文注释
- `V6__backfill_cancel_lock_match_day_0100.sql`：取消锁定时间回填
- `V7__reset_session_and_align_seed_timezone.sql`：Flyway 会话时区重置与新库种子回填
- `V8__add_audit_log_operated_at.sql`：审计操作时间列、回填与索引
- `V9__player_skill_decimal.sql`：球员星级与分组手动分改为两位小数

迁移在应用启动时执行；数据库不建任何 CHECK，业务规则由应用层强校验（见 `docs/design/04.数据库设计.md` §8.1）。
