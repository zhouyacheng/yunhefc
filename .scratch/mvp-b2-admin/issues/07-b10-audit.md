# 07 — B10 操作日志

Type: implementation
Status: ready-for-agent
Blocked by: 无

**What to build:** 按 02 §6.12 交付只读操作日志页：筛选、时间倒序分页、脱敏后的前后快照展开；不提供导出/编辑/删除。

## Scope

- 路由：`sectionComponent('audit')` 指向新 `AuditLogPage.vue`。
- 入口 `audit:view:any`；无权限不进入该分区（路由守卫已覆盖）。
- 筛选：时间范围（`el-date-picker` datetimerange，提交转 UTC ISO）、操作人（文本，姓名或用户 ID）、动作、对象类型、对象 ID（数字）；点「搜索」生效并回第 1 页；「重置」清空并重新请求。
- 动作/对象类型使用 `el-select filterable allow-create clearable`，选项来自共享标签表。
- 表格列：时间、操作人、动作、对象类型、对象 ID、IP；固定 `created_at DESC`；分页 10/20/50/100。
- 展开行展示 `beforeRedacted`/`afterRedacted` 格式化 JSON；两者都为空显示「无变更快照」。
- 共享 `frontend/packages/shared/src/audit.ts`：`AuditLog`/`AuditActor` 类型、38 个动作码与 11 个对象类型中文标签；未知码原样显示。
- `scripts/sql/test-data.sql` 追加 B10 fixtures：固定 ID 6001–6003，按 `audit_log` 列写入；一条 `SETTINGS_UPDATE` 的 before/after 含手机号原文，一条未知动作码 `E2E_UNKNOWN_ACTION`；区块开头 `DELETE FROM audit_log WHERE id BETWEEN 6001 AND 6003;`。
- 加载中占位、失败重试、空态；无 console error。

## Owned files

- `frontend/apps/admin/src/router/index.ts`
- `frontend/apps/admin/src/pages/AuditLogPage.vue`
- `frontend/packages/shared/src/audit.ts`
- `frontend/packages/shared/src/index.ts`
- `frontend/e2e/admin.spec.ts`
- `scripts/sql/test-data.sql`（B10 区块）

## API

- AUD-01 `GET /b/audit-logs`

## Test obligations

- Vitest（shared）：动作/对象类型标签映射；未知码回退原值。
- Playwright：打开操作日志 → 筛选动作 `SETTINGS_UPDATE` → 展开行 → 断言手机号掩码（如 `139****0001`）且无 console error。
- 重放 `scripts/sql/test-data.sql` 后 admin 套件通过。
- `pnpm -r test`、`pnpm -r typecheck`、`pnpm -r build`。

## Result

- 交付：`AuditLogPage.vue`（AUD-01 筛选：时间范围转 UTC ISO、操作人、动作/对象类型可搜索可输入、对象 ID；时间倒序表格、展开行 JSON 快照、10/20/50/100 分页、加载/失败重试/空态；只读无导出/编辑/删除）；路由 `audit` 指向新页；shared 新增 `audit.ts`（`AuditLog`/`AuditActor`、38 个动作码与 11 个对象类型中文标签、未知码回退、空快照判定）并导出。
- Fixtures：`test-data.sql` 新增 B10 区块 6001–6003（含手机号原文的 `SETTINGS_UPDATE`、普通 `MATCH_CREATE`、未知动作/对象类型），已重放开发库。
- 决策：标签文案后端与设计文档均未定义，按语义落表并在本 Result 记录；E2E 额外按操作人 `E2E审计员` 锁定 6001，避免运行期 `SETTINGS_UPDATE` 干扰；B09 用例改用 1022 管理员分摊 AUTH-15 限流。
- 验证：shared 40、permissions 9、api-client 21、web 93；`pnpm -r typecheck`、`pnpm -r build`、`pnpm lint` 通过；admin Playwright 21/21 通过（含 B10）。
- 审查修复：`AuditActor.name` 改为可空并区分「用户 #id」与「系统」；E2E 先用 allow-create 筛出未知动作码，避免 fixture 因分页漂移；展开行改为按非空快照列表渲染。
- 未执行：无后端/schema/Flyway 改动；未 push。
