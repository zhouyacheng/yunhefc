# 云河FC 部署与运维 Runbook

适用：单 ECS（2C2G）+ Compose（MySQL 8.4 / Redis / backend / Nginx）。MySQL 使用宿主已预置的官方镜像 `mysql:8.4`，仅 Compose 内网可达（P-168）。
事实源：设计文档 03（`docs/design/03.整体架构设计.md`）§6.4、§7；`.scratch/mvp-release/issues/12`、`13`、`16`、`17`。

> 生产密钥只放 ECS `/opt/yunhefc/.env`（600），同一份内容在密码管理器离线保管；不入库、不进 GitHub Secrets。

---

## 0. 目录、权限与前置

```text
/opt/yunhefc/
├── .env                    600，运行时配置（见 deploy/.env.example）
├── env/acr.env             600，ACR_REGISTRY / ACR_NAMESPACE / ACR_USERNAME / ACR_PASSWORD（只读拉取）
├── certs/                  fullchain.pem、privkey.pem（只读挂载到 nginx）
├── bin/release.sh          应急脚本；发布/回滚优先用 releases/<sha7>/bin/release.sh
├── releases/<sha7>/        deploy/ 快照：compose、nginx/、bin/、topology、*.Dockerfile、RUNBOOK.md、.env.example、release.env（不含 .env/.env.*）
├── current -> releases/<sha7>
└── state/previous         上一次可用版本目录；release.lock
```

- ECS：系统盘 ≥40GB、2GB swap、Docker + Compose plugin、`rsync`（`deploy.yml` 用它同步发布快照）；`deploy` 用户在 `docker` 组，非 root。
- 内存上限：backend 832m、Redis 192m、Nginx 48m、MySQL 512m，主机/OS 预留约 0.3GB，另建 2GB swap 兜底。`free -m` 可用内存 <1.9GB 时，把 `docker-compose.prod.yml` 的 backend `-Xmx` 降至 384m；仍 OOM 则升配 4C8G。
- 宿主必须预置 `mysql:8.4` 镜像（生产发布链路不访问 Docker Hub）：`docker image inspect mysql:8.4`。缺失时从可信来源离线导入后再发布。
- 目录初始化与可选应急脚本（首次引导；发布/回滚脚本随快照下发，不再依赖这里更新）：

```bash
sudo install -d -o deploy -g deploy /opt/yunhefc/{bin,releases,state,env,certs}
sudo chown deploy:deploy /opt/yunhefc   # release.sh 需在根目录切换 current 符号链接
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

env_value() { sed -n "s/^$1=//p" /opt/yunhefc/.env | tail -1; }

# 容器内客户端默认字符集由挂载的 mysql/conf.d/utf8mb4.cnf 固定为 utf8mb4；这里仍显式传参，
# 兼容未挂载该配置的容器（locale 为 C/POSIX 时会推断成 latin1，中文落库即不可逆）。
mysql_current() {
  compose_current exec -T -e MYSQL_PWD="$(env_value DB_PASSWORD)" \
    mysql mysql -u "$(env_value DB_USERNAME)" --default-character-set=utf8mb4 yunhefc "$@"
}

mysql_root() {
  compose_current exec -T -e MYSQL_PWD="$(env_value MYSQL_ROOT_PASSWORD)" \
    mysql mysql -uroot --default-character-set=utf8mb4 "$@"
}
```

---

## 1. 首次部署

### 1.1 自建 MySQL（Compose 内）

1. 使用宿主已预置的官方镜像 `mysql:8.4`，Compose 服务 `mysql`；不发布 3306，仅 `app` 网络可达，数据落命名卷 `mysql-data`。
2. 首次启动由容器环境变量初始化：库 `yunhefc`（utf8mb4 / utf8mb4_0900_ai_ci）、账号 `yunhefc`（`DB_USERNAME`/`DB_PASSWORD` 取 `.env`；口令只写 `.env`、不入库）、root 口令取 `.env` 的 `MYSQL_ROOT_PASSWORD`（随机生成）。迁移与应用共用 `yunhefc` 账号（该账号对 `yunhefc.*` 有 DDL 权限）。账号口令只在数据卷首次初始化时生效；之后修改 `.env` 的 `DB_PASSWORD` 需同步执行 `ALTER USER 'yunhefc'@'%' IDENTIFIED BY '<新口令>';`。
3. 时区：容器 `TZ=Asia/Shanghai` 使 `system_time_zone=CST`；`--default-time-zone=+08:00` 使全局/会话 `time_zone=+08:00`。
4. 字符集：服务端 `--character-set-server=utf8mb4`；容器内客户端默认字符集由只读挂载的 `mysql/conf.d/utf8mb4.cnf`（`[client] default-character-set=utf8mb4`）固定，否则 locale 为 C/POSIX 时客户端会推断成 latin1。挂载生效需重建 `mysql` 容器（`compose_current up -d mysql`，会重启 MySQL）。
5. 资源：`mem_limit=512m`、`innodb_buffer_pool_size=192M`、`max_connections=50`，关闭 `performance_schema` 与 X Plugin（`mysqlx`）。
6. 校验（首次发布后）：

```bash
mysql_current -e "SELECT @@system_time_zone, @@global.time_zone, @@session.time_zone;
  SELECT @@character_set_client, @@character_set_connection, @@character_set_results;
  SELECT COUNT(*) FROM flyway_schema_history;"
```

预期 `CST / +08:00 / +08:00`；字符集三项均为 `utf8mb4`（若为 `latin1`，中文会显示 `?`，且手工写操作会以 `?` 落库）；空库时 `flyway_schema_history` 为 0 或表不存在。

### 1.1.1 从 RDS 迁移存量数据（一次性 cutover）

> 生产自 RDS 切到自建 MySQL 时执行一次；后续环境从空库跑 V1–V21 即可。全程保留 RDS 实例与旧 `.env` 至少 7 天用于回退。
>
> **前置条件**：包含 `mysql` 服务的 P-168 版本已发布（`release.sh` 会启动容器并等待 healthy），`yunhefc` 为空库，backend 仍连 RDS；`.env` 已写入 `MYSQL_ROOT_PASSWORD`（600）。未满足时不要开始停 backend。

