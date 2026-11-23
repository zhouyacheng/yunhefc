-- 云河FC 测试数据清理脚本（事故恢复用）
-- 用途：在误执行 scripts/sql/test-data.sql 的库中，硬删除其插入的测试数据及运行期衍生数据。
-- 前提：生产库由 Flyway V1–V14 初始化，且不存在需要保留的真实用户/比赛；本脚本保留种子与 id=1 管理员。
--
-- 注意：
--   1. 本脚本是事故清理，不是业务删除路径。正常业务流程仍按 docs/rules/database-rules.md §4
--      使用状态或 is_deleted 伪删除；审计日志仍永久保留（仅删除 test-data 自带的 6001–6003）。
--   2. 执行前必须完成 RDS 备份或确认 PITR 时间点；建议停 backend 或维护窗口执行，避免 Outbox/消费者并发写入。
--   3. 使用具备 CREATE TEMPORARY TABLES 与业务表 DELETE 权限的账号（yunhefc_migrator 或 DBA），
--      不要使用仅有 DML 权限的 yunhefc_app。
--   4. 不删除：team id=1、app_role、permission、role_permission、system_setting、V4 预置阵型模板、id=1 管理员。
--   5. 不重置 AUTO_INCREMENT；清理后新 id 从当前自增位继续（不回收已删除 id），功能无影响。
--   6. 执行命令：mysql -h <rds-host> -u <dba> -p yunhefc < scripts/sql/test-data-cleanup.sql
--   7. 预检未通过时脚本会在删除前报错退出（错误含 __yunhefc_preflight_failed_abort__），
--      此时未删除任何数据；请核对 precheck.* 输出后人工处理。
--
-- 保留项说明：
--   - consumed_event 无实体外键，按 7 天保留策略自然过期；
--   - system_setting.updated_by 可能为测试账号，但 test-data 未修改配置值，不处理；
--   - Redis 会话无需清理：SessionAuthFilter 在下一次请求加载不到用户时会自动 invalidate；
--   - 运行期由测试账号产生的 audit_log 行不在本脚本删除范围（审计日志不手工删除），执行后请在核对输出中确认。

SET NAMES utf8mb4;
SET time_zone = '+08:00';
SET SESSION sql_safe_updates = 0;

-- ---------------------------------------------------------------------------
-- fixture ID 清单（来自 scripts/sql/test-data.sql）
-- ---------------------------------------------------------------------------

