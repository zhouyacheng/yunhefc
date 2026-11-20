# 03 — M05 管理员考勤与自动缺勤

**What to build:** 管理员在 B03 看到全队考勤名单与来源，标记正常/缺勤，补录临时到场；比赛结束 +6 小时后系统自动给未签到的确认报名记缺勤；临时到场不占名额、计入出场、不计缺勤。

**Blocked by:** 02 — M05 自助签到与本人考勤.

**Status:** resolved

- [ ] `ATT-03 GET /c/matches/{id}/attendance` 与 `ATT-04 GET /b/matches/{id}/attendance`：权限 `attendance:view:any`，支持 `status,page,pageSize`，返回 `Page<AttendanceRow>`（含球员姓名/号码/位置与来源）；含临时到场；外挂无该权限只看本人。
- [ ] `ATT-05 PUT /b/matches/{id}/attendance/{playerId}`：权限 `attendance:mark:any`，要求 `Idempotency-Key`；只接受 `PRESENT/ABSENT`，写 `source=ADMIN`；已取消比赛 40905；可覆盖修正。
- [ ] `ATT-06 POST /b/matches/{id}/attendance/temp`：权限 `attendance:mark:any`，要求 `Idempotency-Key`；`{playerId}`；球员须属于本队、未注销，且本场无 CONFIRMED 报名；写 `TEMP_PRESENT/ADMIN`；不占报名名额；已取消比赛 40905。
- [ ] 缺勤任务：Quartz 每 10 分钟扫描 `FINISHED` 且实际结束 +6h 已到的比赛；实际结束时间优先 `status_updated_at`（提前结束），否则 `end_at`；跳过 `CANCELLED/POSTPONED`；对无考勤记录的 CONFIRMED 报名写 `ABSENT/SYSTEM`；已有记录不覆盖；重复执行幂等；断言 `JobDetail/Trigger` 的 key 与表达式。
- [ ] 比赛只读快照补充 `statusUpdatedAt`（以及本票需要的字段）；不改变 05 对外契约。
- [ ] 管理员标记/补录写审计（`ATTENDANCE_MARK`/`ATTENDANCE_TEMP`，精确枚举由 M10 统一）。
- [ ] B03：比赛选择器按时间倒序；统计卡正式/外挂/候补/临时到场，已取消场次隐藏临时到场；名单分正式/候补/外挂三栏；行内显示报名状态、考勤状态、来源与操作；标记正常/缺勤、补录临时到场、移除报名（确认 + 真实递补提示）；终态不可移除、已取消不可考勤；加载中/失败/空数据可用。
- [ ] IT/单元：全队可见性、外挂只看本人、标记/修正、临时到场资格与不占名额、+6h 实际结束/跳过取消延期/幂等、来源 Label、审计落库。
- [ ] Playwright B 端黄金路径：登录 → B03 → 标记考勤；无 console error。
- [ ] 不改 `schema.sql`、不新增 Flyway；`mvn verify` 全绿。

## Result

- 交付：`dfc92d6`、`9aa8543`。
- 验证：批次收尾 `mvn verify` 全绿（107 单元 + 250 集成）；前端 lint/typecheck 通过；Playwright 14 条黄金路径通过（2026-11-11）。
