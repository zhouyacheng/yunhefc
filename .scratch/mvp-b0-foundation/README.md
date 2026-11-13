# B0 基座 — 实施规格

Status: resolved（2026-11-13）
Type: spec

事实源：`.scratch/mvp-release/batches.md` §B0/§4/§5/§6；决策票 `.scratch/mvp-release/issues/12-grilling-deploy-topology.md`、`13-grilling-ci-cd.md`、`16-grilling-security-baseline.md`、`17-grilling-backup-retention.md`；现状盘点 `.scratch/mvp-release/research/04-deploy-baseline.md`；`docs/design/03.整体架构设计.md` §6.4/§7；`docs/rules/logging-rules.md`、`java-rules.md`；`backend/`、`.github/workflows/ci.yml`、`frontend/` 现状。

本规格只定义 B0 的实施边界与验收口径，不写部署文件、不改业务代码、不改 `schema.sql` / Flyway / Quartz DDL。

---

## Problem Statement

上线所需的运行基座仍为零：

- `deploy/` 完全不存在：没有 Dockerfile、Compose、Nginx、发布/回滚脚本、`.env.example`、首位管理员引导脚本与 runbook。
- 没有 `application-prod.yml`：Cookie 仍默认 `Secure=false`、无 forward-headers、CORS 默认 localhost、springdoc 与错误详情未关闭、Flyway 仍在应用启动时执行。
- 没有 `logback-spring.xml`：无 ECS JSON、无 requestId/userId、无按天/100MB 滚动与 14 天保留；Actuator 只有 health/info，无 liveness/readiness。
- CI 只有 prototype 的 `node --check`：后端 `mvn verify`、前端 typecheck/build/test、E2E、双镜像与手动发布全部未接入。
- 结果：B1–B4 的代码可以本地跑通，但无法以可回滚、可观测、可验收的方式部署到生产。

## Solution

按 3 张实施票交付生产基座，票间按依赖边执行：

| 票 | 内容 | 前置 | 主要交付 |
|---|---|---|---|
| 01 | prod profile 与可观测性 | 无 | `application-prod.yml`、启动 fail-fast、JSON 日志、Actuator probes |
| 02 | 部署文件与发布脚本 | 01 | 双 Dockerfile、Compose + override、Nginx、`release.sh`、RUNBOOK、首位管理员 SQL |
| 03 | CI/CD 流水线 | 01、02 | PR/main 检查、E2E、`sha-<7>` 镜像、手动发布 workflow |

**批次完成门**

- 干净 ECS 上 `compose up`：readiness ≤180s，冒烟（health + C 端首页 + 一条公开读接口）通过。
- 首位管理员 runbook 执行成功：B 端登录、ADMIN 权限 28、`/c/me` 的 `player` 非空、改密。
- CI PR 三项必需检查绿；main 的 e2e/images 绿；`deploy.yml` 完成一次 RC 发布与一次回滚演练。
- 空库 Flyway 幂等；无 schema/Flyway/Quartz DDL 改动。

---

## Implementation Decisions

### 生产拓扑与配置

- 单 ECS 2C2G、系统盘 ≥40GB；MySQL 用阿里云 RDS MySQL 8.4（同 VPC 私网）；Redis 6.2.24、Kafka 3.7.2 自建 Compose；Nginx 唯一对外 80/443；后端单实例（Quartz `isClustered=false`）。
- Compose 资源：backend 832m、kafka 512m、redis 192m、nginx 48m；JVM `-Xms256m -Xmx448m -XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=48m -XX:ReservedCodeCacheSize=64m -XX:ActiveProcessorCount=2 -Xss512k`；2GB swap 仅兜底。
- 连接参数：Hikari `maximum-pool-size=10`、`connection-timeout=5s`、`max-lifetime≤30m`；Kafka 单节点 KRaft，topic 预创建（`yunhefc.notification.events`、`yunhefc.audit.events` 及各自 `.DLT`，1 分区 1 副本），禁止自动建 topic。
- 三子域：`www`/`admin` 托管 SPA 并同源反代 `/api/`；`api` 仅直连别名（`/api/` + `/actuator/health`）；前端 `baseUrl` 保持相对 `/api/v1/{c|b}`，B 端构建 `ADMIN_BASE_URL=/`。
- Cookie host-only（不设 Domain）、生产 Secure、HttpOnly、SameSite=Lax；CORS 仅 `https://www.<domain>` / `https://admin.<domain>` 兜底。
- Nginx 覆写 `X-Forwarded-For $remote_addr`（不用 append），后端 `server.forward-headers-strategy=framework`；backend 不发布端口。
- 迁移账号分离：发布前用迁移账号执行 Flyway，应用 `spring.flyway.enabled=false` 且仅 DML 权限。

