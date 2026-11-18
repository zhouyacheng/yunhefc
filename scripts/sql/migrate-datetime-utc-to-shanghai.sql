-- 一次性迁移：DATETIME(3) 存储从 UTC 墙钟改为 Asia/Shanghai（+08:00）墙钟。
--
-- 背景：2026-11-17 起应用连接池固定 connectionTimeZone=Asia/Shanghai +
-- session time_zone='+08:00'；存量行仍是 UTC 墙钟，必须整体 +8h，否则读取后 instant 偏移 8 小时。
--
-- 前置：
--   1. 停止 backend，完成 RDS 备份或确认 PITR 时间点。
--   2. 确认 audit_log 没有执行过 fix-audit-log-utc.sql 之外的二次修正；混杂数据需先人工核对。
--   3. 仅执行一次。回滚方式：对同一批列统一 - INTERVAL 8 HOUR。
--
-- 说明：每张表显式同时更新 updated_at，避免 ON UPDATE CURRENT_TIMESTAMP(3) 覆盖成 NOW()。

SET NAMES utf8mb4;
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

UPDATE audit_log SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR);

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
