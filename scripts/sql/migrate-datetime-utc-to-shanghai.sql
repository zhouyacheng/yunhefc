-- 一次性迁移：DATETIME(3) 存储从 UTC 墙钟改为 Asia/Shanghai（+08:00）墙钟。
--
-- 背景：2026-11-17 起应用连接池固定 connectionTimeZone=Asia/Shanghai +
-- session time_zone='+08:00'；此前写入的存量行仍是 UTC 墙钟，需整体 +8h。
--
-- 禁止场景（2026-11-18 起）：生产镜像已以 +08 墙钟写入（首个 +08 镜像 c5fcb4a，
-- 2026-11-21 为 0fe8195）。库中若已有该时间之后的行，整表平移会把它们再推快 8 小时；
-- 跨口径库只能按切换时间点定向修复，或接受旧数据偏差。执行前先按 RUNBOOK §1.3 做只读判定。
--
-- 前置：
--   1. 停止 backend，并手工 mysqldump 留底（生产无自动备份，P-168）。
--   2. 已完成 Flyway V1–V13（V8 新增并回填 audit_log.operated_at）。
--   3. 确认 audit_log 没有执行过 fix-audit-log-utc.sql 之外的二次修正；混杂数据需先人工核对。
--   4. 仅执行一次。回滚方式：对同一批列统一 - INTERVAL 8 HOUR。
--
-- 说明：每张表显式同时更新 updated_at，避免 ON UPDATE CURRENT_TIMESTAMP(3) 覆盖成 NOW()。

SET NAMES utf8mb4;

-- 防重复执行：首次平移后，最近 8 小时内写入的行会落在“未来”。
-- 命中时下面的 SELECT 因子查询返回多行而中止（ERROR 1242），避免再次 +8 小时。
-- 需要 CREATE ROUTINE 之外的额外权限为零，迁移账号可直接执行。
SET @suspect := (
    (SELECT COUNT(*) FROM team WHERE created_at > NOW() + INTERVAL 2 HOUR)
  + (SELECT COUNT(*) FROM app_user WHERE created_at > NOW() + INTERVAL 2 HOUR)
  + (SELECT COUNT(*) FROM team_match WHERE created_at > NOW() + INTERVAL 2 HOUR)
  + (SELECT COUNT(*) FROM audit_log WHERE created_at > NOW() + INTERVAL 2 HOUR)
  + (SELECT COUNT(*) FROM notification WHERE created_at > NOW() + INTERVAL 2 HOUR)
);
SELECT IF(@suspect > 0, (SELECT 1 UNION ALL SELECT 2), 1) AS _datetime_shift_guard;

START TRANSACTION;

UPDATE team SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

UPDATE app_user SET last_login_at = DATE_ADD(last_login_at, INTERVAL 8 HOUR),
                    created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                    updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

UPDATE player SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                  updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

UPDATE app_role SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                    updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

UPDATE permission SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR);

UPDATE user_role SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR);

UPDATE role_permission SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR);

UPDATE invite_link SET expires_at = DATE_ADD(expires_at, INTERVAL 8 HOUR),
                       revoked_at = DATE_ADD(revoked_at, INTERVAL 8 HOUR),
                       created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                       updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

UPDATE invite_use SET used_at = DATE_ADD(used_at, INTERVAL 8 HOUR);

UPDATE team_match SET start_at = DATE_ADD(start_at, INTERVAL 8 HOUR),
                      end_at = DATE_ADD(end_at, INTERVAL 8 HOUR),
                      signup_deadline = DATE_ADD(signup_deadline, INTERVAL 8 HOUR),
                      cancel_lock = DATE_ADD(cancel_lock, INTERVAL 8 HOUR),
                      status_updated_at = DATE_ADD(status_updated_at, INTERVAL 8 HOUR),
                      created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                      updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

UPDATE registration SET registered_at = DATE_ADD(registered_at, INTERVAL 8 HOUR),
                        cancelled_at = DATE_ADD(cancelled_at, INTERVAL 8 HOUR),
                        updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

UPDATE attendance SET recorded_at = DATE_ADD(recorded_at, INTERVAL 8 HOUR),
                      updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

UPDATE match_event SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                       updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR),
                       deleted_at = DATE_ADD(deleted_at, INTERVAL 8 HOUR);

UPDATE event_history SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR);

UPDATE formation_template SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                              updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

UPDATE lineup SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                  updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

UPDATE match_grouping SET generated_at = DATE_ADD(generated_at, INTERVAL 8 HOUR),
                          saved_at = DATE_ADD(saved_at, INTERVAL 8 HOUR),
                          created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                          updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

UPDATE match_grouping_player SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR);

UPDATE player_stats_summary SET updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR),
                                created_at = DATE_ADD(created_at, INTERVAL 8 HOUR);

UPDATE notification SET read_at = DATE_ADD(read_at, INTERVAL 8 HOUR),
                        created_at = DATE_ADD(created_at, INTERVAL 8 HOUR);

UPDATE audit_log SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                     operated_at = DATE_ADD(operated_at, INTERVAL 8 HOUR);

UPDATE outbox_event SET next_retry_at = DATE_ADD(next_retry_at, INTERVAL 8 HOUR),
                        lease_until = DATE_ADD(lease_until, INTERVAL 8 HOUR),
                        created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                        sent_at = DATE_ADD(sent_at, INTERVAL 8 HOUR);

UPDATE consumed_event SET processed_at = DATE_ADD(processed_at, INTERVAL 8 HOUR);

UPDATE idempotency_key SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                          expires_at = DATE_ADD(expires_at, INTERVAL 8 HOUR);

UPDATE system_setting SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                          updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR);

COMMIT;