### 发布与回滚

- 镜像 tag `sha-<git sha 前 7 位>`，禁止 `latest`；发布目录 `/opt/yunhefc/releases/<sha7>/`，`current` 软链，保留当前 + 前 2 版。
- `release.sh`：`set -euo pipefail` + `flock`，可重入；校验双镜像 → 写 `state/previous` → 切 `current` → pull → `up -d --no-deps backend nginx` → readiness ≤180s → 冒烟；`--rollback [sha7]` 默认回 `state/previous`；失败保留 `current` 并退出非零。
- 发布由 `deploy.yml` 手动触发 + production Environment 人工批准；失败不自动回滚。

### 可观测性与安全

- Actuator 仅 `health,info` + liveness/readiness，readiness 覆盖 DB + Redis，`show-details=never`；Kafka/Outbox 积压走脚本监控，不进 readiness。
- `logback-spring.xml`：ECS JSON、requestId/userId、按天 + 100MB、保留 14 天 / 2GB；prod INFO。
- prod 启动自检：`SESSION_COOKIE_SECURE=true`、无 Cookie Domain、CORS 仅两个 https 域名、`DB_PASSWORD` 非默认、`REDIS_PASSWORD` 非空；叠加短信/OSS/微信已有 fail-fast。
- springdoc 与 Swagger 在 prod 关闭；`server.error.include-*=never`；HSTS 15552000 + includeSubDomains（不 preload）、nosniff、Referrer-Policy、X-Frame-Options DENY、最小 Permissions-Policy；不加 CSP。

### CI/CD

- PR：`prototype` / `backend` / `frontend` 三项必需；main 追加 `e2e` 与 `images`；无 tag 发布、无路径过滤。
- E2E：MySQL 8.4 + Redis 6.2.24 service，dev profile 后台启动 boot jar，`test-data.sql` 灌种子，Playwright Chromium；失败上传 test-results/报告 7 天。
- 镜像：`yunhefc-backend`、`yunhefc-nginx` 双 ACR，同 `sha-<7>`，`linux/amd64`，两个 manifest 都存在才算完整。
- GitHub Secrets 只放 ACR 写凭证、ECS SSH、webhook；业务密钥只放 ECS `/opt/yunhefc/.env`（600）。

### 外部前置（不阻塞代码票）

ECS / RDS / 三子域 DNS / 通配符证书 / ACR 双仓库 / `.env`；RAM 用户与 dev/prod OSS Bucket；短信 APPCODE/签名/模板；微信 AppID/Secret 与网页授权域名、`MP_verify_*.txt`。缺项时 B0 代码可完成，但完成门不通过，RC 顺延。

---

## Out of Scope

- B4 C03 补齐与微信前端授权流程。
- 真实短信/微信/OSS 的业务验收（归 B4 与上线检查单）。
- 压测、备份恢复演练、依赖漏洞扫描、Prometheus/Grafana、K8s/staging。
- 自动回滚、蓝绿发布、多实例横向扩展。
- 任何业务代码、表结构、Flyway、Quartz DDL、API 契约变更。

---

## Further Notes

### 实施票序

| 票 | 文件 | Blocked by |
|---|---|---|
| 01 | `issues/01-prod-profile-and-observability.md` | — |
| 02 | `issues/02-deploy-files.md` | 01 |
| 03 | `issues/03-ci-pipeline.md` | 01、02 |

建议每票单独会话、票间 `/clear`。`02` 依赖 `01` 冻结的配置键；`03` 的镜像 job 依赖 `02` 的 Dockerfile 与 `release.sh`。

### 完成标准

- 3 票全部 resolved；每票在 `## Result` 记录提交与验证。
- 批次完成门全部通过，外部证据（证书有效期、Put/Get、真机收码、微信回调、RDS PITR）留档。
- 未执行项（如未做真实发布）在汇报中明确写出。

### 复核偏差记录（2026-11-14）

- `scripts/setup-ci-external.sh` 是 `/wizard` 产出的外部前置交互脚本（ECS/ACR/DNS/证书/微信凭证首次配置），不在 B0 票面内，不参与部署流水线。
- `application.yml` 新增 `AUTH_LOGIN_RATE_LIMIT_*`，CI 的 E2E job 设为 1000 次，用于固定测试账号绕过登录限流；运行时默认仍为 10 次/1 分钟，未新增对外契约。
