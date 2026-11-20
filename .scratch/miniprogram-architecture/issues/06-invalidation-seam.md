# 06 用显式 invalidation 取代隐藏 dirty 布尔

Status: needs-triage
Blocked by: none

## 目标

把跨页刷新从模块级布尔改成命名的失效资源；写入方显式声明失效，列表方显式订阅。

## 现状与摩擦

- `utils/registration-signal.ts` 只有一个模块级布尔；首页报名、详情报名/取消、我的进入详情都打标，`profile.onShow` 消费后重载。
- 任何页面的写操作都能通过隐藏全局影响 profile；调用点不可追踪，该交互无测试。
- `utils/paged-feed.ts` 本身是深 module（generation、分页守卫、210 行测试），问题在失效协议，不在分页 Feed。

## 深化方向

- 一个 invalidation module：`invalidate('my-registrations')` 与订阅/消费接口；资源名是接口的一部分。
- profile 显式订阅 `my-registrations`，通知列表可复用 `my-notifications`。
- 保持小程序无全局状态库；模块级注册表可以，但语义必须显式。

## 删除测试

删掉 invalidation module，失效逻辑会回到每个写入页面并继续用隐藏布尔——复杂度集中，seam 有保留价值。

## 收益

- 失效原因可追踪。
- 订阅协议可测。
- 新页面不共享隐藏状态。
- 现有调用点语义不变。

## 范围

- 改 `utils/registration-signal.ts`（或替换为 invalidation module）、`pages/home/index.ts`、`pages/match-detail/actions.ts`、`pages/profile/index.ts` 与测试。

## 不做

- 不引入事件总线库或状态管理库。
- 不改分页 Feed 的 generation 逻辑。
- 不改页面导航与 tab 结构。

## 验收

- [ ] 跨页失效以命名资源表达，不再暴露裸布尔。
- [ ] profile 显式订阅并在 `onShow` 消费。
- [ ] 新增测试覆盖标记、消费一次即复位、未订阅资源不受影响。
- [ ] 首页报名、详情报名/取消后返回我的仍会重载。
- [ ] `typecheck`、`test`、`pnpm lint` 通过。

## 风险

- 这是 Speculative 候选：如果 01/02 落地后跨页协议变简单，可以直接关闭本票，不为抽象而抽象。

## Comments

- 2026-11-20 架构扫描开票（Speculative）。
