-- 内容（动态与相册，P-142）：四张业务表，DDL 与 scripts/sql/schema.sql 逐字一致。
-- 业务表 32 -> 36，业务索引 73 -> 80；notification.type 列注释补 14-内容举报。

CREATE TABLE content_album (
  id             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id        BIGINT UNSIGNED  NOT NULL COMMENT '球队ID',
  title          VARCHAR(64)      NOT NULL COMMENT '相册名称;去除首尾空白后1-64字符',
  match_id       BIGINT UNSIGNED  NULL COMMENT '关联比赛ID;与activity_id至多一个非空',
  activity_id    BIGINT UNSIGNED  NULL COMMENT '关联活动ID;与match_id至多一个非空',
  cover_photo_id BIGINT UNSIGNED  NULL COMMENT '封面照片ID;为空取相册内最早照片;封面删除后置空',
  created_by     BIGINT UNSIGNED  NOT NULL COMMENT '创建人用户ID',
  created_at     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  KEY idx_content_album_team_created (team_id, created_at, id),
  KEY idx_content_album_match (match_id, id),
  KEY idx_content_album_activity (activity_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='相册';

CREATE TABLE content_album_photo (
  id          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  album_id    BIGINT UNSIGNED  NOT NULL COMMENT '相册ID',
  object_key  VARCHAR(512)     NOT NULL COMMENT '照片OSS对象键;不含签名参数',
  uploaded_by BIGINT UNSIGNED  NOT NULL COMMENT '上传人用户ID',
  created_at  DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at  DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  KEY idx_content_photo_album (album_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='相册照片';

CREATE TABLE content_post (
  id             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  team_id        BIGINT UNSIGNED  NOT NULL COMMENT '球队ID',
  author_user_id BIGINT UNSIGNED  NOT NULL COMMENT '作者用户ID',
  content        VARCHAR(2000)    NOT NULL COMMENT '正文;去除首尾空白后1-2000字符;允许换行',
  album_id       BIGINT UNSIGNED  NULL COMMENT '引用相册ID;为空表示纯文字',
  created_at     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  KEY idx_content_post_team_created (team_id, created_at, id),
  KEY idx_content_post_album (album_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='队内动态';

CREATE TABLE content_post_like (
  id         BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  post_id    BIGINT UNSIGNED  NOT NULL COMMENT '动态ID',
  user_id    BIGINT UNSIGNED  NOT NULL COMMENT '点赞用户ID',
  created_at DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_post_like (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态点赞';

ALTER TABLE notification
  MODIFY COLUMN type TINYINT UNSIGNED NOT NULL COMMENT '0-报名成功;1-候补递补;2-比赛创建;3-报名截止;4-名单锁定;5-比赛开始;6-比赛结束;7-比赛延期;8-比赛取消;9-角色变更;10-审核通过;11-注销;12-活动创建;13-活动取消;14-内容举报';
