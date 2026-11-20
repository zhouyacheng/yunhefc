# 03 — 编辑比赛（MAT-05）

**What to build:** 管理员修改比赛信息并受版本与容量约束：时间、场地、赛事、轮次、主客队名称、名额、含外挂可改；赛季、类型、我方主客、比分不可改；时间变更自动重算报名截止与锁定；名额不得低于已确认报名人数；终态与已延期比赛不可编辑。

**Blocked by:** 01 — 建赛与后台读取.

**Status:** ready-for-agent

- [ ] 读 05 MAT-05、02 §6.4、需求 MATCH-16。
- [ ] `PATCH /api/v1/b/matches/{id}`，权限 `match:edit:any`，请求 `MatchUpdate` + `version`；响应 `MatchAdmin`。
- [ ] 可改 `title,startAt,endAt,venue,competition,round,homeTeamName,awayTeamName,capacity,guestCapacity,waitlistCapacity,includeGuests`；赛季/类型/我方主客/比分不在此接口修改。
- [ ] `startAt/endAt` 变更重算 `signupDeadline=当天 00:00`、`cancelLock=当天 10:00`；校验 2–4 小时、开始晚于当天 10:00；违规 42201。
- [ ] 终态（FINISHED/CANCELLED）不可编辑；POSTPONED 只能走 MAT-11；违规返回 40905 `MATCH_STATE_CONFLICT`。
- [ ] `capacity` 不得低于 CONFIRMED 正式人数，`guestCapacity` 不得低于 CONFIRMED 外挂人数；经 registration 只读端口统计；违规 42201。
- [ ] `version` 不匹配返回 40901 `VERSION_CONFLICT`，响应 `data.currentVersion` 为当前版本；新增该错误码。
- [ ] 写审计前后快照。
- [ ] 测试：编辑成功、不可改字段被拒、时间重算、容量下限、终态/POSTPONED 拒绝、版本冲突、审计、权限。
