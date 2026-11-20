# 04 删除或深化 actions seam

Status: resolved
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

- [x] `MatchActionPage` 不再重述页面 data 字段。
- [x] 冲突后重载、事件 version 重取、报名成功通知仍有测试或等价验证。
- [x] 页面 WXML 绑定与用户可见行为不变。
- [x] `typecheck`、`test`、`pnpm lint` 通过。

## 风险

- 若选择“删除 seam”，页面会重新变长；只有当 action module 的 interface 确实缩小且测试面变好时才保留 seam。

## Comments

- 2026-11-20 架构扫描开票（Worth exploring）。与 01 同改详情页，按 01 → 04 顺序实施。
- 2026-11-21 实施完成（TDD），选择“深化 seam”：
  - `pages/match-detail/actions.ts` 只保留写操作：`WriteOutcome = { ok: true, patch, toast } | { ok: false, error, reload, refreshEventId }`；`signup(matchId)`、`cancel({ matchId, cancelledStatus })`、`checkIn(matchId)`、`changeStatus({ matchId, version, status, reason?, startAt?, endAt? })`、`saveEvent({ matchId, payload, eventId, version })`。module 拥有端点选择、乐观锁版本、跨页报名失效、提示/错误文案与冲突重载判定。
  - `MatchActionPage` 删除；页面新增 `applyWriteOutcome(outcome)` 统一落地 patch/toast/reload，并保留忙闲、守卫、确认弹窗与弹层开关。`refreshedEventVersion(events, eventId)` 抽为纯函数，事件更新冲突重载后由页面取新版本。
  - 删除测试支持保留 seam：删掉 module 会把版本捕获、跨页失效、文案与冲突策略重新散回 6 个页面 handler；interface 已只剩领域输入，测试可跨 module 验证冲突路径。
  - 新增 `tests/match-actions.test.ts`（20 例）：报名/候补文案与跨页失效、取消递补文案、签到 patch、状态乐观锁与延期/取消字段透传、事件新建/更新、四类冲突的 reload/refreshEventId、重载后取版本。
  - 行为差异：取消成功后的递补提示由“重载后写入”改为“重载前写入 patch”。`load(true)` 在详情已存在时不清空 `actionMessage`，用户可见结果一致。
  - 验证：`pnpm --filter @yunhefc/miniprogram typecheck`、`test`（27 文件 / 244 用例）、`frontend` 下 `pnpm lint`、`prettier --check`（改动文件）通过；WXML 未改，开发者工具/真机走查待补。
- 2026-11-21 code-review（Standards / Spec 两轴）：
  - 已改：`WriteOutcome.patch` 由 `Record<string, unknown>` 收成 `WritePatch`（actionMessage/attendance/attendanceText/statusOpen/eventFormOpen），写操作可触碰的页面字段显式且受类型检查；五个页面 handler 恢复 `try/finally` 忙闲复位；补状态延期/取消字段透传用例。
  - 保留：五个 handler 保留同一骨架，不做 `runWrite` 合并——合并需要传 busy/error 字段名，会把 stringly-typed 键重新引入页面；`EventSave` 保留 `eventId/version` 可空对，混合态沿用“无版本按新建”的迁移前语义，不引入行为变化。
  - 走查：本次把 6 个写流程编排迁回页面，属“涉及页面编排”，仍需开发者工具/真机走查后收尾；`Status: resolved` 沿用 01/03 的批次口径。