1. 确认宿主已预置 `mysql:8.4`；在 `.env` 增加 `MYSQL_ROOT_PASSWORD`（随机，600 文件），`DB_URL` 暂时保持原 RDS：

   ```bash
   grep -q '^MYSQL_ROOT_PASSWORD=' /opt/yunhefc/.env || \
     printf 'MYSQL_ROOT_PASSWORD=%s\n' "$(openssl rand -hex 16)" >> /opt/yunhefc/.env
   ```

   发布包含 `mysql` 服务的版本，`release.sh` 会启动容器并等待 healthy（backend 此刻仍连 RDS）。`release.sh` 缺少 `MYSQL_ROOT_PASSWORD` 会直接拒绝发布。
2. 停 backend，冻结 RDS 写入：`compose_current stop backend`。**判定以 `yunhefc` 48 张表的 `CHECKSUM TABLE` 10 秒不变为准**；`SHOW GLOBAL STATUS` 的 DML 计数是整实例的，若 RDS 还有其它 schema 在写会变化，只作告警。
3. 在 ECS 上导出 RDS（迁移账号有 `SELECT`）：

```bash
mysqldump -h <rds-host> -P 3306 -u yunhefc_migrator -p \
  --single-transaction --no-tablespaces --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 yunhefc > /opt/yunhefc/cutover-$(date +%F).sql
```

4. 导入空库（首次初始化后 `yunhefc` 为空；此步 `.env` 仍是 RDS 凭据，直接用容器 root）：

```bash
mysql_root yunhefc < /opt/yunhefc/cutover-<date>.sql
```

5. 创建应用账号。容器首次初始化按**当时** `.env` 的 `DB_USERNAME` 建号（cutover 阶段通常是 RDS 的 `yunhefc_app`，不会自动建 `yunhefc`；若 `.env` 的 `DB_USERNAME` 已经是 `yunhefc`，初始化账号就是目标账号，本步只改口令，第 8 步无需删除）。下面 `<DB_PASSWORD>` 替换为第 7 步写入 `.env` 的口令，不要把口令写进文档或仓库：

```bash
mysql_root -e "
  CREATE USER IF NOT EXISTS 'yunhefc'@'%' IDENTIFIED BY '<DB_PASSWORD>';
  ALTER USER 'yunhefc'@'%' IDENTIFIED BY '<DB_PASSWORD>';
  GRANT ALL PRIVILEGES ON yunhefc.* TO 'yunhefc'@'%';
  FLUSH PRIVILEGES;"
```

6. 校验（表数 = 36 业务 + 11 Quartz + `flyway_schema_history` = 48）：

```bash
mysql_root yunhefc \
  -e "SELECT COUNT(*) AS tables_total FROM information_schema.tables
        WHERE table_schema = 'yunhefc';
      SELECT MAX(version) AS flyway_max FROM flyway_schema_history WHERE success = 1;
      SELECT (SELECT COUNT(*) FROM app_user) AS users,
             (SELECT COUNT(*) FROM team_match) AS matches;"
```

预期 `flyway_max = 21`、行数与 RDS 侧一致；时区按 §1.1 第 5 步校验。

> **本生产 RDS 为 MySQL 8.4.7，server 默认字符集 `utf8mb3`、`lower_case_table_names=1`。** 字符集以业务表 collation 为准（业务表 utf8mb4；Quartz/flyway 历史表允许 utf8mb3）；Quartz 表在 RDS 上存为 `qrtz_*`，导入 `lctn=0` 的容器后必须重命名为 `QRTZ_*` 并校验 5 个 FK，否则 Quartz 启动失败。向导 Stage 8 已包含该归一化；手工执行时不要跳过。若导入后某表 `auto_increment` 低于 RDS（`mysqldump` 常见回落），按 RDS `information_schema.tables.auto_increment` 执行 `ALTER TABLE ... AUTO_INCREMENT=N` 抬回，避免复用已删除/回滚的 ID。

7. 切换 `.env`：先备份原文件，再把三行改为下方值。

```bash
cp /opt/yunhefc/.env /opt/yunhefc/.env.rds-$(date +%F)
```

```dotenv
DB_URL=jdbc:mysql://mysql:3306/yunhefc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8
DB_USERNAME=yunhefc
DB_PASSWORD=<数据库口令>
```

> 口令与第 5 步一致，只写 `.env`（600），不写进文档、仓库或发布包。

8. 重建 backend 并验收：`/opt/yunhefc/current/bin/release.sh --sha <当前sha7>` → readiness 通过 → §1.6 冒烟。验收通过后删除容器初始化残留账号（避免保留 RDS 应用口令）。**初始化账号名取自第 7 步备份的旧 `.env`**；若它已经是 `yunhefc`，不要删除，否则会删掉应用账号：

   ```bash
   init_user=$(sed -n 's/^DB_USERNAME=//p' /opt/yunhefc/.env.rds-<date> | tail -1)
   if [[ -n "$init_user" && "$init_user" != "yunhefc" ]]; then
     mysql_root -e "DROP USER IF EXISTS '$init_user'@'%'; DROP USER IF EXISTS '$init_user'@'localhost';"
   fi
   ```

9. 稳定 7 天后释放 RDS；期间回退 = 恢复 `.env.rds-<date>` 并重建 backend。**切换后新库一旦产生写入，回退 RDS 会丢失这些写入（无双向同步/PITR）；默认向前修复，回退仅用于切换后尚无新写入的窗口。**

### 1.2 DNS / 证书 / 环境文件 / ACR

1. `www.<domain>` 解析到 ECS；如需裸域 HTTP 301，再添加一条 `@` A 记录（裸域不提供 HTTPS）。`admin`/`api` 仅作 80 过渡跳转，可稍后删除。
2. 证书放到 `/opt/yunhefc/certs/fullchain.pem`、`privkey.pem`，**只需覆盖 `www.<domain>`**（SAN 含 `DNS:www.<domain>` 或 `DNS:*.<domain>`），剩余有效期 >30 天。裸域与旧 `admin`/`api` 子域不提供 HTTPS。

> 旧 `admin`/`api` 仅保留 80 端口 301/308 过渡（admin 页面补 `/admin`，API 保持路径），不依赖证书；HTTPS 已停止。HSTS `includeSubDomains` 下，已缓存客户端会直接连接失败，应尽快删除旧 A 记录。

3. `cp deploy/.env.example /opt/yunhefc/.env && chmod 600 /opt/yunhefc/.env`，填真实值；`SESSION_COOKIE_SECURE=true`、`SESSION_COOKIE_DOMAIN=` 留空。
4. ACR 只读凭证 `/opt/yunhefc/env/acr.env`（600）：

