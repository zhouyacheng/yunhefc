# 04 B06 报名球员按 A/B 两列展示

Status: ready-for-agent
Blocked by:

## 问题

B06 有分组时，“本场确认报名”区域按**正式球员 / 外挂球员**两列展示，A/B 分组只体现在每行徽标上（`frontend/apps/admin/src/pages/GroupingPage.vue:848-876`）。

设计与原型要求有分组时按 **A（橙队）/ B（兰队）** 两列展示：

- `docs/design/02.页面结构与交互设计.md:419`（§6.8）：展示本场所有确认报名球员；有分组时按结果分两列展示，未分组/不参与单独列出。
- `docs/design/06.设计方案评审结果.md:296`（§18，v1.14）：有分组时按 A/B（橙队/兰队）两列展示。
- `prototype/assets/js/admin.js:1259-1265`：有分组时按 A/B 两列，无分组时单列表。

影响：管理员无法按列直接核对分组结果，只能逐行看徽标，与设计/原型不一致。

## 修复

- `GroupingPage.vue` 报名区：存在分组时，两列改为 A / B，标题沿用页面既有格式 `{{ team }} 队 · {{ groupingTeamName(selectedMatch, team) }}`。
- 行数据从 `confirmedRoster` 按 `groupedTeamByPlayer` 过滤；行内保留正式/外挂及“球队球员/外挂”标识。
- 未分组、不参与分组区块维持现状，且不得在 A/B 列中重复出现。
- 无分组时维持现有展示（正式/外挂两列），不扩大改动范围。
- 扩展 `frontend/e2e/admin.spec.ts` 的 B06 用例：在 `.roster-section` 内断言橙队/兰队两列，且 A 列球员与 `.group-admin-column` 的 A 队成员一致。

## 验收

- 有分组时 `.roster-section` 出现橙队/兰队两列，列内球员归属与分组调整区一致。
- 未分组/不参与仍单独展示，不重复出现在 A/B 列。
- 无分组时页面行为不变。
- `pnpm --filter @yunhefc/admin typecheck`、admin 单测、B06 E2E 通过，无 console error。
- 设计文档与原型无需改动（已是正确口径）。

## Comments

- 2026-11-17 由分组实现核对出票，仅登记偏差，未改代码。
