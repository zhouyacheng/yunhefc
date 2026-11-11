-- 云河FC 测试数据（20 名球员及关联数据）
-- 适用：MySQL 8.4，先执行 schema.sql；测试库使用，不要在生产执行。
-- 枚举字段使用 04 §2.4 的数字编码；角色 code 仍为字符串 GUEST/PLAYER/ADMIN。
-- 假设：球队为“云河FC”，创建/审核人为测试账号 1001；球员 1001-1015 为正式，1016-1020 为外挂。
-- 说明：仅为 1001（ADMIN）/1002（PLAYER）写入测试密码 Passw0rd!，其余账号 password_hash 为空。

SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;

-- 球队与默认角色（已存在则复用）
INSERT INTO team (id, name, short_name)
VALUES (1, '云河FC', '云河FC')
ON DUPLICATE KEY UPDATE name = VALUES(name), short_name = VALUES(short_name);

SET @team_id = (SELECT id FROM team WHERE name = '云河FC' LIMIT 1);

INSERT INTO app_role (team_id, code, name, is_default)
VALUES
  (@team_id, 'GUEST', '外挂球员', 1),
  (@team_id, 'PLAYER', '正式球员', 1),
  (@team_id, 'ADMIN', '管理员', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), is_default = VALUES(is_default);

SET @guest_role_id = (SELECT id FROM app_role WHERE team_id = @team_id AND code = 'GUEST' LIMIT 1);
SET @player_role_id = (SELECT id FROM app_role WHERE team_id = @team_id AND code = 'PLAYER' LIMIT 1);
SET @admin_role_id = (SELECT id FROM app_role WHERE team_id = @team_id AND code = 'ADMIN' LIMIT 1);

-- 账号：1001-1015 正式，1016-1020 外挂
-- 1001（ADMIN）与 1002（PLAYER）写入测试密码 Passw0rd!，供 dev profile + Playwright 冒烟登录；其余账号保留 NULL。
INSERT INTO app_user (id, phone, password_hash, openid, status)
VALUES
  (1001, '13910000001', '$2a$10$qmeKj/JGIZ0ySYfKgjile.pL0O8Z64ucVOfOEC83DlI4jP24hVcpC', NULL, 1),
  (1002, '13910000002', '$2a$10$qmeKj/JGIZ0ySYfKgjile.pL0O8Z64ucVOfOEC83DlI4jP24hVcpC', NULL, 1),
  (1003, '13910000003', NULL, NULL, 1),
  (1004, '13910000004', NULL, NULL, 1),
  (1005, '13910000005', NULL, NULL, 1),
  (1006, '13910000006', NULL, NULL, 1),
  (1007, '13910000007', NULL, NULL, 1),
  (1008, '13910000008', NULL, NULL, 1),
  (1009, '13910000009', NULL, NULL, 1),
  (1010, '13910000010', NULL, NULL, 1),
  (1011, '13910000011', NULL, NULL, 1),
  (1012, '13910000012', NULL, NULL, 1),
  (1013, '13910000013', NULL, NULL, 1),
  (1014, '13910000014', NULL, NULL, 1),
  (1015, '13910000015', NULL, NULL, 1),
  (1016, '13910000016', NULL, NULL, 1),
  (1017, '13910000017', NULL, NULL, 1),
  (1018, '13910000018', NULL, NULL, 1),
  (1019, '13910000019', NULL, NULL, 1),
  (1020, '13910000020', NULL, NULL, 1)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash);

-- 球员档案：正式球员号码 21-35 唯一，外挂 36-40 不参与正式唯一约束
INSERT IGNORE INTO player
  (id, team_id, user_id, name, jersey, type, position, secondary_positions, region, avatar_url, skill)
VALUES
  (1001, @team_id, 1001, '测试门将01', 21, 1, 0, '[]', '上海', NULL, 4),
  (1002, @team_id, 1002, '测试后卫01', 22, 1, 1, '[2]', '上海', NULL, 4),
  (1003, @team_id, 1003, '测试后卫02', 23, 1, 1, '[]', '苏州', NULL, 3),
  (1004, @team_id, 1004, '测试后卫03', 24, 1, 1, '[]', '上海', NULL, 3),
  (1005, @team_id, 1005, '测试后卫04', 25, 1, 1, '[]', '南通', NULL, 4),
  (1006, @team_id, 1006, '测试中场01', 26, 1, 2, '[3]', '上海', NULL, 4),
  (1007, @team_id, 1007, '测试中场02', 27, 1, 2, '[]', '上海', NULL, 3),
  (1008, @team_id, 1008, '测试中场03', 28, 1, 2, '[1]', '杭州', NULL, 4),
  (1009, @team_id, 1009, '测试中场04', 29, 1, 2, '[]', '上海', NULL, 3),
  (1010, @team_id, 1010, '测试中场05', 30, 1, 2, '[]', '嘉兴', NULL, 3),
  (1011, @team_id, 1011, '测试前锋01', 31, 1, 3, '[2]', '上海', NULL, 5),
  (1012, @team_id, 1012, '测试前锋02', 32, 1, 3, '[]', '昆山', NULL, 4),
  (1013, @team_id, 1013, '测试前锋03', 33, 1, 3, '[]', '上海', NULL, 3),
  (1014, @team_id, 1014, '测试前锋04', 34, 1, 3, '[]', '宁波', NULL, 3),
  (1015, @team_id, 1015, '测试中场06', 35, 1, 2, '[1]', '上海', NULL, 2),
  (1016, @team_id, 1016, '测试外挂01', 36, 0, 3, '[]', '上海', NULL, 3),
  (1017, @team_id, 1017, '测试外挂02', 37, 0, 2, '[]', '苏州', NULL, 3),
  (1018, @team_id, 1018, '测试外挂03', 38, 0, 1, '[]', '上海', NULL, 2),
  (1019, @team_id, 1019, '测试外挂04', 39, 0, 0, '[]', '上海', NULL, 3),
  (1020, @team_id, 1020, '测试外挂05', 40, 0, 2, '[]', '南通', NULL, 2);