```dotenv
ACR_REGISTRY=registry.cn-hangzhou.aliyuncs.com
ACR_NAMESPACE=<namespace>
ACR_USERNAME=<read-only-user>
ACR_PASSWORD=<read-only-password>
```

> 上述 1–4 可运行 `scripts/rc-bootstrap-wizard.sh`（仓库根执行）交互完成：检查 DNS、上传证书、写 ACR 凭证、确认 `mysql:8.4` 镜像、生成生产 `.env`（600）。密钥只经 stdin 写入 ECS，不落本地。

### 1.3 发布前迁移（应用启动前，人工执行）

迁移文件只在仓库里（不随 release 目录下发）。在目标 commit 的代码检出中：

```bash
sha7=<7位commit>
mkdir -p /opt/yunhefc/migrations
cp backend/src/main/resources/db/migration/*.sql /opt/yunhefc/migrations/
```

#### 迁移前预检（P-68：V9 星级精度）

V9 会把 `player.skill` 由 `TINYINT UNSIGNED` 改为 `DECIMAL(3,2)`、`match_grouping_player.manual_score` 由 `DECIMAL(3,1)` 改为 `DECIMAL(3,2)`。`TINYINT` 可存 0–255，超出 `DECIMAL(3,2)` 上限 9.99 的值会让 `ALTER` 直接失败；先用 `mysql_current` 检查：

```sql
SELECT COUNT(*) AS skill_out_of_range FROM player WHERE skill < 1 OR skill > 5;
SELECT COUNT(*) AS skill_over_decimal FROM player WHERE skill > 9.99;
SELECT COUNT(*) AS manual_score_out_of_range FROM match_grouping_player
  WHERE manual_score IS NOT NULL AND (manual_score < 1 OR manual_score > 5);
```

- 三个计数必须为 0 才能执行 `migrate`；非 0 时先备份并按业务确认修数，禁止在迁移脚本里静默改成 `3.00`。

> 首次部署时 MySQL 容器还没启动：先按 §1.4 第 1 步准备 release 目录，再只启动 MySQL（迁移完成前不要启动 backend）：
>
> ```bash
> cd /opt/yunhefc/releases/<sha7>
> IMAGE_REGISTRY=<registry>/<namespace> IMAGE_TAG=sha-<sha7> \
>   docker compose --project-name yunhefc --env-file /opt/yunhefc/.env \
>     -f docker-compose.yml -f docker-compose.prod.yml up -d mysql
> ```
>
> 已有环境直接使用 `mysql` 服务，跳过此步。

```bash
# 在 Compose 网络内连 mysql 服务；flyway 镜像需宿主预置（不从 Docker Hub 拉取）。
# 网络名默认 yunhefc_app（COMPOSE_PROJECT=yunhefc）。
docker run --rm --network yunhefc_app \
  -v /opt/yunhefc/migrations:/flyway/sql:ro \
  flyway/flyway:11.7.2 \
  -url="jdbc:mysql://mysql:3306/yunhefc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8" \
  -user=yunhefc -password='<DB_PASSWORD>' -connectRetries=10 migrate
```

- 迁移失败：不发版，修复后重跑。
- DDL 已生效但历史记为 failed（例如执行完 DDL 后连接中断）：先备份，并逐项核对列/索引与迁移文件一致。对象未创建时用 `flyway repair` 删除失败记录后重跑；对象已全部创建且失败行 `checksum` 非空时，可执行 `UPDATE flyway_schema_history SET success=1, execution_time=0 WHERE version='<目标版本>' AND success=0`。改动历史表前必须显式确认。
- 迁移成功后确认 `flyway_schema_history` 与目标版本一致。
- 应用以 `spring.flyway.enabled=false` 启动；发布前若迁移未执行，应用不会补跑。

#### 迁移后校验（P-66：V8 操作时间；P-68：V9 星级精度；P-72：V10 分组分数；V11/V12：手机号约束；V14：排行榜统计列；V16/V17：活动与账本表与权限；V18/V19：活动图片与人数上限；V20/V21：内容四表、索引与权限）

`flyway migrate` 成功后执行，确认最新版本与目标一致（当前 V21；V15 为空号）：

```bash
docker run --rm --network yunhefc_app \
  -v /opt/yunhefc/migrations:/flyway/sql:ro \
  flyway/flyway:11.7.2 \
  -url="jdbc:mysql://mysql:3306/yunhefc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8" \
  -user=yunhefc -password='<DB_PASSWORD>' -connectRetries=10 info

mysql_current -e "
  SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
  SHOW COLUMNS FROM audit_log LIKE 'operated_at';
  SHOW INDEX FROM audit_log WHERE Key_name = 'idx_audit_team_operated';
  SELECT COUNT(*) AS null_operated_at FROM audit_log WHERE operated_at IS NULL;
  SHOW COLUMNS FROM player LIKE 'skill';
  SHOW COLUMNS FROM match_grouping_player LIKE 'manual_score';
  SELECT COUNT(*) AS invalid_skill FROM player WHERE skill < 1 OR skill > 5;
  SELECT COUNT(*) AS invalid_manual_score FROM match_grouping_player
    WHERE manual_score IS NOT NULL AND (manual_score < 1 OR manual_score > 5);
  SELECT COUNT(*) AS grouping_settings FROM system_setting
    WHERE setting_key LIKE 'grouping.%' AND is_deleted = 0;
  SHOW COLUMNS FROM app_user LIKE 'phone';
  SHOW COLUMNS FROM app_user LIKE 'mp_openid';
  SHOW INDEX FROM app_user WHERE Key_name = 'uk_user_mp_openid';
  SHOW COLUMNS FROM player_stats_summary LIKE 'internal_appearances';
  SHOW COLUMNS FROM player_stats_summary LIKE 'internal_wins';
  SHOW COLUMNS FROM player_stats_summary LIKE 'own_goals';
  SELECT COUNT(*) AS activity_tables FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('team_activity', 'activity_signup', 'ledger_category', 'team_ledger_entry');
  SELECT COUNT(*) AS permissions FROM permission;
  SELECT code FROM permission
    WHERE code IN ('activity:signup:own', 'activity:view:any', 'activity:manage:any',
                   'ledger:view:any', 'ledger:manage:any') ORDER BY code;
  SELECT COUNT(*) AS admin_new_grants FROM role_permission rp
    JOIN permission p ON p.id = rp.permission_id
    JOIN app_role r ON r.id = rp.role_id
    WHERE r.code = 'ADMIN' AND p.code IN ('activity:signup:own', 'activity:view:any',
      'activity:manage:any', 'ledger:view:any', 'ledger:manage:any');
  SELECT COUNT(*) AS player_new_grants FROM role_permission rp
    JOIN permission p ON p.id = rp.permission_id
    JOIN app_role r ON r.id = rp.role_id
    WHERE r.code = 'PLAYER' AND p.code IN ('activity:signup:own', 'activity:view:any',
      'ledger:view:any');
  SHOW COLUMNS FROM notification LIKE 'type';
  SELECT COUNT(*) AS ledger_categories FROM ledger_category WHERE is_deleted = 0;
  SHOW COLUMNS FROM team_activity LIKE 'family_allowed';
  SHOW COLUMNS FROM team_activity LIKE 'capacity';
  SELECT COUNT(*) AS null_activity_capacity FROM team_activity WHERE capacity IS NULL;
  SELECT COUNT(*) AS activity_image_table FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'activity_image';
  SHOW INDEX FROM activity_image WHERE Key_name = 'idx_activity_image_activity';
  SELECT COUNT(*) AS content_tables FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('content_album', 'content_album_photo', 'content_post', 'content_post_like');
  SELECT COUNT(DISTINCT index_name) AS content_indexes FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name LIKE 'content\\_%' AND index_name <> 'PRIMARY';
  SELECT code FROM permission WHERE code LIKE 'content:%' ORDER BY code;
  SELECT COUNT(*) AS admin_content_grants FROM role_permission rp
    JOIN permission p ON p.id = rp.permission_id
    JOIN app_role r ON r.id = rp.role_id
    WHERE r.code = 'ADMIN' AND p.code LIKE 'content:%';
  SELECT COUNT(*) AS player_content_grants FROM role_permission rp
    JOIN permission p ON p.id = rp.permission_id
    JOIN app_role r ON r.id = rp.role_id
    WHERE r.code = 'PLAYER' AND p.code IN ('content:view:any', 'content:create:own');"
```

