# 云河FC 部署与运维 Runbook

适用：单 ECS（2C2G）+ 阿里云 RDS MySQL 8.4 + Compose（backend / Kafka / Redis / Nginx）。
事实源：设计文档 03（`docs/design/03.整体架构设计.md`）§6.4、§7；`.scratch/mvp-release/issues/12`、`13`、`16`、`17`。

> 生产密钥只放 ECS `/opt/yunhefc/.env`（600），同一份内容在密码管理器离线保管；不入库、不进 GitHub Secrets。

---

## 0. 目录、权限与前置

```text
/opt/yunhefc/
├── .env                    600，运行时配置（见 deploy/.env.example）
├── env/acr.env             600，ACR_REGISTRY / ACR_NAMESPACE / ACR_USERNAME / ACR_PASSWORD（只读拉取）
├── certs/                  fullchain.pem、privkey.pem（只读挂载到 nginx）
├── bin/release.sh          固定安装，不随发布自更新
├── releases/<sha7>/        docker-compose.yml、docker-compose.prod.yml、release.env
├── current -> releases/<sha7>
└── state/previous         上一次可用版本目录；release.lock
```

- ECS：系统盘 ≥40GB、2GB swap、Docker + Compose plugin；`deploy` 用户在 `docker` 组，非 root。
- 验证：`free -m` 可用内存 <1.9GB 时，把 `docker-compose.prod.yml` 的 backend `-Xmx` 降至 384m、Kafka `-Xmx` 降至 192m；仍 OOM 则升配 4C8G。
- 安装/更新发布脚本（脚本变更时执行一次）：

```bash
sudo install -d -o deploy -g deploy /opt/yunhefc/{bin,releases,state,env,certs}
sudo install -m 755 deploy/bin/release.sh /opt/yunhefc/bin/release.sh
```

后续排障命令不经过 `release.sh`，需要手动补齐 compose 变量。在当前 shell 定义一次：

```bash
compose_current() {
  local registry namespace tag
  registry=$(sed -n 's/^ACR_REGISTRY=//p' /opt/yunhefc/env/acr.env | tail -1)
  namespace=$(sed -n 's/^ACR_NAMESPACE=//p' /opt/yunhefc/env/acr.env | tail -1)
  tag=$(sed -n 's/^IMAGE_TAG=//p' /opt/yunhefc/current/release.env | tail -1)
  IMAGE_REGISTRY="${registry%/}/$namespace" IMAGE_TAG="$tag" \
    docker compose --project-name yunhefc --env-file /opt/yunhefc/.env \
      -f /opt/yunhefc/current/docker-compose.yml \
      -f /opt/yunhefc/current/docker-compose.prod.yml "$@"
}
```

---

## 1. 首次部署

### 1.1 RDS 与账号分离

1. RDS MySQL 8.4，与 ECS 同地域同 VPC（私网），ECS 私网 IP 加入白名单。
2. 建库 `yunhefc`（utf8mb4 / utf8mb4_0900_ai_ci）。
3. 两个账号：
   - `yunhefc_migrator`：Flyway 迁移，需要 DDL（`CREATE/ALTER/INDEX/DROP` 等）。
   - `yunhefc_app`：仅 `SELECT, INSERT, UPDATE, DELETE`，供应用使用（`spring.flyway.enabled=false`）。

```sql
CREATE DATABASE IF NOT EXISTS yunhefc
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'yunhefc_migrator'@'%' IDENTIFIED BY '<migrator-pwd>';
CREATE USER 'yunhefc_app'@'%' IDENTIFIED BY '<app-pwd>';
GRANT ALL PRIVILEGES ON yunhefc.* TO 'yunhefc_migrator'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON yunhefc.* TO 'yunhefc_app'@'%';
```

4. 确认空库：`SELECT COUNT(*) FROM flyway_schema_history;` 应为 0 或表不存在。

### 1.2 DNS / 证书 / 环境文件 / ACR

