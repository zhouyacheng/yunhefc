# 13 — CI/CD 流水线与 Secrets

Type: grilling
Status: resolved
Blocked by: 04

## Question

现有 `.github/workflows/ci.yml` 只有 prototype `node --check`。确定完整流水线：

- GitHub Actions 阶段：后端 `mvn test` / ArchUnit / 镜像构建推阿里云 ACR；前端 pnpm 安装/构建/Vitest；Playwright E2E；部署阶段 SSH 到 ECS 执行 compose 更新。
- 触发策略：push / PR / tag；镜像不可变 tag；与「部署拓扑、发布与回滚」的发布步骤如何衔接。
- Secrets 清单：ACR、ECS SSH、OSS、微信、短信、数据库等；密钥只放仓库 Secrets 或服务器环境变量。
- 失败处理、通知、回滚与最小可行阶段（哪些先上，哪些可后补）。
- 票 12 约束：发布为手动批准；镜像 tag `sha-<7>`；保留最近 3 个版本；回滚由 runbook 手动执行。

产出流水线草案与 Secrets 清单；不写 workflow 文件。事实指针：03 §6.4、04 答案、`.github/workflows/ci.yml`、根 `pnpm-workspace.yaml`、`backend/pom.xml`。

## Answer

状态：resolved（2026-11-12）。以下为已确认决策与流水线草案；本票不写 workflow、Dockerfile、compose、nginx 或 release.sh，配置落地归 18 号票切片后的实施批次。

### 触发与 job 布局

| 触发 | job | 说明 |
|---|---|---|
| `pull_request` | `prototype` / `backend` / `frontend` | 三项均为必需检查 |
| `push` → main | 同上 + `e2e` + `images` | `e2e` needs backend；`images` needs backend/frontend/e2e |
| `workflow_dispatch`（deploy.yml） | `deploy` | Environment `production` 人工批准 |

- 无 tag 发布、无 `latest`、无路径过滤（避免必需检查被静默跳过）。
- PR 并发组 `ci-<ref>` 可取消旧运行；main 不取消；deploy 并发组 `deploy-production` 不取消。
- 后端：`./mvnw -B -ntp verify`（现有 Failsafe 配置下 `mvn test` 不跑 45 个 `*IT`）；main 上把 boot jar 作为 artifact 供 e2e/images 复用，避免重复构建。
- 前端（cwd `frontend/`）：`pnpm install --frozen-lockfile` → `pnpm typecheck` → `pnpm build` → `pnpm test`；lint/format 暂不设为门禁。
- 工具：temurin 21 + Maven 缓存；pnpm 12.16.1 + node 24 + pnpm store 缓存；各 job 超时 5/20/15/20/20/20 分钟。

### E2E（仅 main）

1. services：`mysql:8.4`（`MYSQL_DATABASE=yunhefc`、应用账号、`mysqladmin ping`）+ `redis:6.2.24`（`redis-cli ping`），端口映射到 runner。
2. 下载 backend jar，以 `SPRING_PROFILES_ACTIVE=dev`、`DB_URL`/`REDIS_HOST` 指向 `127.0.0.1` 后台启动；轮询 `/actuator/health` ≤180s（现有端点已 permitAll；probes 属 prod profile，不阻塞 E2E）。
3. Flyway 随启动完成后灌种子：`mysql -h127.0.0.1 ... yunhefc < scripts/sql/test-data.sql`（用 `mysql:8.4` 容器执行亦可）。
4. `pnpm exec playwright install --with-deps chromium` → `CI=1 pnpm test:e2e`（沿用 5273/5274 Vite webServer，代理到 8080）。
5. 失败上传 `frontend/test-results` 与 `playwright-report`，保留 7 天。
6. 已知局限：dev profile 覆盖不到生产 Kafka/Outbox 与真实微信/短信；由部署冒烟与人工验收补。

### 镜像

- 两个 ACR 仓库：`yunhefc-backend`、`yunhefc-nginx`；同一 `sha-<7>`，`linux/amd64`，buildx `type=gha` 缓存，禁止 `latest`。两个 manifest 都存在才算该 tag 完整。
- backend：`deploy/backend.Dockerfile`，运行时 `eclipse-temurin:21-jre`，非 root，复制 CI 产出的 boot jar；JVM 限额留在 Compose。
- nginx：`deploy/web.Dockerfile`，node 24 + pnpm 构建 C/B dist，最终 `nginx:1.27-alpine`；web dist → `/usr/share/nginx/html/www`，admin dist → `.../admin`；`deploy/nginx/` 配置打进镜像。
- 构建期变量：`ADMIN_BASE_URL=/`（生产 admin 子域根路径；dev/E2E 仍是 `/admin/`，不能混用）、`VITE_WEB_URL=https://www.<domain>`、`VITE_ADMIN_URL=https://admin.<domain>`；域名来自仓库变量 `PUBLIC_DOMAIN`，改域名需重建镜像。
- TLS 私钥绝不入镜像；证书由宿主机只读挂载到 `/etc/nginx/certs`；域名用 `/etc/nginx/templates` + `DOMAIN` 运行时 envsubst。

### 发布

