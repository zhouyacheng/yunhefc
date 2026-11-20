# 12 — 部署拓扑、发布与回滚

Type: grilling
Status: resolved
Blocked by: 04

## Question

基于部署基线盘点，确定生产部署与发布：

- Compose 服务清单、JVM/MySQL/Redis/Kafka 资源上限与磁盘保留；Kafka 单节点与 Quartz JDBC 的运维要点。
- 三子域 `www.` / `admin.` / `api.` 的 Nginx 路由、TLS 证书、静态资源缓存与 SPA 回退。
- 发布与回滚：不可变镜像 tag、保留上一版本、回滚步骤与触发条件。
- 首位管理员与空库引导：V3 无管理员用户；Flyway 失败即无法启动时的前置校验与初始化顺序。
- 最低可观测性：Actuator 健康检查、结构化日志、日志轮转、磁盘与 Kafka 积压告警。
- Nginx 后的真实 IP 传递（`X-Forwarded-For` / forward-headers）；与安全基线票衔接。

产出部署清单与 runbook 要点；不写 Compose 文件。事实指针：03 §5、§6.4、04 答案、`V3__seed_team_roles_permissions_settings.sql`、`application.yml`、`deploy/` 规划。

## Answer

状态：resolved（2026-11-11）。以下为已确认决策；本票不写 Compose/Dockerfile/Nginx 配置，配置落地归 18 号票切片后的实施批次。

### 拓扑（已确认）

- 生产 ECS **2 vCPU / 2GB**，系统盘按 **≥40GB** 核实；**MySQL 使用阿里云 RDS MySQL 8.4**（同地域同 VPC、私网），不跑在 Compose 内。
- Redis 6.2.24 与 Kafka 3.7.2 仍自建 Compose；Nginx 是唯一对外暴露 80/443 的服务；后端单实例（Quartz `isClustered=false` 的前提）。
- 这是对 03 已冻结形态的两处修订：MySQL 由 Compose 迁到 RDS；容量基线下沉为 2C2G 具体值。已同步 03 + 06 §47。

| 服务 | 镜像 | mem_limit | 上限参数 |
|---|---|---|---|
| backend | ACR `yunhefc-backend:sha-<7>` | 832m | `-Xms256m -Xmx448m -XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=48m -XX:ReservedCodeCacheSize=64m -XX:ActiveProcessorCount=2 -Xss512k` |
| kafka | `apache/kafka:3.7.2` | 512m | `KAFKA_HEAP_OPTS=-Xms256m -Xmx256m`；`log.cleaner.enable=false` |
| redis | `redis:6.2.24` | 192m | `maxmemory 128mb`、`noeviction`、AOF everysec + RDB |
| nginx | `nginx:1.27-alpine` | 48m | `worker_processes 2`；80/443 |

- 容器合计约 1.58GB，主机（OS、dockerd、journald、云监控）预留约 0.4GB，2GB 机器刚好可行；建 2GB swap 仅作 OOM 兜底。若 `free -m` 显示可用内存 <1.9GB，backend 堆降至 384m、Kafka 堆降至 192m；仍 OOM 时第一处置是升配 4C8G，不通过加容器解决。
- 单机只能跑一个 backend 实例，发布接受 10–30 秒停机，不做蓝绿/双实例。
- 后端连接：`DB_URL` 指向 RDS 私网（`serverTimezone=UTC`、`characterEncoding=utf8`，TLS 开关归 16 号票）；Hikari `maximum-pool-size=10`、`connection-timeout=5s`、`max-lifetime≤30m`（低于 RDS `wait_timeout`）；`REDIS_HOST=redis`、`KAFKA_BOOTSTRAP_SERVERS=kafka:9092`；新增 `application-prod.yml` 归 13/16 票落地。
- Kafka：单节点 KRaft（broker+controller 合并）；topic 预创建、禁止自动建 topic——`yunhefc.notification.events`、`yunhefc.audit.events` 及各自 `.DLT`，各 1 分区、副本 1；`log.retention.hours=168`、`log.retention.bytes=256MB`（**按分区计**，4 个 topic 合计约 1GB，另受时间保留约束，Kafka 总数据 <4GB）、`log.segment.bytes=128MB`、`min.insync.replicas=1`。
- Quartz：JobStore 落在 RDS（V2 的 11 张表）；单实例、`overwrite-existing-jobs=true`；后端开启 `server.shutdown=graceful`（30s）避免任务与发布竞争。
- 磁盘与日志：Kafka/Redis 用命名卷；Docker 日志 `json-file` 10m×5；应用日志 14 天、总量上限 2GB。

### Nginx / TLS

| 子域 | 路由 |
|---|---|
| `www.` | C 端 SPA 静态 + `/api/` → `backend:8080`；SPA fallback `index.html` |
| `admin.` | B 端 SPA 静态 + `/api/` → `backend:8080`；SPA fallback `index.html` |
| `api.` | 仅直连别名（运维/未来客户端）：`/api/` 透传 + `/actuator/health`；前端不调用 |

- 前端 `baseUrl` 保持相对 `/api/v1/{c|b}`，dev Vite 代理不变；C 端构建 base `/`，B 端 `ADMIN_BASE_URL=/`（vite 已支持）。
- 静态文件来自 `/opt/yunhefc/current/web/{www,admin}`，只读挂载；带 hash 的 assets 长缓存，`index.html` no-cache；gzip；`client_max_body_size 2m`。
- TLS：`*.domain` 通配符证书挂载；80 → 301 https；HSTS/协议版本归 16 号票。
- Cookie 改为 **host-only（不设 Domain）**：C/B 分别落在 www/admin，Path 前缀已隔离；同源下不需要跨子域。CORS 白名单保留作兜底，正常路径不触发。
- 微信 OAuth 回调固定 `https://www.<domain>/api/v1/c/auth/wechat/callback`，避免 host-only Cookie 落到 api 子域导致 SPA 读不到会话；`WECHAT_CALLBACK_URL` 由 14 票按此配置。`PUBLIC_BASE_URL=https://www.<domain>`。

