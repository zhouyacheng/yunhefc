# 02 — 取消报名与自动递补（REG-02，需求 REG-05/07）

**What to build:** 球员在锁定前取消自己的报名；确认报名取消后，同池最早候补立即递补，响应返回真实的递补结果。

**Blocked by:** 01 — 报名与候补主流程（REG-01/09）

**Status:** ready-for-agent

- [ ] 读需求 §6.4 REG-05/07、02 §7.2、03 §4.3/§5.3、05 REG-02、06 P-05。
- [ ] `DELETE /api/v1/c/matches/{id}/registrations/me`，权限 `registration:create:own`，要求 `Idempotency-Key`，响应 `{cancelled, promotedPlayerId?, promoted}`。
- [ ] 取消窗口：`now < cancelLock`；锁定及之后返回 40905。比赛已取消或已延期返回 40905。无有效报名返回 40401。
- [ ] `CONFIRMED/WAITLISTED → CANCELLED`，写 `cancelled_at/cancelled_by`；不删除记录。
- [ ] 取消 CONFIRMED 时，同池最早 WAITLISTED（`registered_at,id` 升序）置为 CONFIRMED，返回 `promoted=true` 与 `promotedPlayerId`；无候补时 `promoted=false`。
- [ ] 外挂取消不触发递补；候补取消不触发递补。
- [ ] 行锁防止并发重复递补；一次取消最多递补一人。
- [ ] 取消/递补与票 01 报名共用同一 `team_match` 行锁（经 `MatchRegistrationContextPort` 或同语义端口）：并发报名与取消不得出现「confirmed 未满但球员滞留守候」或超卖。
- [ ] P-05 注销路径复用同一取消/递补逻辑并先锁涉及比赛的 `team_match` 行，保持同一本地事务；报名事务内加锁复核账号 ACTIVE，注销扫描使用当前读，保证注销后不残留有效报名。
- [ ] 本票不发送通知（票 06 接入）。
- [ ] 测试：截止前取消成功、锁定后拒绝、已取消/已延期比赛拒绝、候补取消、确认取消递补最早候补、无候补、并发取消只递补一次、注销路径回归、并发报名与取消/注销交错（不超卖、不滞留守候、注销后无有效报名）、幂等键。
