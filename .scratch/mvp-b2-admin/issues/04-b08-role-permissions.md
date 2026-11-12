# 04 — B08 角色与权限（角色 CRUD + 权限勾选）

Type: implementation
Status: resolved
Blocked by: 无

**What to build:** 按 02 §6.10 交付 B08 的角色与权限部分：左侧角色列表，右侧按分组勾选权限；支持新建、重命名、删除角色与保存权限。用户角色分配由 05 号票追加到同一页面。

## Scope

- 路由：`sectionComponent('roles')` 指向新 `RolesPage.vue`，替换 `SectionPage`。
- 入口权限 `role:view:any`；`role:manage:any` 才渲染新建/重命名/删除/保存按钮，否则整页只读。
- 左栏：ROL-01 `{items}`；每行展示名称、code、默认/自定义标签；默认角色可改名、不可删除（40914）；被用户占用删除返回 40908。
- 右栏：ROL-06 `{items}`，按 7 组展示（比赛、报名、考勤、事件、阵容/分组、球员/权限、邀请）；未知权限码回退到 `resource` 组；勾选后 ROL-05 保存，带 `Idempotency-Key`。
- 新建角色：ROL-02；code 前端校验 `^[A-Z][A-Z0-9_]{0,31}$`（后端只有非空与唯一校验），name ≤64；40912 提示编码重复；创建后选中新角色。
- 重命名：ROL-03；删除：ROL-04，二次确认。
- 权限保存至少勾选 1 项（ROL-05 `@NotEmpty`），否则提示「至少选择一个权限」。
- 切换角色时以服务端数据重渲染右侧，未保存勾选直接丢弃（与原型一致，不引入额外守卫）。
- 错误映射：40001 展示后端消息；40301 提示无权限；40401 刷新角色列表。
- 加载/失败重试/空态齐备。

## Owned files

- `frontend/apps/admin/src/router/index.ts`
- `frontend/apps/admin/src/pages/RolesPage.vue`
- `frontend/packages/shared/src/role.ts`
- `frontend/packages/shared/src/index.ts`
- `frontend/e2e/admin.spec.ts`

## API

- ROL-01 `GET /b/roles`
- ROL-02 `POST /b/roles`
- ROL-03 `PATCH /b/roles/{id}`
- ROL-04 `DELETE /b/roles/{id}`
- ROL-05 `PUT /b/roles/{id}/permissions`
- ROL-06 `GET /b/permissions`

## Test obligations

- Vitest（shared）：28 个权限码映射到 7 组；未知码回退 `resource`。
- Playwright：登录 → `/admin/roles` → 新建 `E2E_<时间戳>` 角色 → 勾选权限 → 保存 → 重载后仍勾选 → 重命名 → 删除；无 console error。
- 权限场景：拦截 `/api/v1/b/me` 仅给 `role:view:any`，写操作按钮数量为 0。
- `pnpm -r test`、`pnpm -r typecheck`、`pnpm -r build`。

## Result

- 交付：`RolesPage.vue` 全量 B08（左栏 ROL-01 角色列表与默认/自定义标签、右栏 ROL-06 按 7 组勾选、新建/重命名/删除/保存权限、权限显隐与只读模式、加载失败重试与空态、40908/40912/40914 展示后端消息）；路由 `roles` 指向新页；shared 新增 `role.ts`（`PermissionView`、7 组映射、`groupPermissions`、`roleCodeError`/`roleNameError`）并导出。
- 决策：role code 前端正则对齐后端 `^[A-Z][A-Z0-9_]{1,31}$`（票面 `{0,31}` 与后端不一致，经确认取后端）；按 06 §21 在保存 ADMIN 时提交全部 28 个权限码（勾选框保持可点，页面给出说明文案）。
- 错误映射：40401 提示后重新拉取角色列表；40301 提示「无权限执行此操作」；40001/40908/40912/40913/40914 展示后端 `message`。ROL-05 带 `Idempotency-Key`；ROL-02/03/04 按 05 §3.13 不带幂等键。
- 测试：shared 新增 3 条用例（28 码→7 组、未知 resource 回退、code/name 校验），其中补一条 resource 与 code 前缀不一致的判别用例；Playwright 新增 B08 黄金路径（新建 → 空勾选提示 → 勾选保存 → 重载仍勾选 → 重命名 → 删除）与只读权限用例；现有 admin 套件通过。
- 审查修复：权限目录为空时右侧显示空态；40301 改用 `api-client` 的 `isObjectForbidden`；ADMIN 编码收敛为 shared `ADMIN_ROLE_CODE`；E2E `loginAdmin` 支持自定义登录落点，去掉只读用例重复的登录步骤。
- 验证：`pnpm -r test`（permissions 9、shared 31、api-client 21、web 93）、`pnpm -r typecheck`、`pnpm -r build`、`pnpm lint` 通过；admin Playwright 最终 14/14 通过（含 B08 两条；中途连续重跑时 1001 登录触发 AUTH-15 限流，等待窗口后复跑全绿）。
- 未执行：无后端/DB/Flyway 改动；未重放 `test-data.sql`；未 push。全量 C 端套件存在既有测试数据漂移导致的失败（多出比赛/球员），与本票无关。
