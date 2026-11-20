# 08 — M08 分组调整、保存与清除

**What to build:** 管理员拖拽/点选调整两队，实时看到总分差与位置分布；保存快照后事件与阵容按已保存分组约束；可清除后重新生成；保存后调整需确认并回到草稿。

**Blocked by:** 07 — M08 分组生成.

**Status:** resolved

- [ ] `GRP-04 PATCH /b/matches/{id}/grouping`：权限 `grouping:generate:any`，`{teams:{A,B},expectedFingerprint}` + `Idempotency-Key`；仅队内/训练赛且 `LOCKED`；无分组 42201；A/B 必须是当前候选池的无重复、无遗漏划分（未知/非候选/重复/遗漏 42201，结构错误 40001）；当前候选池指纹或 `expectedFingerprint` 与存储不符 40911；已保存且 teams 有变化需 `confirm=true`，否则 42202，确认后 `saved_at=NULL`；teams 与现状相同为空操作，保留 `saved_at`、不写审计；分数由服务端快照，不接受客户端改分。
- [ ] `GRP-05 POST /b/matches/{id}/grouping/save`：权限 `grouping:generate:any`，`{expectedFingerprint,confirm?}` + `Idempotency-Key`；无分组 42201；指纹不符 40911；当前候选池指纹与存储不符（名单变化）或 warnings 非空且 `confirm!=true` 返回 42202；保存保留生成时指纹，只写 `saved_at=now`，不重算分数；重复保存刷新 `savedAt` 并写审计。
- [ ] `GRP-06 DELETE /b/matches/{id}/grouping`：权限 `grouping:generate:any`，`Idempotency-Key`；仅 `LOCKED`；物理删除成员行后删除主行；无分组时幂等返回 `{cleared:true}` 且不写审计；写审计。
- [ ] 所有写操作在事务内锁比赛行串行化；生成/调整/保存/清除写审计（`grouping.generate/adjust/save/clear`，精确枚举由 M10 统一）；物理删除前取旧行作为 before 快照。
- [ ] B06：桌面拖拽 + 移动端点选移动；实时总分差与位置分布；保存前有警告或名单变化时二次确认并列出警告；保存后显示已保存状态；清除需确认；加载中/失败/空数据可用。
- [ ] IT：A/B 划分校验、名单变化 40911、生成覆盖 confirm、已保存调整 confirm 与 `saved_at` 回 NULL、保存 confirm、物理删除后重新生成、无分组 clear 幂等、重复 save 刷新 `savedAt`、审计、并发写由行锁串行化。
- [ ] Playwright：B06 调整 → 保存 → 清除 → 重新生成；无 console error。
- [ ] 不改 `schema.sql`、不新增 Flyway；`mvn verify` 全绿。

## Result

- 交付：`bd7d538`。
- 验证：批次收尾 `mvn verify` 全绿（107 单元 + 250 集成）；前端 lint/typecheck 通过；Playwright 14 条黄金路径通过（2026-11-11）。