- 期望：最新版本 21 且 `success=1`；`operated_at` 为 `datetime(3) NULL`；索引存在；历史行已回填（发布滚动窗口内由旧消息产生的行可能为 NULL）；`player.skill` 为 `decimal(3,2) NOT NULL DEFAULT 3.00`，`manual_score` 为 `decimal(3,2) NULL`；两个非法值计数均为 0；`grouping.*` 配置为 0 行；`app_user.phone` 为 `varchar(20) NOT NULL`；`app_user.mp_openid` 为 `varchar(64) NULL` 且位于 `openid` 之后，存在唯一索引 `uk_user_mp_openid`；`player_stats_summary` 的 `internal_appearances`、`internal_wins`、`own_goals` 均为 `int unsigned NOT NULL DEFAULT 0`；`activity_tables` 为 4；`permissions` 为 37 且包含 5 个活动/账本权限码与 4 个 `content:*` 权限码；`admin_new_grants` 为 5、`player_new_grants` 为 3；`notification.type` 注释包含 12-活动创建/13-活动取消/14-内容举报；`ledger_categories` 为 7；`team_activity.family_allowed` 为 `tinyint unsigned NOT NULL DEFAULT 0`；`team_activity.capacity` 为 `smallint unsigned NOT NULL DEFAULT 50` 且 NULL 计数为 0；`activity_image` 表存在且 `idx_activity_image_activity` 索引存在；`content_tables` 为 4、`content_indexes` 为 7、`admin_content_grants` 为 4、`player_content_grants` 为 2。
- 顺序：先 `migrate` 再部署新镜像。V8 只新增可空列与索引，V9 为无损值域扩展（TINYINT→DECIMAL、DECIMAL(3,1)→DECIMAL(3,2)），旧镜像可继续运行；但旧镜像以整数读取会截断小数，V9 执行后至新镜像完全切换前，避免在 B07 修改球员星级。V10 伪删除分组权重配置并更新列注释；旧镜像仍可运行，但分组分数以新镜像的球员星级为准。注意：旧镜像保存系统设置时会 upsert 全部 9 个键，可能把已伪删除的 `grouping.*` 重新写回 `is_deleted=0`，因此上述「`grouping.*` 为 0 行」校验必须在新镜像完全切换后执行；若被复活，执行 `UPDATE system_setting SET is_deleted = 1 WHERE setting_key LIKE 'grouping.%';` 后重新校验。回滚只回代码，不做逆向迁移。V14 只为 `player_stats_summary` 新增三列（`NOT NULL DEFAULT 0`），不触碰存量数据，旧镜像可继续运行。
- V13 只新增 `app_user.mp_openid` 与唯一索引，不改动旧 `openid` 与存量数据；旧镜像可继续运行。旧 `openid` 列仍保留（只读），删除需另立版本并显式批准。
- V14 为排行榜统计新增 `player_stats_summary.internal_appearances`（胜率分母）、`internal_wins`、`own_goals`，均为 `int unsigned NOT NULL DEFAULT 0`；不重算历史数据，统计由新镜像按赛季写入。
- V16 新增 `team_activity`、`activity_signup`、`ledger_category`、`team_ledger_entry` 四张新表，并把 `notification.type` 列注释扩展到 12-活动创建/13-活动取消（不改存量表结构与数据），旧镜像可继续运行，不做数据回填；V17 新增 5 个权限码（`activity:signup:own`、`activity:view:any`、`activity:manage:any`、`ledger:view:any`、`ledger:manage:any`）与角色授权，并写入 7 个内置账本分类。迁移后校验：四张新表存在、`permission` 表 37 行（V21 后）、`ledger_category` 7 行、`role_permission` 包含新权限码。V18 把活动图片改为 `activity_image` 子表并新增 `team_activity.family_allowed`，先把存量 `location_image_key` 回填为 `sort_no=0` 后删列；V19 把 `team_activity.capacity` 的存量 NULL 回填 50 并改为 `NOT NULL DEFAULT 50`。V18 删除旧列，含活动模块的旧镜像不能再运行，必须迁移后立即切新镜像；迁移后校验见上一条命令块，`flyway_schema_history` 最新为 V21。V20 新增 `content_album`、`content_album_photo`、`content_post`、`content_post_like` 四张新表与 7 条索引，并把 `notification.type` 注释扩展到 14-内容举报（不改存量表结构与数据）；V21 新增 4 个 `content:*` 权限码（ADMIN 全授、PLAYER 授 `view`/`create`、GUEST 不授）。两版都是纯增量，滚动窗口内旧镜像可继续运行。
- V11 曾把 `app_user.phone` 改为可空；V12 会**删除**所有 `phone IS NULL` 的账号，并级联清理其球员档案、报名、考勤、事件（含事件历史）、阵容槽位、分组成员、统计、通知、角色、邀请使用与幂等键，然后恢复 `NOT NULL`。仍由其他球员参与的事件只置空被删的助攻。执行 V12 前必须先备份，并运行 `SELECT COUNT(*) FROM app_user WHERE phone IS NULL;` 核对范围；若存在真实账号，先人工补绑或另案处理，不要直接迁移。

