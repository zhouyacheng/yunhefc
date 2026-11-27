#!/usr/bin/env bash
# 本地联调启动后端：dev profile（短信 mock；微信小程序与 OSS 默认使用真实凭证；Outbox 中继与事件分发沿用默认开启）。
# 凭证来源：~/.zshrc 中 export 的 WECHAT_MINI_APP_ID / WECHAT_MINI_APP_SECRET / OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET；
# 已存在的同名环境变量优先。OSS_MODE / WECHAT_MINI_MODE 为 mock 时跳过对应凭证。
set -euo pipefail

cd "$(dirname "$0")/../backend"

load_zshrc_export() {
  local name=$1 line value
  if [ -z "${!name:-}" ]; then
    line=$(grep -E "^export ${name}=" "$HOME/.zshrc" 2>/dev/null | tail -n 1 || true)
    if [ -n "$line" ]; then
      value=${line#"export ${name}="}
      case $value in
        \'*\') value=${value#\'}; value=${value%\'} ;;
        \"*\") value=${value#\"}; value=${value%\"} ;;
      esac
      export "${name}=${value}"
    fi
  fi
  if [ -z "${!name:-}" ]; then
    echo "缺少 ${name}：请在 ~/.zshrc 中 export，或直接注入环境变量" >&2
    exit 1
  fi
}

if [ "${OSS_MODE:-real}" = "real" ]; then
  load_zshrc_export OSS_ACCESS_KEY_ID
  load_zshrc_export OSS_ACCESS_KEY_SECRET
fi
if [ "${WECHAT_MINI_MODE:-real}" = "real" ]; then
  load_zshrc_export WECHAT_MINI_APP_ID
  load_zshrc_export WECHAT_MINI_APP_SECRET
fi

export SPRING_PROFILES_ACTIVE=dev
# 本机 Docker 端口：MySQL 3307、Redis 6380；与仓库默认值不同，可用同名环境变量覆盖。
export DB_URL="${DB_URL:-jdbc:mysql://localhost:3307/yunhefc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8}"
export REDIS_PORT="${REDIS_PORT:-6380}"
# 本地反复登录/走查时放宽登录限流。
export AUTH_LOGIN_RATE_LIMIT_MAX_ATTEMPTS="${AUTH_LOGIN_RATE_LIMIT_MAX_ATTEMPTS:-1000}"

# macOS 上未设置 JAVA_HOME 时选择 JDK 21（项目要求）。
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
  export JAVA_HOME
fi

exec ./mvnw spring-boot:run "$@"
