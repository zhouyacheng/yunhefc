# 02 — 部署文件与发布脚本

Type: implementation
Status: resolved
Blocked by: 01

**What to build:** 在一台干净 ECS 上，用仓库里的部署文件启动后端、Redis、Kafka 与 Nginx；通过 `release.sh` 完成一次不可变版本的发布与回滚；按 runbook 创建首位管理员并完成登录验收。

## Scope

- 镜像：`backend.Dockerfile`（temurin 21 JRE、非 root、复制 boot jar）；`web.Dockerfile`（node 24 + pnpm 构建 C/B dist，最终 nginx 1.27-alpine；www → `/usr/share/nginx/html/www`，admin → `/usr/share/nginx/html/admin`）。
- Compose：`docker-compose.yml` + prod override；服务 backend/kafka/redis/nginx（MySQL 用 RDS）；mem_limit 与 JVM 参数按 B0 规格；backend readiness、`redis-cli ping`、`kafka-broker-api-versions` healthcheck；Kafka topic 预创建（4 个 topic，1 分区 1 副本，禁止自动建 topic）。
- Nginx：`www` / `admin` / `api` 三子域；TLS 1.2/1.3；80 → 301；HSTS 与安全头；`X-Forwarded-For $remote_addr` 覆写 + `X-Forwarded-Proto/Host`；SPA fallback；带 hash 的 assets 长缓存、`index.html` no-cache；gzip；`client_max_body_size 3m`。
- 发布脚本 `release.sh`：`sha-<7>`、`/opt/yunhefc/releases/<sha7>/`、`current` 软链、`state/previous`；pull → 切 current → `up -d --no-deps backend nginx` → readiness ≤180s → 冒烟；`--rollback [sha7]`；`flock` 与可重入；只保留当前 + 前 2 版。
- `RUNBOOK.md`：RDS PITR、`.env` 恢复、证书重签、审计补导、FAILED Outbox 处理、清理失败处理、首位管理员、迁移账号分离与发布前 `flyway migrate`。
- `scripts/sql/bootstrap-admin.sql`：按 `phone`/角色 `code` 查 ID，不硬编码；`app_user` + `user_role(ADMIN)` + `player`；重复执行只恢复状态、不重置密码。
- `deploy/.env.example`：只放占位符，覆盖 DB/Redis/Kafka/Cookie/CORS/PUBLIC_BASE_URL/微信/短信/OSS。

## Owned files

- `deploy/**`（Dockerfile、compose、nginx、bin、RUNBOOK、.env.example）
- `scripts/sql/bootstrap-admin.sql`

## Test obligations

- 两个镜像可构建；backend 容器以非 root 运行。
- 干净主机 `compose up` 后 readiness ≤180s；冒烟：`/actuator/health` + C 端首页 + 一条公开读接口。
- `release.sh` 发布一次、`--rollback` 一次，均能回到 readiness/冒烟通过；同一 `--sha` 重跑幂等。
- 首位管理员 runbook：B 端登录、ADMIN 权限数 28、`/c/me` 的 `player` 非空、改密成功。
- 空库 Flyway 幂等；无 schema/Flyway/Quartz DDL 改动。
- 不执行生产 SQL 演练；真实域名/证书/ACR 属于外部前置，用本地或临时环境验证脚本逻辑。

## Result

- 提交：`b32c893`（分支 `qua`）。
- 交付：`deploy/backend.Dockerfile`、`deploy/web.Dockerfile`、`deploy/docker-compose.yml`、`deploy/docker-compose.prod.yml`、`deploy/nginx/`（nginx.conf、snippets、templates）、`deploy/bin/release.sh`、`deploy/.env.example`、`deploy/RUNBOOK.md`、`scripts/sql/bootstrap-admin.sql`；另有构建上下文必需的根 `.dockerignore`。
- 设计同步：`docs/design/03.整体架构设计.md` §6.4 目录树对齐 `deploy/bin/release.sh`、`deploy/.env.example`、`docker-compose.prod.yml`（v1.19）；未改需求/01/02/04/05/06、schema/Flyway/Quartz。
- 镜像：backend 镜像构建成功，容器以 uid/gid 10001 非 root 运行、无端口发布、`/app/app.jar` 存在；web 镜像用 node 24 + pnpm 12 构建 C/B dist，最终 nginx 1.27-alpine，产物落到 `/usr/share/nginx/html/{www,admin}`。
- 本机为 arm64；amd64 构建在 QEMU 下 `vue-tsc` 触发 Illegal instruction，属跨架构模拟限制，未记为失败；CI 原生 linux/amd64 构建不受影响。
- Nginx：`nginx -t` 通过；80→301 且保留 path；www/admin 首页 200、SPA fallback 200；assets `public, max-age=31536000, immutable`、index `no-cache`；HSTS/nosniff/Referrer-Policy/X-Frame-Options/Permissions-Policy 齐全；api 子域非白名单路径 404，`/actuator/health/readiness` 404，`/actuator/health` 经 Nginx 返回 UP。
- Compose：`docker compose config` 通过；本机完整栈（backend + Kafka 3.7.2 + Redis + nginx + 临时 MySQL 8.4 scratch 库）`up -d` 成功，backend healthy、readiness `{"status":"UP"}`、C 端首页 200、公开读接口 `code=0`；4 个 topic 预创建；Redis 未认证 `NOAUTH`、带密码 `PONG`；backend 以 10001 非 root 运行且无端口发布；ECS JSON 日志写入文件。
- 首位管理员：scratch 库执行 `bootstrap-admin.sql` 两次，`status=1`、密码哈希不被重置、`user_role`/`player` 恢复 `is_deleted=0`、`admin_permissions=28`；B 端登录成功（28 权限）、C 端 `/c/me` 的 `player` 非空。改密当前只能走 C 端短信重置，依赖真实短信通道，未执行。
- `release.sh`：stub docker/curl 下发布、同一 `--sha` 重跑、默认/指定 sha 回滚、readiness 失败保留 `current`、保留 current+previous+最新一个目录均通过；`bash -n` 通过。
- `mvn -B -ntp verify` BUILD SUCCESS（failsafe 306，0 失败，5:12）；`pnpm test` 114 passed（api-client 21、web 93；admin 无测试）。
- `grep -c '^CREATE TABLE' scripts/sql/schema.sql` = 27；未改 schema/Flyway/Quartz DDL。
- 评审修正：bootstrap SQL 查询补 `is_deleted=0`；nginx www/admin 公共 location 抽到 `snippets/spa-locations.conf`；release.sh 去掉重复分支与不存在的旧格式兼容、镜像清理改固定字符串匹配、基础设施仅在未运行时启动；compose 增加 `stop_grace_period=40s`；backend Dockerfile 对多 jar 构建直接失败；RUNBOOK 排障命令补 compose 变量函数，审计补导改为 backend 启动后更新 Quartz 触发时间（避免 `overwrite-existing-jobs=true` 覆盖）。
- 未执行/外部前置：真实 ACR 双仓库与拉取、三子域 DNS、通配符证书、RDS/Redis 实网、真实发布与回滚、首位管理员短信改密、空库 Flyway 重跑（本票未改迁移）；`.github/workflows/deploy.yml` 与 CI 归 03。