#### 启动自检兜底（P-83）

新镜像在 prod 启动时比对 `flyway_schema_history` 最高成功版本与代码期望版本：库落后时 fail-fast，readiness 不会通过，`release.sh` 以「readiness 未通过」失败。此时先补跑 `flyway migrate` 再重新部署，不手工修改历史表；库比代码新（回滚旧镜像）放行。

#### 一次性迁移：DATETIME 转 Asia/Shanghai（P-63）

> **2026-11-18 起生产已运行含 +08 统一的镜像**（首个部署 `c5fcb4a`，2026-11-21 为 `0fe8195`）。
> 本脚本只适用于“尚未产生 +08 写入”的库。**若库中已有 2026-11-18 之后的行，禁止整表平移**，
> 否则这些行会被再推快 8 小时。跨口径库只能按切换时间点定向修复，或接受旧数据偏差。

**判定（只读，先做）**：

```sql
SELECT MAX(created_at) AS max_created, NOW() AS db_now,
       TIMESTAMPDIFF(MINUTE, MAX(created_at), NOW()) AS lag_minutes
FROM audit_log;

SELECT DATE(created_at) AS d, COUNT(*) AS c
FROM audit_log
WHERE created_at BETWEEN '2026-11-16' AND '2026-11-21'
GROUP BY DATE(created_at) ORDER BY d;
```

- `lag_minutes` 接近 0、按天计数连续 → 已在 +08 口径，**跳过**。
- 11-18 前后出现 8 小时断崖 → 跨口径库，**禁止整表平移**，另按切换点处理。

仅在“库中全部业务行都早于首个 +08 镜像且从未平移过”时执行：

1. 停止 backend，并手工 `mysqldump` 留底（生产无自动备份，P-168）。
2. 执行一次性脚本（**只执行一次**，重复执行会再次 +8h）：

```bash
mysql_current < scripts/sql/migrate-datetime-utc-to-shanghai.sql
```

3. 抽样确认 `team_match.start_at`、`audit_log.created_at` 与 `audit_log.operated_at` 等恰好 +8h，且 `updated_at` 未被 `NOW()` 覆盖。脚本自带防重复执行检查：若检测到未来时间戳会直接中止。
4. 确认 `/opt/yunhefc/.env` 的 `DB_URL` 已改为 `serverTimezone=Asia/Shanghai`（应用连接池会覆盖该参数，但保持一致便于排查），再启动新镜像。
5. 历史 `fix-audit-log-utc.sql` 已作废，禁止再执行。

### 1.4 首次发布（break-glass）

> 常规发布入口是 GitHub Actions `deploy`（§6.2）。本节的手工路径只在 Actions 不可用或首次引导时使用；每次 break-glass 发布必须在 `.scratch/mvp-release/release-runs/<date>.md` 记录原因、执行人、sha 与结果。

1. 由 CI/Rsync 准备 release 目录（正常由 `deploy.yml` 完成）：

```bash
sha7=<7位commit>
install -d /opt/yunhefc/releases/$sha7
rsync -a --include '.env.example' --exclude '.env' --exclude '.env.*' \
  --exclude 'rc-acceptance-results.md' deploy/ /opt/yunhefc/releases/$sha7/
printf 'IMAGE_TAG=sha-%s\n' "$sha7" > /opt/yunhefc/releases/$sha7/release.env
```

2. 执行发布：

```bash
/opt/yunhefc/releases/$sha7/bin/release.sh --sha "$sha7"
```

脚本顺序：校验双应用镜像、`mysql:8.4` 本地镜像与 ACR Redis 镜像 → `docker compose pull backend nginx` → 写 `state/previous` → 切 `current` → 基础设施未运行时启动 `mysql`/`redis` 并等 MySQL healthy（已运行则不动）→ `up -d --no-deps backend nginx` → readiness ≤180s → 冒烟（C 端首页、`/admin/`、`/actuator/health`、公开读接口、敏感路径 404）。
`compose up` 退出 0 不代表健康；以 readiness 与冒烟为准。任一步失败脚本退出非零，`current` 保留在失败版本。
基础设施镜像：Redis 来自 ACR（CI `mirror-infra` 产出），MySQL 来自宿主预置的 `mysql:8.4`；发布链路不访问 Docker Hub。

### 1.5 首位管理员

1. 生成 BCrypt 哈希（存裸哈希，60 字符，`$2a$` 开头）：

```bash
htpasswd -bnBC 10 "" '<strong-password>' | tr -d ':\n' | sed 's/^\$2y\$/$2a$/'
```

2. 编辑 `scripts/sql/bootstrap-admin.sql`：设置 `@phone`、`@name`、`@position`，用上一步结果替换 `@password_hash`。
3. 执行（按 `phone` 与角色 `code` 查 ID；重复执行只恢复 `status`/`is_deleted`，不重置密码）：

```bash
mysql_current < scripts/sql/bootstrap-admin.sql
```

4. 校验：
   - 末尾 `admin_permissions` = **37**；
   - `https://www.<domain>/admin/` 用该手机号/密码登录成功；
   - C 端 `/c/me` 返回的 `player` 非空；
   - 登录后立即走 C 端「忘记密码」短信重置（AUTH-06）把初始口令换成个人口令，确认新口令可登录，并从运维记录中删除初始口令。
   - 当前没有「已登录状态改密」接口，只能用短信重置；改密需要真实短信通道可用。
5. 生产空库**不得**执行 `test-data.sql`。

### 1.6 首次验收

```bash
curl -fsS https://www.<domain>/actuator/health          # {"status":"UP"}
curl -fsSI https://www.<domain>/                        # 200；HSTS/nosniff/Referrer-Policy/X-Frame-Options
curl -fsSI https://www.<domain>/admin/                  # 200；B 端静态入口
curl -fsS https://www.<domain>/api/v1/c/matches?page=1&pageSize=1 | head
curl -sS -o /dev/null -w '%{http_code}\n' https://www.<domain>/actuator/env   # 404
curl -sS -o /dev/null -w '%{http_code}\n' http://<domain>/   # 301
```

