# Review 遗留项（2026-11-17）

## 事实源

- 代码审核范围：`40ca455^..HEAD`（5 个提交），两轴结论见会话记录。
- 设计依据：`docs/design/06.设计方案评审结果.md` §72–75（P-48/P-49/P-50/P-51）、`docs/design/02.页面结构与交互设计.md` §5.1/§6.4、`docs/design/05.API接口设计.md` MAT-02/05/07/11。
- 已修复项随 `411234f` 提交：V6 文档同步、`test-data.sql` 注释、`matchEditable` 孤儿导出、`manualStatus` 断言、`FlywayBackfillIT` 参数化。

## 票序

1. `issues/01-e2e-fixture-replay-reset.md` — 本地 E2E 种子重放复位（`resolved`）
2. `issues/02-hero-registration-refresh-e2e.md` — Hero 报名/静默刷新失败 E2E 覆盖（`resolved`，seam B）
3. `issues/03-history-section-naming.md` — 「历史赛程」文案与语义对齐（`resolved`，文案定为「最近赛程」）
4. `issues/04-b06-roster-ab-columns.md` — B06 报名球员按 A/B 两列展示（`ready-for-agent`）

## 约束

- 设计冻结：不改 DB/API 契约、权限码、错误码与接口数量。
- 已发布的 Flyway 迁移不可修改（checksum 不可变）。
- `.scratch/` 不提交 git。

## 未出票的判断项

- 行操作分发在 `MatchesPage.vue`/`PlayersPage.vue`/E2E helper 中重复：页面命令集不同，抽象收益低。
- V6 时区表达式重复：迁移已发布，不可改。
- `FlywayMigrationIT` 硬编码迁移数：用于强制每次新增迁移时人工复核，有意保留。
- B07 操作列收窄、C03 下拉占位项、Hero 已结束标签样式：均为已提交的用户请求改动，不回退。
