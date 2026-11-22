# 04 报名/取消写流程收成共享 module

Status: open
Blocked by: 03

## 目标

报名与取消只有一份实现；首页、我的、比赛详情三页共用同一 `WriteOutcome` 协议，各自只负责 UI 反应。

## 现状与摩擦

同一流程三处各写一遍：

- `pages/match-detail/actions.ts:65-92`：`signup`/`cancel` 返回 `WriteOutcome`；调用方 `index.ts:633-655` 统一分发。
- `pages/home/index.ts:155-171`：内联 `signupMatch`、`invalidate('my-registrations')`、WAITLISTED 文案、冲突后 `load(true)`。
- `pages/profile/index.ts:245-305`：内联 `cancelMyRegistration`、`promotionText` 弹窗、冲突后重载列表。

三处对“候补成功文案、冲突刷新、递补提示”的规则相同，但实现各自演化，上一轮复审已记录为“有意保留”的遗留项。

## 深化方向

新建 `utils/registration-writes.ts`：

- 迁入 `WritePatch`、`WriteOutcome` 类型。
- 迁入 `signup(matchId)` 与 `cancel({ matchId, cancelledStatus })`；保持现有实现逐字一致（含 `invalidate('my-registrations')`、WAITLISTED 文案、`promotionText`、`shouldRefreshAfterConflict`）。
- `pages/match-detail/actions.ts` 保留签到、状态修改、事件录入与 `refreshedEventVersion`；类型从新 module 引入，不再导出 `signup`/`cancel`。
- `pages/match-detail/index.ts` 改从 `registration-writes` 引入报名/取消。
- `pages/home/index.ts` 用 outcome：成功 → toast `toast` 并 `load(true)`；失败 → toast `error`，`reload` 为真时再 `load(true)`。
- `pages/profile/index.ts` 用 outcome：成功 → `wx.showModal({ title: '已取消', content: patch.actionMessage })` 并重载报名 feed；失败 → toast `error`，`reload` 为真时重载 feed。

页面侧仍保留：忙闲标记、确认弹窗、取消资格判定（`registrationDecision`）、列表/首屏重载方式。

## 删除测试

把 `signup`/`cancel` 从 `actions.ts` 删除后，详情页不会变复杂——它本来就在用 outcome；变复杂的是三个页面，而三页共享带来的净代码量是下降的。module 的 interface 只有两个函数 + 两个类型，足够深。

## 收益

- leverage：一个报名/取消实现覆盖三页。
- locality：候补文案、失效标记、冲突语义只在一处。
- 一致性：首页与“我的”不再可能落后于详情页的规则修正。

## 范围

- 新增 `utils/registration-writes.ts`。
- 改 `pages/match-detail/actions.ts`、`pages/match-detail/index.ts`、`pages/home/index.ts`、`pages/profile/index.ts`。
- 测试：新增 `tests/registration-writes.test.ts`，从 `tests/match-actions.test.ts` 迁出报名/取消用例；其余写操作用例留在原文件。

## 不做

- 不抽公共 `succeeded`/`failed` 构造器：`actions.ts` 保留自己的私有实现；出现第三个消费者再拆 `write-outcome`。
- 不改签到、状态修改、事件录入。
- 不改确认弹窗文案（`确认取消本次报名？`）与“已取消”标题。
- 不把页面重载方式也塞进 module：首页重载 Hero、我的重载 feed、详情页重载详情，各不相同。

## 验收

- [ ] `grep -rn "signupMatch(" miniprogram/` 只剩 `api-client.ts` 定义与 `registration-writes.ts` 调用。
- [ ] `grep -rn "cancelMyRegistration(" miniprogram/` 同上。
- [ ] 成功文案逐字一致：`报名成功`、`已加入候补`、取消后的 `promotionText` 结果。
- [ ] 冲突刷新行为一致：`shouldRefreshAfterConflict` 为真时三页都重载各自数据。
- [ ] `match-actions.test.ts` 去掉报名/取消用例后仍通过；新文件覆盖成功、WAITLISTED、冲突失败、普通失败四条路径。
- [ ] `typecheck`、`test`、workspace `pnpm lint` 通过。
- [ ] 开发者工具手点：首页报名/候补、我的取消（含递补提示）、详情页报名/取消。

## 风险

- “我的”取消需要先取详情算资格（245-275），这段留页面；只把真正写调用与结果解释搬走，不要为了“纯”把详情请求塞进 module。
- `cancel` 的 `cancelledStatus` 决定弹窗文案；首页/详情有值，“我的”用 `detail.myRegistration?.status ?? row.registrationStatus`，迁移时不要丢这个回退。
- 04 与 03 都改 `home/index.ts`，03 必须先落地。

## Comments

- 2026-11-22 架构扫描开票（Strong）。