-- 用户角色：正式 -> PLAYER，外挂 -> GUEST；1001 叠加 ADMIN，用于后台审核/管理联调
INSERT IGNORE INTO user_role (user_id, role_id)
SELECT u.id, @player_role_id FROM app_user u WHERE u.id BETWEEN 1001 AND 1015;

INSERT IGNORE INTO user_role (user_id, role_id)
SELECT u.id, @guest_role_id FROM app_user u WHERE u.id BETWEEN 1016 AND 1020;

INSERT IGNORE INTO user_role (user_id, role_id) VALUES (1001, @admin_role_id);

-- 两场测试比赛：1001 锁定（用于分组/阵容），1002 已结束（用于事件/考勤/统计）
INSERT IGNORE INTO team_match
  (id, team_id, title, type, format, start_at, end_at, signup_deadline, cancel_lock,
   venue, competition, season, round, home_team_name, away_team_name, our_side,
   status, manual_status, capacity, guest_capacity, waitlist_capacity, include_guests,
   score_home, score_away, season_match_no, total_match_no, version, created_by)
VALUES
  (1001, @team_id, '橙队VS兰队', 1, '8人制',
   '2026-11-15 20:00:00', '2026-11-15 22:00:00', '2026-11-15 00:00:00', '2026-11-15 10:00:00',
   '聚龙足球场', NULL, '2026', 1, '橙队', '兰队', NULL,
   2, 1, 30, 4, 2, 1, 0, 0, 1, 1, 0, 1001),
  (1002, @team_id, '橙队VS兰队', 1, '8人制',
   '2026-11-01 20:00:00', '2026-11-01 22:00:00', '2026-11-01 00:00:00', '2026-11-01 10:00:00',
   '聚龙足球场', NULL, '2026', 2, '橙队', '兰队', NULL,
   4, 0, 30, 4, 2, 1, 2, 2, 2, 2, 0, 1001);

-- 报名：1001 场 19 人（15 正式 + 4 外挂），1002 场 12 人（10 正式 + 2 外挂）
INSERT IGNORE INTO registration (match_id, player_id, type, status, registered_at)
SELECT 1001, id, type, 0, '2026-11-10 09:00:00'
FROM player WHERE id BETWEEN 1001 AND 1019;

INSERT IGNORE INTO registration (match_id, player_id, type, status, registered_at)
SELECT 1002, id, type, 0, '2026-10-25 09:00:00'
FROM player WHERE id BETWEEN 1001 AND 1010 OR id BETWEEN 1016 AND 1017;

-- 考勤：1002 场部分正常/缺勤/临时到场（1020 未报名，临时到场）
INSERT IGNORE INTO attendance (match_id, player_id, status, source, recorded_by)
VALUES
  (1002, 1001, 0, 0, NULL),
  (1002, 1002, 0, 0, NULL),
  (1002, 1003, 0, 1, 1001),
  (1002, 1004, 0, 0, NULL),
  (1002, 1005, 0, 0, NULL),
  (1002, 1006, 0, 0, NULL),
  (1002, 1007, 0, 0, NULL),
  (1002, 1008, 0, 0, NULL),
  (1002, 1009, 1, 2, NULL),
  (1002, 1010, 0, 1, 1001),
  (1002, 1016, 0, 0, NULL),
  (1002, 1017, 1, 2, NULL),
  (1002, 1020, 2, 1, 1001);

-- 事件：1002 场进球/乌龙/黄牌/换人
INSERT IGNORE INTO match_event
  (id, match_id, team, type, player_id, assist_player_id, in_player_id, out_player_id,
   minute, half, penalty, created_by, version)
VALUES
  (2001, 1002, 0, 0, 1002, 1003, NULL, NULL, 12, 0, 0, 1001, 0),
  (2002, 1002, 1, 0, 1016, NULL, NULL, NULL, 30, 0, 0, 1001, 0),
  (2003, 1002, 0, 1, 1004, NULL, NULL, NULL, 55, 1, 0, 1001, 0),
  (2004, 1002, 1, 3, 1017, NULL, NULL, NULL, 60, 1, 0, 1001, 0),
  (2005, 1002, 0, 2, NULL, NULL, 1007, 1008, 70, 1, 0, 1001, 0),
  (2006, 1002, 0, 0, 1002, NULL, NULL, NULL, 80, 1, 1, 1001, 0);