### 发布与回滚

- 镜像 tag `sha-<git-sha 前 7 位>`，前端 dist 同 tag 打包；禁止 `latest`。发布目录 `/opt/yunhefc/releases/<sha7>/`，`current` 软链切换，保留当前 + 前 2 个版本（共 3 个）。
- **触发：手动批准发布**。main 合并只跑 CI 并推不可变镜像；在 GitHub Actions 手动触发 Deploy（13 票落地），上线期不自动发布。
- 步骤：拉镜像 → 切 `current` → `docker compose up -d backend nginx`（静态挂载随软链与容器重建生效）→ Flyway 随启动执行 → 轮询 readiness（≤180s）→ 冒烟（health + C 端首页 + 一条公开读接口）。
- 回滚：`release.sh --rollback` 切回上一版本镜像与静态产物，重新 readiness + 冒烟。
- 数据库只做 expand/contract，不做逆向迁移；迁移不兼容时按 RDS 备份/PITR 恢复（17 票）。
- 回滚触发：readiness 超时、Flyway 失败、冒烟失败、发布后 30 分钟内 5xx 或关键路径异常。

### 首位管理员与空库引导（runbook）

1. RDS 建库 `yunhefc`、建应用账号、加 ECS 私网白名单；确认 `flyway_schema_history` 为空。
2. 首次部署，Flyway V1–V5 完成后应用可启动（`baseline-on-migrate=false`，非空未托管库快速失败）。
3. 执行一次性 SQL（`scripts/sql/bootstrap-admin.sql`，实施批次创建）：插 `app_user`（`status=1`；`0` 为待审核，B 端登录会被拒）+ `user_role` 绑定 V3 的 ADMIN 角色 + 一条 `player`（`/c/me` 的 `MeView.player` 期望非空；`jersey=NULL` 避开正式号码唯一键）。按 `phone`/角色 `code` 查 ID，不硬编码；`user_role` 与 `player` 用 `ON DUPLICATE KEY UPDATE is_deleted=0` 处理重复执行，`app_user` 重复执行只恢复 `status`/`is_deleted`，**不重置密码**。
4. 校验：ADMIN 权限数 = 28；B 端登录成功；`/c/me` 返回 `player` 非空。登录后立即改密，并从运维记录清除口令。
- BCrypt：存裸哈希（60 字符，不带 `{bcrypt}` 前缀）；用应用同款 `BCryptPasswordEncoder` 生成最稳，或用 `htpasswd -bnBC 10 "" '<pwd>' | tr -d ':\n' | sed 's/^\$2y\$/$2a$/'` 生成 `$2a$`。
- 不新增 Flyway 种子、不加启动建号代码；生产空库不得执行 `test-data.sql`。
- Flyway 在应用启动时执行，运行时数据库账号因此需要 DDL 权限；是否拆分迁移账号归 16 票。

### 可观测性

- Actuator 仅暴露 `health,info`；开启 liveness/readiness，readiness 覆盖 DB + Redis（Kafka 与 Outbox 积压用脚本监控，不进入 readiness，避免 Kafka 抖动把实例摘除）；不暴露 details；healthcheck `start_period=180s`（JVM + Flyway + Kafka 冷启动）。
- `logback-spring.xml` 输出 ECS JSON，含 requestId/userId；按天 + 100MB 滚动，保留 14 天、总量 2GB；Nginx 访问/错误日志按 Docker 轮转。
- 容器 healthcheck：backend readiness、`redis-cli ping`、`kafka-broker-api-versions`。
- 告警用阿里云云监控 + 轻量 cron（不引入 Prometheus）：磁盘 >75%、内存 >90% 持续 5 分钟、容器 unhealthy、Kafka 消费组 lag >1000 持续 10 分钟、Outbox 积压 >500 持续 15 分钟、备份新鲜度（17 票）；渠道为钉钉/企业微信 webhook，严重项短信。
- runbook 固定排障入口：readiness、`/actuator/health`、最近 200 行日志、消费组 lag。

### 真实 IP

- Nginx 设 `X-Forwarded-For $proxy_add_x_forwarded_for`、`X-Forwarded-Proto`、`X-Forwarded-Host`；后端 `server.forward-headers-strategy=framework`。
- 只有 Nginx 能访问后端（Compose 内网，backend 不发布端口）；限流/锁定阈值与 api 子域暴露面归 16 票。

### 影响

- 17 票：备份范围改为 RDS 自动备份 + binlog/PITR，不再使用 mysqldump；本地副本是否保留由该票定。
- 13 票：流水线按手动批准、`sha-<7>` tag、保留 3 版落地。
- 14 票：微信回调固定在 www 同源路径。
- 16 票：CORS 仅兜底、Cookie host-only、Actuator 只经 Nginx 内网。
- 文档：03 v1.14、06 §47（v1.44）；需求/01/02/04/05/`schema.sql`/Flyway 不改。

## Comments

- 票 15 修订（2026-11-12）：Nginx `client_max_body_size` 由 `2m` 调整为 `3m`（multipart 头部开销），后端仍以 2MiB 校验文件字节；03 §7 已同步。
- 票 17 修订（2026-11-12）：告警清单中的「备份新鲜度」不再接入云监控，改为票 19 的发布前人工检查项；RDS 仅保留 7 天自动备份 + binlog/PITR，不做 OSS/本地副本、手动/ECS 快照；Redis 仅 AOF+RDB 命名卷，不独立备份。03 §7/§9/§10 已同步。
