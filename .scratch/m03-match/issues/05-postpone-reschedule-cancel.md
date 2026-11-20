# 05 — 延期、重新排期与取消（MAT-07/08/11 + MAT-12 对应分支）

**What to build:** 管理员把未开赛比赛延期并等待重排，重新排期后回到报名中并恢复自动流转；取消非终态比赛并保留报名记录。C 端管理员状态入口的 POSTPONED/CANCELLED 分支复用同一逻辑。

**Blocked by:** 04 — 状态机与手动状态修改.

**Status:** ready-for-agent

- [ ] 读 05 MAT-07/08/11/12、02 §6.4、03 §5.4、需求 MATCH-07/08、06 P-02。
- [ ] `POST /api/v1/b/matches/{id}/postpone`，权限 `match:edit:any` + `Idempotency-Key`；请求 `{startAt,endAt,version}`；仅 SIGNUP_OPEN/SIGNUP_CLOSED/LOCKED/IN_PROGRESS；重算截止/锁定并校验 `startAt > 当天 10:00`、2–4 小时、`cancelLock < startAt`；状态置 POSTPONED 且 `manualStatus=true`；响应 `MatchAdmin`。
- [ ] `POST /api/v1/b/matches/{id}/reschedule`，权限 `match:edit:any` + `Idempotency-Key`；仅 POSTPONED；校验新时间并重算截止/锁定；状态回 SIGNUP_OPEN、`manualStatus=false` 恢复自动流转；响应 `MatchAdmin`。
- [ ] `POST /api/v1/b/matches/{id}/cancel`，权限 `match:status:any` + `Idempotency-Key`；请求 `{reason,version}`；仅非终态；状态置 CANCELLED；报名记录保留且不修改报名状态；原因写入审计快照；响应 `MatchAdmin`。
- [ ] MAT-12 对应分支：POSTPONED 需同时具备 `match:edit:any` 且必传 `startAt/endAt`（复用 MAT-07）；CANCELLED 必传 `reason`（复用 MAT-08）。
- [ ] `version` 不匹配 40901；状态不允许该操作 40905；时间违规 42201。
- [ ] 已报名者通知留到 M04（本票只落状态与审计，不解析接收人）。
- [ ] 测试：各允许状态延期、非法状态延期、重新排期、取消非终态、终态拒绝、MAT-12 两分支、权限与幂等键、审计含原因。
