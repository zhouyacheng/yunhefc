#!/usr/bin/env bash
# 云河FC 发布/回滚脚本。
# 约定：release 目录由 deploy.yml 提前放好 compose 快照与 release.env；本脚本只做校验、切换、启动与验证。
set -euo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/yunhefc}"
RELEASES_DIR="$DEPLOY_ROOT/releases"
CURRENT_LINK="$DEPLOY_ROOT/current"
STATE_DIR="$DEPLOY_ROOT/state"
PREVIOUS_FILE="$STATE_DIR/previous"
ENV_FILE="${YUNHEFC_ENV_FILE:-$DEPLOY_ROOT/.env}"
ACR_ENV_FILE="${ACR_ENV_FILE:-$DEPLOY_ROOT/env/acr.env}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-yunhefc}"
READINESS_TIMEOUT="${READINESS_TIMEOUT:-180}"
SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-15}"

usage() {
    cat <<'EOF'
用法:
  release.sh --sha <sha7>            发布指定版本
  release.sh --rollback [sha7]       回滚到指定版本；省略时回滚到 state/previous
  release.sh --rollback --force      强制跨拓扑回滚（需先手工恢复旧 .env/脚本/DNS）
环境变量:
  DEPLOY_ROOT (默认 /opt/yunhefc)、YUNHEFC_ENV_FILE、ACR_ENV_FILE、
  READINESS_TIMEOUT (默认 180 秒)、SMOKE_TIMEOUT (默认 15 秒)、COMPOSE_PROJECT
EOF
}

log() { printf '[release] %s\n' "$*" >&2; }
die() { printf '[release] ERROR: %s\n' "$*" >&2; exit 1; }

# 从 dotenv 文件读取单个值，不 source（避免执行文件内容与特殊字符问题）。
env_value() {
    local file="$1" key="$2"
    [[ -f "$file" ]] || return 0
    sed -n "s/^${key}=//p" "$file" | tail -n 1 | sed -e "s/^['\"]//" -e "s/['\"]$//"
}

