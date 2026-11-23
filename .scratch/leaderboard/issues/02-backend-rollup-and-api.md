# 02 后端：汇总扩展 + STA-03

Status: done
Blocked by: 01

## 目标

让 `player_stats_summary` 产出乌龙、队内赛出场与胜场，并提供 `GET /c/team/leaderboards`（STA-03，05 §3.14）。

## 口径

- 乌龙：`match_event.type=1`（OWN_GOAL），按 `player_id` 计，取消比赛不计。
- 队内赛：`team_match.type=1` 且 `status=FINISHED`（`MatchSnapshot.finished()`），已保存分组存在，球员 `countsAsAppearance()`（PRESENT/TEMP_PRESENT），最终比分已知。
- 归属：`SavedGroupingSnapshot.playerTeams` 0=A/HOME、1=B/AWAY；最终比分 `scoreHome/scoreAway`；赢 = 本方得分 > 对方。
- 比分未知（无计分事件且兜底为空）不计入分母。平局计入分母。
- 不统计外挂/注销：汇总可含外挂行，读取层按 `findPublicRoster` 过滤。

## 改动

1. **Flyway** `V14__leaderboard_stats.sql`：加 `internal_appearances`、`internal_wins`、`own_goals`（`INT UNSIGNED NOT NULL DEFAULT 0`，带中文 COMMENT）。
2. **实体与写入**：`stats/domain/PlayerStatsSummary.java` 加三字段；`stats/infrastructure/PlayerStatsSummaryMapper.java` upsert SQL 加三列；`stats/application/StatsSummaryWriter.java` 透传。
3. **端口**：`contracts/grouping/GroupingReadPort` 新增批量 `findSavedByMatchIds(Collection<Long>)`（实现 `grouping`，避免 N+1）；比分用现有 `contracts/match/MatchSummaryReadPort.findSummaries(matchIds, teamId)`（计分事件优先、兜底其次）。
4. **汇总任务**：`stats/application/StatsRollupApplicationService.java` 批取分组与最终比分，传给 `stats/domain/StatsAggregator.java`；聚合器只处理映射，不调用端口。`StatsAggregator` 增加 `ownGoal()` 计数与队内赛出场/胜场计数（`EventStatsSnapshot` 可加 `ownGoal()` 判定）。
5. **查询**：`stats/application/StatsQueryService.java` 组装榜单（`scope=season` 单赛季 / `scope=all` 按 `player_id` 求和），公开名单补 0，派生 `winRate`（分母 0 → null，1 位小数）、`goalsPerMatch`/`assistsPerMatch`（2 位小数）；复用 Redis 统计缓存并按赛季刷新。
6. **接口**：新增 `stats/controller/TeamLeaderboardsController.java` + DTO `Leaderboard`/`LeaderboardEntry`；`common/SecurityConfig.java` 与 `common/security/PendingAccountFilter.java` 加 `/c/team/leaderboards` 公开路径。
7. **测试**：`StatsAggregator` 单测覆盖全部边界（未分组、平局、比分未知、取消/延期、临时到场、外挂）；`StatsRollupIT` 覆盖三列重算与幂等；接口 IT 覆盖 `season`/`scope`、零分母、非法参数 `40001`、公开访问（含 PENDING）。

## 验收

- 空库 Flyway 迁移成功；`mvn verify` 通过。
- 任一场队内赛录入分组与比分后 ≤1 分钟，STA-03 对应球员 `internalAppearances/internalWins` 正确。
- 未分组队内赛与训练赛/对外比赛不进胜率分母。
- 接口契约与 05 §3.14、`LeaderboardEntry` 一致。
