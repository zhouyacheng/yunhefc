# 01 — 报名与候补主流程（REG-01/09）

**What to build:** 登录球员对“报名中”的比赛提交报名后，系统按正式/外挂分池立即返回确认、候补或拒绝，并保证并发下不超卖。这是 M04 的第一条端到端路径。

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] 读需求 §6.4（REG-01/02/04/09）、01 §4.5、02 §5.3/§7.2、04 §5.11、05 §3.5 REG-01。
- [ ] `POST /api/v1/c/matches/{id}/registrations`，权限 `registration:create:own`，要求 `Idempotency-Key`，无 body，响应 `{registration: Registration}`，枚举带 `typeLabel/statusLabel`。
- [ ] 资格：账号 ACTIVE（PENDING 由既有过滤器拦截）、比赛存在且属当前球队、状态 `SIGNUP_OPEN`、`now < signupDeadline` 且 `now < startAt`、球员档案存在；不满足返回 40905，比赛不存在/跨队返回 40401。
- [ ] 分池：正式球员 `type=PLAYER`，已确认正式人数 < `capacity` 则 CONFIRMED；否则正式候补人数 < `waitlistCapacity` 则 WAITLISTED；否则 40903。外挂 `type=GUEST`，已确认外挂人数 < `guestCapacity` 则 CONFIRMED；否则 40903，外挂不进入候补。
- [ ] 同一比赛同一球员仅一条 CONFIRMED/WAITLISTED；冲突返回 40902，由 `(match_id,player_id,active_unique)` 唯一键兜底。
- [ ] 取消/移除后允许再次报名，历史记录保留。
- [ ] 名额判断与写入在同一事务内：经 contracts 的比赛报名上下文端口锁定 `team_match` 行并读取状态、报名截止、锁定、`capacity/guestCapacity/waitlistCapacity`；报名域不直接访问 `team_match`。
- [ ] 扩展比赛只读快照覆盖报名所需字段，保持现有 MAT-* 响应不变；报名计数由报名域自身查询。
- [ ] 写 `type/status/registered_at`；`active/active_unique` 由数据库生成列维护；本票不写审计（审计范围仅移除报名）。
- [ ] 本票不发送通知（票 06 接入）。
- [ ] 测试：正式未满确认、正式满进候补、候补满拒绝、外挂满拒绝、外挂不进候补、重复报名、截止后/已开赛/非报名中拒绝、取消后重新报名、并发抢最后一个名额不超卖、权限与幂等键。