RC 验收可运行 `scripts/rc-acceptance-wizard.sh`（仓库根执行），逐项覆盖冒烟、TLS、响应头、CORS、暴露面、数据库时区与端口暴露、限流与数据库备份口径；结果写入 `deploy/rc-acceptance-results.md`。向导默认只读；唯一会写生产的是 §6.9 锁定验证（会锁定测试号约 15 分钟，需显式确认）。

---

## 2. 日常发布与回滚

发布只能手动触发并人工批准；主分支合并只产出不可变镜像 `sha-<7>`，禁止 `latest`。

**唯一常规入口**：GitHub Actions `deploy`（§6.2）。下面的 SSH 命令仅用于 break-glass（workflow 不可用或紧急回滚），每次使用需在 `.scratch/mvp-release/release-runs/<date>.md` 记录原因、执行人、sha 与结果。

```bash
# 发布（release 目录由 deploy.yml 准备）
/opt/yunhefc/releases/<sha7>/bin/release.sh --sha <sha7>

# 回滚到 state/previous（使用 current 快照里的脚本）
/opt/yunhefc/current/bin/release.sh --rollback

# 回滚到指定版本
/opt/yunhefc/current/bin/release.sh --rollback <sha7>
```

- `flock` 保证同一时间只有一个发布/回滚；SSH 中断后重跑同一 `--sha` 幂等。
- 发布/回滚脚本随 release 快照下发：`deploy.yml` 直接执行目标快照的 `bin/release.sh`，回滚使用 `current/bin/release.sh`；`/opt/yunhefc/bin/release.sh` 固定安装仅作首次引导/应急，不随发布更新。
- **跨拓扑回滚保护**：当前版本成功部署后（存在 `.deployed`）若目标 release 的 `topology` 与当前不同（例如回到三子域版本），脚本会拒绝。先备份/恢复旧 `.env`、旧脚本与 `admin`/`api` DNS，确认后显式加 `--force`。
- 同域切换前备份 `/opt/yunhefc/.env` 并把 `CORS_ALLOWED_ORIGINS` 改为 `https://www.<domain>` 单源。`admin`/`api` 仅保留 80 过渡，只依赖旧 DNS、不依赖证书；确认不再需要后再删除 A 记录。
- 保留 current + previous + 最新一个其他版本；清理旧目录与对应镜像，绝不删除 current/previous。
- 回滚不修改 `state/previous`；回滚后如需继续回退，请显式传 `--rollback <sha7>`。
- 回滚触发：readiness 超时、迁移失败、冒烟失败、发布后 30 分钟内 5xx 或关键路径异常。
- 回滚优先只回代码；生产数据库无自动备份（P-168，风险接受），迁移不向后兼容或数据损坏时只能人工修复或从手工 dump 恢复，不做逆向迁移。
- 数据库切到自建 MySQL 后，回滚到 P-168 之前的 release 快照前必须先恢复 RDS `.env` 并确认 RDS 可用（旧快照没有 `mysql` 服务，`DB_URL=mysql` 会连不上）。快照拓扑标记已改为 `www-single+mysql`，脚本会拒绝跨拓扑回滚，恢复后需显式加 `--force`。

发布前人工检查：

1. ACR 中 `yunhefc-backend:sha-<7>`、`yunhefc-nginx:sha-<7>`、`yunhefc-infra-redis:6.2.24` 三个 manifest 都存在（`release.sh` 会自动校验；手工检查仅用于提前确认）。
2. `flyway migrate` 已成功，`flyway_schema_history` 为最新。
3. 数据库无自动备份（P-168，风险接受），不阻塞发布；如需留底，发布前自行执行 `mysqldump`。

---

## 3. 故障恢复

### 3.1 数据库不可用 / 数据卷损坏

生产数据库无自动备份、无 PITR（P-168 风险接受），恢复只能靠手工 dump 或人工修复。

1. 确认范围：`compose_current logs --tail=200 mysql`、`docker volume inspect yunhefc_mysql-data`；区分容器启动失败与数据卷损坏。
2. 容器启动失败（如参数或初始化错误）：修复 `.env`/Compose 后 `compose_current up -d mysql`，等待 healthy；`mysql-data` 卷不清空。
3. 数据卷损坏或误删：只能从最近的手工 `mysqldump` 恢复到新卷；无 dump 即数据丢失，需按业务决定是否从 RDS 历史导出（若 RDS 尚未释放）或重跑 V1–V21 空库。
4. 恢复后校验 §1.1.1 第 5 步的表数、`flyway_schema_history` 与行数，再重建 backend 并跑 readiness/冒烟。
5. 卷路径与保留：`docker volume inspect yunhefc_mysql-data`；**不要**在未确认前执行 `docker volume rm`。

### 3.2 `.env` 丢失或损坏

1. 从密码管理器取回离线副本，写回 `/opt/yunhefc/.env`，`chmod 600`。
2. 确认 `SESSION_COOKIE_SECURE=true`、`SESSION_COOKIE_DOMAIN=` 为空、`CORS_ALLOWED_ORIGINS` 恰为 `https://www.<domain>` 单源、`PUBLIC_BASE_URL` 与该源完全一致。
3. 重新创建 backend/nginx 让环境变量生效：

```bash
/opt/yunhefc/bin/release.sh --sha "$(basename "$(readlink -f /opt/yunhefc/current)")"
```

### 3.3 证书续期/重签

1. 替换 `/opt/yunhefc/certs/fullchain.pem`、`privkey.pem`。
2. 校验：`openssl x509 -noout -enddate -in /opt/yunhefc/certs/fullchain.pem`（只需覆盖 www；剩余 >30 天）。SAN 必须含 `DNS:www.<domain>` 或 `DNS:*.<domain>`：
   ```bash
   openssl x509 -in /opt/yunhefc/certs/fullchain.pem -noout -ext subjectAltName
   ```
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

`UNIX_TIMESTAMP(NOW(3))` 是 epoch 语义，与会话时区无关；本段不需要 `SET time_zone`。

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

1. 先查根因：应用日志中的 `Outbox event failed` 是否由事件处理器异常、数据库连接失败或去重键冲突导致；`last_error` 只保留最近一次错误。
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

确认失败原因后可按相同窗口手工分批补删（每批 1000，循环到 0）。手工 SQL 必须先固定会话时区，不要依赖容器默认：

