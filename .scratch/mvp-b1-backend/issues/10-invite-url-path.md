# 10 — 邀请链接路径对齐 C 端路由

Type: implementation
Status: resolved
Blocked by: 无

**What to build:** INV-03 / INV-05 返回的 `inviteUrl` 当前是 `${publicBaseUrl}/invite.html?token={token}`，但 C 端 Web 路由是 `/invite/:token`（`frontend/apps/web/src/router/index.ts:54`），复制出的链接无法打开。改为 `${publicBaseUrl}/invite/{token}`，字段名与 API 契约不变。

## Scope

- `InviteService.inviteUrl(String rawToken)` 输出 `${publicBaseUrl}/invite/{rawToken}`。
- 同步更新从 URL 解析 token 的测试 helper，改为取最后一个 `/` 之后的路径段：
  - `backend/src/test/java/com/yunhefc/AuthFlowIT.java:155`
  - `backend/src/test/java/com/yunhefc/InviteFlowIT.java:30`
  - `backend/src/test/java/com/yunhefc/InviteLifecycleIT.java:277`
  - `backend/src/test/java/com/yunhefc/MessagingReliabilityIT.java:270`
- 新增断言：INV-05 返回的 `inviteUrl` 匹配 `^.*/invite/[0-9a-f]{32}$`。
- 不改 `public-base-url` 配置语义，不新增接口、不改字段。

## Owned files

- `backend/src/main/java/com/yunhefc/auth/application/InviteService.java`
- `backend/src/test/java/com/yunhefc/AuthFlowIT.java`
- `backend/src/test/java/com/yunhefc/InviteFlowIT.java`
- `backend/src/test/java/com/yunhefc/InviteLifecycleIT.java`
- `backend/src/test/java/com/yunhefc/MessagingReliabilityIT.java`

## API

- INV-03 `POST /c/matches/{id}/invites`（响应 `inviteUrl`）
- INV-05 `POST /b/invites`（响应 `inviteUrl`）

## Test obligations

- `mvn verify` 全绿。
- 空库 Flyway 迁移仍幂等；无 schema/Flyway 改动。
- INV-03 与 INV-05 两条创建路径都断言 URL 形态。

## Notes

- 05 只约定 `inviteUrl` 字段，未约定 URL 形态，不需要改 05/06。
- 这是 B11「创建成功弹窗复制」与 C03 分享入口的前置修复。
