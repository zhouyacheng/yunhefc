# 06 用显式 invalidation 取代隐藏 dirty 布尔

Status: resolved
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

- [x] 跨页失效以命名资源表达，不再暴露裸布尔。
- [x] profile 显式订阅并在 `onShow` 消费。
- [x] 新增测试覆盖标记、消费一次即复位、未订阅资源不受影响。
- [x] 首页报名、详情报名/取消后返回我的仍会重载（6 个写入调用点与 profile 消费点保持不变；开发者工具/真机走查待补）。
- [x] `typecheck`、`test`、`pnpm lint` 通过。

## 风险

- 这是 Speculative 候选：如果 01/02 落地后跨页协议变简单，可以直接关闭本票，不为抽象而抽象。

## Comments

- 2026-11-20 架构扫描开票（Speculative）。
- 2026-11-21 实施完成（TDD）：
  - 新增 `utils/invalidation.ts`：`InvalidationResource` 命名资源（`my-registrations`、`my-notifications`），写入方 `invalidate(resource)`，订阅方 `subscribe(resource).consume()`（消费一次即复位）。模块级 `Set` 取代隐藏布尔，资源名成为接口的一部分。
  - `pages/profile/index.ts` 模块级显式订阅 `my-registrations`，`bootstrap` 中 `consume()` 决定是否重载报名列表；通知点击与进入详情改用 `invalidate('my-registrations')`。`pages/home/index.ts` 报名成功、`pages/match-detail/actions.ts` 报名/取消成功同步改为命名失效。
  - 删除 `utils/registration-signal.ts` 与 `tests/registration-signal.test.ts`（经用户确认）；新增 `tests/invalidation.test.ts` 6 例；`tests/match-actions.test.ts` 改用订阅句柄断言。
  - `my-notifications` 只登记资源名（隔离测试需要第二个资源，供后续通知列表复用），当前无生产写入方/订阅方。
  - 验证：`pnpm --filter @yunhefc/miniprogram typecheck`、`test`（27 文件 / 272 用例）、`pnpm lint`、`prettier --check`（改动文件）通过；开发者工具/真机走查待补。
- 2026-11-21 code-review（Standards / Spec 两轴）：
  - 已改：恢复 profile 中无关的 import 顺序（精准修改）；注释明确“每资源单消费者、共享一次失效”，并补“同一资源的多个订阅共享一次失效”测试；README 文件树与 tests 描述同步 `invalidation` 与跨页失效；票据收口。
  - 保留：`my-notifications` 资源名——票面要求登记，隔离测试需要第二个资源；`subscribe` 返回句柄而非 `consume(resource)`——票面要求订阅/消费接口，句柄在声明处锁定资源；不做按订阅者分发——当前无第二个消费者，属投机复杂度。
  - 行为不变：报名/取消的 6 处写入标记与 profile 的 1 处消费点一一对应；分页 Feed、导航/tab、页面 data 字段未动，未引入状态库。
