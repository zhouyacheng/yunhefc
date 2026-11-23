# 03 Web C 端：C10 排行榜

Status: done
Blocked by: 01, 02

## 目标

新增 C10 `/leaderboards`，按 02 §5.11 展示 7 个榜单，入口在 C04 球队页页头。

## 改动

1. **类型**：`frontend/packages/shared/src/auth.ts` 或新文件加 `Leaderboard`/`LeaderboardEntry`（与 05 §3.14 一致）。
2. **纯逻辑**：新建 `frontend/apps/web/src/features/leaderboard/query.ts`（解析/序列化 `season`、`scope`、`board`，复用 `features/team/query.ts` 的 `mergeSeasonOptions` 与 `currentShanghaiSeason`）与 `ranking.ts`（7 榜比较器、标准竞赛排名 1/1/3、零分母过滤、原始分数比较）；Vitest 覆盖并列、次级指标、号码兜底、零分母。
3. **页面**：`frontend/apps/web/src/pages/LeaderboardPage.vue`；`src/router/index.ts` 在 `AppShell` 下注册 `leaderboards`（公开，不要求登录）。
4. **入口**：`pages/TeamPage.vue` 页头加「排行榜」链接并带上当前 `season`；`pages/HomePage.vue` 的 `FEATURES` 把「排行榜与身价」改为「AI 身价」。
5. **请求**：走现有 `src/api/client.ts` 的 `api.get<Leaderboard>('/team/leaderboards', { query: { season, scope } })`，不新增请求封装。
6. **E2E**：`frontend/e2e/web-leaderboard.spec.ts`（拦截 STA-03 + MAT-14，覆盖默认出场榜、赛季/生涯切换、胜率文案、无 console error）；`web-team.spec.ts` 补页头入口断言。

## 验收

- 未登录可访问；7 榜切换不重新请求；URL `?season=&scope=all&board=` 可直链恢复。
- 名次 1、1、3 与 02 §5.11 完全一致；零分母球员不出现在场均/胜率榜。
- 首页二期能力不再出现排行榜卡片；球队页入口可用。