DROP TEMPORARY TABLE IF EXISTS tmp_test_user;
CREATE TEMPORARY TABLE tmp_test_user (
  id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

INSERT INTO tmp_test_user (id) VALUES
  (1001), (1002), (1003), (1004), (1005),
  (1006), (1007), (1008), (1009), (1010),
  (1011), (1012), (1013), (1014), (1015),
  (1016), (1017), (1018), (1019), (1020),
  (1021), (1022), (1023),
  (1030), (1031), (1032), (1033), (1034), (1035),
  (1040), (1041), (1042), (1043), (1044), (1045), (1046), (1047), (1048), (1049),
  (1050), (1051), (1052), (1053), (1054), (1055), (1056), (1057), (1058), (1059);

DROP TEMPORARY TABLE IF EXISTS tmp_test_player;
CREATE TEMPORARY TABLE tmp_test_player (
  id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

INSERT INTO tmp_test_player (id)
SELECT id FROM player WHERE user_id IN (SELECT id FROM tmp_test_user);

DROP TEMPORARY TABLE IF EXISTS tmp_test_match;
CREATE TEMPORARY TABLE tmp_test_match (
  id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

-- 固定 1001–1005；另覆盖测试管理员运行期创建的比赛（如 E2E比赛%）。
INSERT INTO tmp_test_match (id)
SELECT id FROM team_match
WHERE id IN (1001, 1002, 1003, 1004, 1005)
   OR created_by IN (SELECT id FROM tmp_test_user);

DROP TEMPORARY TABLE IF EXISTS tmp_test_event;
CREATE TEMPORARY TABLE tmp_test_event (
  id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

INSERT INTO tmp_test_event (id)
SELECT id FROM match_event WHERE match_id IN (SELECT id FROM tmp_test_match);

DROP TEMPORARY TABLE IF EXISTS tmp_test_lineup;
CREATE TEMPORARY TABLE tmp_test_lineup (
  id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

INSERT INTO tmp_test_lineup (id)
SELECT id FROM lineup WHERE match_id IN (SELECT id FROM tmp_test_match);

DROP TEMPORARY TABLE IF EXISTS tmp_test_grouping;
CREATE TEMPORARY TABLE tmp_test_grouping (
  id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

INSERT INTO tmp_test_grouping (id)
SELECT id FROM match_grouping WHERE match_id IN (SELECT id FROM tmp_test_match);

DROP TEMPORARY TABLE IF EXISTS tmp_test_invite;
CREATE TEMPORARY TABLE tmp_test_invite (
  id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

-- 固定 5001–5005；另覆盖测试账号运行期创建的邀请与 B3 固定 token。
INSERT INTO tmp_test_invite (id)
SELECT id FROM invite_link
WHERE id IN (5001, 5002, 5003, 5004, 5005)
   OR inviter_user_id IN (SELECT id FROM tmp_test_user)
   OR token_hash IN (
     SHA2('B3-INVITE-TEAM', 256), SHA2('B3-INVITE-MATCH', 256),
     SHA2('B3-INVITE-EXPIRED', 256), SHA2('B3-INVITE-REVOKED', 256),
     SHA2('B3-INVITE-EXHAUSTED', 256)
   );

-- ---------------------------------------------------------------------------
-- 预检：固定 ID 若被非测试数据占用，立即中止（不进入事务、不删除任何行）。
-- 预期 precheck.failures = 0。
-- ---------------------------------------------------------------------------

SET @pf_user = (
  SELECT COUNT(*) FROM app_user
  WHERE id IN (SELECT id FROM tmp_test_user)
    AND phone NOT LIKE '139100000%'
);
SET @pf_match = (
  SELECT COUNT(*) FROM team_match
  WHERE id IN (SELECT id FROM tmp_test_match)
    AND created_by NOT IN (SELECT id FROM tmp_test_user)
);
SET @pf_invite = (
  SELECT COUNT(*) FROM invite_link
  WHERE id IN (SELECT id FROM tmp_test_invite)
    AND inviter_user_id NOT IN (SELECT id FROM tmp_test_user)
);
SET @pf_admin = (
  SELECT COUNT(*) FROM app_user WHERE id = 1 AND is_deleted = 0
);
SET @preflight_failures = @pf_user + @pf_match + @pf_invite + IF(@pf_admin = 1, 0, 1);

SELECT 'precheck.user_phone_mismatch' AS item, @pf_user AS rows_count
UNION ALL SELECT 'precheck.match_foreign_creator', @pf_match
UNION ALL SELECT 'precheck.invite_foreign_inviter', @pf_invite
UNION ALL SELECT 'precheck.admin_present', @pf_admin
UNION ALL SELECT 'precheck.failures', @preflight_failures;

-- 失败时 PREPARE 一个不存在的表，让脚本在删除前报错退出；成功时执行 SELECT 1。
SET @guard_sql = IF(
  @preflight_failures = 0,
  'SELECT 1',
  'SELECT * FROM __yunhefc_preflight_failed_abort__'
);
PREPARE cleanup_guard FROM @guard_sql;
EXECUTE cleanup_guard;
DEALLOCATE PREPARE cleanup_guard;

-- ---------------------------------------------------------------------------
-- 删除前行数快照（执行人核对；每条独立查询，避免临时表被同一语句重复引用）
-- ---------------------------------------------------------------------------

SELECT 'before.app_user' AS item, COUNT(*) AS rows_count
FROM app_user WHERE id IN (SELECT id FROM tmp_test_user);
SELECT 'before.player' AS item, COUNT(*) AS rows_count
FROM player WHERE id IN (SELECT id FROM tmp_test_player);
SELECT 'before.team_match' AS item, COUNT(*) AS rows_count
FROM team_match WHERE id IN (SELECT id FROM tmp_test_match);
SELECT 'before.match_event' AS item, COUNT(*) AS rows_count
FROM match_event WHERE id IN (SELECT id FROM tmp_test_event);
SELECT 'before.event_history' AS item, COUNT(*) AS rows_count
FROM event_history WHERE event_id IN (SELECT id FROM tmp_test_event);
SELECT 'before.lineup' AS item, COUNT(*) AS rows_count
FROM lineup WHERE id IN (SELECT id FROM tmp_test_lineup);
SELECT 'before.lineup_slot' AS item, COUNT(*) AS rows_count
FROM lineup_slot WHERE lineup_id IN (SELECT id FROM tmp_test_lineup);
SELECT 'before.lineup_slot_by_player' AS item, COUNT(*) AS rows_count
FROM lineup_slot WHERE player_id IN (SELECT id FROM tmp_test_player);
SELECT 'before.match_grouping' AS item, COUNT(*) AS rows_count
FROM match_grouping WHERE id IN (SELECT id FROM tmp_test_grouping);
SELECT 'before.match_grouping_player' AS item, COUNT(*) AS rows_count
FROM match_grouping_player WHERE grouping_id IN (SELECT id FROM tmp_test_grouping);
SELECT 'before.match_grouping_player_by_player' AS item, COUNT(*) AS rows_count
FROM match_grouping_player WHERE player_id IN (SELECT id FROM tmp_test_player);
SELECT 'before.registration_by_match' AS item, COUNT(*) AS rows_count
FROM registration WHERE match_id IN (SELECT id FROM tmp_test_match);
SELECT 'before.registration_by_player' AS item, COUNT(*) AS rows_count
FROM registration WHERE player_id IN (SELECT id FROM tmp_test_player);
SELECT 'before.attendance_by_match' AS item, COUNT(*) AS rows_count
FROM attendance WHERE match_id IN (SELECT id FROM tmp_test_match);
SELECT 'before.attendance_by_player' AS item, COUNT(*) AS rows_count
FROM attendance WHERE player_id IN (SELECT id FROM tmp_test_player);
SELECT 'before.invite_link' AS item, COUNT(*) AS rows_count
FROM invite_link WHERE id IN (SELECT id FROM tmp_test_invite);
SELECT 'before.invite_use_by_invite' AS item, COUNT(*) AS rows_count
FROM invite_use WHERE invite_id IN (SELECT id FROM tmp_test_invite);
SELECT 'before.invite_use_by_user' AS item, COUNT(*) AS rows_count
FROM invite_use WHERE user_id IN (SELECT id FROM tmp_test_user);
SELECT 'before.notification_by_user' AS item, COUNT(*) AS rows_count
FROM notification WHERE user_id IN (SELECT id FROM tmp_test_user);
SELECT 'before.notification_by_match' AS item, COUNT(*) AS rows_count
FROM notification WHERE ref_type = 'MATCH' AND ref_id IN (SELECT id FROM tmp_test_match);
SELECT 'before.notification_by_user_ref' AS item, COUNT(*) AS rows_count
FROM notification WHERE ref_type = 'USER' AND ref_id IN (SELECT id FROM tmp_test_user);
SELECT 'before.player_stats_summary' AS item, COUNT(*) AS rows_count
FROM player_stats_summary WHERE player_id IN (SELECT id FROM tmp_test_player);
SELECT 'before.formation_template' AS item, COUNT(*) AS rows_count
FROM formation_template WHERE is_preset = 0 AND created_by IN (SELECT id FROM tmp_test_user);
SELECT 'before.formation_slot' AS item, COUNT(*) AS rows_count
FROM formation_slot fs
JOIN formation_template ft ON ft.id = fs.template_id
WHERE ft.is_preset = 0 AND ft.created_by IN (SELECT id FROM tmp_test_user);
SELECT 'before.user_role' AS item, COUNT(*) AS rows_count
FROM user_role WHERE user_id IN (SELECT id FROM tmp_test_user);
SELECT 'before.idempotency_key' AS item, COUNT(*) AS rows_count
FROM idempotency_key WHERE user_id IN (SELECT id FROM tmp_test_user);
SELECT 'before.outbox_match' AS item, COUNT(*) AS rows_count
FROM outbox_event WHERE aggregate_type = 'MATCH' AND aggregate_id IN (SELECT id FROM tmp_test_match);
SELECT 'before.outbox_user' AS item, COUNT(*) AS rows_count
FROM outbox_event WHERE aggregate_type = 'USER' AND aggregate_id IN (SELECT id FROM tmp_test_user);
SELECT 'before.outbox_audit_target_user' AS item, COUNT(*) AS rows_count
FROM outbox_event WHERE aggregate_type = 'AUDIT' AND aggregate_id IN (SELECT id FROM tmp_test_user);
SELECT 'before.outbox_audit_target_match' AS item, COUNT(*) AS rows_count
FROM outbox_event WHERE aggregate_type = 'AUDIT' AND aggregate_id IN (SELECT id FROM tmp_test_match);
SELECT 'before.outbox_audit_actor' AS item, COUNT(*) AS rows_count
FROM outbox_event
WHERE aggregate_type = 'AUDIT'
  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.actorUserId'))
      IN (SELECT CAST(id AS CHAR) FROM tmp_test_user);
SELECT 'before.audit_log' AS item, COUNT(*) AS rows_count
FROM audit_log WHERE id IN (6001, 6002, 6003);

-- ---------------------------------------------------------------------------
-- 事务删除：子表 → 父表
-- ---------------------------------------------------------------------------

START TRANSACTION;

-- 先删 Outbox：避免清库期间 relay 继续投递测试事件，重新生成通知/审计行。
DELETE FROM outbox_event
WHERE aggregate_type = 'MATCH' AND aggregate_id IN (SELECT id FROM tmp_test_match);
DELETE FROM outbox_event
WHERE aggregate_type = 'USER' AND aggregate_id IN (SELECT id FROM tmp_test_user);
DELETE FROM outbox_event
WHERE aggregate_type = 'AUDIT' AND aggregate_id IN (SELECT id FROM tmp_test_user);
DELETE FROM outbox_event
WHERE aggregate_type = 'AUDIT' AND aggregate_id IN (SELECT id FROM tmp_test_match);
DELETE FROM outbox_event
WHERE aggregate_type = 'AUDIT'
  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.actorUserId'))
      IN (SELECT CAST(id AS CHAR) FROM tmp_test_user);

DELETE FROM event_history WHERE event_id IN (SELECT id FROM tmp_test_event);

DELETE FROM match_event WHERE id IN (SELECT id FROM tmp_test_event);

DELETE FROM lineup_slot
WHERE lineup_id IN (SELECT id FROM tmp_test_lineup)
   OR player_id IN (SELECT id FROM tmp_test_player);

DELETE FROM lineup WHERE id IN (SELECT id FROM tmp_test_lineup);

DELETE FROM match_grouping_player
WHERE grouping_id IN (SELECT id FROM tmp_test_grouping)
   OR player_id IN (SELECT id FROM tmp_test_player);

DELETE FROM match_grouping WHERE id IN (SELECT id FROM tmp_test_grouping);

DELETE FROM registration
WHERE match_id IN (SELECT id FROM tmp_test_match)
   OR player_id IN (SELECT id FROM tmp_test_player);

DELETE FROM attendance
WHERE match_id IN (SELECT id FROM tmp_test_match)
   OR player_id IN (SELECT id FROM tmp_test_player);

DELETE FROM team_match WHERE id IN (SELECT id FROM tmp_test_match);

DELETE FROM invite_use
WHERE invite_id IN (SELECT id FROM tmp_test_invite)
   OR user_id IN (SELECT id FROM tmp_test_user);

DELETE FROM invite_link WHERE id IN (SELECT id FROM tmp_test_invite);

-- 通知按三个来源分句删除，避免同一语句重复引用临时表。
DELETE FROM notification WHERE user_id IN (SELECT id FROM tmp_test_user);
DELETE FROM notification WHERE ref_type = 'MATCH' AND ref_id IN (SELECT id FROM tmp_test_match);
DELETE FROM notification WHERE ref_type = 'USER' AND ref_id IN (SELECT id FROM tmp_test_user);

DELETE FROM player_stats_summary WHERE player_id IN (SELECT id FROM tmp_test_player);

-- 仅删除测试账号创建的非预置阵型；仍被现存 lineup 引用的模板保留。
DELETE fs FROM formation_slot fs
JOIN formation_template ft ON ft.id = fs.template_id
WHERE ft.is_preset = 0
  AND ft.created_by IN (SELECT id FROM tmp_test_user);

DELETE FROM formation_template
WHERE is_preset = 0
  AND created_by IN (SELECT id FROM tmp_test_user)
  AND NOT EXISTS (
    SELECT 1 FROM lineup l WHERE l.formation_template_id = formation_template.id
  );

DELETE FROM user_role WHERE user_id IN (SELECT id FROM tmp_test_user);

DELETE FROM player WHERE id IN (SELECT id FROM tmp_test_player);

DELETE FROM idempotency_key WHERE user_id IN (SELECT id FROM tmp_test_user);

DELETE FROM audit_log WHERE id IN (6001, 6002, 6003);

DELETE FROM app_user WHERE id IN (SELECT id FROM tmp_test_user);

COMMIT;

-- ---------------------------------------------------------------------------
-- 删除后校验：fixture 残留应全部为 0
-- ---------------------------------------------------------------------------

SELECT 'after.app_user' AS item, COUNT(*) AS rows_count
FROM app_user WHERE id IN (SELECT id FROM tmp_test_user);
SELECT 'after.player' AS item, COUNT(*) AS rows_count
FROM player WHERE id IN (SELECT id FROM tmp_test_player);
SELECT 'after.team_match' AS item, COUNT(*) AS rows_count
FROM team_match WHERE id IN (SELECT id FROM tmp_test_match);
SELECT 'after.match_event' AS item, COUNT(*) AS rows_count
FROM match_event WHERE id IN (SELECT id FROM tmp_test_event);
SELECT 'after.event_history' AS item, COUNT(*) AS rows_count
FROM event_history WHERE event_id IN (SELECT id FROM tmp_test_event);
SELECT 'after.lineup' AS item, COUNT(*) AS rows_count
FROM lineup WHERE id IN (SELECT id FROM tmp_test_lineup);
SELECT 'after.lineup_slot' AS item, COUNT(*) AS rows_count
FROM lineup_slot WHERE lineup_id IN (SELECT id FROM tmp_test_lineup);
SELECT 'after.lineup_slot_by_player' AS item, COUNT(*) AS rows_count
FROM lineup_slot WHERE player_id IN (SELECT id FROM tmp_test_player);
SELECT 'after.match_grouping' AS item, COUNT(*) AS rows_count
FROM match_grouping WHERE id IN (SELECT id FROM tmp_test_grouping);
SELECT 'after.match_grouping_player' AS item, COUNT(*) AS rows_count
FROM match_grouping_player WHERE grouping_id IN (SELECT id FROM tmp_test_grouping);
SELECT 'after.match_grouping_player_by_player' AS item, COUNT(*) AS rows_count
FROM match_grouping_player WHERE player_id IN (SELECT id FROM tmp_test_player);
SELECT 'after.registration_by_match' AS item, COUNT(*) AS rows_count
FROM registration WHERE match_id IN (SELECT id FROM tmp_test_match);
SELECT 'after.registration_by_player' AS item, COUNT(*) AS rows_count
FROM registration WHERE player_id IN (SELECT id FROM tmp_test_player);
SELECT 'after.attendance_by_match' AS item, COUNT(*) AS rows_count
FROM attendance WHERE match_id IN (SELECT id FROM tmp_test_match);
SELECT 'after.attendance_by_player' AS item, COUNT(*) AS rows_count
FROM attendance WHERE player_id IN (SELECT id FROM tmp_test_player);
SELECT 'after.invite_link' AS item, COUNT(*) AS rows_count
FROM invite_link WHERE id IN (SELECT id FROM tmp_test_invite);
SELECT 'after.invite_use_by_invite' AS item, COUNT(*) AS rows_count
FROM invite_use WHERE invite_id IN (SELECT id FROM tmp_test_invite);
SELECT 'after.invite_use_by_user' AS item, COUNT(*) AS rows_count
FROM invite_use WHERE user_id IN (SELECT id FROM tmp_test_user);
SELECT 'after.notification_by_user' AS item, COUNT(*) AS rows_count
FROM notification WHERE user_id IN (SELECT id FROM tmp_test_user);
SELECT 'after.notification_by_match' AS item, COUNT(*) AS rows_count
FROM notification WHERE ref_type = 'MATCH' AND ref_id IN (SELECT id FROM tmp_test_match);
SELECT 'after.notification_by_user_ref' AS item, COUNT(*) AS rows_count
FROM notification WHERE ref_type = 'USER' AND ref_id IN (SELECT id FROM tmp_test_user);
SELECT 'after.player_stats_summary' AS item, COUNT(*) AS rows_count
FROM player_stats_summary WHERE player_id IN (SELECT id FROM tmp_test_player);
SELECT 'after.formation_template' AS item, COUNT(*) AS rows_count
FROM formation_template WHERE is_preset = 0 AND created_by IN (SELECT id FROM tmp_test_user);
SELECT 'after.formation_slot' AS item, COUNT(*) AS rows_count
FROM formation_slot fs
JOIN formation_template ft ON ft.id = fs.template_id
WHERE ft.is_preset = 0 AND ft.created_by IN (SELECT id FROM tmp_test_user);
SELECT 'after.user_role' AS item, COUNT(*) AS rows_count
FROM user_role WHERE user_id IN (SELECT id FROM tmp_test_user);
SELECT 'after.idempotency_key' AS item, COUNT(*) AS rows_count
FROM idempotency_key WHERE user_id IN (SELECT id FROM tmp_test_user);
SELECT 'after.outbox_match' AS item, COUNT(*) AS rows_count
FROM outbox_event WHERE aggregate_type = 'MATCH' AND aggregate_id IN (SELECT id FROM tmp_test_match);
SELECT 'after.outbox_user' AS item, COUNT(*) AS rows_count
FROM outbox_event WHERE aggregate_type = 'USER' AND aggregate_id IN (SELECT id FROM tmp_test_user);
SELECT 'after.outbox_audit_target_user' AS item, COUNT(*) AS rows_count
FROM outbox_event WHERE aggregate_type = 'AUDIT' AND aggregate_id IN (SELECT id FROM tmp_test_user);
SELECT 'after.outbox_audit_target_match' AS item, COUNT(*) AS rows_count
FROM outbox_event WHERE aggregate_type = 'AUDIT' AND aggregate_id IN (SELECT id FROM tmp_test_match);
SELECT 'after.outbox_audit_actor' AS item, COUNT(*) AS rows_count
FROM outbox_event
WHERE aggregate_type = 'AUDIT'
  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.actorUserId'))
      IN (SELECT CAST(id AS CHAR) FROM tmp_test_user);
SELECT 'after.audit_log' AS item, COUNT(*) AS rows_count
FROM audit_log WHERE id IN (6001, 6002, 6003);

-- ---------------------------------------------------------------------------
-- 删除后校验：Flyway 种子与 id=1 管理员应保留
-- ---------------------------------------------------------------------------

SELECT
  (SELECT COUNT(*) FROM team WHERE id = 1 AND name = '云河FC')                     AS team_id1,
  (SELECT COUNT(*) FROM app_role WHERE team_id = 1)                                 AS roles,
  (SELECT COUNT(*) FROM permission)                                                 AS permissions,
  (SELECT COUNT(*) FROM role_permission)                                            AS role_permissions,
  (SELECT COUNT(*) FROM system_setting)                                             AS settings,
  (SELECT COUNT(*) FROM formation_template WHERE is_preset = 1 AND team_id IS NULL) AS preset_templates,
  (SELECT COUNT(*) FROM formation_slot fs
     JOIN formation_template ft ON ft.id = fs.template_id
    WHERE ft.is_preset = 1)                                                         AS preset_slots,
  (SELECT COUNT(*) FROM app_user WHERE id = 1)                                      AS admin_user,
  (SELECT COUNT(*) FROM player WHERE user_id = 1)                                   AS admin_player,
  (SELECT COUNT(*) FROM user_role ur
     JOIN app_role r ON r.id = ur.role_id
    WHERE ur.user_id = 1 AND r.code = 'ADMIN')                                      AS admin_role;

-- 总行数与剩余账号：预期 app_user/player 各 1（id=1），team_match 及业务关联表为 0。
SELECT
  (SELECT COUNT(*) FROM app_user)                AS app_user_total,
  (SELECT COUNT(*) FROM player)                  AS player_total,
  (SELECT COUNT(*) FROM team_match)              AS team_match_total,
  (SELECT COUNT(*) FROM registration)            AS registration_total,
  (SELECT COUNT(*) FROM attendance)              AS attendance_total,
  (SELECT COUNT(*) FROM match_event)             AS match_event_total,
  (SELECT COUNT(*) FROM event_history)           AS event_history_total,
  (SELECT COUNT(*) FROM lineup)                  AS lineup_total,
  (SELECT COUNT(*) FROM lineup_slot)             AS lineup_slot_total,
  (SELECT COUNT(*) FROM match_grouping)          AS match_grouping_total,
  (SELECT COUNT(*) FROM match_grouping_player)   AS match_grouping_player_total,
  (SELECT COUNT(*) FROM player_stats_summary)    AS player_stats_total,
  (SELECT COUNT(*) FROM notification)            AS notification_total,
  (SELECT COUNT(*) FROM invite_link)             AS invite_link_total,
  (SELECT COUNT(*) FROM invite_use)              AS invite_use_total,
  (SELECT COUNT(*) FROM audit_log)               AS audit_log_total;

-- 预期为空；若非空，执行人必须停下人工核查（正常只应有 id=1 管理员）。
SELECT id, phone, status, created_at
FROM app_user
WHERE id <> 1
ORDER BY id;
