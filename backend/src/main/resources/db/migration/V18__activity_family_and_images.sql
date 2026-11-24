-- P-105：活动级「允许携带家属」开关。
-- P-106：活动地点图片改为 activity_image 子表（最多 10 张），存量单图回填为 sort_no=0 后删除旧列。
-- 业务表 31 -> 32，业务索引 72 -> 73。

ALTER TABLE team_activity
  ADD COLUMN family_allowed TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-不允许携带家属;1-允许携带家属' AFTER capacity;

CREATE TABLE activity_image (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  activity_id BIGINT UNSIGNED NOT NULL COMMENT '活动ID',
  image_key   VARCHAR(512)    NOT NULL COMMENT '活动地点图片OSS对象键;不含签名参数',
  sort_no     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序号;从0开始;升序展示',
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  is_deleted  TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除',
  PRIMARY KEY (id),
  KEY idx_activity_image_activity (activity_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动图片';

INSERT INTO activity_image (activity_id, image_key, sort_no, created_at, updated_at)
SELECT id, location_image_key, 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM team_activity
WHERE location_image_key IS NOT NULL AND location_image_key <> '';

ALTER TABLE team_activity
  DROP COLUMN location_image_key;
