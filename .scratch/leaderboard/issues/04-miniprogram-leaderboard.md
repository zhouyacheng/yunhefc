# 04 小程序：MP09 排行榜

Status: done
Blocked by: 01, 02

## 目标

新增 MP09 排行榜页，与 C10 同口径；入口在 MP05 球队页页头。

## 改动

1. **注册**：`frontend/apps/miniprogram/miniprogram/app.json` 的 `pages` 加 `pages/leaderboard/index`（非 tab）。
2. **页面**：新增 `pages/leaderboard/{index.ts,index.wxml,index.wxss,index.json}`，结构参考 `pages/team`；`onShow` 加载，赛季下拉 + 「生涯累计」、7 榜切换（`chip-row` 或分段）。
3. **请求与类型**：`utils/api-client.ts` 加 `fetchLeaderboards({ season, scope })`（GET，无需写操作辅助）；`types/api.ts` 加 `Leaderboard`/`LeaderboardEntry`。
4. **视图逻辑**：新建 `utils/leaderboard-view.ts`（与 Web `ranking.ts` 同构，不跨端引包）并配 Vitest：比较器、标准竞赛排名、零分母、展示格式化。
5. **入口**：`pages/team/index.wxml/ts` 页头加「排行榜」，沿用当前赛季；`utils/home-view.ts` 的 `HOME_FEATURES` 把「排行榜与身价」改为「AI 身价」，同步 `tests/home-view.test.ts` 期望值。

## 验收

- 未登录可浏览；7 榜与赛季/生涯切换正确；开发者工具与真机无报错。
- 与 Web 同一组数据得到相同的名次与顺序（同一组测试用例）。
- 首页二期能力不再出现排行榜卡片。
