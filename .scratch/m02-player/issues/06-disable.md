# 06 — PLR-09 账号注销

**What to build:** 管理员注销账号：账号置 DISABLED、会话立即失效、保留历史数据；取消未开赛比赛的报名并递补候补；最后管理员保护；原因写入审计。

**Blocked by:** 01 — 球员域读模型与 PLR-05/06.

**Status:** ready-for-agent.

- [ ] 读 05 PLR-09、06 P-05、03 §4.3、02 §6.9、04 `registration`/`notification`。
- [ ] `POST /b/players/{id}/disable`，权限 `player:edit:any`，要求 `Idempotency-Key`；请求 `{reason}`。
- [ ] 仅 ACTIVE 可注销；注销不可恢复；已是 DISABLED 时幂等返回。
- [ ] 最后一名管理员保护：按 ACTIVE 用户角色并集校验 `role:manage:any`，否则 40913。
- [ ] 同一事务内：账号置 DISABLED、写审计（含 reason 快照）、Outbox 通知（`notification.type=11`）。
- [ ] 旧会话立即失效，旧 Cookie 再访问返回 401；权限/角色读取无需缓存清理（每请求加载）。
- [ ] 报名处理：未开赛 `CONFIRMED` 报名取消并递补同池最早正式候补；`WAITLISTED` 直接取消。在 `registration` 域实现一个 contracts 取消/递补端口，供 P-05 事务调用；完整 M04 接口后续复用该端口。
- [ ] 返回 `PlayerAdmin`；错误码按 05。
- [ ] 测试：注销成功、非 ACTIVE 冲突、最后管理员 40913、旧会话 401、取消+递补、审计含原因、通知 Outbox、无权限。
