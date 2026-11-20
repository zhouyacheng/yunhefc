# 04 — 状态机与手动状态修改（MAT-06 + MAT-12 普通状态）

**What to build:** 管理员在后台或 C 端详情把比赛重置为任意普通状态，含回退与终态恢复；手动修改后停止自动流转，可显式恢复自动流转。POSTPONED 与 CANCELLED 不在此接口处理，分别交给 MAT-07/MAT-08。

**Blocked by:** 01 — 建赛与后台读取.

**Status:** ready-for-agent

- [ ] 读 05 MAT-06/12、03 §5.4、需求 MATCH-04/06、06 P-02/P-10。
- [ ] 领域状态机：7 个状态；自动流转只前进；手动重置允许任意普通状态，含回退与终态恢复。
- [ ] `POST /api/v1/b/matches/{id}/status`，权限 `match:status:any`，请求 `{status,version,manualStatus?}` + `Idempotency-Key`；响应 `MatchAdmin`。
- [ ] 该接口拒绝 POSTPONED/CANCELLED（返回 40905），二者分别走 MAT-07/MAT-08。
- [ ] 手动修改默认置 `manualStatus=true` 并停止自动流转；`manualStatus=false` 恢复自动流转；更新 `statusUpdatedAt/statusUpdatedBy`。
- [ ] `POST /api/v1/c/matches/{id}/status`，权限 `match:status:any`：普通状态复用 MAT-06 逻辑；仅管理员可用；响应 `MatchAdmin`。POSTPONED/CANCELLED 分支由 05 票接入。
- [ ] `version` 不匹配 40901 `VERSION_CONFLICT`；状态不允许 40905 `MATCH_STATE_CONFLICT`（如已取消/已结束的非法操作）。
- [ ] 写审计前后快照。
- [ ] 测试：任意状态重置/回退/终态恢复、manualStatus 开关、POSTPONED/CANCELLED 拒绝、版本冲突、C 端入口、权限与幂等键、审计。
