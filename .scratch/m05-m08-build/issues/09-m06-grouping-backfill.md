# 09 — M06 分组校验回填

**What to build:** 事件写入在报名校验之后，再按**已保存**分组校验球员/助攻/换上/换下归属：主队只能选 A 组、客队只能选 B 组；不匹配返回 42203 并指出字段与球员；草稿分组不约束；不回溯已有事件。

**Blocked by:** 08 — M08 分组调整、保存与清除.

**Status:** resolved

- [ ] 新增 contracts 分组只读端口与已保存分组快照：按比赛返回 `savedAt` 与 `playerTeams`；适配器查询 `saved_at` 非空且 `is_deleted=0` 的分组；未生成、仅草稿、已清除均返回空。
- [ ] `EVT-02/03/06/07` 在现有「本场 CONFIRMED 且账号 ACTIVE」前置之后校验分组归属：HOME=A 组、AWAY=B 组；仅 `saved_at` 非空才约束；不回退报名名单；管理员不豁免；PATCH 按合并后的有效 `team` 与球员字段校验。
- [ ] `OWN_GOAL` 球员按 `event.team`（乌龙方）校验，比分仍记给对方；`includeGuests=false` 的已确认外挂不在快照中，不能被选入事件。
- [ ] 新增错误码 `42203 GROUPING_MISMATCH`，HTTP 422，message 含违规字段与 playerId；报名前置失败仍返回 42201，且优先于 42203。
- [ ] ArchUnit 定向规则：`event` 只依赖 contracts 分组端口，不依赖 grouping application/domain/表。
- [ ] IT 矩阵：四个球员字段 × 错队 → 42203；`OWN_GOAL` 按事件所属队通过、对方队分组球员被拒；仅草稿不约束；无分组只校验报名；管理员错队仍 42203；分组内但报名取消/账号非 ACTIVE → 42201 优先；PATCH 只改 `team` 导致原球员跨队 → 42203；分组生成/调整/保存/清除后已有事件不变。
- [ ] B04/C03 选人器在 42203 时行内提示字段与球员，并引导到 B06 重新生成分组。
- [ ] 不改 `schema.sql`、不新增 Flyway；`mvn verify` 全绿。

## Result

- 交付：`89c9b4f`。
- 验证：批次收尾 `mvn verify` 全绿（107 单元 + 250 集成）；前端 lint/typecheck 通过；Playwright 14 条黄金路径通过（2026-11-11）。
