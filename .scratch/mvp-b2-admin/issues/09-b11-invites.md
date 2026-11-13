# 09 — B11 邀请管理

Type: implementation
Status: resolved
Blocked by: `mvp-b1-backend/issues/10`（邀请链接路径修复，已随 `563eeec` 完成）

**What to build:** 按 02 §6.13 交付邀请管理页：列表、创建入队/比赛邀请、创建成功弹窗复制链接、行内撤销；列表不提供复制。

## Scope

- 路由：`sectionComponent('invites')` 指向新 `InvitesPage.vue`。
- 入口 `invite:manage:any`。
- 列表 INV-04：类型、关联比赛、邀请人、有效期、已用/上限（`maxUses` 为空显示「不限」）、状态；固定 id 倒序；仅分页，不加筛选 UI。
- 状态展示：`status=1` 已撤销；`status=2` 或 `expiresAt < now` 已过期；`exhausted` 已用尽；否则有效。
- 创建弹窗：类型入队/比赛；比赛邀请用 `publicApi` 的 `GET /c/matches?scope=upcoming&pageSize=100&sort=startAt:asc`，前端只保留报名中/报名截止/锁定；有效期 1–30 默认 7；次数上限留空为不限（≥1）。
- INV-05 + `Idempotency-Key`；成功弹窗展示 `inviteUrl` 与复制按钮，剪贴板失败时展示可选中的 URL。
- 撤销：二次确认 → INV-06 + `Idempotency-Key` → 刷新列表。
- 列表行只保留撤销，不提供复制（token 只返回一次）。
- 错误映射：40001 展示后端消息，40401 刷新列表。
- 共享 `frontend/packages/shared/src/invite.ts`：状态展示 helper；复用 `auth.ts` 现有 `Invite` 类型。
- 加载/失败重试/空态齐备。

## Owned files

- `frontend/apps/admin/src/router/index.ts`
- `frontend/apps/admin/src/pages/InvitesPage.vue`
- `frontend/packages/shared/src/invite.ts`
- `frontend/packages/shared/src/index.ts`
- `frontend/e2e/admin.spec.ts`

## API

- INV-04 `GET /b/invites`
- INV-05 `POST /b/invites`
- INV-06 `POST /b/invites/{id}/revoke`
- MAT-02 `GET /c/matches`（比赛选择器，公开接口）

## Test obligations

- Playwright：创建入队邀请 → 结果弹窗复制 → 关闭 → 列表行撤销 → 状态变更；无 console error。
- 比赛邀请未选择比赛时阻止提交并提示。
- 复用种子邀请 5001–5005；UI 创建的邀请只撤销，不额外清理。
- `pnpm -r test`、`pnpm -r typecheck`、`pnpm -r build`。

## Result

- 交付：`InvitesPage.vue`（INV-04 分页列表，类型/关联比赛/邀请人/有效期/已用上限/状态/操作；创建弹窗支持入队与比赛邀请，比赛选择器走 MAT-02 `scope=upcoming` 并保留报名中/报名截止/锁定；有效期 1–30 默认 7、上限留空不限；INV-05/06 均带 `Idempotency-Key`；成功弹窗只读 URL + 复制，剪贴板失败选中文本；行内撤销二次确认、已撤销禁用；40001 展示后端消息、40401 刷新列表；加载/失败重试/空态齐备）。
- 路由：`sectionComponent('invites')` 指向新页；分区、菜单与后端 `sections` 映射沿用既有实现。
- shared 新增 `invite.ts`：`inviteTypeText`、`inviteStatusText`/`inviteStatusTone`（撤销 > 过期 > 用尽 > 有效，过期按当前时间重算）、`inviteQuotaText`（`maxUses=null` 显示「不限」）、`inviteMatchTitle` 与 `InviteCreated`/`InviteMatchRef` 类型；已从入口导出。
- 测试：shared 新增邀请展示用例（状态优先级、过去时间但 `status=0`、不限次数、比赛标题缺失）；Playwright 新增「创建入队邀请 → 复制链接 → 关闭 → 撤销 → 状态变更」与「比赛邀请未选比赛阻止提交」两条。
- 评审修正：创建弹窗打开时生成幂等键、创建成功后清空，网络超时重试不会重复建邀请；列表每 60 秒刷新一次当前时间，页面停留期间过期状态自动更新。
- 验证：`pnpm --filter @yunhefc/shared test` 47/47；`pnpm -r test`、`pnpm -r typecheck`、`pnpm -r build`、`pnpm lint` 全绿；admin Playwright 23/23 通过（B11 两条包含在套件内）。
- 环境说明：本地后端为验证 B1 邀请链接修复已用 JDK 21 重新编译并重启（`mvnw spring-boot:run`，dev profile）。全量 web Playwright 存在既有测试数据漂移（赛程 9 张而非 5 张、首页下一场已随时间变化），与本票无关，未重跑 `test-data.sql`。
- 未执行：无后端/DB/Flyway/契约改动；未执行 SQL；未 push。
