# 05 — M06 管理员事件与比分

**What to build:** 管理员在 B04 录入/修改/删除任意事件，软删除并保留历史；独立录入/修正存储比分，与事件推导不一致时只提示；已取消/延期比赛全部只读。

**Blocked by:** 04 — M06 事件核心（球员）.

**Status:** resolved

- [ ] `EVT-06 POST /b/matches/{id}/events`：权限 `event:edit:any`，要求 `Idempotency-Key`；可录入任意类型与任意球员；选人前置与 EVT-02 相同，管理员不豁免；已取消/延期 40905。
- [ ] `EVT-07 PATCH /b/events/{id}`：权限 `event:edit:any`，`EventUpdate + version + Idempotency-Key`；可修改任意事件；选人前置与 EVT-03 相同，管理员不豁免；版本冲突 40901；已取消/延期 40905。
- [ ] `EVT-04 DELETE /b/events/{id}`：权限 `event:delete:any`，`{version} + Idempotency-Key`；软删除：`is_deleted=1` 并写 `deleted_at/deleted_by`；写 `event_history` DELETE；已取消/延期 40905；删除后时间线与推导比分不再计入，原记录仍可追溯。
- [ ] `EVT-05 PATCH /b/matches/{id}/score`：权限 `event:edit:any`，`{scoreHome,scoreAway,version} + Idempotency-Key`；存储比分与事件独立；版本冲突 40901；与推导值不一致只提示不阻断；已取消/延期 40905。
- [ ] 比分响应同时含存储比分与推导比分；比赛详情两者都可见。
- [ ] B04：比赛选择器；比分编辑器；事件时间线支持编辑/删除；录入弹窗含类型/球队/球员/助攻/换上/换下/半场/分钟/点球；换人仅管理员；已取消/延期只读；选人器预留行内错误位（分组校验在票 09 接入）；加载中/失败/空数据可用。
- [ ] 管理员事件删除与比分修正写审计（`EVENT_DELETE`/`SCORE_UPDATE`）。
- [ ] IT：管理员任意事件、删除与历史、软删除后推导比分、存储比分/版本冲突/不一致提示、已取消/延期、选人前置。
- [ ] Playwright B 端黄金路径：登录 → B04 → 录入/编辑事件并保存比分；无 console error。
- [ ] 不改 `schema.sql`、不新增 Flyway；`mvn verify` 全绿。

## Result

- 交付：`e1e6ea1`。
- 验证：批次收尾 `mvn verify` 全绿（107 单元 + 250 集成）；前端 lint/typecheck 通过；Playwright 14 条黄金路径通过（2026-11-11）。
