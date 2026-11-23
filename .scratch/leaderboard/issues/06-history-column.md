# 06 排行榜生涯/历史累计固定列

Status: ready-for-human
Blocked by: —

## 目标

C10 与 MP09 单赛季窗口每行展示三组带标签数据：主指标为所选赛季（当前自然年标「当前赛季」），行内固定展示「历史累计」与「生涯累计」。三组都只展示当前榜单主指标（如出场榜只显示出场数）。历史累计 = 早于当前 Asia/Shanghai 自然年（不含当前赛季与未来赛季）；生涯累计 = 当前赛季 + 历史累计。累计数值不随所选赛季或榜单切换变化；`scope=all` 时隐藏。

## 口径

- 生涯累计：`season <= 当前自然年`；历史累计：`season < 当前自然年`。
- 未来赛季与非 4 位赛季不计入；历史累计不含当前赛季。
- 字段：三组均只展示当前榜单主指标（出场数/进球数/助攻数/场均/胜率/乌龙）；接口仍返回 9 项累计字段，UI 不展开。
- 无对应行补 0；胜率分母为 0 为 `null`，场均无出场为 `0.00`。
- 不新增表、列、接口、权限码、错误码或 Flyway。

## 改动

1. **契约**：05 STA-03 与 `LeaderboardEntry` 增加 `career: LeaderboardTotals|null`、`history: LeaderboardTotals|null`；`scope=season` 恒为非空对象，`scope=all` 均为 `null`。
2. **后端**：`LeaderboardDtos` 增加 `LeaderboardTotals`；`StatsQueryService` 抽出 `aggregateSeasons` 与 `metricsUpTo`（`inclusive` 区分生涯/历史），用 `Cumulative` 成对传递两组累计，复用 `seasonStats` 缓存与 `Metrics.plus`。
3. **共享**：`packages/shared/src/leaderboard.ts` 增加 `leaderboardMainLabel`（窗口标题）与 `isZeroOrPlaceholder`（零值/占位符弱化判定）；Web 行视图由 `features/leaderboard/rows.ts` 统一预计算。
4. **Web**：`LeaderboardPage.vue` 改为单个表格式容器 + 共享表头（名次/球员/当前窗口/历史累计/生涯累计）；当前窗口绿色高亮、历史累计弱化、生涯累计加粗；≤900px 表头保留三列统计标签、行下统计条对齐；E2E 更新 fixture 并断言三列与 `scope=all` 隐藏。
5. **小程序**：`types/api.ts`、`utils/leaderboard-view.ts`、`pages/leaderboard/*` 同步；共享表头 + 行下三列统计条，行视图预计算 `careerText/historyText` 与零值标记。
6. **文档/原型**：需求 v1.36、01 v0.26、02 v0.63、05 v0.58、06 v2.06、`CONTEXT.md`、`CLAUDE.md`、`prototype/assets/js/public.js`。

## 验收

- `LeaderboardIT`：生涯累计=当前+历史、历史累计排除当前年与未来年、非 4 位赛季不计入、`scope=all` 两者为 `null`、无对应行零填充。
- Web/小程序：单赛季每行三组均只展示当前榜单主指标；切榜展示随榜单变化且不重新请求；`scope=all` 不渲染。
- `mvn verify`、共享/Web/小程序 Vitest、`pnpm typecheck`、Web E2E 全绿；`prototype` 脚本 `node --check` 通过。
- 计数不变：96 接口、28 权限码、124 需求 ID、27 表、0 业务外键。
- 小程序开发者工具/真机验收待执行。
