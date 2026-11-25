-- P-121：活动人数上限必填，默认 50，取消无上限。
UPDATE team_activity SET capacity = 50 WHERE capacity IS NULL;

ALTER TABLE team_activity
  MODIFY COLUMN capacity SMALLINT UNSIGNED NOT NULL DEFAULT 50
    COMMENT '总人数上限;只计报名人数,不含家属;默认50';
