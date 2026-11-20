# 11 — 剩余 DTO 对齐与性能清理

**What to build:** 剩余 M01 DTO 与 05 §4 对齐；处理明显的查询与投递性能问题；清理评审遗留代码。

**Blocked by:** None — can start immediately.

**Status:** done — 待提交

- [x] INV-01 补 `matchId`；INV-04 返回 `Page<Invite>`（含 `exhausted`，列表不返回 `inviteUrl`）；补 `typeLabel`/`statusLabel`
- [x] 邀请 `inviter` 按 05 使用 `UserSummary`
- [x] `RegisterResponse` 与 `BindPhoneResponse` 合并为共享响应类型
- [x] `SmsCodeServiceTest` 复用服务 key 构造，不硬编码 Redis key
- [x] `SessionAuthFilter` 每请求 4–5 条 SQL 收敛；`listUsers` 消除 N+1；`OutboxRelay` 避免串行阻塞发送
- [x] `RegistrationMapper` 跨模块 JOIN `player` 改为 read port
- [x] 确认后清理未使用的 `Hashing.java` 与 `@EnableScheduling`
- [x] 测试与 05 契约一致