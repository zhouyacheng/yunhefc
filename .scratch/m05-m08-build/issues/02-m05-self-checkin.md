# 02 — M05 自助签到与本人考勤

**What to build:** 已确认报名的球员在签到窗口内点击签到后得到 PRESENT/SELF 考勤；窗口外按钮禁用并说明原因；球员能在比赛详情看到自己的报名与考勤状态；REG-04 的 `attendance` 不再是 null。

**Blocked by:** 01 — 前端工程骨架与首个纵向切片.

**Status:** resolved

- [ ] `ATT-01 POST /c/matches/{id}/check-in`，权限 `attendance:checkin:own`，要求 `Idempotency-Key`；仅本场 CONFIRMED 报名可签；写 `PRESENT/SELF`；重复/重试返回同一条记录。
- [ ] 签到窗口：开赛前 4 小时至比赛当天 23:59（`Asia/Shanghai` 推导后转 UTC）；由窗口驱动、不按比赛状态开关；窗口外返回 42201；`CANCELLED/POSTPONED` 返回 40905。
- [ ] 已有 `ADMIN/SYSTEM` 考勤记录不被自助签到覆盖，直接返回原记录；已有 `SELF` 记录幂等返回。
- [ ] `ATT-02 GET /c/matches/{id}/attendance/me`：本人；无记录时 `attendance` 为 null。
- [ ] `REG-04 GET /c/matches/{id}/registrations/me` 返回本人 `attendance`；未报名或无考勤记录时对应字段为 null。
- [ ] 考勤响应含 `status/statusLabel`、`source/sourceLabel`、`recordedAt`；来源为自签/管理员/系统。
- [ ] C03 比赛详情：本人报名卡显示签到按钮、窗口状态与禁用原因；已取消比赛隐藏签到；加载中/失败/空数据可用。
- [ ] IT：窗口内/外、非 CONFIRMED 拒绝、重复签到幂等、已取消/延期 40905、REG-04 填充、外挂只看本人。
- [ ] Playwright C 端黄金路径：登录 → 比赛详情 → 签到 → 看到考勤状态；无 console error。
- [ ] 不改 `schema.sql`、不新增 Flyway；`mvn verify` 全绿。

## Result

- 交付：`91d7fac`、`8173468`。
- 验证：批次收尾 `mvn verify` 全绿（107 单元 + 250 集成）；前端 lint/typecheck 通过；Playwright 14 条黄金路径通过（2026-11-11）。
