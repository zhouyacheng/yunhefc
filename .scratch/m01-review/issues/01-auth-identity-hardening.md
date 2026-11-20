# 01 — 认证与身份授权加固

**What to build:** 只有具备后台权限的账号才能登录 B 端；比赛邀请只能由本场正式球员或管理员创建；账号相关响应不再暴露原始手机号；限流响应符合契约；PENDING 账号的 M01 白名单完整。

**Blocked by:** None — can start immediately.

**Status:** done — 8f0ca82

- [x] GUEST 账号调用 B 端登录返回 403，不能获得 `YHFC_B` 会话
- [x] INV-03 校验调用者是比赛双方的本场正式球员或管理员；非本场球员返回 403
- [x] AUTH-03/04/10/17 的用户对象返回脱敏手机号，并补齐规格要求的 `name`/`createdAt` 字段
- [x] 429 响应包含 `Retry-After` 头，值与 `data.retryAfterSeconds` 一致
- [x] PENDING 账号可执行 M01 已定义的允许写操作，其余写操作返回 40303
- [x] 集成测试覆盖以上分支
