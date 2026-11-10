# 03 — 管理员移除报名与递补（REG-07，需求 REG-06/07）

**What to build:** 管理员移除某人的有效报名并留下审计；移除确认报名后自动递补，返回真实递补结果。

**Blocked by:** 02 — 取消报名与自动递补（REG-02，需求 REG-05/07）

**Status:** ready-for-agent

- [ ] 读需求 §6.4 REG-06/07、02 §6.5、05 REG-07、01 §4.11 审计留痕范围。
- [ ] `DELETE /api/v1/b/registrations/{id}`，权限 `registration:remove:any`，要求 `Idempotency-Key`，响应 `{removed, promotedPlayerId?, promoted}`。
- [ ] 报名不存在或跨队返回 40401；`CANCELLED/REMOVED` 终态返回 40905；比赛已结束或已取消返回 40905。
- [ ] `CONFIRMED/WAITLISTED → REMOVED`，写 `cancelled_at/cancelled_by`；不删除记录。
- [ ] 移除 CONFIRMED 触发同池最早候补递补；状态变更、递补与 Outbox 审计在同一事务内完成。
- [ ] 移除/递补复用票 02 的同一比赛 `team_match` 行锁语义，与报名串行化；并发下不得超卖或滞留守候。
- [ ] 写审计 `REGISTRATION_REMOVE`，`target_type=REGISTRATION`，前后快照包含报名状态与递补结果。
- [ ] 本票不发送通知（票 06 接入）。
- [ ] 测试：移除确认报名、移除候补、终态拒绝、终态比赛拒绝、递补真实结果、审计前后快照、权限、幂等键。
