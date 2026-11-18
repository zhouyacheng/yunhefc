ALTER TABLE player
  MODIFY COLUMN skill DECIMAL(3, 2) NOT NULL DEFAULT 3.00
    COMMENT '星级1.00-5.00（两位小数）';

ALTER TABLE match_grouping_player
  MODIFY COLUMN manual_score DECIMAL(3, 2) NULL
    COMMENT '手动分（按半星档向下量化，1.00-5.00）';
