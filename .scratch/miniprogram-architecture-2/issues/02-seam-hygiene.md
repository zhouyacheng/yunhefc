# 02 seam 卫生包：死资源与错误文案重复

Status: open
Blocked by: none

## 目标

清掉 invalidation 的死资源，让页面错误文案统一走 `errorMessage`；不改任何行为与文案。

## 现状与摩擦

- `utils/invalidation.ts:3` 声明 `'my-notifications'`，源码中零写者、零消费者；只有 `tests/invalidation.test.ts:6,33` 拿它当“第二个资源”测试隔离性。
- 6 处页面仍用 `(error as Error).message || fallback`，与 `utils/api-errors.ts:19` 的 `errorMessage(error, fallback)` 逻辑重复：
  - `pages/login/index.ts:27`
  - `pages/phone-bind/index.ts:68,102`
  - `pages/profile-edit/index.ts:82,118,204,243`（其中 82/118 同一 fallback）
- `pages/home/index.ts:117` 与 `pages/team/index.ts:91` 同类，但属于票 03 的重写范围，本票刻意不碰。

## 深化方向

1. 从 `InvalidationResource` 联合类型删除 `'my-notifications'`；`invalidation.ts` 注释补一句“新增资源先登记”。
2. 6 处替换为 `errorMessage(error, '<原 fallback>')`；删除因此变孤儿的 import。
3. `tests/invalidation.test.ts` 删除“消费一个资源不影响其他资源”与“未标记的资源保持未失效”两个用例（单资源下已无意义），并删除 `notifications` 订阅与 `beforeEach` 中的对应 reset；保留未标记消费、消费一次、重复标记、同资源多订阅共享四个用例。

## 删除测试

`errorMessage` 已是 `api-errors` 的公开函数；页面内的等价表达式删除后不会让页面变复杂。`'my-notifications'` 没有删除成本——它从未被实现。

## 收益

- locality：错误文案回退只有一份实现。
- 接口诚实：资源名联合类型不再包含未实现项。
- 减少下一批“这个资源是谁在用”的排查成本。

## 范围

- `utils/invalidation.ts`、`tests/invalidation.test.ts`。
- `pages/{login,phone-bind,profile-edit}/index.ts`。

## 不做

- 不替换 `home`/`team` 的同类代码（票 03 重写）。
- 不修 `team` 的 `requestSeq` 残留（票 03 消除）。
- 不新增 invalidation 资源；不改 consume-once 语义。

## 验收

- [x] `grep -rn "my-notifications" miniprogram/ tests/` 无结果。
- [x] `grep -rn "(error as Error).message"` 只剩 `home`/`team` 两处，且由票 03 覆盖。
- [x] 6 处替换后的 fallback 文案逐字不变（登录、验证码、绑定、资料加载、头像上传、保存）。
- [x] `invalidation.test.ts` 保留 4 个有效用例，删除的 2 个在 `## Comments` 记录理由。
- [x] `typecheck`、`test`、workspace `pnpm lint` 通过。
- [ ] 开发者工具手点：手机绑定页验证码失败、资料保存失败仍显示原文案。

## 风险

- 删除联合成员会让任何遗漏的调用点变成编译错误——这是预期的保护，不要用 `as InvalidationResource` 绕过。
- `errorMessage` 对非 `Error` 值返回 fallback，与旧表达式一致；不要顺手改成 `String(error)`。

## Comments

- 2026-11-22 架构扫描开票（Medium）。
- 2026-11-22 实施：`InvalidationResource` 删除 `'my-notifications'`；`tests/invalidation.test.ts` 删除“消费一个资源不影响其他资源”“未标记的资源保持未失效”两个用例（单资源下无意义），保留 4 个用例；`login`、`phone-bind`、`profile-edit` 共 6 处改用 `errorMessage`，文案不变。验证：`typecheck`、`test`（29 文件 / 282 用例）、`eslint`、`prettier --check` 通过。开发者工具手点待做。
