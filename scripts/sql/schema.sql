-- 云河FC 业务表结构（MySQL 8.4）
-- 规范：docs/rules/database-rules.md；架构：docs/design/03.整体架构设计.md
-- 说明：
--   1. 仅 DDL，不含种子数据；球队/角色/权限/配置由 Flyway 种子脚本初始化。
--   2. 主键统一 BIGINT UNSIGNED AUTO_INCREMENT；不建物理外键，关联字段建索引。
--   3. 时间字段统一 UTC，使用 DATETIME(3)。
--   4. 表名单数 snake_case，避开 MySQL 保留字（team_match / app_user / app_role）。
--   5. 枚举字段统一 TINYINT UNSIGNED，数字映射见列 COMMENT 与 docs/design/04 §2.4；数据库永久禁用 CHECK，枚举与跨字段业务规则均由应用层强校验。
--   6. 所有业务表包含 is_deleted（0-未删除;1-伪删除）。
--   7. 所有字段带中文 COMMENT；枚举/布尔字段同时写明数字含义。

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ---------------------------------------------------------------------------
-- 球队与账号
-- ---------------------------------------------------------------------------

CREATE TABLE team (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  name          VARCHAR(64)     NOT NULL COMMENT '球队名称',
  short_name    VARCHAR(32)     NOT NULL COMMENT '球队简称',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='球队';

CREATE TABLE app_user (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  phone         VARCHAR(20)     NOT NULL COMMENT '登录手机号',
  password_hash VARCHAR(100)    NULL COMMENT '密码哈希',
  openid        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '微信openid',
  status        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-待审核;1-正常;2-已注销',
  last_login_at DATETIME(3)     NULL COMMENT '最近登录时间',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_phone (phone),
  UNIQUE KEY uk_user_openid (openid),
  KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号';

CREATE TABLE player (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id             BIGINT UNSIGNED NOT NULL COMMENT '球队ID',
  user_id             BIGINT UNSIGNED NOT NULL COMMENT '账号用户ID',
  name                VARCHAR(32)     NOT NULL COMMENT '球员姓名',
  jersey              SMALLINT UNSIGNED NULL COMMENT '球衣号',
  type                TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-外挂;1-正式',
  formal_jersey       SMALLINT UNSIGNED GENERATED ALWAYS AS (
                        CASE WHEN type = 1 THEN jersey ELSE NULL END
                      ) STORED COMMENT '正式球员号码；外挂为NULL',
  position            TINYINT UNSIGNED NOT NULL COMMENT '0-门将;1-后卫;2-中场;3-前锋',
  secondary_positions JSON            NULL COMMENT '副位置数组(JSON)',
  region              VARCHAR(64)     NULL COMMENT '地区',
  avatar_url          VARCHAR(512)    NULL COMMENT '头像URL',
  skill               TINYINT UNSIGNED NOT NULL DEFAULT 3 COMMENT '星级1-5',
  created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_player_team_user (team_id, user_id),
  UNIQUE KEY uk_player_team_formal_jersey (team_id, formal_jersey),
  KEY idx_player_team_type (team_id, type),
  KEY idx_player_team_jersey (team_id, jersey),
  KEY idx_player_team_position (team_id, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='球员档案';

CREATE TABLE app_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id     BIGINT UNSIGNED NOT NULL COMMENT '球队ID',
  code        VARCHAR(32)     NOT NULL COMMENT '角色编码',
  name        VARCHAR(64)     NOT NULL COMMENT '角色名称',
  is_default  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '0-自定义;1-默认',
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_team_code (team_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

CREATE TABLE permission (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  code        VARCHAR(64)     NOT NULL COMMENT '权限码',
  name        VARCHAR(64)     NOT NULL COMMENT '权限名称',
  resource    VARCHAR(32)     NOT NULL COMMENT '资源',
  action      VARCHAR(32)     NOT NULL COMMENT '动作',
  scope       VARCHAR(16)     NOT NULL COMMENT '范围',
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限点';

CREATE TABLE user_role (
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  role_id     BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (user_id, role_id),
  KEY idx_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联';

CREATE TABLE role_permission (
  role_id        BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
  permission_id  BIGINT UNSIGNED NOT NULL COMMENT '权限ID',
  created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (role_id, permission_id),
  KEY idx_role_permission_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联';

-- ---------------------------------------------------------------------------
-- 邀请
-- ---------------------------------------------------------------------------

CREATE TABLE invite_link (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id          BIGINT UNSIGNED NOT NULL COMMENT '球队ID',
  type             TINYINT UNSIGNED NOT NULL COMMENT '0-入队;1-比赛',
  token_hash       CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邀请token哈希',
  match_id         BIGINT UNSIGNED NULL COMMENT '比赛ID',
  inviter_user_id  BIGINT UNSIGNED NOT NULL COMMENT '邀请人用户ID',
  expires_at       DATETIME(3)     NOT NULL COMMENT '过期时间',
  max_uses         INT UNSIGNED    NULL COMMENT '最大使用次数',
  used_count       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '已使用次数',
  status           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-有效;1-已撤销;2-已过期',
  revoked_at       DATETIME(3)     NULL COMMENT '撤销时间',
  created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_invite_token_hash (token_hash),
  KEY idx_invite_team_status (team_id, status, expires_at),
  KEY idx_invite_match (match_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邀请链接';

CREATE TABLE invite_use (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  invite_id   BIGINT UNSIGNED NOT NULL COMMENT '邀请ID',
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '使用人用户ID',
  used_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '使用时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_invite_use (invite_id, user_id),
  KEY idx_invite_use_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邀请使用记录';

-- ---------------------------------------------------------------------------
-- 比赛与参与
-- ---------------------------------------------------------------------------

CREATE TABLE team_match (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id           BIGINT UNSIGNED NOT NULL COMMENT '球队ID',
  title             VARCHAR(128)    NOT NULL COMMENT '比赛名称',
  type              TINYINT UNSIGNED NOT NULL COMMENT '0-正式;1-队内;2-训练;3-友谊',
  format            VARCHAR(16)     NOT NULL DEFAULT '8人制' COMMENT '赛制',
  start_at          DATETIME(3)     NOT NULL COMMENT '开始时间',
  end_at            DATETIME(3)     NOT NULL COMMENT '结束时间',
  signup_deadline   DATETIME(3)     NOT NULL COMMENT '报名截止时间',
  cancel_lock       DATETIME(3)     NOT NULL COMMENT '锁定时间',
  venue             VARCHAR(128)    NOT NULL COMMENT '场地',
  competition       VARCHAR(64)     NULL COMMENT '赛事名称',
  season            CHAR(4)         NOT NULL COMMENT '赛季',
  round             SMALLINT UNSIGNED NULL COMMENT '轮次',
  home_team_name    VARCHAR(64)     NOT NULL COMMENT '主队名称',
  away_team_name    VARCHAR(64)     NOT NULL COMMENT '客队名称',
  our_side          TINYINT UNSIGNED NULL COMMENT '0-主队;1-客队',
  status            TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-报名中;1-报名截止;2-锁定;3-进行中;4-已结束;5-已延期;6-已取消',
  manual_status     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '0-自动;1-手动',
  capacity          SMALLINT UNSIGNED NOT NULL COMMENT '正式名额',
  guest_capacity    SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '外挂名额',
  waitlist_capacity SMALLINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '候补上限',
  include_guests    TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '0-不含外挂;1-含外挂',
  score_home        TINYINT UNSIGNED NULL COMMENT '主队比分',
  score_away        TINYINT UNSIGNED NULL COMMENT '客队比分',
  season_match_no   INT UNSIGNED    NOT NULL COMMENT '赛季场次编号',
  total_match_no    INT UNSIGNED    NOT NULL COMMENT '累计场次编号',
  version           INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '版本号',
  status_updated_at DATETIME(3)     NULL COMMENT '状态更新时间',
  status_updated_by BIGINT UNSIGNED NULL COMMENT '状态更新人用户ID',
  created_by        BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
  created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_total_no (team_id, total_match_no),
  UNIQUE KEY uk_match_season_no (team_id, season, season_match_no),
  KEY idx_match_team_status_start (team_id, status, start_at),
  KEY idx_match_team_start (team_id, start_at),
  KEY idx_match_team_type_season (team_id, type, season),
  KEY idx_match_team_venue (team_id, venue)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='比赛';

CREATE TABLE registration (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  match_id      BIGINT UNSIGNED NOT NULL COMMENT '比赛ID',
  player_id     BIGINT UNSIGNED NOT NULL COMMENT '球员ID',
  type          TINYINT UNSIGNED NOT NULL COMMENT '0-外挂;1-正式球员',
  status        TINYINT UNSIGNED NOT NULL COMMENT '0-已确认;1-候补;2-已取消;3-已移除',
  registered_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '报名时间',
  cancelled_at  DATETIME(3)     NULL COMMENT '取消时间',
  cancelled_by  BIGINT UNSIGNED NULL COMMENT '取消人用户ID',
  active        TINYINT UNSIGNED GENERATED ALWAYS AS (
                  CASE WHEN status IN (0, 1) THEN 1 ELSE 0 END
                ) STORED COMMENT '1-有效;0-无效',
  active_unique TINYINT UNSIGNED GENERATED ALWAYS AS (
                  CASE WHEN status IN (0, 1) THEN 1 ELSE NULL END
                ) STORED COMMENT '内部唯一键：1-有效;NULL-无效',
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_registration_active (match_id, player_id, active_unique),
  KEY idx_registration_match_pool (match_id, type, status, registered_at),
  KEY idx_registration_player (player_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报名记录';

CREATE TABLE attendance (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  match_id     BIGINT UNSIGNED NOT NULL COMMENT '比赛ID',
  player_id    BIGINT UNSIGNED NOT NULL COMMENT '球员ID',
  status       TINYINT UNSIGNED NOT NULL COMMENT '0-正常;1-缺勤;2-临时到场',
  source       TINYINT UNSIGNED NOT NULL COMMENT '0-自助;1-管理员;2-系统',
  recorded_by  BIGINT UNSIGNED NULL COMMENT '记录人用户ID',
  recorded_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录时间',
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_attendance_match_player (match_id, player_id),
  KEY idx_attendance_match_status (match_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考勤记录';

-- ---------------------------------------------------------------------------
-- 赛况
-- ---------------------------------------------------------------------------

CREATE TABLE match_event (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  match_id         BIGINT UNSIGNED NOT NULL COMMENT '比赛ID',
  team             TINYINT UNSIGNED NOT NULL COMMENT '0-主队;1-客队',
  type             TINYINT UNSIGNED NOT NULL COMMENT '0-进球;1-乌龙;2-换人;3-黄牌;4-红牌',
  player_id        BIGINT UNSIGNED NULL COMMENT '球员ID',
  assist_player_id BIGINT UNSIGNED NULL COMMENT '助攻球员ID',
  in_player_id     BIGINT UNSIGNED NULL COMMENT '换上球员ID',
  out_player_id    BIGINT UNSIGNED NULL COMMENT '换下球员ID',
  minute           TINYINT UNSIGNED NOT NULL COMMENT '比赛分钟',
  half             TINYINT UNSIGNED NOT NULL COMMENT '0-上半场;1-下半场',
  penalty          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '0-非点球;1-点球',
  created_by       BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
  created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_by       BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_at       DATETIME(3)     NULL COMMENT '删除时间',
  deleted_by       BIGINT UNSIGNED NULL COMMENT '删除人用户ID',
  version          INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '版本号',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  KEY idx_event_timeline (match_id, deleted_at, half, minute),
  KEY idx_event_player (player_id),
  KEY idx_event_assist (assist_player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='比赛事件';

CREATE TABLE event_history (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  event_id      BIGINT UNSIGNED NOT NULL COMMENT '事件ID',
  action        TINYINT UNSIGNED NOT NULL COMMENT '0-创建;1-修改;2-删除',
  before_json   JSON            NULL COMMENT '变更前快照',
  after_json    JSON            NULL COMMENT '变更后快照',
  actor_user_id BIGINT UNSIGNED NULL COMMENT '操作人用户ID',
  request_id    VARCHAR(64)     NULL COMMENT '请求ID',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  KEY idx_event_history_event (event_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事件变更历史';

-- ---------------------------------------------------------------------------
-- 阵容与分组
-- ---------------------------------------------------------------------------

CREATE TABLE formation_template (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id          BIGINT UNSIGNED NULL COMMENT '球队ID',
  name             VARCHAR(64)     NOT NULL COMMENT '模板名称',
  is_preset        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '0-自定义;1-预置',
  base_template_id BIGINT UNSIGNED NULL COMMENT '基础模板ID',
  created_by       BIGINT UNSIGNED NULL COMMENT '创建人用户ID',
  created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  KEY idx_formation_team_preset (team_id, is_preset)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阵型模板';

CREATE TABLE formation_slot (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  template_id  BIGINT UNSIGNED NOT NULL COMMENT '模板ID',
  slot_key     VARCHAR(16)     NOT NULL COMMENT '槽位标识',
  role         TINYINT UNSIGNED NOT NULL COMMENT '0-门将;1-后卫;2-中场;3-前锋',
  x            DECIMAL(5, 2)   NOT NULL COMMENT '横坐标0-100',
  y            DECIMAL(5, 2)   NOT NULL COMMENT '纵坐标0-100',
  sort_order   SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_formation_slot (template_id, slot_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阵型槽位';

CREATE TABLE lineup (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  match_id              BIGINT UNSIGNED NOT NULL COMMENT '比赛ID',
  side                  TINYINT UNSIGNED NOT NULL COMMENT '0-主队;1-客队',
  formation_template_id BIGINT UNSIGNED NOT NULL COMMENT '阵型模板ID',
  version               INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '版本号',
  updated_by            BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lineup_match_side (match_id, side)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阵容';

CREATE TABLE lineup_slot (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  lineup_id   BIGINT UNSIGNED NOT NULL COMMENT '阵容ID',
  slot_key    VARCHAR(16)     NOT NULL COMMENT '槽位标识',
  slot_type   TINYINT UNSIGNED NOT NULL COMMENT '0-首发;1-替补',
  player_id   BIGINT UNSIGNED NULL COMMENT '球员ID',
  sort_order  SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_lineup_slot_key (lineup_id, slot_key),
  UNIQUE KEY uk_lineup_slot_player (lineup_id, player_id),
  KEY idx_lineup_slot_type (lineup_id, slot_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阵容槽位';

CREATE TABLE match_grouping (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  match_id            BIGINT UNSIGNED NOT NULL COMMENT '比赛ID',
  manual_weight       DECIMAL(5, 2)   NOT NULL COMMENT '手动权重',
  auto_weight         DECIMAL(5, 2)   NOT NULL COMMENT '自动权重',
  roster_fingerprint  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '名单指纹',
  generated_at        DATETIME(3)     NOT NULL COMMENT '生成时间',
  saved_at            DATETIME(3)     NULL COMMENT '保存时间',
  created_by          BIGINT UNSIGNED NOT NULL COMMENT '生成人用户ID',
  created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_grouping_match (match_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='队内分组';

CREATE TABLE match_grouping_player (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  grouping_id  BIGINT UNSIGNED NOT NULL COMMENT '分组ID',
  player_id    BIGINT UNSIGNED NOT NULL COMMENT '球员ID',
  team         TINYINT UNSIGNED NOT NULL COMMENT '0-A队;1-B队',
  manual_score DECIMAL(3, 1)   NULL COMMENT '手动分',
  auto_score   DECIMAL(6, 2)   NULL COMMENT '自动分',
  final_score  DECIMAL(6, 2)   NULL COMMENT '综合分',
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_grouping_player (grouping_id, player_id),
  KEY idx_grouping_team (grouping_id, team)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分组成员';

-- ---------------------------------------------------------------------------
-- 统计与通知
-- ---------------------------------------------------------------------------

CREATE TABLE player_stats_summary (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id      BIGINT UNSIGNED NOT NULL COMMENT '球队ID',
  player_id    BIGINT UNSIGNED NOT NULL COMMENT '球员ID',
  season       CHAR(4)         NOT NULL COMMENT '赛季',
  appearances  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '出场次数',
  goals        INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '进球数',
  assists      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '助攻数',
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stats_team_player_season (team_id, player_id, season),
  KEY idx_stats_team_season (team_id, season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='球员统计汇总';

CREATE TABLE notification (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '接收人用户ID',
  type        TINYINT UNSIGNED NOT NULL COMMENT '0-报名成功;1-候补递补;2-比赛创建;3-报名截止;4-名单锁定;5-比赛开始;6-比赛结束;7-比赛延期;8-比赛取消;9-角色变更;10-审核通过;11-注销',
  title       VARCHAR(128)    NOT NULL COMMENT '通知标题',
  body        VARCHAR(512)    NOT NULL COMMENT '通知内容',
  ref_type    VARCHAR(32)     NULL COMMENT '关联类型',
  ref_id      BIGINT UNSIGNED NULL COMMENT '关联ID',
  read_at     DATETIME(3)     NULL COMMENT '已读时间',
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  KEY idx_notification_user_read (user_id, read_at, created_at),
  KEY idx_notification_ref (ref_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知';

CREATE TABLE audit_log (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id       BIGINT UNSIGNED NULL COMMENT '球队ID',
  actor_user_id BIGINT UNSIGNED NULL COMMENT '操作人用户ID',
  actor_name    VARCHAR(64)     NULL COMMENT '操作人名称',
  action        VARCHAR(64)     NOT NULL COMMENT '操作动作',
  target_type   VARCHAR(32)     NOT NULL COMMENT '对象类型',
  target_id     BIGINT UNSIGNED NULL COMMENT '对象ID',
  before_json   JSON            NULL COMMENT '变更前快照',
  after_json    JSON            NULL COMMENT '变更后快照',
  request_id    VARCHAR(64)     NULL COMMENT '请求ID',
  ip            VARCHAR(45)     NULL COMMENT '客户端IP',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  KEY idx_audit_team_created (team_id, created_at),
  KEY idx_audit_target (target_type, target_id),
  KEY idx_audit_actor (actor_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';

-- ---------------------------------------------------------------------------
-- 基础设施：Outbox / 消费去重 / 幂等 / 配置
-- ---------------------------------------------------------------------------

CREATE TABLE outbox_event (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  event_id       CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件UUID',
  team_id        BIGINT UNSIGNED NULL COMMENT '球队ID',
  aggregate_type VARCHAR(32)     NOT NULL COMMENT '聚合类型',
  aggregate_id   BIGINT UNSIGNED NOT NULL COMMENT '聚合ID',
  event_type     VARCHAR(64)     NOT NULL COMMENT '事件类型',
  payload_json   JSON            NOT NULL COMMENT '事件载荷',
  status         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-待发送;1-已发送;2-失败',
  attempts       SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数',
  next_retry_at  DATETIME(3)     NULL COMMENT '下次重试时间',
  lease_owner    VARCHAR(64)     NULL COMMENT '租约持有者（实例/线程标识）',
  lease_until    DATETIME(3)     NULL COMMENT '租约到期时间；过期后可被重新认领',
  last_error     VARCHAR(512)    NULL COMMENT '最近错误',
  created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  sent_at        DATETIME(3)     NULL COMMENT '发送时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_outbox_event_id (event_id),
  KEY idx_outbox_status_retry (status, next_retry_at),
  KEY idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事务性 Outbox 事件';

CREATE TABLE consumed_event (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  consumer_group  VARCHAR(64)     NOT NULL COMMENT '消费组',
  event_id        CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件UUID',
  topic           VARCHAR(128)    NOT NULL COMMENT 'Kafka主题',
  partition_no    INT UNSIGNED    NOT NULL COMMENT '分区号',
  offset_no       BIGINT UNSIGNED NOT NULL COMMENT '偏移量',
  processed_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '处理时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_consumed_group_event (consumer_group, event_id),
  UNIQUE KEY uk_consumed_offset (topic, consumer_group, partition_no, offset_no),
  KEY idx_consumed_processed (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Kafka 消费去重记录';

CREATE TABLE idempotency_key (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_id         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  endpoint        VARCHAR(128)    NOT NULL COMMENT '接口标识',
  idem_key        VARCHAR(64)     NOT NULL COMMENT '幂等键',
  request_hash    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求哈希',
  response_status SMALLINT UNSIGNED NULL COMMENT '响应状态码',
  response_body   JSON            NULL COMMENT '响应快照',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  expires_at      DATETIME(3)     NOT NULL COMMENT '过期时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_idempotency (user_id, endpoint, idem_key),
  KEY idx_idempotency_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='幂等键记录';

CREATE TABLE system_setting (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id       BIGINT UNSIGNED NOT NULL COMMENT '球队ID',
  setting_key   VARCHAR(64)     NOT NULL COMMENT '配置键',
  setting_value JSON            NOT NULL COMMENT '配置值',
  value_type    VARCHAR(16)     NOT NULL DEFAULT 'JSON' COMMENT '值类型',
  updated_by    BIGINT UNSIGNED NULL COMMENT '更新人用户ID',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_setting_team_key (team_id, setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置';
