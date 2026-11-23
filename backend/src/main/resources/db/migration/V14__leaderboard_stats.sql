-- 排行榜（P-84）：汇总表新增乌龙、队内赛出场与胜场；胜率分母刻意入库。
ALTER TABLE player_stats_summary
    ADD COLUMN internal_appearances INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '队内赛出场且已分组场次（胜率分母）' AFTER assists,
    ADD COLUMN internal_wins INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '队内赛中本方获胜场次' AFTER internal_appearances,
    ADD COLUMN own_goals INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '乌龙球数（按赛季）' AFTER internal_wins;