```sql
SET time_zone = '+08:00';
DELETE FROM notification WHERE created_at < NOW(3) - INTERVAL 90 DAY LIMIT 1000;
DELETE FROM outbox_event WHERE status = 1 AND sent_at < NOW(3) - INTERVAL 7 DAY LIMIT 1000;
DELETE FROM outbox_event WHERE status = 2 AND created_at < NOW(3) - INTERVAL 7 DAY LIMIT 1000;
DELETE FROM consumed_event WHERE processed_at < NOW(3) - INTERVAL 7 DAY LIMIT 1000;
DELETE FROM idempotency_key WHERE expires_at < NOW(3) LIMIT 1000;
```

邀请必须先删 `invite_use` 再删 `invite_link`（同一事务，每批 1000）：

```sql
SET time_zone = '+08:00';
START TRANSACTION;
DELETE iu FROM invite_use iu
JOIN invite_link il ON il.id = iu.invite_id
WHERE (il.status = 2 AND il.expires_at < NOW(3) - INTERVAL 30 DAY)
   OR (il.status = 1 AND il.revoked_at < NOW(3) - INTERVAL 30 DAY)
LIMIT 1000;
DELETE FROM invite_link
WHERE (status = 2 AND expires_at < NOW(3) - INTERVAL 30 DAY)
   OR (status = 1 AND revoked_at < NOW(3) - INTERVAL 30 DAY)
LIMIT 1000;
COMMIT;
```

审计日志永久在线保留，不手工删除；每年导出到 OSS。

### 3.7 微信小程序登录失败

先确认 `.env`：

- 生产 `WECHAT_MINI_ENABLED=true` 时必须 `WECHAT_MINI_MODE=real`，且 AppID/AppSecret 齐全；否则应用启动失败。
- `WECHAT_MINI_APP_ID` 必须是 `wx` 开头的 18 位小程序 AppID（`^wx[0-9a-zA-Z]{16}$`）；32 位十六进制通常是 AppSecret，prod 启动时会 fail-fast。
- 小程序后台「开发管理 → 服务器域名 → request 合法域名」必须包含 `https://www.<domain>`；校验仅支持 443/Https，且域名需已备案。
- 个人主体小程序可用 `wx.login`，但**不能**使用 `getPhoneNumber`；手机号由用户手输 + 短信验证码绑定。

| 症状 | 排查 |
|---|---|
| 启动失败提示 `WECHAT_MINI_MODE` | 生产 mode 填成了 `mock`；改为 `real` |
| `40029` / `40163`（映射为 `40001「微信登录已失效」`） | `wx.login` code 已用过或过期；重新登录；日志只记 errcode/errmsg |
| `40013`（`invalid appid`）或 `40125`（`invalid appsecret`） | AppID/AppSecret 填错或不是同一个小程序；按小程序后台「开发管理 → 开发设置」修正后重建 backend |
| `45011`（频率限制） | 小程序侧调用过频；降低重试频率 |
| 请求被拒绝、提示不在合法域名 | 小程序后台未登记 `www.<domain>`，或使用了裸域/HTTP |
| 登录后立即 401 | 小程序端未维护 Cookie jar；`wx.request` 不自动回放 `Set-Cookie`，需手动回传 `Cookie` 头（见 `.scratch/miniprogram-login/spike.md`） |
| 绑定手机号返回 `40101` | `bindTicket` 过期（10 分钟）、已使用或绑手机失败已消耗；重新 `wx.login` 获取新票据 |

小程序发布流程：微信开发者工具上传 → 小程序后台提交审核 → 审核通过后发布。后端镜像不包含小程序代码。

**小程序隐私合规（提审前必查）**：

- 填写入口：现网版本用「账号设置 → 服务内容声明 → 用户隐私保护指引 → 去完善」（入口1）；提审版本用「管理 → 版本管理 → 提交代码审核 → 信息填写页面」（入口2）。入口2 只影响提审版本，审核通过并发布后覆盖现网；提审被拦截时在入口2更新。
- 必须声明「手机号」（账号注册/登录/绑定与短信验证）、「选中的照片或视频信息」（更换头像、上传动态/相册照片）、「读取你的剪切板」（复制管理后台/比赛邀请链接）；「摄像头」不是 `chooseMedia` 的平台必填项，建议补充勾选以与小程序内隐私政策一致。未声明的类型会被平台直接禁用对应接口。
- 小程序内《用户服务协议》（`pages/terms`）与《隐私政策》（`pages/privacy`）的运营者名称（上海云河FC俱乐部）与联系邮箱（841603636@qq.com）必须与小程序备案主体一致；变更时同步修改两页。
- 未同意当前协议版本的用户冷启动不会静默微信登录；协议版本升级后已恢复的会话也会被清除并回到登录，需在登录页主动勾选同意后登录。
- 登录与绑定手机号前必须勾选协议，未勾选时登录、发送短信与绑定提交均被拦截；审核驳回涉及「用户隐私和数据安全」时，先核对以上三处与后台隐私保护指引是否一致。

> 公众号网页授权已随 P-74 删除，`WECHAT_*` 旧变量不再读取；`.env` 里的旧值可以安全移除。`MP_verify_*.txt` 仅保留用于域名验证与历史申诉材料。

### 3.8 小程序上线向导

首次上线或更换小程序主体时，在仓库根运行：

```bash
scripts/miniprogram-launch-wizard.sh
```

逐段引导：request 合法域名、服务类目与隐私指引、采集 AppID/AppSecret、生成 ECS `.env` 片段、V21 迁移（含 V16–V18 活动与账本表、V20/V21 内容表与权限）、发布镜像、生产 HTTPS 真机复验、上传与提审。AppSecret 走隐藏输入，只写入本地 `deploy/.env.mini` 与 ECS `/opt/yunhefc/.env`；迁移与发布前各有一道确认门，顺序反了不会继续。

---

## 4. 固定排障入口

```bash
# 容器健康
docker ps --filter name=yunhefc-backend

# readiness（DB + Redis + schema）
compose_current exec backend \
  curl -fsS http://127.0.0.1:8080/actuator/health/readiness

# 最近 200 行应用日志（JSON 行格式）
compose_current logs --tail=200 backend

# Outbox 分发失败日志
compose_current logs --tail=200 backend | grep 'Outbox event failed'

# Outbox 积压
mysql_current -e "SELECT status, COUNT(*) FROM outbox_event GROUP BY status;"
```

---

## 5. 已知未自动化项

