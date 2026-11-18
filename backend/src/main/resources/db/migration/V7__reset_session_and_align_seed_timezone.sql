-- V7：重置 Flyway 连接会话，并把新库种子时间戳补成 +08:00 墙钟。
--
-- V1 在整轮迁移复用的连接上执行 SET time_zone='+00:00'，V3/V4 的种子行
-- DEFAULT CURRENT_TIMESTAMP(3) 因此写成 UTC 墙钟；连接归还 Hikari 后也可能仍是 UTC。
-- 这里先把会话重置为 +08:00，再只对“刚写入的 UTC 种子”做 +8h 回填。
--
-- 守卫（双重判断，60 分钟窗口）：
--   1) V3 的 installed_on 接近 NOW()：说明这是本次 Flyway 运行刚执行的新库，而非历史库；
--   2) team.id=1 的 created_at 接近 UTC_TIMESTAMP：说明种子确实按 UTC 墙钟写入（V1 泄漏）。
-- 两者同时成立才回填。历史库、已是 +08 的种子、以及执行过一次性 +8h 脚本的旧数据都不会命中，
-- 避免已有库二次偏移；Flyway 元数据不受一次性数据迁移影响，也消除了“旧种子被 +8h 后恰好落进窗口”的边界。

SET time_zone = '+08:00';

SET @fresh_utc_seed = (
    SELECT COUNT(*) = 1
    FROM team t
    JOIN flyway_schema_history h ON h.version = '3'
    WHERE t.id = 1
      AND ABS(TIMESTAMPDIFF(MINUTE, t.created_at, UTC_TIMESTAMP(3))) <= 60
      AND ABS(TIMESTAMPDIFF(MINUTE, h.installed_on, NOW(3))) <= 60
);

UPDATE team SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR)
WHERE @fresh_utc_seed = 1;

UPDATE app_role SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                    updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR)
WHERE @fresh_utc_seed = 1;

UPDATE permission SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR)
WHERE @fresh_utc_seed = 1;

UPDATE role_permission SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR)
WHERE @fresh_utc_seed = 1;

UPDATE system_setting SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                          updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR)
WHERE @fresh_utc_seed = 1;

UPDATE formation_template SET created_at = DATE_ADD(created_at, INTERVAL 8 HOUR),
                              updated_at = DATE_ADD(updated_at, INTERVAL 8 HOUR)
WHERE @fresh_utc_seed = 1;
