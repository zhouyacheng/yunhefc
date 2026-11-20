# 04 — M06 事件核心（球员）

**What to build:** 球员在比赛进行中或赛后 24 小时内录入/修改自己的进球、乌龙、黄红牌；事件时间线可读且排序稳定；比赛头部显示事件推导比分；C03 事件 Tab 可用。

**Blocked by:** 01 — 前端工程骨架与首个纵向切片.

**Status:** resolved

- [ ] `EVT-01 GET /c/matches/{id}/events` 公开；只返回 `is_deleted=0` 且 `deleted_at` 为空的事件；返回 `{items,derivedScore}`；排序为半场升序 → 分钟升序 → 同分钟 `GOAL/OWN_GOAL` 优先 → id 升序。
- [ ] 推导比分：`GOAL` 记事件所属队，`OWN_GOAL` 记对方；比赛详情 `MatchDetail.derivedScore` 由 null 改为推导值。
- [ ] `EVT-02 POST /c/matches/{id}/events`：权限 `event:create:own` / `event:edit:any`，要求 `Idempotency-Key`；仅 `IN_PROGRESS/FINISHED`；球员创建限进行中或赛后 24h；换人仅管理员；对外比赛球队锁定本人所属队；队内/训练赛球员可为主客任意球员创建。
- [ ] `EVT-03 PATCH /c/events/{id}`：权限 `event:edit:own` / `event:edit:any`，`EventUpdate + version + Idempotency-Key`；球员仅改自己的事件且赛后 24h；管理员不限；换人仅管理员；版本冲突 40901；已取消/延期 40905。
- [ ] 字段矩阵：`type/team/minute(1–120)/half` 必填；`GOAL` 可带助攻与点球；`OWN_GOAL` 不带助攻/点球；`SUBSTITUTION` 必须 `inPlayerId/outPlayerId` 且不同、`playerId` 为空；非换人 `playerId` 必填、换上/换下为空；结构错误 40001，业务规则 42201。
- [ ] 选人前置（第一阶段）：球员/助攻/换上/换下须为本场 CONFIRMED 且账号 ACTIVE 的报名，否则 42201；本票不做分组校验。
- [ ] `event_history` 记录 CREATE/UPDATE 前后 JSON、操作人、requestId；写审计 `EVENT_CREATE/EVENT_UPDATE`。
- [ ] 事件响应含 `type/typeLabel`、`team/teamLabel`、`half/halfLabel`、球员/助攻/换上/换下、`minute`、`penalty`、`version`、`updatedAt`。
- [ ] C03 事件 Tab：统计卡、时间线（主队左/客队右、时间轴居中）、按权限与窗口显示/禁用录入入口；弹窗字段含类型/球队/球员/助攻（仅进球）/点球/半场/分钟；球员端不显示换人；已取消/延期隐藏操作；加载中/失败/空数据可用。
- [ ] IT/单元：字段矩阵、权限与 24h、队内赛任意球员、对外比赛锁定本队、推导比分（含乌龙记对方）、时间线排序、历史与审计、已取消/延期。
- [ ] Playwright C 端黄金路径：登录 → 比赛详情 → 录入进球 → 时间线与推导比分更新；无 console error。
- [ ] 不改 `schema.sql`、不新增 Flyway；`mvn verify` 全绿。

## Result

- 交付：`018a94a`。
- 验证：批次收尾 `mvn verify` 全绿（107 单元 + 250 集成）；前端 lint/typecheck 通过；Playwright 14 条黄金路径通过（2026-11-11）。
