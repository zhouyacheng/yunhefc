# 05 — B08 用户角色分配

Type: implementation
Status: resolved
Blocked by: 04（RolesPage 与角色/权限数据已就绪）

**What to build:** 在 B08 页面下方增加「用户角色分配」卡片：按姓名/手机号搜索用户，查看当前角色并保存 ROL-07。

## Scope

- 卡片仅在 `role:manage:any` 时渲染（AUTH-13 自身要求该权限）；无权限时不请求 `/b/users`。
- 搜索：AUTH-13 `keyword,status,page,pageSize`；关键词去首尾空格；分页 10/20/50/100；搜索生效回第 1 页。
- 用户表格列：姓名、脱敏手机号、状态、当前角色、球员类型；默认不按状态过滤。
- 选中用户后用 ROL-01 角色渲染复选框，当前角色预选；保存 ROL-07，请求体 `{roles: number[]}`（角色 ID）+ `Idempotency-Key`。
- 前端守卫（与后端一致）：至少一个角色；GUEST/PLAYER 互斥；ADMIN 必须叠加 PLAYER；40913 提示「不能移除最后一个管理员」。
- 类型一致守卫：`type=0`（外挂）只能保留 GUEST + 自定义角色；`type=1`（正式）只能保留 PLAYER + 自定义/ADMIN；尝试外挂↔正式切换时提示「外挂转正请到 B07 球员页操作」。
- 保存成功后刷新该行用户数据与角色列。
- 后端已知缺口：ROL-07 不同步 `player.type`、不校验号码冲突；本票只在 UI 拦截，不改后端（记入 Result 偏差）。

## Owned files

- `frontend/apps/admin/src/pages/RolesPage.vue`
- `frontend/e2e/admin.spec.ts`

## API

- AUTH-13 `GET /b/users`
- ROL-01 `GET /b/roles`
- ROL-07 `PUT /b/users/{id}/roles`

## Test obligations

- Playwright：搜索用户 → 给正式球员 1002 分配 ADMIN → 保存 → 行内角色更新 → 撤回 ADMIN 复原。
- 对唯一管理员 1001 取消 ADMIN → 显示 40913 文案。
- 搜索外挂 1016 勾选 PLAYER → 前端拦截并提示去 B07。
- 权限场景：拦截 `/api/v1/b/me` 仅有 `role:view:any` 时卡片不存在。
- `pnpm -r test`、`pnpm -r typecheck`、`pnpm -r build`。

## Result

- 交付：`RolesPage.vue` 追加用户角色卡片（AUTH-13 姓名/手机号搜索、角色勾选、ROL-07 带 `Idempotency-Key` 保存），仅 `role:manage:any` 渲染；前端拦截外挂/正式互斥、管理员必须叠加正式、外挂转正提示去 B07；40913 映射「不能移除最后一个管理员」。
- 测试：Playwright 覆盖分配 ADMIN → 撤回、最后管理员保护、外挂转正拦截、只读权限卡片不渲染。
- 验证：随 `fac24dc` 交付；`pnpm -r test`、`pnpm -r typecheck`、`pnpm -r build` 通过。
- 已知偏差：后端 ROL-07 不同步 `player.type`、不校验号码冲突，本票仅在 UI 拦截。