- `workflow_dispatch` 输入 `sha`（`^[0-9a-f]{7}$`，另可带 `note`）；检出该 commit，`git merge-base --is-ancestor HEAD origin/main`；解析 sha7；runner 登录 ACR 后 `docker manifest inspect` 校验双镜像存在。
- Environment `production`：required reviewers、限制 main；`permissions: contents: read`；SSH 使用 `ECS_KNOWN_HOSTS` 固定主机指纹，不用 `ssh-keyscan`；非 root `deploy` 用户。
- 发布目录：`/opt/yunhefc/releases/<sha7>/` 保存该版本的 compose 快照（base + override）与 `release.env`（`IMAGE_TAG=sha-<7>`）；`current` 软链指向 release 目录，`state/previous` 记录上一个可用版本。稳定的 `/opt/yunhefc/bin/release.sh` 由基础设施批次安装，不随每次发布自更新。
- workflow：rsync/scp 该 commit 的 `deploy/` 配置到 release 目录 → SSH 执行 `release.sh --sha <sha7>`。
- `release.sh` 契约：`set -euo pipefail` + `flock`，可重入；ACR 只读凭证 `docker login`；校验双镜像 → 切换 `current` 前写 `state/previous` → `docker compose -f current/docker-compose.yml pull backend nginx` → `up -d --no-deps backend nginx` → 轮询 DB+Redis readiness ≤180s → 冒烟（health + C 端首页 + 一条公开读接口）。`compose up` 退出 0 不代表健康，**以 readiness/冒烟为准**；任一步失败退出非零并保留 `current` 在失败版本。
- 手动回滚：`release.sh --rollback [sha7]`，默认切回 `state/previous`，重跑 readiness + 冒烟；保留当前 + 前 2 个 release，清理时绝不删除运行中与 previous 的目录/镜像。
- 失败检测依赖脚本退出码；SSH 中断后重跑同一 `--sha` 必须幂等。
- 通知：`if: always()` 向 `DEPLOY_WEBHOOK_URL` POST `{"msgtype":"text","text":{"content":...}}`（钉钉/企业微信群机器人通用），含 result、失败阶段、sha7、actor、run URL；webhook 失败不影响 job 结果。

### Secrets / 变量

GitHub（仅部署凭证，无运行时业务密钥）：

| 名称 | 类型 | 用途 |
|---|---|---|
| `ACR_REGISTRY` / `ACR_NAMESPACE` / `PUBLIC_DOMAIN` | vars | 镜像地址与构建期域名 |
| `ACR_USERNAME` / `ACR_PASSWORD` | secrets | CI 推送（写权限） |
| `ECS_HOST` / `ECS_USER` / `ECS_PORT` | vars | 部署目标（默认 `deploy` / 22） |
| `ECS_SSH_KEY` / `ECS_KNOWN_HOSTS` | secrets | SSH |
| `DEPLOY_WEBHOOK_URL` | secret | 发布结果通知 |

ECS（权限 600，不入库）：`/opt/yunhefc/.env` 放 DB_URL/账号/密码、Redis 密码、`SESSION_COOKIE_SECURE=true`、CORS、`PUBLIC_BASE_URL`、`DOMAIN`、微信、短信、OSS；ACR 只读凭证放 ECS 的 docker config 或 `/opt/yunhefc/env/acr.env`（与 CI 写权限账号分离）；证书在 `/opt/yunhefc/certs`。**生产密钥不进 GitHub Secrets。**

### 失败处理与范围

- main 分支保护：PR 必需、禁止强推；必需检查 `prototype`/`backend`/`frontend`；e2e/images 只在 main，不作为 PR 必需检查。
- 失败处理：GitHub 通知；Deploy 额外 webhook；无自动重试、无自动回滚。
- 不在 CI：压测、备份恢复、真实短信/微信/OSS 调用、TLS 续期、漏洞扫描、覆盖率/Sonar、PR E2E/镜像、部署后持续监控。

### 前置事实与风险（留给 18 号票/实施批次）

- ECS 的 OS、Compose plugin 版本、SSH 用户/端口、证书文件名与挂载路径、ACR 是否走 VPC 端点，需在基础设施批次核实；本票给默认值，不臆断。
- `test-data.sql` 与 Flyway V3 种子共存需在 E2E 首次运行验证（重跑会覆盖 1001/1002 密码哈希）。
- prod profile 必须开启 Actuator probes（DB+Redis）并配置 `forward-headers`，否则 readiness 判定无意义。
- Nginx 配置随镜像走；证书与 `.env` 不随 release 回滚，避免把密钥/证书带回旧值。

### 与票 12 的偏差

- 前端不再「随发布包挂载」；release 目录保存 compose 快照与 `release.env`（不是静态产物）；回滚改为**手动**；失败版本会留在线上，依赖运维响应。票 12 的静态挂载与自动检测措辞由本票取代，已同步 03 与 06 §48。
- 需求/01/02/04/05/`schema.sql`/Flyway 不变。

### 验证

本次仅文档与 tracker 改动；未写 workflow/Dockerfile/deploy 文件，未执行 SQL、未写业务代码、未运行测试。

## Comments

- 票 15 修订（2026-11-12）：nginx 镜像内 `client_max_body_size 3m`；后端 Spring `max-file-size=2MB`、`max-request-size=3MB`。E2E 上传走 mock `OssGateway`，不调用真实 OSS。
