#!/usr/bin/env bash
#
# 生产库重建：清理手工建表 + test-data 残留，改用 Flyway V1–V13 初始化，
# 并用真实 BCrypt 哈希创建首位管理员（RUNBOOK §1.3、§1.5）。
#
# 破坏性操作：会清空 yunhefc 库全部对象。必须输入确认短语才会执行。

set -euo pipefail
cd "$(cd "$(dirname "$0")/.." && pwd)"

GREEN=$'\033[32m'; RED=$'\033[31m'; YELLOW=$'\033[33m'; DIM=$'\033[2m'; RESET=$'\033[0m'
say()  { printf '  %s\n' "$1"; }
ok()   { printf '  %s✓%s %s\n' "$GREEN" "$RESET" "$1"; }
warn() { printf '  %s⚠ %s%s\n' "$YELLOW" "$1" "$RESET"; }
die()  { printf '  %s✗ %s%s\n' "$RED" "$1" "$RESET" >&2; exit 1; }

ASK_ENV="deploy/.env.acceptance"
[[ -f "$ASK_ENV" ]] || die "缺少 $ASK_ENV"
existing() { grep -m1 "^$1=" "$ASK_ENV" 2>/dev/null | cut -d= -f2- || true; }

ECS_SSH="${ECS_SSH:-$(existing ECS_SSH)}"
ECS_SSH="${ECS_SSH:-root@47.116.72.163}"
ECS_PORT="${ECS_PORT:-$(existing ECS_PORT)}"
ECS_PORT="${ECS_PORT:-22}"
RC_DOMAIN="${RC_DOMAIN:-$(existing RC_DOMAIN)}"
RC_DOMAIN="${RC_DOMAIN:-yunhefc.com}"
SSH_OPTS=(-p "$ECS_PORT" -o ConnectTimeout=10 -o BatchMode=yes)
SCP_OPTS=(-P "$ECS_PORT" -o ConnectTimeout=10 -o BatchMode=yes)
remote() { ssh "${SSH_OPTS[@]}" "$ECS_SSH" "$@"; }

REMOTE_ENV=/tmp/.rc-db-env
cleanup_remote() {
  remote "rm -f '$REMOTE_ENV'" >/dev/null 2>&1 || true
  [[ -n "${LOCAL_ADMIN:-}" ]] && rm -f "$LOCAL_ADMIN"
}
trap cleanup_remote EXIT

printf '\n%s生产库重建（Flyway V1–V13 + 首位管理员）%s\n\n' "$DIM" "$RESET"

say "目标主机：$ECS_SSH"
remote 'echo ok' >/dev/null 2>&1 || die "SSH 不可用"

read -r -p '  迁移账号（需 DDL 权限）：' MIG_USER
[[ -n "$MIG_USER" ]] || die "迁移账号不能为空"
read -rs -p '  迁移账号密码：' MIG_PASS; printf '\n'
[[ -n "$MIG_PASS" ]] || die "密码不能为空"

