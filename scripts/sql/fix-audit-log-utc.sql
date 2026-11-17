-- 已废弃：2026-11-17 起 DATETIME 统一存 Asia/Shanghai 墙钟，本脚本不再适用，禁止再执行。
--
-- 一次性修复：audit_log.created_at 由本地墙钟（Asia/Shanghai）转为 UTC。
--
-- 背景：历史版本插入审计行未显式写 created_at，由 MySQL DEFAULT CURRENT_TIMESTAMP(3)
-- 按 RDS 会话时区（+08:00）生成；JDBC 又按 serverTimezone=UTC 解释，导致 API/页面多 8 小时。
-- 应用侧已在 Hikari 配置连接会话 time_zone='+00:00'，本脚本只修正历史行。
--
-- 前置：停止 backend；执行后部署含 connection-init-sql 的新版本再启动。
-- test-data 的 3 条夹具（用 UTC_TIMESTAMP 写入）按 request_id 排除，不用 id 范围。
-- 只执行一次；回滚方式：对同一筛选条件 + INTERVAL 8 HOUR。

SELECT id, request_id, created_at, UTC_TIMESTAMP(3) AS utc_now
FROM audit_log ORDER BY id DESC LIMIT 10;

UPDATE audit_log
SET created_at = created_at - INTERVAL 8 HOUR
WHERE request_id IS NULL
   OR request_id NOT IN ('e2e-audit-settings', 'e2e-audit-match', 'e2e-audit-unknown');

SELECT id, request_id, created_at, UTC_TIMESTAMP(3) AS utc_now
FROM audit_log ORDER BY id DESC LIMIT 10;
