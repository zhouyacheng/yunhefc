-- 分组改为直接使用球员星级（P-72），伪删除不再读取的分组权重配置。
SET time_zone = '+08:00';

UPDATE system_setting
SET is_deleted = 1, updated_at = NOW(3)
WHERE is_deleted = 0
  AND setting_key IN (
    'grouping.manualWeight',
    'grouping.autoWeight',
    'grouping.appearanceWeight',
    'grouping.goalWeight',
    'grouping.assistWeight',
    'grouping.minAppearances'
);

ALTER TABLE match_grouping
    MODIFY COLUMN manual_weight DECIMAL(5, 2) NOT NULL COMMENT '历史权重快照；P-72 起新生成固定 100.00',
    MODIFY COLUMN auto_weight DECIMAL(5, 2) NOT NULL COMMENT '历史权重快照；P-72 起新生成固定 0.00';

ALTER TABLE match_grouping_player
    MODIFY COLUMN manual_score DECIMAL(3, 2) NULL COMMENT 'P-72 起新生成写球员星级快照（1.00-5.00）',
    MODIFY COLUMN auto_score DECIMAL(6, 2) NULL COMMENT 'P-72 起新生成恒为 NULL；历史行保留自动分',
    MODIFY COLUMN final_score DECIMAL(6, 2) NULL COMMENT 'P-72 起新生成写球员星级快照（1.00-5.00）';
