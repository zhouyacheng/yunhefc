-- 队内活动与账本（P-92~P-94）：四张业务表，DDL 与 scripts/sql/schema.sql 逐字一致。
-- P-99：notification.type 列注释补 12-活动创建、13-活动取消，保持空库与 schema.sql 一致。

CREATE TABLE team_activity (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id            BIGINT UNSIGNED NOT NULL COMMENT '球队ID',
  title              VARCHAR(128)    NOT NULL COMMENT '活动名称',
  type               TINYINT UNSIGNED NOT NULL COMMENT '0-聚餐;1-年会;2-其他',
  start_at           DATETIME(3)     NOT NULL COMMENT '活动开始时间',
  end_at             DATETIME(3)     NULL COMMENT '活动结束时间',
  signup_deadline    DATETIME(3)     NOT NULL COMMENT '报名截止时间',
  capacity           SMALLINT UNSIGNED NULL COMMENT '总人数上限;只计报名人数,不含家属;NULL为不限',
  cost_per_person    DECIMAL(10,2)   NULL COMMENT '人均消费;单位元;仅展示,不自动摊分',
  location           VARCHAR(128)    NOT NULL COMMENT '活动地址',
  location_image_key VARCHAR(512)    NULL COMMENT '活动地点图片OSS对象键;不含签名参数',
  description        VARCHAR(1000)   NULL COMMENT '活动说明',
  status             TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-报名中;1-报名截止;2-已结束;3-已取消',
  cancel_reason      VARCHAR(255)    NULL COMMENT '取消原因',
  version            INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '版本号',
  created_by         BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
  created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  KEY idx_activity_team_start (team_id, start_at),
  KEY idx_activity_team_status_start (team_id, status, start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='队内活动';

CREATE TABLE activity_signup (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  activity_id   BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
  player_id     BIGINT UNSIGNED NOT NULL COMMENT '球员ID',
  with_family   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-不带家属;1-携带家属',
  family_note   VARCHAR(255)    NULL COMMENT '家属备注',
  status        TINYINT UNSIGNED NOT NULL COMMENT '0-已报名;1-已取消',
  signed_up_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '报名时间',
  cancelled_at  DATETIME(3)     NULL COMMENT '取消时间',
  cancelled_by  BIGINT UNSIGNED NULL COMMENT '取消人用户ID',
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_signup (activity_id, player_id),
  KEY idx_activity_signup_activity (activity_id, status, signed_up_at),
  KEY idx_activity_signup_player (player_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动报名';

CREATE TABLE ledger_category (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id     BIGINT UNSIGNED NOT NULL COMMENT '球队ID',
  name        VARCHAR(32)     NOT NULL COMMENT '分类名称',
  direction   TINYINT UNSIGNED NULL COMMENT '0-收入;1-支出;NULL-通用',
  sort_no     SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序号;升序展示',
  is_active   TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1-启用;0-停用;停用后不出现在新建与筛选下拉',
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ledger_category_name (team_id, name),
  KEY idx_ledger_category_team (team_id, direction, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账本分类';

CREATE TABLE team_ledger_entry (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id      BIGINT UNSIGNED NOT NULL COMMENT '球队ID',
  type         TINYINT UNSIGNED NOT NULL COMMENT '0-收入;1-支出',
  category_id  BIGINT UNSIGNED NOT NULL COMMENT '分类ID;关联ledger_category',
  amount       DECIMAL(10,2)   NOT NULL COMMENT '金额;正数;单位元;两位小数',
  description  VARCHAR(255)    NOT NULL COMMENT '摘要',
  occurred_at  DATETIME(3)     NOT NULL COMMENT '发生时间',
  player_id    BIGINT UNSIGNED NULL COMMENT '归属球员ID',
  activity_id  BIGINT UNSIGNED NULL COMMENT '关联活动ID',
  version      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '版本号',
  created_by   BIGINT UNSIGNED NOT NULL COMMENT '创建人用户ID',
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  KEY idx_ledger_team_occurred (team_id, occurred_at, id),
  KEY idx_ledger_team_type_occurred (team_id, type, occurred_at),
  KEY idx_ledger_team_category (team_id, category_id),
  KEY idx_ledger_team_activity (team_id, activity_id),
  KEY idx_ledger_team_player (team_id, player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='球队账本流水';

ALTER TABLE notification
  MODIFY COLUMN type TINYINT UNSIGNED NOT NULL COMMENT '0-报名成功;1-候补递补;2-比赛创建;3-报名截止;4-名单锁定;5-比赛开始;6-比赛结束;7-比赛延期;8-比赛取消;9-角色变更;10-审核通过;11-注销;12-活动创建;13-活动取消';
