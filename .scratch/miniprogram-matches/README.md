# P-77 小程序比赛页对齐 C02/C03

## 事实源

- 需求：`docs/云河FC-需求文档.md` v1.30
- 页面：`docs/design/02.页面结构与交互设计.md` §4.1.1、§5.8、§5.9
- 决策：`docs/design/06.设计方案评审结果.md` §101（P-77）
- Web C02：`frontend/apps/web/src/pages/SchedulePage.vue`、`ScheduleFilters.vue`、`MatchCard.vue`、`features/schedule/query.ts`
- Web C03：`frontend/apps/web/src/pages/MatchDetailPage.vue`（头部与事件）

## 范围

- MP03 比赛列表：移除即将开始/历史切换，默认全部比赛按开赛时间降序；类型/地点/状态/月份/自定义时间筛选 + 重置；300ms 合并生效、地点回车立即生效；月份默认预填当月但首屏不筛选；抽屉保持打开、遮罩关闭；富卡片（结果标签、场次、类型/状态 pill、三栏报名人数）；下拉刷新 + 上拉加载、总数、错误重试、空态重置；从详情返回不重置分页。
- MP04 比赛详情：C03 头部（类型/状态 pill、结果标签、主客队/推导比分/胜方、取消/延期横幅、日期/时段/截止/锁定/场次/场地元信息）+ 单列事件时间线；修正事件球员字段与中文标签。
- 不提供报名/取消报名/签到/录事件/邀请/管理员状态修改。
- 不改后端/DB/API/权限码/错误码。

## 票序

1. `issues/01-match-pages.md` —— MP03/MP04 对齐实现。

## 约束

- 小程序不引入 `@yunhefc/shared`，展示映射与筛选模型在 `utils/` 本地移植，用 Vitest 锁定与 Web 的口径。
- MP03/MP06 共用 WXML 模板与全局卡片样式，避免重复与漂移。
- 验证：`pnpm --filter @yunhefc/miniprogram typecheck/test`、`pnpm lint`；微信开发者工具与真机验收。