- 分支保护、Environment 审批人、仓库变量与 Secrets 需在 GitHub 设置中配置（见 §6.5）；workflow 本身见 `.github/workflows/`。
- 迁移文件不随 release 目录下发，发布前需从目标 commit 复制（见 §1.3）。
- 证书续期、数据库手工备份（生产无自动备份）、审计补导、FAILED Outbox 处置均为人工执行。
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
| `push` main | `backend` | 10 min | `./mvnw -B -ntp package -DskipTests` 产出 boot jar；测试已在 PR 阶段通过 |
| `push` main | `e2e` | 20 min | 依赖 `backend`；MySQL 8.4 + Redis 6.2.24 service + 真实后端 |
| `push` main | `images` | 40 min | 依赖 `backend`/`frontend`/`e2e`；推送 ACR 双镜像 |
| `push` main | `mirror-infra` | 10 min | 条件同步 `redis:6.2.24` 到 ACR（已存在则跳过） |

- 无 tag 发布、无路径过滤；PR 的三项检查均为必需检查，失败即阻断合并（分支保护见 §6.3）。
- PR 并发组 `ci-<ref>` 取消旧运行；main 不取消，避免镜像/E2E 半途中断。
- `workflow_dispatch`：手动跑 `backend`（package + 上传 jar）与 `e2e`，不触发 `images`/`mirror-infra`，用于验证 E2E 链路改动；可对任意 ref 触发。
- `e2e`：下载 main 的 boot jar，以 `dev` profile 启动并轮询 `/actuator/health` ≤180s；注入 `SMS_MOCK_ENABLED=true`、`OSS_MODE=mock`、`WECHAT_MINI_MODE=mock`、`OUTBOX_ENABLED=false`；Flyway 建表后重放 `scripts/sql/test-data.sql`（可重复执行），再跑 Playwright Chromium。失败上传 `frontend/test-results` 与 `frontend/playwright-report`，保留 7 天。
- `e2e` 运行在自建公开镜像 `danche1013/ubuntu:<tag>` 内（见 §6.6）；services 按服务名 `mysql`/`redis` 直连，不再映射端口；seed 用镜像内 `mysql` 客户端（容器 job 无 docker socket）。
- `images`：`linux/amd64`，tag 固定为该 commit 的 `sha-<7>`（`github.sha` 前 7 位），双仓库 `yunhefc-backend`、`yunhefc-nginx`；两个 manifest 都校验存在；禁止 `latest`。构建期变量：`ADMIN_BASE_URL=/admin/`、`VITE_WEB_URL=https://www.<PUBLIC_DOMAIN>`、`VITE_ADMIN_URL=/admin/`。

### 6.2 手动发布

1. 发布前按 §1.3 用 `yunhefc` 账号执行 `flyway migrate`，并完成 §2 的发布前检查；workflow 不执行 SQL、不补迁移。库落后时后端启动自检会让 readiness 失败，部署失败后先补迁移再重试。
2. 合并到 main 后等待 `ci` 的 `e2e` 与 `images` 成功，记下该 commit 的 7 位 sha。
3. `Actions → deploy → Run workflow`：填 `sha`（7 位小写十六进制）与可选 `note`。
4. workflow 依次：校验 sha 格式与仓库变量 → 校验 commit 属于 `origin/main` → `docker manifest inspect` 校验双镜像 → 固定主机指纹 SSH → 把目标 commit 的 `deploy/` 快照（compose、nginx、bin、Dockerfile、RUNBOOK；不含 `.env`/`.env.*`）与 `release.env`（`IMAGE_TAG=sha-<7>`）写入 `/opt/yunhefc/releases/<sha7>/` → 执行 `release.sh --sha <sha7>`。
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
- `PUBLIC_DOMAIN` 填裸域名（如 `example.com`），用于生成 C 端跨端链接 `https://www.<domain>`；改域名必须重新构建 nginx 镜像。

### 6.5 外部前置（不在仓库内）

- ACR 两个仓库、写权限与只读账号；ECS `deploy` 用户、SSH key 与 known_hosts；webhook 地址。
- 分支保护、Environment 审批人需在 GitHub 仓库设置中配置；仓库变量/Secrets 见 §6.4。
- 首次配置镜像流水线前置：在仓库根目录运行 `./scripts/setup-ci-external.sh`（交互式；公开值写到 `~/.config/yunhefc/ci-external.env`，密钥经 `gh` 写入 GitHub）。它只覆盖 `ACR_*`、`PUBLIC_DOMAIN`、分支保护与 `production` Environment。
- 缺少 `ACR_*`/`ECS_*` 时 PR 三项检查仍可运行；main 的 `images` 会失败，`deploy` 会在校验阶段直接拒绝。

### 6.6 CI 基础镜像（e2e 容器）

`e2e` job 使用 Docker Hub 公开镜像 `danche1013/ubuntu:<tag>`，tag 为不可变自描述格式
`24.04_pw<playwright>_node<node>`（例如 `24.04_pw1.65.0_node24.22.3`）。镜像内固化 Node、pnpm、
Playwright Chromium 及系统依赖、JDK 21、mysql client；决策与取舍见 `docs/adr/0001-ci-base-image.md`。

重建时机（必须同时改三处，否则只是变慢不会报错）：

1. `frontend/pnpm-lock.yaml` 的 `@playwright/test` 版本变化 → 改 `NODE_VERSION`/Playwright 依赖并换 tag；
2. `frontend/package.json` 的 `engines.node` 或 `packageManager` 变化 → 换 Node/pnpm 版本并换 tag；
3. `.github/workflows/ci.yml` 的 `container.image` 改为新 tag。

```bash
# 构建（本机 Docker VM 直连 443 会被重置，需经宿主机代理；网络可直连的机器去掉 --build-arg）
docker buildx build --platform linux/amd64 --load \
  --build-arg HTTP_PROXY=http://host.docker.internal:7897 \
  --build-arg HTTPS_PROXY=http://host.docker.internal:7897 \
  -f deploy/ci-base/ubuntu-v1.Dockerfile \
  -t danche1013/ubuntu:24.04_pw1.65.0_node24.22.3 deploy/ci-base
docker push danche1013/ubuntu:24.04_pw1.65.0_node24.22.3

# 验证
docker run --rm danche1013/ubuntu:24.04_pw1.65.0_node24.22.3 bash -lc \
  'java -version; mysql --version; node -v; pnpm -v; pnpm exec playwright --version'
```

- 镜像公开，不含业务代码与凭证；Docker Hub 匿名拉取有按 IP 限流，若 CI 出现 429，需改为 token 登录后拉取。