read -r -p '  管理员手机号（11 位）：' ADMIN_PHONE
[[ "$ADMIN_PHONE" =~ ^1[0-9]{10}$ ]] || die "手机号格式不正确"
read -r -p '  管理员姓名：' ADMIN_NAME
[[ -n "$ADMIN_NAME" && "$ADMIN_NAME" != *"'"* ]] || die "姓名不能为空且不能含单引号"
read -rs -p '  管理员密码（将生成 BCrypt 哈希）：' ADMIN_PASS; printf '\n'
[[ ${#ADMIN_PASS} -ge 8 ]] || die "密码至少 8 位"
read -r -p '  管理员位置 0门将/1后卫/2中场/3前锋 [2]：' ADMIN_POS
ADMIN_POS="${ADMIN_POS:-2}"
[[ "$ADMIN_POS" =~ ^[0-3]$ ]] || die "位置必须是 0–3"

say ""
say "将执行："
say "  1. 复制 V1–V13 到 /opt/yunhefc/migrations"
say "  2. 停止 backend 容器"
say "  3. flyway clean + migrate（清空 yunhefc 库）"
say "  4. 生成 BCrypt 哈希并执行 bootstrap-admin.sql"
say "  5. 启动 backend 并校验"
say ""
read -r -p "  输入 'RESET yunhefc' 继续：" CONFIRM
[[ "$CONFIRM" == "RESET yunhefc" ]] || die "已取消"

# ── 1. 迁移文件 ───────────────────────────────────────────────────────────
MIG_DIR=backend/src/main/resources/db/migration
compgen -G "$MIG_DIR/V*.sql" >/dev/null || die "$MIG_DIR 下没有 V*.sql"
# 版本重复会让 flyway migrate 直接失败，必须在清库前发现。
dups=$(ls "$MIG_DIR"/V*.sql | xargs -n1 basename | sed -n 's/^V\([0-9][0-9._]*\)__.*/\1/p' | sort | uniq -d)
[[ -z "$dups" ]] || die "迁移版本重复：$(echo "$dups" | tr '\n' ' ')"
if [[ -n "$(git status --porcelain "$MIG_DIR")" ]]; then
  warn "迁移目录有未提交改动，确认这是要迁到生产的版本"
fi
branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)
[[ "$branch" == "main" ]] || warn "当前分支 $branch 不是 main，确认迁移文件与生产代码一致"

remote 'install -d /opt/yunhefc/migrations'
# 旧版本残留会让 flyway 报 version 冲突或迁到错误的最高版本，先清空目录内的 SQL。
remote 'rm -f /opt/yunhefc/migrations/*.sql'
scp "${SCP_OPTS[@]}" "$MIG_DIR"/*.sql "$ECS_SSH:/opt/yunhefc/migrations/" >/dev/null
ok "迁移文件已上传（$(ls "$MIG_DIR"/V*.sql | wc -l | tr -d ' ') 个）"

# ── 2. 解析连接信息 ───────────────────────────────────────────────────────
read -r DB_HOST DB_PORT DB_DB < <(remote '
  DBURL=$(grep -m1 "^DB_URL=" /opt/yunhefc/.env | cut -d= -f2-)
  hp="${DBURL#jdbc:mysql://}"; hp="${hp%%[?]*}"
  hostport="${hp%%/*}"; db="${hp#*/}"
  host="${hostport%%:*}"; port="3306"
  [[ "$hostport" == *:* ]] && port="${hostport##*:}"
  echo "$host $port $db"
')
[[ -n "$DB_HOST" && -n "$DB_DB" ]] || die "无法从 /opt/yunhefc/.env 解析数据库地址"
DB_PORT="${DB_PORT:-3306}"
say "  目标库：$DB_DB @ $DB_HOST:$DB_PORT"

# ── 3. 凭据文件（600，仅经 stdin 写入）────────────────────────────────────
{
  printf 'MYSQL_PWD=%s\n' "$MIG_PASS"
  printf 'FLYWAY_USER=%s\n' "$MIG_USER"
  printf 'FLYWAY_PASSWORD=%s\n' "$MIG_PASS"
} | remote "umask 077; cat > '$REMOTE_ENV'"
ok "凭据已写入临时文件（会话结束删除）"

# ── 4. 停 backend ─────────────────────────────────────────────────────────
compose() {
  # release.sh 会导出 IMAGE_REGISTRY / IMAGE_TAG；这里按同样规则补齐，
  # 否则 compose 因 ${IMAGE_REGISTRY:?...} 直接报错。
  # 只按行读取变量，不 source：ACR 口令里的 $ / 反引号在 set -u 下会中断。
  remote 'bash -s' <<EOF
set -euo pipefail
acr_env=/opt/yunhefc/env/acr.env
ACR_REGISTRY=\$(sed -n 's/^ACR_REGISTRY=//p' "\$acr_env" | tail -1)
ACR_NAMESPACE=\$(sed -n 's/^ACR_NAMESPACE=//p' "\$acr_env" | tail -1)
[ -n "\$ACR_REGISTRY" ] && [ -n "\$ACR_NAMESPACE" ] || { echo "acr.env 缺少 ACR_REGISTRY/ACR_NAMESPACE" >&2; exit 1; }
export IMAGE_REGISTRY="\${ACR_REGISTRY%/}/\${ACR_NAMESPACE}"
export IMAGE_TAG="\$(sed -n 's/^IMAGE_TAG=//p' /opt/yunhefc/current/release.env | tail -1)"
[ -n "\$IMAGE_TAG" ] || { echo "release.env 缺少 IMAGE_TAG（current 链接或发布记录不可用）" >&2; exit 1; }
docker compose --project-name yunhefc --env-file /opt/yunhefc/.env -f /opt/yunhefc/current/docker-compose.yml -f /opt/yunhefc/current/docker-compose.prod.yml $*
EOF
}
# 停不掉 backend 就不能清库：否则旧连接持有表，clean 与 migrate 都会失控。
if ! compose stop backend >/tmp/rc-compose-stop.log 2>&1; then
  tail -5 /tmp/rc-compose-stop.log | sed 's/^/    /'
  die "停止 backend 失败，未执行任何数据库操作"
fi
ok "backend 已停止"

# ── 5. 库存在性 / 建库 / Flyway ──────────────────────────────────────────
run_sql() {
  remote "umask 077; docker run --rm -i --network host --env-file '$REMOTE_ENV' \
    mysql:8.4 mysql --default-character-set=utf8mb4 -N -h '$DB_HOST' -P '$DB_PORT' -u '$MIG_USER' -e \"$1\""
}
# 连接失败不能被当成“库不存在”，否则会在错误实例上建库。
if ! db_list=$(run_sql "SHOW DATABASES LIKE '$DB_DB'" 2>/tmp/rc-db-probe.log); then
  tail -3 /tmp/rc-db-probe.log | sed 's/^/    /'
  die "无法连接 $DB_HOST:$DB_PORT，请检查网络/迁移账号（未做任何变更）"
fi
if [[ -n "$(printf '%s' "$db_list" | tr -d '\r')" ]]; then
  say "  库 $DB_DB 已存在，clean 后重建"
  FLYWAY_CMDS="-cleanDisabled=false clean migrate"
else
  say "  库 $DB_DB 不存在，创建空库"
  if run_sql "CREATE DATABASE IF NOT EXISTS $DB_DB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" >/dev/null 2>&1; then
    ok "已创建 ${DB_DB}（utf8mb4_0900_ai_ci）"
  else
    die "迁移账号无权建库，请手工执行 RUNBOOK §1.1 的 CREATE DATABASE 后重跑"
  fi
  FLYWAY_CMDS="migrate"
fi

read -r DB_URL_ONLY < <(remote "grep -m1 '^DB_URL=' /opt/yunhefc/.env | cut -d= -f2-")
say "  执行 flyway $FLYWAY_CMDS …"
if remote "docker run --rm --network host --env-file '$REMOTE_ENV' \
      -v /opt/yunhefc/migrations:/flyway/sql:ro \
      -e FLYWAY_URL='$DB_URL_ONLY' \
      flyway/flyway:11.7.2 -connectRetries=10 $FLYWAY_CMDS" >/tmp/rc-flyway.log 2>&1; then
  grep -E 'Successfully applied|Successfully dropped' /tmp/rc-flyway.log | sed 's/^/    /' || true
  ok "Flyway 迁移完成"
else
  tail -20 /tmp/rc-flyway.log | sed 's/^/    /'
  die "Flyway 失败，backend 保持停止"
fi

# ── 6. 首位管理员 ─────────────────────────────────────────────────────────
# 密码经 stdin 传给远端，避免出现在命令行或被本地展开。
# set -e 下管道失败会直接退出，先兜住错误再判断哈希格式，否则库已重建却没有任何提示。
BCRYPT_RAW=$(printf '%s\n' "$ADMIN_PASS" \
  | remote 'docker run --rm -i httpd:2.4-alpine sh -c "read -r p; htpasswd -bnBC 10 \"\" \"\$p\""' 2>/tmp/rc-bcrypt.log || true)
BCRYPT=$(printf '%s' "$BCRYPT_RAW" | tr -d ':\r\n' | sed 's/^\$2y\$/\$2a\$/')
[[ "$BCRYPT" == \$2a\$* ]] || die "BCrypt 哈希生成失败（库已迁移，管理员未创建）：$(tail -1 /tmp/rc-bcrypt.log)"
ok "BCrypt 哈希已生成"

# sed 替换串里 & | \ 有特殊含义，姓名必须先转义。
sed_escape() { printf '%s' "$1" | sed -e 's/[\\&|]/\\&/g'; }
LOCAL_ADMIN=$(mktemp)
sed \
  -e "s|^SET @phone = .*|SET @phone = '$ADMIN_PHONE';|" \
  -e "s|^SET @name = .*|SET @name = '$(sed_escape "$ADMIN_NAME")';|" \
  -e "s|^SET @password_hash = .*|SET @password_hash = '$BCRYPT';|" \
  -e "s|^SET @position = .*|SET @position = $ADMIN_POS;|" \
  scripts/sql/bootstrap-admin.sql > "$LOCAL_ADMIN"

ADMIN_OUT=$(remote "umask 077; docker run --rm -i --network host --env-file '$REMOTE_ENV' \
    mysql:8.4 mysql --default-character-set=utf8mb4 -N -h '$DB_HOST' -P '$DB_PORT' -u '$MIG_USER' '$DB_DB'" < "$LOCAL_ADMIN")
printf '%s\n' "$ADMIN_OUT" | sed 's/^/    /'
PERMS=$(printf '%s\n' "$ADMIN_OUT" | awk 'END{print $1}')
[[ "$PERMS" == "28" ]] || die "ADMIN 权限数为 ${PERMS:-未知}，期望 28"
ok "首位管理员已创建（权限 28）"

# ── 7. 启 backend 并校验 ──────────────────────────────────────────────────
compose up -d --no-deps backend >/dev/null
compose restart backend >/dev/null   # 库已重建，旧连接必须丢弃
# readiness 跟着验收域名走，避免探到旧环境或无关域名上报 200。
HEALTH_URL="${RC_HEALTH_URL:-https://www.$RC_DOMAIN/actuator/health}"
say "  等待 backend readiness（$HEALTH_URL）…"
ready=0
for _ in $(seq 1 36); do
  code=$(curl -skS --noproxy '*' -o /dev/null -w '%{http_code}' --max-time 5 "$HEALTH_URL" 2>/dev/null || true)
  if [[ "$code" == "200" ]]; then ready=1; break; fi
  sleep 5
done
(( ready )) || die "readiness 未通过（$HEALTH_URL），查看 docker logs yunhefc-backend-1"
ok "backend 就绪"

remote "umask 077; docker run --rm --network host --env-file '$REMOTE_ENV' \
    mysql:8.4 mysql -h '$DB_HOST' -P '$DB_PORT' -u '$MIG_USER' '$DB_DB' -N -e \
    'SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;'" \
  | sed 's/^/    /'

printf '\n'
ok "完成：库已由 Flyway 初始化，管理员 $ADMIN_PHONE"
say "  下一步：重跑 deploy.yml 获取一次成功发布记录"
