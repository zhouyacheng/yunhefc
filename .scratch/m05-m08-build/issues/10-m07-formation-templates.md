# 10 — M07 阵型模板

**What to build:** 管理员在 B05 看到 8 套预置阵型与球队自定义模板；可从预置复制并命名新建、重命名/删除自定义模板；预置不可改删；删除为软删除，历史阵容仍可读取被删模板。

**Blocked by:** 01 — 前端工程骨架与首个纵向切片.

**Status:** resolved

- [ ] `LIN-05 GET /b/formation-templates`：权限 `lineup:edit:any`，`includePreset=true`；返回预置 + 自定义模板及槽位（`slotKey/role/x/y`）。
- [ ] `LIN-06 POST /b/formation-templates`：权限 `lineup:edit:any`，`{baseTemplateId,name}`；复制预置槽位并命名，写为球队自定义模板；名称/来源非法 40001/42201。
- [ ] `LIN-07 PATCH /b/formation-templates/{id}`：权限 `lineup:edit:any`，`{name}`；仅自定义可重命名，预置返回 42201。
- [ ] `LIN-08 DELETE /b/formation-templates/{id}`：权限 `lineup:edit:any`；仅自定义可删，预置返回 42201；软删除（`is_deleted=1`），不物理删除；即使被阵容引用也允许删除，历史阵容仍可按 `formation_template_id` 读取。
- [ ] Flyway V4 的 8 套预置、64 个槽位保持不变；本票不新增/修改预置。
- [ ] B05 模板管理：预置/自定义分组展示；复制预置并命名；自定义重命名/删除；预置的改删按钮禁用并说明；加载中/失败/空数据可用。
- [ ] IT：列表、复制、重命名、删除、预置不可改删、软删除后历史阵容仍可读。
- [ ] Playwright：B05 模板列表 → 复制 → 重命名 → 删除自定义模板；无 console error。
- [ ] 不改 `schema.sql`、不新增 Flyway；`mvn verify` 全绿。

## Result

- 交付：`6238869`。
- 验证：批次收尾 `mvn verify` 全绿（107 单元 + 250 集成）；前端 lint/typecheck 通过；Playwright 14 条黄金路径通过（2026-11-11）。
