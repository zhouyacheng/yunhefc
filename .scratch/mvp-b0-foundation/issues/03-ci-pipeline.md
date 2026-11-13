# 03 — CI/CD 流水线

Type: implementation
Status: resolved
Blocked by: 01、02

**What to build:** PR 在合并前强制跑通原型语法、后端与前端门禁；main 合并额外产出可复现的 `sha-<7>` 双镜像并跑真实后端 E2E；通过手动触发、人工批准的 workflow 完成一次 RC 发布与回滚。

## Scope

- `ci.yml`：
  - PR：`prototype`（Node 24 `node --check`）、`backend`（temurin 21 + Maven 缓存，`./mvnw -B -ntp verify`）、`frontend`（pnpm 12 / Node 24，`install --frozen-lockfile` → `typecheck` → `build` → `test`）三项必需。
  - main 追加：`e2e`（MySQL 8.4 + Redis 6.2.24 service、下载 boot jar、dev profile 启动并轮询 health ≤180s、执行 `test-data.sql`、Playwright Chromium、失败上传 test-results/report 7 天）；`images`（buildx、ACR 登录、`yunhefc-backend`/`yunhefc-nginx`、`sha-<7>`、`linux/amd64`、禁 `latest`）。
  - 并发组：PR 可取消除旧运行；main 不取消；job 超时按票 13 设置。
- `deploy.yml`：`workflow_dispatch` 输入 `sha`（7 位 hex，可选 note）；校验 commit 属于 `origin/main`；production Environment 人工批准；`docker manifest inspect` 校验双镜像；rsync 该 commit 的 `deploy/` 到 release 目录；SSH 执行 `release.sh --sha <sha7>`；`if: always()` 向 webhook 发结果（result、阶段、sha、actor、run URL），webhook 失败不影响 job。
- 仓库文档：分支保护、必需检查、Secrets/Variables 清单写入 `deploy/RUNBOOK.md` 或部署 README。

## Test obligations

- PR 事件下三项检查均为必需且失败即阻断；无路径过滤导致必需检查被静默跳过。
- main 事件下 e2e 与 images 成功；镜像 tag 为 `sha-<7>`，两个 manifest 都存在。
- `deploy.yml` 在非 main commit、非法 sha、缺镜像时拒绝；批准后完成一次 RC 发布，回滚一次成功。
- E2E 使用的种子可重复执行；`SMS_MOCK_ENABLED=true`、`OSS_MODE=mock`、`WECHAT_MODE=mock`。
- 不在 CI 中做压测、备份恢复、真实短信/微信/OSS 调用、漏洞扫描。
- 不改业务代码、schema、Flyway；未配置的外部 Secrets 在票面标注为外部前置。

## Result

- 交付：`.github/workflows/ci.yml`、`.github/workflows/deploy.yml`、`deploy/RUNBOOK.md` §6（分支保护、必需检查、Variables/Secrets、手动发布与外部前置），并更新 §5 过时条目。
- `ci.yml`：PR 门禁 `prototype`（Node 24、8 个原型 JS `node --check`，5 min）、`backend`（temurin 21 + Maven 缓存、`./mvnw -B -ntp verify`，20 min）、`frontend`（pnpm 12.16.1 / Node 24、`install --frozen-lockfile` → `typecheck` → `build` → `test`，15 min）；main push 追加 `e2e`（依赖 backend、MySQL 8.4 + Redis 6.2.24 service、下载 boot jar、dev profile 启动并轮询 health ≤180s、重放 `test-data.sql`、Playwright Chromium、失败产物保留 7 天，20 min）与 `images`（依赖 backend/frontend/e2e、buildx + ACR 登录、`linux/amd64`、双仓库 `sha-<7>`、manifest 校验，20 min）。PR 并发组可取消旧运行，main 不取消；无路径过滤、无 tag 发布、无 `latest`。
- `deploy.yml`：`workflow_dispatch` 输入 `sha`（7 位小写 hex）与可选 `note`；校验仓库变量、commit 属于 `origin/main`、`docker manifest inspect` 双镜像；`production` Environment 人工批准；固定 `ECS_KNOWN_HOSTS` 指纹（不用 `ssh-keyscan`）；把目标 commit 的 compose 快照与 `release.env`（`IMAGE_TAG=sha-<7>`）写入 `/opt/yunhefc/releases/<sha7>/` 后 SSH 执行 `release.sh --sha`；`if: always()` webhook 发送 result/stage/tag/actor/note/run URL，失败不影响 job；并发组 `deploy-production` 不取消。
- 验证：`actionlint`（rhysd/actionlint:latest，含 shellcheck）对两份 workflow 退出 0；prototype 8 文件 `node --check` 通过；`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw -B -ntp verify` BUILD SUCCESS（surefire 197、failsafe 306，0 失败，4:52）；`frontend` 的 `pnpm install --frozen-lockfile` / `typecheck` / `build` / `test` 全部通过（web 93 项）；`grep -c '^CREATE TABLE' scripts/sql/schema.sql` = 27。
- E2E 种子可重复：临时 MySQL 8.4 + Redis 6.2.24，boot jar 以 dev profile 启动，`/actuator/health` 9s 通过；`test-data.sql` 连续执行两次均 exit 0（`app_user` 29、`team_match` 5、1021 `status=0`），与 CI 的 fresh DB + Flyway 场景一致。
- deploy 校验逻辑：非法格式与不存在的 sha 被拒绝；`sh -s -- <sha>` 传参的远端脚本本地演练通过。
- 评审修正：health 轮询改为 `SECONDS` deadline（严格 ≤180s）；CI 下 Playwright 增加 HTML reporter，使失败上传的 `playwright-report` 真实存在；RUNBOOK 补 Flyway 迁移前置与「Environment deployment branches 是手动触发 ref 的唯一防线」。
- 说明：release 目录只同步两份 compose 快照与 `release.env`（`release.sh` 契约、票 13 与 03 §6.4 的 compose 快照决策），不复制整个 `deploy/` 树。
- 未执行/外部前置：真实 GitHub Actions PR/main 运行、ACR 双仓库推送与 manifest 校验、ECS 上的 RC 发布与回滚、webhook 投递、分支保护与 Environment 审批配置。commit 祖先校验依赖 `origin/main`，本机无该 ref，未做真实分支演练。
- 未改业务代码、schema、Flyway、Quartz DDL。