1. 三子域解析到 ECS：`www.<domain>`、`admin.<domain>`、`api.<domain>`。
2. 通配符证书放到 `/opt/yunhefc/certs/fullchain.pem`、`privkey.pem`，剩余有效期 >30 天。
3. `cp deploy/.env.example /opt/yunhefc/.env && chmod 600 /opt/yunhefc/.env`，填真实值；`SESSION_COOKIE_SECURE=true`、`SESSION_COOKIE_DOMAIN=` 留空。
4. ACR 只读凭证 `/opt/yunhefc/env/acr.env`（600）：

```dotenv
ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
ACR_NAMESPACE=<namespace>
ACR_USERNAME=<read-only-user>
ACR_PASSWORD=<read-only-password>
```

### 1.3 发布前迁移（应用启动前，人工执行）

迁移文件只在仓库里（不随 release 目录下发）。在目标 commit 的代码检出中：

```bash
sha7=<7位commit>
mkdir -p /opt/yunhefc/migrations
cp backend/src/main/resources/db/migration/*.sql /opt/yunhefc/migrations/

docker run --rm --network host \
  -v /opt/yunhefc/migrations:/flyway/sql:ro \
  flyway/flyway:11.7.2 \
  -url="jdbc:mysql://<rds-host>:3306/yunhefc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8" \
  -user=yunhefc_migrator -password='<migrator-pwd>' -connectRetries=10 migrate
```

- 迁移失败：不发版，修复后重跑；禁止用应用账号执行 DDL。
- 迁移成功后确认 `flyway_schema_history` 与目标版本一致。
- 应用以 `spring.flyway.enabled=false` 启动；发布前若迁移未执行，应用不会补跑。

### 1.4 首次发布

1. 由 CI/Rsync 准备 release 目录（正常由 `deploy.yml` 完成）：

```bash
sha7=<7位commit>
install -d /opt/yunhefc/releases/$sha7
rsync -a deploy/docker-compose.yml deploy/docker-compose.prod.yml \
  /opt/yunhefc/releases/$sha7/
printf 'IMAGE_TAG=sha-%s\n' "$sha7" > /opt/yunhefc/releases/$sha7/release.env
```

2. 执行发布：

```bash
/opt/yunhefc/bin/release.sh --sha "$sha7"
```

脚本顺序：校验双镜像 → `docker compose pull backend nginx` → 写 `state/previous` → 切 `current` → 基础设施未运行时启动 `redis/kafka/kafka-init`（已运行则不动）→ `up -d --no-deps backend nginx` → readiness ≤180s → 冒烟（C 端首页、`/actuator/health`、公开读接口）。
`compose up` 退出 0 不代表健康；以 readiness 与冒烟为准。任一步失败脚本退出非零，`current` 保留在失败版本。

### 1.5 首位管理员

1. 生成 BCrypt 哈希（存裸哈希，60 字符，`$2a$` 开头）：

```bash
htpasswd -bnBC 10 "" '<strong-password>' | tr -d ':\n' | sed 's/^\$2y\$/$2a$/'
```

2. 编辑 `scripts/sql/bootstrap-admin.sql`：设置 `@phone`、`@name`、`@position`，用上一步结果替换 `@password_hash`。
3. 执行（按 `phone` 与角色 `code` 查 ID；重复执行只恢复 `status`/`is_deleted`，不重置密码）：

```bash
mysql -h <rds-host> -u yunhefc_migrator -p yunhefc < scripts/sql/bootstrap-admin.sql
```

4. 校验：
   - 末尾 `admin_permissions` = **28**；
   - `https://admin.<domain>` 用该手机号/密码登录成功；
   - C 端 `/c/me` 返回的 `player` 非空；
   - 登录后立即走 C 端「忘记密码」短信重置（AUTH-06）把初始口令换成个人口令，确认新口令可登录，并从运维记录中删除初始口令。
   - 当前没有「已登录状态改密」接口，只能用短信重置；改密需要真实短信通道可用。
