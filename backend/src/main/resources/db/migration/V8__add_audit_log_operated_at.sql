-- 操作时间来自 AUDIT_RECORDED 生产端写入的 operateAt；created_at 仍为消费入库时间。
-- 历史行按创建时间回填；旧消息缺失该字段时消费者落 NULL。

ALTER TABLE audit_log
  ADD COLUMN operated_at DATETIME(3) NULL
    COMMENT '操作时间（Kafka 生产时写入；旧数据回填为创建时间）'
    AFTER created_at;

UPDATE audit_log SET operated_at = created_at WHERE operated_at IS NULL;

ALTER TABLE audit_log ADD KEY idx_audit_team_operated (team_id, operated_at);