MODE="deploy"
SHA=""
FORCE_TOPOLOGY=0
while (($#)); do
    case "$1" in
        --sha)
            (($# >= 2)) || die "--sha 需要 7 位 commit sha"
            MODE="deploy"
            SHA="$2"
            shift 2
            ;;
        --rollback)
            MODE="rollback"
            shift
            if (($# > 0)) && [[ "$1" =~ ^[0-9a-f]{7}$ ]]; then
                SHA="$1"
                shift
            fi
            ;;
        --force)
            FORCE_TOPOLOGY=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            die "未知参数: $1"
            ;;
    esac
done

if [[ -n "$SHA" ]] && ! [[ "$SHA" =~ ^[0-9a-f]{7}$ ]]; then
    die "sha 必须为 7 位小写十六进制"
fi

[[ -d "$RELEASES_DIR" ]] || die "release 根目录不存在: $RELEASES_DIR"
[[ -f "$ENV_FILE" ]] || die "缺少环境文件: $ENV_FILE"
mkdir -p "$STATE_DIR"

# flock：同一时间只允许一个发布/回滚进程。
exec 9>"$STATE_DIR/release.lock"
flock -w 600 9 || die "另一个发布/回滚进程持有锁（等待 600 秒超时）"

# ACR 凭证与镜像仓库；生产从 /opt/yunhefc/env/acr.env 读取只读凭证。
ACR_REGISTRY="${ACR_REGISTRY:-}"
ACR_NAMESPACE="${ACR_NAMESPACE:-}"
ACR_USERNAME="${ACR_USERNAME:-}"
ACR_PASSWORD="${ACR_PASSWORD:-}"
if [[ -f "$ACR_ENV_FILE" ]]; then
    [[ -n "$ACR_REGISTRY" ]] || ACR_REGISTRY="$(env_value "$ACR_ENV_FILE" ACR_REGISTRY)"
    [[ -n "$ACR_NAMESPACE" ]] || ACR_NAMESPACE="$(env_value "$ACR_ENV_FILE" ACR_NAMESPACE)"
    [[ -n "$ACR_USERNAME" ]] || ACR_USERNAME="$(env_value "$ACR_ENV_FILE" ACR_USERNAME)"
    [[ -n "$ACR_PASSWORD" ]] || ACR_PASSWORD="$(env_value "$ACR_ENV_FILE" ACR_PASSWORD)"
fi

if [[ -z "${IMAGE_REGISTRY:-}" ]]; then
    [[ -n "$ACR_REGISTRY" && -n "$ACR_NAMESPACE" ]] \
        || die "需要 IMAGE_REGISTRY，或在 $ACR_ENV_FILE 配置 ACR_REGISTRY/ACR_NAMESPACE"
    IMAGE_REGISTRY="${ACR_REGISTRY%/}/${ACR_NAMESPACE}"
fi
export IMAGE_REGISTRY
export YUNHEFC_ENV_FILE="$ENV_FILE"

if [[ -n "$ACR_USERNAME" && -n "$ACR_PASSWORD" ]]; then
    printf '%s' "$ACR_PASSWORD" | docker login "$ACR_REGISTRY" \
        --username "$ACR_USERNAME" --password-stdin >/dev/null
fi

DOMAIN_VALUE="$(env_value "$ENV_FILE" DOMAIN)"
[[ -n "$DOMAIN_VALUE" ]] || die "$ENV_FILE 未配置 DOMAIN"
[[ "$DOMAIN_VALUE" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]] \
    || die "$ENV_FILE 的 DOMAIN 必须是小写裸域名（如 example.com），不含 www./scheme/路径/端口"
[[ "$DOMAIN_VALUE" != www.* ]] \
    || die "$ENV_FILE 的 DOMAIN 不能带 www. 前缀"

# prod 启动会校验 PUBLIC_BASE_URL 与 CORS 单源；这里提前拦截，避免切版后才 crash-loop。
PUBLIC_BASE_URL_VALUE="$(env_value "$ENV_FILE" PUBLIC_BASE_URL)"
[[ "$PUBLIC_BASE_URL_VALUE" == "https://www.$DOMAIN_VALUE" ]] \
    || die "$ENV_FILE 的 PUBLIC_BASE_URL 必须等于 https://www.${DOMAIN_VALUE}（不带路径与末尾斜杠）"
CORS_ORIGINS_VALUE="$(env_value "$ENV_FILE" CORS_ALLOWED_ORIGINS)"
CORS_ORIGIN_COUNT="$(printf '%s\n' "$CORS_ORIGINS_VALUE" \
    | tr ',' '\n' | sed '/^[[:space:]]*$/d' | wc -l | tr -d ' ')"
[[ "$CORS_ORIGIN_COUNT" == "1" && "$CORS_ORIGINS_VALUE" == "$PUBLIC_BASE_URL_VALUE" ]] \
    || die "$ENV_FILE 的 CORS_ALLOWED_ORIGINS 必须恰为 $PUBLIC_BASE_URL_VALUE 单源"

# 与 ProdStartupValidator 对齐：这四项不合格会在切版后 crash-loop。
[[ "$(env_value "$ENV_FILE" SESSION_COOKIE_SECURE)" == "true" ]] \
    || die "$ENV_FILE 的 SESSION_COOKIE_SECURE 必须为 true"
[[ -z "$(env_value "$ENV_FILE" SESSION_COOKIE_DOMAIN)" ]] \
    || die "$ENV_FILE 的 SESSION_COOKIE_DOMAIN 必须为空（host-only Cookie）"
DB_PASSWORD_VALUE="$(env_value "$ENV_FILE" DB_PASSWORD)"
[[ -n "$DB_PASSWORD_VALUE" ]] \
    || die "$ENV_FILE 的 DB_PASSWORD 不得为空"
[[ -n "$(env_value "$ENV_FILE" REDIS_PASSWORD)" ]] \
    || die "$ENV_FILE 的 REDIS_PASSWORD 不得为空"

release_dir=""
if [[ "$MODE" == "rollback" ]]; then
    if [[ -n "$SHA" ]]; then
        release_dir="$RELEASES_DIR/$SHA"
    elif [[ -s "$PREVIOUS_FILE" ]]; then
        release_dir="$(cat "$PREVIOUS_FILE")"
    else
        die "没有 state/previous，无法默认回滚；请显式传 --rollback <sha7>"
    fi
else
    [[ -n "$SHA" ]] || die "发布必须传 --sha <sha7>"
    release_dir="$RELEASES_DIR/$SHA"
fi

[[ -d "$release_dir" ]] || die "release 目录不存在: $release_dir"
[[ -f "$release_dir/docker-compose.yml" && -f "$release_dir/docker-compose.prod.yml" ]] \
    || die "release 目录缺少 compose 快照: $release_dir"

# 跨拓扑回滚保护：当前版本已成功部署（有 .deployed）且目标拓扑不同/未标记时拒绝。
# 拓扑标记随 deploy/ 快照下发；旧的三子域 release 没有 topology 文件。
CURRENT_DIR="$(readlink -f "$CURRENT_LINK" 2>/dev/null || true)"
if [[ "$MODE" == "rollback" && "$FORCE_TOPOLOGY" -eq 0 \
        && -n "$CURRENT_DIR" && -f "$CURRENT_DIR/.deployed" \
        && "$CURRENT_DIR" != "$release_dir" ]]; then
    CURRENT_TOPOLOGY="$(cat "$CURRENT_DIR/topology" 2>/dev/null || true)"
    TARGET_TOPOLOGY="$(cat "$release_dir/topology" 2>/dev/null || true)"
    if [[ "$TARGET_TOPOLOGY" != "$CURRENT_TOPOLOGY" ]]; then
        die "回滚目标拓扑（${TARGET_TOPOLOGY:-未标记}）与当前（${CURRENT_TOPOLOGY:-未标记}）不同；请先恢复旧 .env/旧脚本/DNS，确认后加 --force"
    fi
fi

IMAGE_TAG="$(env_value "$release_dir/release.env" IMAGE_TAG)"
IMAGE_TAG="${IMAGE_TAG:-sha-${SHA:-${release_dir##*/}}}"
if [[ -n "$SHA" && "$IMAGE_TAG" != "sha-$SHA" ]]; then
    die "release.env 的 IMAGE_TAG=$IMAGE_TAG 与 --sha $SHA 不一致"
fi
export IMAGE_TAG

compose() {
    docker compose \
        --project-name "$COMPOSE_PROJECT" \
        --env-file "$ENV_FILE" \
        -f "$release_dir/docker-compose.yml" \
        -f "$release_dir/docker-compose.prod.yml" \
        "$@"
}

# 是否含某服务按目标快照 YAML 判断：P-168 之前的旧快照没有 mysql 服务。
# 不用 compose config，避免其因缺变量失败被误判为“无服务”而漏起、漏等 MySQL。
has_service() { grep -q "^  $1:" "$release_dir/docker-compose.yml"; }
if has_service mysql; then
    [[ -n "$(env_value "$ENV_FILE" MYSQL_ROOT_PASSWORD)" ]] \
        || die "$ENV_FILE 的 MYSQL_ROOT_PASSWORD 不得为空（追加方式见 RUNBOOK §1.1.1）"
fi

image_exists() {
    local image="$1"
    docker manifest inspect "$image" >/dev/null 2>&1 \
        || docker image inspect "$image" >/dev/null 2>&1
}

verify_images() {
    local name image
    for name in yunhefc-backend yunhefc-nginx; do
        image="$IMAGE_REGISTRY/$name:$IMAGE_TAG"
        image_exists "$image" || die "镜像不存在: $image"
        log "镜像可用: $image"
    done
    # 基础设施镜像由 CI mirror-infra 同步到 ACR（P-35 / RUNBOOK §2）。
    image="$IMAGE_REGISTRY/yunhefc-infra-redis:6.2.24"
    image_exists "$image" || die "基础设施镜像不存在: $image"
    log "镜像可用: $image"
    # MySQL 使用宿主预置的官方镜像，发布链路不访问 Docker Hub。
    # P-168 之前的旧快照没有 mysql 服务，回滚时不要求该镜像。
    if has_service mysql; then
        docker image inspect mysql:8.4 >/dev/null 2>&1 \
            || die "宿主缺少 mysql:8.4 镜像（生产不访问 Docker Hub，需提前预置）"
        log "镜像可用: mysql:8.4"
    fi
}

wait_ready() {
    local deadline=$((SECONDS + READINESS_TIMEOUT))
    local body
    while ((SECONDS < deadline)); do
        if body="$(compose exec -T backend curl -fsS --max-time 5 \
                http://127.0.0.1:8080/actuator/health/readiness 2>/dev/null)"; then
            log "readiness 通过: $body"
            return 0
        fi
        sleep 5
    done
    log "readiness 在 ${READINESS_TIMEOUT}s 内未通过"
    compose logs --tail=200 backend >&2 || true
    return 1
}

smoke() {
    local www="https://www.$DOMAIN_VALUE"
    local resolve_www="$DOMAIN_VALUE:443:127.0.0.1"

    curl -fsSk --max-time "$SMOKE_TIMEOUT" \
        --resolve "www.$resolve_www" "$www/" >/dev/null \
        || { log "冒烟失败: C 端首页"; return 1; }

    curl -fsSk --max-time "$SMOKE_TIMEOUT" \
        --resolve "www.$resolve_www" "$www/admin/" >/dev/null \
        || { log "冒烟失败: B 端首页"; return 1; }

    curl -fsSk --max-time "$SMOKE_TIMEOUT" \
        --resolve "www.$resolve_www" "$www/actuator/health" \
        | grep -q '"status":"UP"' \
        || { log "冒烟失败: /actuator/health"; return 1; }

    curl -fsSk --max-time "$SMOKE_TIMEOUT" \
        --resolve "www.$resolve_www" "$www/api/v1/c/matches?page=1&pageSize=1" \
        | grep -q '"code":0' \
        || { log "冒烟失败: 公开读接口"; return 1; }

    local path code
    for path in /actuator/env /actuator/beans /v3/api-docs /swagger-ui/index.html; do
        code="$(curl -sSk -o /dev/null -w '%{http_code}' --max-time "$SMOKE_TIMEOUT" \
            --resolve "www.$resolve_www" "$www$path")"
        [[ "$code" == "404" ]] \
            || { log "冒烟失败: $path 期望 404，实际 $code"; return 1; }
    done

    # 冒烟只断言每次发版都必须成立的不变量；一次性清理项不复验（06 §107）。
    # 小程序登录端点必须拒绝空 code（checklist §5.3）。
    local mini_code
    mini_code="$(curl -sSk -o /dev/null -w '%{http_code}' --max-time "$SMOKE_TIMEOUT" \
        -X POST -H 'Content-Type: application/json' -d '{"code":""}' \
        --resolve "www.$resolve_www" "$www/api/v1/c/auth/mini/login")"
    [[ "$mini_code" == "400" ]] \
        || { log "冒烟失败: mini/login 空 code 期望 400，实际 $mini_code"; return 1; }

    log "冒烟通过"
}

# 保留 current + previous + 最新一个其他版本（共 3 个）；不删除 current 与 previous。
cleanup_releases() {
    local keep_current keep_previous dir dir_name
    local -a keep=()
    # 目录名即 sha7；用 basename 比较，避免符号链接/规范化路径差异。
    keep_current="$(readlink "$CURRENT_LINK" 2>/dev/null || true)"
    keep_current="${keep_current##*/}"
    keep_previous="$(cat "$PREVIOUS_FILE" 2>/dev/null || true)"
    keep_previous="${keep_previous##*/}"

    add_keep() {
        [[ -n "$1" ]] || return 0
        [[ " ${keep[*]-} " == *" $1 "* ]] || keep+=("$1")
    }
    add_keep "$keep_current"
    add_keep "$keep_previous"

    while IFS= read -r dir; do
        dir="${dir%/}"
        [[ -n "$dir" && -d "$dir" ]] || continue
        dir_name="${dir##*/}"
        if [[ " ${keep[*]-} " == *" $dir_name "* ]]; then
            continue
        fi
        if ((${#keep[@]} < 3)); then
            keep+=("$dir_name")
            continue
        fi
        log "清理旧版本目录: $dir"
        rm -rf -- "$dir"
    done < <(ls -1dt "$RELEASES_DIR"/*/ 2>/dev/null || true)
}

# 删除不再对应任何保留目录的镜像 tag；失败只告警，不影响发布结果。
cleanup_images() {
    local kept="" dir tag image
    for dir in "$CURRENT_LINK" "$(cat "$PREVIOUS_FILE" 2>/dev/null || true)" \
            "$RELEASES_DIR"/*/; do
        [[ -n "$dir" && -d "$dir" ]] || continue
        tag="$(env_value "$dir/release.env" IMAGE_TAG)"
        [[ -n "$tag" ]] && kept="$kept $tag"
    done

    docker images --format '{{.Repository}}:{{.Tag}}' 2>/dev/null \
        | while IFS= read -r image; do
            repo="${image%:*}"
            tag="${image##*:}"
            case "$repo" in
                "$IMAGE_REGISTRY/yunhefc-backend"|"$IMAGE_REGISTRY/yunhefc-nginx") ;;
                *) continue ;;
            esac
            [[ "$tag" == sha-* ]] || continue
            if [[ " $kept " != *" $tag "* ]]; then
                log "删除旧镜像: $image"
                docker image rm "$image" >/dev/null 2>&1 || true
            fi
        done || true
}

verify_images

log "拉取镜像: $IMAGE_TAG"
compose pull backend nginx

if [[ "$MODE" == "deploy" ]]; then
    previous_target="$(readlink "$CURRENT_LINK" 2>/dev/null || true)"
    if [[ "$previous_target" != "$release_dir" ]]; then
        if [[ -n "$previous_target" ]]; then
            printf '%s\n' "$previous_target" > "$PREVIOUS_FILE.tmp"
            mv "$PREVIOUS_FILE.tmp" "$PREVIOUS_FILE"
            log "state/previous -> $previous_target"
        fi
        ln -sfn "$release_dir" "$CURRENT_LINK"
        log "current -> $release_dir"
    else
        log "current 已是目标版本，按幂等重跑"
    fi
else
    log "回滚目标: $release_dir"
    ln -sfn "$release_dir" "$CURRENT_LINK"
    log "current -> ${release_dir}（state/previous 保持不变）"
fi

# 首次部署时 MySQL/Redis 尚未运行；正常发布不重启，保持 --no-deps 语义。
# P-168 之前的旧快照没有 mysql 服务，按快照实际服务启动，避免回滚失败。
for infra in mysql redis; do
    has_service "$infra" || continue
    if ! compose ps --status running --services 2>/dev/null | grep -qx "$infra"; then
        log "基础设施未运行，启动 $infra"
        compose up -d "$infra"
    fi
done

# 首次初始化或重启期间等 MySQL healthy，避免 backend 在 Quartz 建表时连不上库而 crash-loop。
# 240s 覆盖 2C2G 冷启动首次初始化（healthcheck start_period 90s + retries）。
if has_service mysql; then
    mysql_id="$(compose ps -q mysql 2>/dev/null || true)"
    if [[ -n "$mysql_id" ]]; then
        deadline=$((SECONDS + 240))
        mysql_health=""
        while ((SECONDS < deadline)); do
            mysql_health="$(docker inspect -f '{{.State.Health.Status}}' "$mysql_id" 2>/dev/null || true)"
            [[ "$mysql_health" == "healthy" ]] && break
            sleep 5
        done
        [[ "$mysql_health" == "healthy" ]] || die "MySQL 在 240s 内未 healthy"
    fi
fi

log "启动 backend 与 nginx"
compose up -d --no-deps backend nginx

wait_ready || die "发布失败：readiness 未通过，current 保留在 $release_dir"
smoke || die "发布失败：冒烟未通过，current 保留在 $release_dir"

# 成功标记：跨拓扑回滚保护只拦截“已成功部署过的当前版本”。
printf '%s %s\n' "$(date -u +%FT%TZ)" "$IMAGE_TAG" > "$release_dir/.deployed"

cleanup_releases
cleanup_images

log "完成: $MODE $release_dir"