5. 生产空库**不得**执行 `test-data.sql`。

### 1.6 首次验收

```bash
curl -fsS https://api.<domain>/actuator/health          # {"status":"UP"}
curl -fsSI https://www.<domain>/                        # 200；HSTS/nosniff/Referrer-Policy/X-Frame-Options
curl -fsS https://www.<domain>/api/v1/c/matches?page=1&pageSize=1 | head
curl -sS -o /dev/null -w '%{http_code}\n' http://<domain>/   # 301
```

---

## 2. 日常发布与回滚

发布只能手动触发并人工批准；主分支合并只产出不可变镜像 `sha-<7>`，禁止 `latest`。

```bash
# 发布（release 目录由 deploy.yml 准备）
/opt/yunhefc/bin/release.sh --sha <sha7>

# 回滚到 state/previous（不修改 previous）
/opt/yunhefc/bin/release.sh --rollback

# 回滚到指定版本
/opt/yunhefc/bin/release.sh --rollback <sha7>
```

- `flock` 保证同一时间只有一个发布/回滚；SSH 中断后重跑同一 `--sha` 幂等。
- 保留 current + previous + 最新一个其他版本；清理旧目录与对应镜像，绝不删除 current/previous。
- 回滚不修改 `state/previous`；回滚后如需继续回退，请显式传 `--rollback <sha7>`。
- 回滚触发：readiness 超时、迁移失败、冒烟失败、发布后 30 分钟内 5xx 或关键路径异常。
- 回滚优先只回代码；仅当迁移不向后兼容或数据损坏时按 RDS PITR 恢复，不做逆向迁移。

发布前人工检查：

1. ACR 中 `yunhefc-backend:sha-<7>` 与 `yunhefc-nginx:sha-<7>` 两个 manifest 都存在。
2. `flyway migrate` 已成功，`flyway_schema_history` 为最新。
3. RDS 控制台可见：自动备份开启、保留 7 天、binlog 开启、最近备份与 PITR 可恢复时间点覆盖回滚窗口；覆盖不了则推迟发布。

---

## 3. 故障恢复

### 3.1 RDS PITR 恢复

1. RDS 控制台 → 备份恢复 → 按时间点创建新实例（保留范围内）。
2. 新实例与 ECS 同 VPC、放行私网；校验账号与权限（应用 DML、迁移 DDL）。
3. 更新 `/opt/yunhefc/.env` 的 `DB_URL`（新私网地址），`release.sh --sha <当前sha7>` 重建 backend。
4. 观察 readiness 与关键接口；确认数据时间点符合预期。
5. 旧实例保留到确认完成后再释放（保留期内仍可用 PITR 再恢复）。

### 3.2 `.env` 丢失或损坏

1. 从密码管理器取回离线副本，写回 `/opt/yunhefc/.env`，`chmod 600`。
2. 确认 `SESSION_COOKIE_SECURE=true`、`SESSION_COOKIE_DOMAIN=` 为空、CORS 恰为两个 https 域名。
3. 重新创建 backend/nginx 让环境变量生效：

```bash
/opt/yunhefc/bin/release.sh --sha "$(basename "$(readlink -f /opt/yunhefc/current)")"
```

### 3.3 证书续期/重签

1. 替换 `/opt/yunhefc/certs/fullchain.pem`、`privkey.pem`。
2. 校验：`openssl x509 -noout -enddate -in /opt/yunhefc/certs/fullchain.pem`（覆盖 www/admin/api，剩余 >30 天）。
3. 测试并平滑重载：

```bash
compose_current exec nginx nginx -t
compose_current exec nginx nginx -s reload
```

4. `curl -I https://www.<domain>/` 确认新证书；证书/私钥丢失后重新签发，不做磁盘快照。

### 3.4 审计补导（年度导出失败）

