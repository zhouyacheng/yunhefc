# P-75 小程序首页（MP06）

## 事实源

- 需求：`docs/云河FC-需求文档.md` v1.28
- 页面：`docs/design/02.页面结构与交互设计.md` §4.1.1、§4.3、§5.8、§5.9、§7.1
- 决策：`docs/design/06.设计方案评审结果.md` §99（P-75）
- Web C01：`frontend/apps/web/src/pages/HomePage.vue`、`components/MatchCard.vue`、`features/home/cta.ts`

## 范围

- 新增 MP06 首页，作为小程序启动页与 tabBar 首项（首页 / 赛程 / 球队）。
- 镜像 C01：Hero（最近一场比赛、报名队列）、最近比赛（最多 3 场）、二期能力、空态、加载/失败重试、下拉刷新。
- 报名 CTA 文案与状态分支同 C01；因 P-74 边界，报名/候补动作只提示前往网页版并支持复制网址。
- 不展示管理后台入口与 Web 全站页脚；不实现报名、通知、分享、订阅消息。
- Hero 胜方高亮为有意修正 Web 缺失的 `.home-hero__teams .team-name--winner` 规则（见 06 §99）。

## 票序

1. `issues/01-home-page.md` —— MP06 首页实现（已实现，待人工验收）。

## 约束

- 不修改后端、DB、API 契约；仅复用 MAT-02 / MAT-03。
- 小程序不引 `@yunhefc/shared`，纯函数本地移植，Vitest 锁定口径。
- 验证：`pnpm --filter @yunhefc/miniprogram typecheck/test`、`pnpm lint`；微信开发者工具真机/模拟器验收。
