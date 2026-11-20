# 01 — 管理员建赛与比赛读取（MAT-04/09/10）

**What to build:** 管理员创建比赛后立即能在后台看到它：创建时带入默认值、配置快照与场次编号；后台列表可按状态/类型/赛季/scope 筛选排序分页；权威详情返回版本号。这是 M03 的第一条端到端路径。

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] 读 05 MAT-04/09/10、02 §6.4、04 §5.10、需求 6.3。
- [ ] `POST /api/v1/b/matches`，权限 `match:create:any`，要求 `Idempotency-Key`；请求 `MatchCreate`，响应 `MatchAdmin`。
- [ ] 必填：`title,type,startAt,endAt,venue,homeTeamName,awayTeamName`，缺失返回 40001（默认值由前端表单预填并显式提交）。可选默认：赛季当前年份、赛制 8 人制、比分 0:0；`capacity/guestCapacity/waitlistCapacity` 未传时读取系统配置 30/4/2。
- [ ] 时间：开始/结束相差 2–4 小时；开始必须晚于当天 10:00（Asia/Shanghai）；自动推导 `signupDeadline=当天 00:00`、`cancelLock=当天 10:00`；违规返回 42201。
- [ ] 对外比赛 `ourSide` 显式传入或按主队名是否含“云河FC”推断，仍为空则拒绝；队内/训练 `our_side` 为空；赛季四位年份；`scoreHome/scoreAway` 必须成对。
- [ ] 场次编号：同赛季最大值 +1、累计最大值 +1；唯一约束冲突需可重试或映射为业务错误。
- [ ] `GET /api/v1/b/matches`，权限 `match:view:any`：`status,type,season,scope=upcoming/history/all,sort=startAt:asc/desc,page,pageSize`；默认 all + startAt desc；返回 `Page<MatchAdmin>`。
- [ ] `GET /api/v1/b/matches/{id}`，权限 `match:view:any`：返回 `MatchAdmin` 含 version；不存在或跨队返回 40401。
- [ ] 新增只读配置端口读取 `registration.maxPlayers/maxGuests/maxWaitlist`；创建时快照，后续配置变更不影响已建比赛。
- [ ] 创建写审计（前后快照）。
- [ ] 测试：创建成功与默认值、非法时长/开赛时间/比分成对、场次编号递增与并发、列表筛选排序分页、详情、权限与幂等键。
