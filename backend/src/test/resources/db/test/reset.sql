-- 每个集成测试方法前执行：清空可变业务数据，随后由 Flyway V3 重建角色/授权/系统配置。
-- 不复制种子数据，避免与迁移脚本漂移。
SET NAMES utf8mb4;

TRUNCATE TABLE app_user;
TRUNCATE TABLE player;
TRUNCATE TABLE user_role;
TRUNCATE TABLE role_permission;
TRUNCATE TABLE app_role;
TRUNCATE TABLE system_setting;
TRUNCATE TABLE invite_link;
TRUNCATE TABLE invite_use;
TRUNCATE TABLE team_match;
TRUNCATE TABLE registration;
TRUNCATE TABLE attendance;
TRUNCATE TABLE match_event;
TRUNCATE TABLE event_history;
TRUNCATE TABLE lineup;
TRUNCATE TABLE lineup_slot;
TRUNCATE TABLE match_grouping;
TRUNCATE TABLE match_grouping_player;
TRUNCATE TABLE player_stats_summary;
TRUNCATE TABLE notification;
TRUNCATE TABLE audit_log;
TRUNCATE TABLE outbox_event;
TRUNCATE TABLE consumed_event;
TRUNCATE TABLE idempotency_key;

-- 保留 Flyway 预置阵型，删除测试创建的自定义阵型。
DELETE fs FROM formation_slot fs
JOIN formation_template ft ON ft.id = fs.template_id
WHERE ft.is_preset = 0;
DELETE FROM formation_template WHERE is_preset = 0;
