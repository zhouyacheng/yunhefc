# 01 MP06 首页

Status: ready-for-human
Blocked by: none

## 目标

小程序新增首页并作为启动页，内容与视觉对齐 Web C01；报名类 CTA 只引导至网页版。

## 验收

- [x] `app.json` 首页为启动页，tabBar 为 首页 / 赛程 / 球队。
- [x] Hero：类型/状态、主客队与比分、时间场地、场次、正式/外挂/候补队列与进度、CTA、详情入口。
- [x] 最近比赛最多 3 场；二期能力 6 张占位卡；空态、加载、失败重试、下拉刷新。
- [x] CTA 文案/禁用/原因与 C01 状态机一致；报名/候补弹窗提示网页版并复制网址，无报名请求。
- [x] 未登录可浏览；登录/绑定成功与「先逛逛」回首页。
- [x] 时间按 Asia/Shanghai；赛程、详情、首页口径一致。
- [x] 文档 02/06/需求/CLAUDE.md/README 同步 P-75。

## 验证记录

- `pnpm --filter @yunhefc/miniprogram typecheck` 通过。
- `pnpm --filter @yunhefc/miniprogram test` 通过。
- `pnpm lint` 通过。
- 微信开发者工具与 iOS/Android 真机人工验收通过（2026-11-20）。

## Comments

- 2026-11-20 第一轮两轴审查：文档同步、`MeView` 删除、`sessionReady` 去重、CTA 枚举常量、时间格式化统一、下拉刷新保留内容、导航标题回到「首页」。
- 2026-11-20 第二轮两轴审查：`sessionReady` 登录/绑定后刷新缓存；空态下拉刷新失败保留「暂无比赛」；`RegistrationView` 收敛为 `status`；票据历史移入 `## Comments`；`CLAUDE.md` 下一步与 02 MP01 入口描述校正；Hero 胜方高亮在 06 §99 记录为有意修正。
- 2026-11-20 第三轮两轴审查：README 补 `home-view.ts`；06 §99 补 AUTH-10；02 §7.1 流程图改为「启动保持匿名，CTA 进入 MP01」；Hero 类型改为 C01 的绿色 eyebrow；401 统一清理会话缓存；`home-view` 抽出共用 core；`onUnload` 递增 `requestSeq`；比赛详情时段支持跨天。
- 2026-11-20 人工验收通过：首页启动、四 tab、Hero/最近比赛/二期能力、CTA 状态分支与网页版引导、空态与刷新。
