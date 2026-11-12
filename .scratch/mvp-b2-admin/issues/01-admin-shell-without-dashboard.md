# 01 — B 端壳层：下线 dashboard

Type: implementation
Status: resolved
Blocked by: —

**What to build:** 按 B2 决策从 B 端壳层移除 dashboard：`ADMIN_SECTIONS`、分区权限映射与标签不再含 dashboard；`/admin` 与登录后都落到首个有权限分区，不固定跳比赛管理；补权限单测与根路径回归用例。

## Scope

- `frontend/packages/permissions`：`ADMIN_SECTIONS` 移除 `dashboard`；`ADMIN_SECTION_PERMISSIONS` 同步；`canAccessSection` 去掉 dashboard 特判。
- `frontend/apps/admin/src/sections.ts`：移除 `dashboard: '工作台'`。
- `frontend/apps/admin/src/router/index.ts`：根路径不再静态重定向 `/dashboard`，改为会话加载后按 `session.homePath` 跳转。
- `frontend/apps/admin/src/stores/session.ts`：`homePath` 只取首个有权限分区，去掉 matches 特判。
- `frontend/e2e/admin.spec.ts`：1002 登录后落 `/admin/registrations`；直访 `/admin` 同样落首个有权限分区。

## Owned files

- `frontend/packages/permissions/src/index.ts`
- `frontend/packages/permissions/src/index.test.ts`
- `frontend/apps/admin/src/sections.ts`
- `frontend/apps/admin/src/router/index.ts`
- `frontend/apps/admin/src/stores/session.ts`
- `frontend/e2e/admin.spec.ts`

## Test obligations

- Vitest：`ADMIN_SECTIONS` 为 10 项且不含 dashboard；`canAccessSection` 对非 dashboard 分区按权限判断。
- Playwright：侧栏过滤用例覆盖 `/admin` 根路径落到 1002 的首个有权限分区。

## Result

- 交付：`a585400`（`ADMIN_SECTIONS`/权限映射/`SECTION_LABELS` 移除 dashboard；根路径改为 `home` 占位路由，由守卫按 `session.homePath` 跳首个有权限分区；`homePath` 去掉 matches 特判；permissions 单测改 10 分区并断言不含 dashboard；admin E2E 增加 `/admin/` 根路径回归）。
- 验证：`pnpm -r test`（permissions 9 项、web 93 项）、`pnpm -r typecheck`、`pnpm lint`、`pnpm -r build` 通过；重放 `test-data.sql` 后 Playwright `--project=admin` 9/9 通过（2026-11-12，30.4s）。
- 偏差：验证时发现相邻权限缺陷（报名页用 B 端 `/b/matches` 拉比赛列表），另开票 08 修复。
- 未执行：B2 其余页面票（B02/B07–B11）；无后端与 DB 改动。
