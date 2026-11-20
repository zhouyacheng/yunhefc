# 04 删除或深化 actions seam

Status: needs-triage
Blocked by: none

## 目标

让比赛详情写操作要么由页面直接拥有，要么由一个 interface 只含领域输入的 action module 拥有；不再保留“重述 25 个页面字段”的浅 seam。

## 现状与摩擦

- `pages/match-detail/actions.ts` 定义 `MatchActionPage`，重新声明 25 个 data 字段与 4 个页面方法，interface 与 implementation 几乎等大。
- 大部分方法只是“守卫 data → 调 typed client → setData → page.load”；写流程理解要在 page、actions、registration、client、registration-signal 之间来回读。
- 删除测试：删掉 actions 只让 index 多 326 行，接口成本一并消失，locality 反而更好。

## 深化方向

二选一，设计时定：

1. **删除 seam**：写流程回到页面，共享的冲突重载/版本重取逻辑抽成小 module。
2. **深化 seam**：action module 只接收领域输入（detail、registration、payload），返回 outcome（patch、toast、reload、error），页面负责 setData。

无论哪种，interface 都不应包含页面 data 字段清单。

## 收益

- interface 只含领域输入。
- 冲突重载、版本重取、幂等键集中。
- 页面不再做 middle man。
- 测试可跨 module interface 验证冲突路径。

## 范围

- 改 `pages/match-detail/index.ts`、`pages/match-detail/actions.ts` 与新增测试。
- 保持报名、取消、签到、状态修改、事件录入的行为与文案不变。

## 不做

- 不改 typed client 的请求语义。
- 不改状态机、权限码与错误码。
- 不与 01 并行修改详情页。

## 验收

- [ ] `MatchActionPage` 不再重述页面 data 字段。
- [ ] 冲突后重载、事件 version 重取、报名成功通知仍有测试或等价验证。
- [ ] 页面 WXML 绑定与用户可见行为不变。
- [ ] `typecheck`、`test`、`pnpm lint` 通过。

## 风险

- 若选择“删除 seam”，页面会重新变长；只有当 action module 的 interface 确实缩小且测试面变好时才保留 seam。

## Comments

- 2026-11-20 架构扫描开票（Worth exploring）。与 01 同改详情页，按 01 → 04 顺序实施。
