# 06 — 自动状态流转（MATCH-05）

**What to build:** 比赛按报名截止、锁定、开赛、结束时间自动推进状态；手动覆盖、已取消、已延期与已结束的比赛不被自动覆盖，且只前进不回退。定时任务可重复执行而无副作用。

**Blocked by:** 04 — 状态机与手动状态修改.

**Status:** ready-for-agent

- [ ] 读 03 §5.4、需求 MATCH-05、06 P-02。
- [ ] 使用 Quartz JDBC（`QuartzJobBean` + `Trigger`）每分钟执行；禁止 Spring `@Scheduled`。
- [ ] 自动推进：SIGNUP_OPEN→SIGNUP_CLOSED（`signup_deadline`）、→LOCKED（`cancel_lock`）、→IN_PROGRESS（`start_at`）、→FINISHED（`end_at`）。
- [ ] 跳过 `manual_status=1`、CANCELLED、POSTPONED、FINISHED；只前进不回退；锁定后不回退报名中。
- [ ] 更新 `status_updated_at`，`status_updated_by` 为空（系统）；任务按主键幂等，重复执行无副作用。
- [ ] 自动流转对应的已报名者通知留到 M04（本票只落状态）。
- [ ] 测试：各时间点触发推进；跳过手动/取消/延期/终态；不回退；重复执行幂等。测试使用可控时钟或直接构造时间边界。
