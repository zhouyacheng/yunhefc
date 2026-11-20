# 06 — stats 汇总切片

**What to build:** Quartz 每 10 分钟把本赛季未取消比赛的考勤与事件汇总成球员出场/进球/助攻，写入 `player_stats_summary` 并刷新 Redis 缓存；球队页与 M08 自动分读取同一口径，允许最多 10 分钟延迟。

**Blocked by:** 03 — M05 管理员考勤与自动缺勤；04 — M06 事件核心（球员）；05 — M06 管理员事件与比分.

**Status:** resolved

- [ ] Quartz 任务每 10 分钟全量扫描本赛季未取消比赛；通过各域只读端口读取考勤、事件、比赛、球员，不直读业务表；使用 `QuartzJobBean` + Trigger，禁止 `@Scheduled`。
- [ ] 口径：出场 = `PRESENT` 或 `TEMP_PRESENT`；进球 = 未删除 `GOAL`（`OWN_GOAL` 不计个人进球）；助攻 = 未删除 `GOAL` 的 `assist_player_id`；取消比赛不计任何统计；赛季取比赛赛季字段，空则开赛年份。
- [ ] 按 `(team_id,player_id,season)` 批量 upsert；失败保留上一次结果并告警；重复执行按球员主键覆盖，幂等。
- [ ] 汇总后刷新 Redis 统计缓存；球队页/球员页缓存未命中回源汇总表；允许最多 10 分钟延迟。
- [ ] `STA-01 GET /c/team/stats` 保持现有契约：缺省当前赛季，显式 season 必须四位数字（否则 40001），无汇总行补 0，`updatedAt` 可为 null。
- [ ] 提供分组可用的只读接口 `seasonStats(teamId, season)`；stats 只读依赖各域端口，仅 `query` 与 `grouping` 可依赖 stats 只读接口。
- [ ] 单元测试汇总口径（临时到场、乌龙、取消、赛季回退）；IT 覆盖任务执行、upsert 幂等、失败保留上次结果、缓存刷新、STA-01 读取；断言 `JobDetail/Trigger`。
- [ ] 不改 `schema.sql`、不新增 Flyway；`mvn verify` 全绿。

## Result

- 交付：`7c36761`。
- 验证：批次收尾 `mvn verify` 全绿（107 单元 + 250 集成）；前端 lint/typecheck 通过；Playwright 14 条黄金路径通过（2026-11-11）。
