# 03 — B07 球员

Type: implementation
Status: resolved
Blocked by: 01（B 端壳层，已 resolved）

**What to build:** 按 02 §6.9 全量交付 B07 球员页：列表筛选、表头排序与分页；编辑档案（含 FIL-02 代传头像）；审核通过、外挂转正、注销；换绑手机号与解绑微信。后端 PLR-05/06/07/08/09、AUTH-11/12、FIL-02 已就绪，不新增接口。

## Scope

- 列表：姓名/地区/类型/管理员/状态/位置/星级筛选，点「搜索」生效并回第 1 页；表头 `jersey/skill/position/region` 单列 `sortable="custom"`，默认 `jersey:asc`；分页 10/20/50/100；加载/失败/空态。
- 表格列：号码、头像、姓名、地区、类型、管理员、状态、位置、星级、微信、操作；头像为空或用姓名首字占位。
- 编辑弹窗：号码、姓名、地区、位置、副位置、星级、头像；头像经 FIL-02 上传压缩后的文件、预览后随 PLR-06 提交 `objectKey`；正式球员号码冲突直接提示。
- 审核通过仅 PENDING 显示（`user:approve:any`）；外挂转正仅 ACTIVE GUEST 显示；注销仅 ACTIVE 显示，需填写原因并二次确认；解绑微信仅已绑定显示；换绑手机号仅存在账号时显示。
- 权限：入口 `player:view:any`；编辑/转正/注销/换绑/解绑按 `player:edit:any`，审核按 `user:approve:any`；无权限按钮不渲染。
- 写操作带 `Idempotency-Key`（PLR-07/08/09）；40904/40907/40913 展示明确文案。

## 头像入口说明

`mvp-release/issues/10` 曾把 FIL-02 入口后置到 OSS 批次；M13（FIL-01/02、`OssGateway`）已在 B1 交付，且 `batches.md` §B2 的 03 号票明确包含 FIL-02 上传入口，故本票按 02 §6.9 一并交付，记一条偏差说明。

## Owned files

- `.scratch/mvp-b2-admin/issues/03-b07-players.md`
- `frontend/packages/shared/src/player.ts`
- `frontend/packages/shared/src/avatar.ts`
- `frontend/packages/shared/src/index.ts`
- `frontend/packages/shared/src/index.test.ts`
- `frontend/packages/shared/src/auth.ts`（`PlayerAdmin.userId` 可空）
- `frontend/packages/api-client/src/errors.ts`（补 40904/40913）
- `frontend/apps/web/src/features/profile/avatar.ts`（改为复用 shared，行为不变）
- `frontend/apps/web/src/features/auth/user-status.ts`、`validation.ts`（状态常量与手机号规则改为复用 shared）
- `frontend/apps/admin/src/pages/PlayersPage.vue`
- `frontend/apps/admin/src/router/index.ts`
- `frontend/e2e/admin.spec.ts`
- `scripts/sql/test-data.sql`（B07 球员/角色区块）

## API

- PLR-05 `GET /b/players`
- PLR-06 `PATCH /b/players/{id}`
- PLR-07 `POST /b/players/{id}/approve`
- PLR-08 `POST /b/players/{id}/convert`
- PLR-09 `POST /b/players/{id}/disable`
- AUTH-11 `PATCH /b/users/{id}/phone`
- AUTH-12 `DELETE /b/users/{id}/wechat-binding`
- FIL-02 `POST /b/players/{id}/avatar`

## Test obligations

- Vitest（shared）：类型/状态文案与状态色调；`sort` 映射白名单与默认；档案校验（姓名、号码、地区、位置、副位置、星级）；审核/转正/注销可用性；手机号格式。
- Playwright：B07 筛选 → 编辑（姓名/地区 + 头像上传）保存；只读权限不显示写操作；无 console error。
- `pnpm -r test`、`pnpm -r typecheck`、`pnpm lint`、`pnpm -r build`。

## Result

- 交付：`PlayersPage.vue` 全量 B07（筛选/表头排序/分页、编辑含 FIL-02 头像压缩上传、审核/转正/注销、换绑手机、解绑微信、权限显隐与 40904/40907/40913 文案）；路由挂载 `players`；shared 新增 `player.ts` 与 `avatar.ts`（C 端改为复用 shared，行为不变）；`api-client` 补 `DUPLICATE_JERSEY`/`LAST_ADMIN_PROTECTED`；`PlayerAdmin.userId` 改为可空；`test-data.sql` 为 1003 预置 openid；admin E2E 增加 B07 黄金路径（筛选 → 编辑 → 头像 + 只读权限）。
- 审查修复：头像上传加代际序号，弹窗关闭/切换球员后迟到的压缩或上传结果不会写回（C06 同步补同类守卫）；编辑/换绑/注销弹窗加提交代际令牌，提交中禁用取消、X、ESC 与遮罩，旧响应不会关闭或污染新弹窗；号码仅在填写时提交（匹配 PLR-06 可选契约），已有号码不允许静默清空；`PlayerAdmin` 无账号时的 `phoneMasked/status/createdAt` 标为可空；B07 E2E 改用种子会复位的 1021，并覆盖姓名、地区与头像；`USER_STATUS` 与 `PHONE_PATTERN` 收敛到 shared，筛选状态文案由 `PLAYER_STATUS_TEXT` 派生；状态筛选下的审核/转正/注销后回到第 1 页，避免越界分页。头像上传状态机与 C06 仍有 UI 层重复（共享了纯校验/压缩且已对齐竞态守卫），跨 Vant/Element 的 composable 抽取留待后续。
- 验证：`pnpm --filter @yunhefc/shared test` 28/28；`pnpm -r test`（shared 28、permissions 9、api-client 21、web 93）；`pnpm -r typecheck`、`pnpm lint`、`pnpm -r build` 通过；重放 `test-data.sql` 后 B07 与 C06 Playwright 单独通过；admin 全量 11/12，B07 因 AUTH-15 每手机号 10 次/分钟限流失败于登录，单独重跑通过；此前全量 Playwright 49/51，另有两条 admin 1002 用例同为限流、单独重跑通过。
- 偏差：按 `batches.md` §B2 与 02 §6.9 一并交付 FIL-02 上传入口（`mvp-release/issues/10` 的延后前提已被 B1 的 M13 交付满足）；顺带修复 B06 E2E 在分组加载空态过渡中的竞态（等待 loading 消失后再判断是否清除）。
- 未执行：无后端/DB 表结构/Flyway 改动；按批准重放 `test-data.sql`；未 push。