-- 事件历史：每个事件补一条 CREATE 记录（可重复执行时跳过已存在记录）
INSERT INTO event_history (event_id, action, after_json, actor_user_id)
SELECT e.id, 0,
       JSON_OBJECT('type', e.type, 'team', e.team, 'playerId', e.player_id,
                   'assistPlayerId', e.assist_player_id, 'inPlayerId', e.in_player_id,
                   'outPlayerId', e.out_player_id, 'minute', e.minute, 'half', e.half,
                   'penalty', e.penalty),
       e.created_by
FROM match_event e
WHERE e.match_id = 1002 AND e.id BETWEEN 2001 AND 2006
  AND NOT EXISTS (SELECT 1 FROM event_history h WHERE h.event_id = e.id AND h.action = 0);

-- 分组：1001 场 19 人，A/B 两队（用于阵容/分组联调）
INSERT IGNORE INTO match_grouping
  (id, match_id, manual_weight, auto_weight, roster_fingerprint, generated_at, saved_at, created_by)
VALUES
  (3001, 1001, 70.00, 30.00,
   SHA2('1001,1002,1003,1004,1005,1006,1007,1008,1009,1010,1011,1012,1013,1014,1015,1016,1017,1018,1019', 256),
   '2026-11-14 12:00:00', '2026-11-14 12:05:00', 1001);

INSERT IGNORE INTO match_grouping_player
  (grouping_id, player_id, team, manual_score, auto_score, final_score)
VALUES
  (3001, 1001, 0, 4.0, 4.10, 4.03),
  (3001, 1002, 0, 4.0, 4.20, 4.06),
  (3001, 1003, 0, 3.0, 3.50, 3.15),
  (3001, 1004, 0, 3.0, 3.20, 3.06),
  (3001, 1005, 0, 4.0, 4.00, 4.00),
  (3001, 1006, 0, 4.0, 4.30, 4.09),
  (3001, 1007, 0, 3.0, 3.10, 3.03),
  (3001, 1008, 0, 4.0, 3.90, 3.97),
  (3001, 1009, 0, 3.0, 3.00, 3.00),
  (3001, 1010, 0, 3.0, 2.90, 2.97),
  (3001, 1011, 1, 5.0, 4.80, 4.94),
  (3001, 1012, 1, 4.0, 4.10, 4.03),
  (3001, 1013, 1, 3.0, 3.20, 3.06),
  (3001, 1014, 1, 3.0, 3.00, 3.00),
  (3001, 1015, 1, 2.0, 2.80, 2.24),
  (3001, 1016, 1, 3.0, 3.40, 3.12),
  (3001, 1017, 1, 3.0, 3.20, 3.06),
  (3001, 1018, 1, 2.0, 2.50, 2.15),
  (3001, 1019, 1, 3.0, 3.10, 3.03);

-- 汇总统计：本赛季 20 名球员（出场按已结束的 1002 场考勤汇总；1001 场未结束，不计出场）
INSERT INTO player_stats_summary (team_id, player_id, season, appearances, goals, assists)
VALUES
  (@team_id, 1001, '2026', 1, 0, 0),
  (@team_id, 1002, '2026', 1, 2, 0),
  (@team_id, 1003, '2026', 1, 0, 1),
  (@team_id, 1004, '2026', 1, 0, 0),
  (@team_id, 1005, '2026', 1, 0, 0),
  (@team_id, 1006, '2026', 1, 0, 0),
  (@team_id, 1007, '2026', 1, 0, 0),
  (@team_id, 1008, '2026', 1, 0, 0),
  (@team_id, 1009, '2026', 0, 0, 0),
  (@team_id, 1010, '2026', 1, 0, 0),
  (@team_id, 1011, '2026', 0, 0, 0),
  (@team_id, 1012, '2026', 0, 0, 0),
  (@team_id, 1013, '2026', 0, 0, 0),
  (@team_id, 1014, '2026', 0, 0, 0),
  (@team_id, 1015, '2026', 0, 0, 0),
  (@team_id, 1016, '2026', 1, 1, 0),
  (@team_id, 1017, '2026', 0, 0, 0),
  (@team_id, 1018, '2026', 0, 0, 0),
  (@team_id, 1019, '2026', 0, 0, 0),
  (@team_id, 1020, '2026', 1, 0, 0)
ON DUPLICATE KEY UPDATE
  appearances = VALUES(appearances), goals = VALUES(goals), assists = VALUES(assists);

COMMIT;

-- 说明：
-- 1. 本脚本只用于测试库；执行前请确认 schema.sql 已执行。
-- 2. 若已有同号球队/角色，会复用；球员/比赛使用 1000+ ID，避免与常规自增数据冲突。
-- 3. 1001/1002 的测试密码为 Passw0rd!；重跑脚本会覆盖这两个账号的密码哈希。
-- 4. 1001 场为状态 2（锁定）+ 已保存分组，可用于阵容/分组联调；1002 场为状态 4（已结束），可用于事件/考勤/统计联调。
