-- 取消锁定规则由开赛当天 10:00 调整为 01:00（Asia/Shanghai，P-51）。
-- start_at 以 UTC DATETIME(3) 存储：先 +8h 得到上海墙钟，取日期后置 01:00，再 -8h 转回 UTC。
-- 仅回填尚未开赛且未伪删除、非进行中/已结束的比赛；已结束/进行中行保持原值。
-- 守卫 < start_at：跳过 00:00–01:00 区间的非法历史行，避免破坏 cancel_lock < start_at。

UPDATE team_match
SET cancel_lock = DATE_SUB(
        DATE_ADD(DATE(DATE_ADD(start_at, INTERVAL 8 HOUR)), INTERVAL 1 HOUR),
        INTERVAL 8 HOUR)
WHERE is_deleted = 0
  AND status NOT IN (3, 4)
  AND start_at > UTC_TIMESTAMP(3)
  AND cancel_lock <> DATE_SUB(
        DATE_ADD(DATE(DATE_ADD(start_at, INTERVAL 8 HOUR)), INTERVAL 1 HOUR),
        INTERVAL 8 HOUR)
  AND DATE_SUB(
        DATE_ADD(DATE(DATE_ADD(start_at, INTERVAL 8 HOUR)), INTERVAL 1 HOUR),
        INTERVAL 8 HOUR) < start_at;