当前实现无管理接口；导出对象 `audit/export/{year}/audit-{year}.jsonl.gz` 存在即跳过，失败只写 ERROR 日志。应急步骤：

1. 修好原因后删除该年对象（如 `ossutil rm oss://<bucket>/audit/export/<year>/audit-<year>.jsonl.gz`）。
2. 在 `/opt/yunhefc/.env` 设 `AUDIT_EXPORT_ENABLED=true`，强制重建 backend 让配置生效，并等 readiness 通过：

```bash
compose_current up -d --no-deps --force-recreate backend
compose_current exec backend curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

3. backend 启动后再把 Quartz 触发器下一次触发时间改到当前（不要重启：`overwrite-existing-jobs=true` 会在启动时按 cron 重算并覆盖这条更新）：

```sql
UPDATE QRTZ_TRIGGERS
SET NEXT_FIRE_TIME = UNIX_TIMESTAMP(NOW(3)) * 1000,
    PREV_FIRE_TIME = -1,
    TRIGGER_STATE = 'WAITING'
WHERE TRIGGER_NAME = 'auditExportTrigger' AND TRIGGER_GROUP = 'yunhefc';
```

4. Quartz JDBC 下一轮取任务时会读到新的触发时间；确认 OSS 对象存在且大小非 0 后，把 `AUDIT_EXPORT_ENABLED` 恢复为 `false` 并再次 `compose_current up -d --no-deps --force-recreate backend`。

### 3.5 FAILED Outbox 处理

Outbox 发送失败重试 5 次后置 `status=2`，7 天后被清理；处理窗口有限。

```sql
SELECT id, event_id, event_type, aggregate_id, attempts, last_error, created_at
FROM outbox_event
WHERE status = 2
ORDER BY created_at
LIMIT 50;
```

1. 先查根因：Kafka 是否健康、topic 是否存在、消费者是否异常；`last_error` 只保留最近一次错误。
2. 修复后把事件重新置为待发送（消费者按事件 ID 去重，允许重复投递）：

```sql
UPDATE outbox_event
SET status = 0, attempts = 0, next_retry_at = NULL,
    lease_owner = NULL, lease_until = NULL, last_error = NULL
WHERE id = <id>;
```

3. 等待下一轮 relay（默认 60 秒）并确认 `status=1`；`PENDING` 事件不清理。

### 3.6 数据清理失败

`DataRetentionJob` 每日 03:30（Asia/Shanghai）执行，单任务失败不阻断其他任务。排查：

```bash
compose_current logs --since 48h backend 2>&1 | grep 'retention cleanup'
```

确认失败原因后可按相同窗口手工分批补删（每批 1000，循环到 0）：

```sql
DELETE FROM notification WHERE created_at < UTC_TIMESTAMP(3) - INTERVAL 90 DAY LIMIT 1000;
DELETE FROM outbox_event WHERE status = 1 AND sent_at < UTC_TIMESTAMP(3) - INTERVAL 7 DAY LIMIT 1000;
DELETE FROM outbox_event WHERE status = 2 AND created_at < UTC_TIMESTAMP(3) - INTERVAL 7 DAY LIMIT 1000;
DELETE FROM consumed_event WHERE processed_at < UTC_TIMESTAMP(3) - INTERVAL 7 DAY LIMIT 1000;
DELETE FROM idempotency_key WHERE expires_at < UTC_TIMESTAMP(3) LIMIT 1000;
```

邀请必须先删 `invite_use` 再删 `invite_link`（同一事务，每批 1000）：

```sql
START TRANSACTION;
DELETE iu FROM invite_use iu
JOIN invite_link il ON il.id = iu.invite_id
WHERE (il.status = 2 AND il.expires_at < UTC_TIMESTAMP(3) - INTERVAL 30 DAY)
   OR (il.status = 1 AND il.revoked_at < UTC_TIMESTAMP(3) - INTERVAL 30 DAY)
