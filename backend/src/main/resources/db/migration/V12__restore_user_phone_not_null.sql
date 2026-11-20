-- 微信注册功能已移除：清理无手机号账号及其相关数据，恢复手机号非空。
-- V11 已合入 main 并可能已执行，不删除、不修改；本迁移只做前向修复。
-- 删除范围：账号、球员档案、报名、考勤、事件（含历史）、阵容槽位、分组成员、
-- 统计、通知、角色、邀请使用与幂等键。账号/球员被删后，仍由其他球员参与的事件
-- 只置空被删的助攻，保留原事件。
DROP TEMPORARY TABLE IF EXISTS tmp_v12_deleted_user;
CREATE TEMPORARY TABLE tmp_v12_deleted_user (
  id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

INSERT INTO tmp_v12_deleted_user (id)
SELECT id FROM app_user WHERE phone IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_v12_deleted_player;
CREATE TEMPORARY TABLE tmp_v12_deleted_player (
  id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

INSERT INTO tmp_v12_deleted_player (id)
SELECT id FROM player WHERE user_id IN (SELECT id FROM tmp_v12_deleted_user);

-- 主体球员被删的非换人事件、任一参与人被删的换人事件，收集后整体删除。
DROP TEMPORARY TABLE IF EXISTS tmp_v12_deleted_event;
CREATE TEMPORARY TABLE tmp_v12_deleted_event (
  id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB;

INSERT IGNORE INTO tmp_v12_deleted_event (id)
SELECT id FROM match_event
WHERE player_id IN (SELECT id FROM tmp_v12_deleted_player);
INSERT IGNORE INTO tmp_v12_deleted_event (id)
SELECT id FROM match_event
WHERE type = 2 AND in_player_id IN (SELECT id FROM tmp_v12_deleted_player);
INSERT IGNORE INTO tmp_v12_deleted_event (id)
SELECT id FROM match_event
WHERE type = 2 AND out_player_id IN (SELECT id FROM tmp_v12_deleted_player);

DELETE FROM event_history WHERE event_id IN (SELECT id FROM tmp_v12_deleted_event);
DELETE FROM match_event WHERE id IN (SELECT id FROM tmp_v12_deleted_event);

-- 助攻球员被删但主体球员仍保留时，只置空助攻，避免丢失仍有效球员的进球记录。
UPDATE match_event SET assist_player_id = NULL
WHERE assist_player_id IN (SELECT id FROM tmp_v12_deleted_player);

DELETE FROM lineup_slot WHERE player_id IN (SELECT id FROM tmp_v12_deleted_player);
DELETE FROM match_grouping_player WHERE player_id IN (SELECT id FROM tmp_v12_deleted_player);
DELETE FROM registration WHERE player_id IN (SELECT id FROM tmp_v12_deleted_player);
DELETE FROM attendance WHERE player_id IN (SELECT id FROM tmp_v12_deleted_player);
DELETE FROM player_stats_summary WHERE player_id IN (SELECT id FROM tmp_v12_deleted_player);
DELETE FROM notification WHERE user_id IN (SELECT id FROM tmp_v12_deleted_user);
DELETE FROM user_role WHERE user_id IN (SELECT id FROM tmp_v12_deleted_user);
DELETE FROM invite_use WHERE user_id IN (SELECT id FROM tmp_v12_deleted_user);
DELETE FROM idempotency_key WHERE user_id IN (SELECT id FROM tmp_v12_deleted_user);
DELETE FROM player WHERE id IN (SELECT id FROM tmp_v12_deleted_player);
DELETE FROM app_user WHERE id IN (SELECT id FROM tmp_v12_deleted_user);

ALTER TABLE app_user
  MODIFY COLUMN phone VARCHAR(20) NOT NULL COMMENT '登录手机号';