LIMIT 1000;
DELETE FROM invite_link
WHERE (status = 2 AND expires_at < UTC_TIMESTAMP(3) - INTERVAL 30 DAY)
   OR (status = 1 AND revoked_at < UTC_TIMESTAMP(3) - INTERVAL 30 DAY)
LIMIT 1000;
COMMIT;
```

审计日志永久在线保留，不手工删除；每年导出到 OSS。

---

## 4. 固定排障入口

```bash
# 容器健康
docker ps --filter name=yunhefc-backend

# readiness（DB + Redis）
compose_current exec backend \
  curl -fsS http://127.0.0.1:8080/actuator/health/readiness

# 最近 200 行应用日志（JSON 行格式）
compose_current logs --tail=200 backend

# Kafka 消费组 lag
compose_current exec kafka \
  /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups

# Outbox 积压
mysql -h <rds-host> -u yunhefc_app -p yunhefc \
  -e "SELECT status, COUNT(*) FROM outbox_event GROUP BY status;"
```

---

## 5. 已知未自动化项

- 分支保护、Environment 审批人、仓库变量与 Secrets 需在 GitHub 设置中配置（见 §6.5）；workflow 本身见 `.github/workflows/`。
- 迁移文件不随 release 目录下发，发布前需从目标 commit 复制（见 §1.3）。
- 证书续期、RDS 备份新鲜度检查、审计补导、FAILED Outbox 处置均为人工执行。
- 不做恢复演练、不做依赖漏洞扫描、无 CSP、无全站基础限流（残余风险见 `docs/design/06.设计方案评审结果.md` §50）。

---

## 6. CI/CD 流水线

事实源：`.github/workflows/ci.yml`、`.github/workflows/deploy.yml`、`.scratch/mvp-release/issues/13-grilling-ci-cd.md`。
分支保护、Environment 审批、仓库变量与 Secrets 是 GitHub 侧设置，无法入库，属于上线外部前置（§6.5）。

### 6.1 PR 门禁与 main 流水线

| 事件 | job | 超时 | 内容 |
|---|---|---|---|
| `pull_request` | `prototype` | 5 min | 8 个原型 JS 逐个 `node --check` |
| `pull_request` | `backend` | 20 min | temurin 21 + Maven 缓存，`./mvnw -B -ntp verify`（含 `*IT`，Testcontainers 需要 runner 的 Docker） |
| `pull_request` | `frontend` | 15 min | pnpm 12.16.1 / Node 24：`install --frozen-lockfile` → `typecheck` → `build` → `test` |
| `push` main | `e2e` | 20 min | 依赖 `backend`；MySQL 8.4 + Redis 6.2.24 service + 真实后端 |
| `push` main | `images` | 20 min | 依赖 `backend`/`frontend`/`e2e`；推送 ACR 双镜像 |

- 无 tag 发布、无路径过滤；PR 的三项检查均为必需检查，失败即阻断合并（分支保护见 §6.3）。
- PR 并发组 `ci-<ref>` 取消旧运行；main 不取消，避免镜像/E2E 半途中断。
- `e2e`：下载 main 的 boot jar，以 `dev` profile 启动并轮询 `/actuator/health` ≤180s；注入 `SMS_MOCK_ENABLED=true`、`OSS_MODE=mock`、`WECHAT_MODE=mock`、`KAFKA_LISTENER_ENABLED=false`、`OUTBOX_ENABLED=false`；Flyway 建表后重放 `scripts/sql/test-data.sql`（可重复执行），再跑 Playwright Chromium。失败上传 `frontend/test-results` 与 `frontend/playwright-report`，保留 7 天。
- `images`：`linux/amd64`，tag 固定为该 commit 的 `sha-<7>`（`github.sha` 前 7 位），双仓库 `yunhefc-backend`、`yunhefc-nginx`；两个 manifest 都校验存在；禁止 `latest`。构建期变量：`ADMIN_BASE_URL=/`、`VITE_WEB_URL=https://www.<PUBLIC_DOMAIN>`、`VITE_ADMIN_URL=https://admin.<PUBLIC_DOMAIN>`。

### 6.2 手动发布

1. 发布前按 §1.3 用迁移账号执行 `flyway migrate`，并完成 §2 的发布前检查；workflow 不执行 SQL、不补迁移。
2. 合并到 main 后等待 `ci` 的 `e2e` 与 `images` 成功，记下该 commit 的 7 位 sha。
3. `Actions → deploy → Run workflow`：填 `sha`（7 位小写十六进制）与可选 `note`。
4. workflow 依次：校验 sha 格式与仓库变量 → 校验 commit 属于 `origin/main` → `docker manifest inspect` 校验双镜像 → 固定主机指纹 SSH → 把目标 commit 的 compose 快照与 `release.env`（`IMAGE_TAG=sha-<7>`）写入 `/opt/yunhefc/releases/<sha7>/` → 执行 `release.sh --sha <sha7>`。
5. `production` Environment 的审批在 job 启动前生效；未批准不会触碰 ECS。
6. 结束（无论成败）向 `DEPLOY_WEBHOOK_URL` 发送 result、stage、tag、actor、note 与 run URL；webhook 失败不影响 job 结果。
7. 发布失败不自动回滚；回滚按 §2 手动执行 `release.sh --rollback`（或 `--rollback <sha7>`）。

### 6.3 分支保护与 Environment（GitHub 设置）

- `main`：只允许通过 PR 合并，禁止直推与强推；必需检查 `prototype`、`backend`、`frontend`。`e2e`/`images` 只在 main 运行，不设为 PR 必需检查。
- Environment `production`：required reviewers ≥1，deployment branches 限制为 `main`。手动触发可选任意 ref，workflow 文件本身可能来自该 ref，因此这条限制必须配置，不能只依赖 job 内的 sha 校验。
- 仓库 Actions 保持可运行；workflow 只申请 `permissions: contents: read`。

### 6.4 仓库变量与 Secrets

| 名称 | 类型 | 用途 |
|---|---|---|
| `ACR_REGISTRY` / `ACR_NAMESPACE` / `PUBLIC_DOMAIN` | Variables | 镜像地址与前端构建期域名 |
| `ACR_USERNAME` / `ACR_PASSWORD` | Secrets | CI 推送镜像与发布前 manifest 校验（写权限账号） |
| `ECS_HOST` / `ECS_USER` / `ECS_PORT` | Variables | 部署目标；`ECS_USER` 默认 `deploy`，`ECS_PORT` 默认 22 |
| `ECS_SSH_KEY` / `ECS_KNOWN_HOSTS` | Secrets | SSH 私钥与固定主机指纹（不使用 `ssh-keyscan`） |
| `DEPLOY_WEBHOOK_URL` | Secret | 发布结果通知（钉钉/企业微信群机器人通用格式）；未配置时跳过 |

- 生产业务密钥只放 ECS `/opt/yunhefc/.env`（600），不进 GitHub Secrets；ECS 侧 ACR 只读凭证在 `/opt/yunhefc/env/acr.env`（600）。
- `PUBLIC_DOMAIN` 填裸域名（如 `example.com`），用于生成 `https://www.<domain>` 与 `https://admin.<domain>`；改域名必须重新构建 nginx 镜像。

### 6.5 外部前置（不在仓库内）

- ACR 两个仓库、写权限与只读账号；ECS `deploy` 用户、SSH key 与 known_hosts；webhook 地址。
- 分支保护、Environment 审批人需在 GitHub 仓库设置中配置；仓库变量/Secrets 见 §6.4。
- 缺少 `ACR_*`/`ECS_*` 时 PR 三项检查仍可运行；main 的 `images` 会失败，`deploy` 会在校验阶段直接拒绝。
