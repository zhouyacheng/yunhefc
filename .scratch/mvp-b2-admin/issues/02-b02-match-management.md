# 02 — B02 比赛管理

Type: implementation
Status: resolved
Blocked by: 01（B 端壳层，已 resolved）

**What to build:** 按 02 §6.4 全量补齐 B02：列表保留筛选/分页并增加操作列；创建（MAT-04）、编辑（MAT-05）、普通状态重置（MAT-06）、延期（MAT-07）、重新排期（MAT-11）、取消二次确认（MAT-08）；前端校验抽到 `packages/shared/src/match.ts` 并在页面复用。

## Scope

- `MatchesPage` 增加「操作」列与新建入口。
- 创建/编辑/延期/重新排期弹窗；状态下拉含全部状态，选择已延期/已取消分别进入对应流程。
- 权限按 `match:create:any` / `match:edit:any` / `match:status:any` 控制按钮显隐；页面入口仍为 `match:view:any`。
- 写操作前用 MAT-10 取权威详情与 `version`；写请求带 `Idempotency-Key` 与 `version`；成功后刷新 MAT-09。
- 前端校验：开赛晚于当天 10:00、时长 2–4 小时、正式/外挂名额不低于已确认人数、终态不可编辑、POSTPONED 仅可重排、赛季四位年份。
- 40901 提示「已被他人修改，请刷新后重试」并刷新列表。

## Owned files

- `frontend/apps/admin/src/pages/MatchesPage.vue`
- `frontend/packages/shared/src/match.ts`
- `frontend/packages/shared/src/index.test.ts`
- `frontend/e2e/admin.spec.ts`

## API

- MAT-04 `POST /b/matches`、MAT-05 `PATCH /b/matches/{id}`、MAT-06 `POST /b/matches/{id}/status`、MAT-07 `POST /b/matches/{id}/postpone`、MAT-08 `POST /b/matches/{id}/cancel`、MAT-09 `GET /b/matches`、MAT-10 `GET /b/matches/{id}`、MAT-11 `POST /b/matches/{id}/reschedule`。

## Test obligations

- Vitest（shared）：时间/时长、名额、终态可编辑、延期可重排、赛季格式。
- Playwright 黄金路径：创建 → 编辑 → 延期 → 重新排期 → 取消；无 console error；权限按钮按 1002/1001 显隐至少覆盖只读不可写。

## Result

- 交付：`665406a`（列表保留筛选/分页并增加操作列与新建入口；创建/编辑/状态重置/延期/重新排期/取消弹窗；写前 MAT-10 取权威 `version`，除 MAT-05 外带幂等键；40901 提示「已被他人修改，请刷新后重试」并刷新，40905 提示后端消息并刷新；复用并补齐 shared 校验 `matchCancellable`；Playwright 黄金路径与只读 `/b/me` 拦截用例；`test-data.sql` 重放时清理 `E2E比赛%`）。
- 验证：`pnpm --filter @yunhefc/shared test` 23/23；`pnpm -r test`（shared 23、api-client 21、web 93，admin 无单测）、`pnpm -r typecheck`、`pnpm lint`、`pnpm -r build` 通过；重放 `scripts/sql/test-data.sql` 后 admin Playwright 11/11、全量 Playwright 48/50（两条 C 端既有用例因共享套件登录限流/邀请 403 偶发失败，单独重跑均通过，与本票改动无关）。
- 偏差：MAT-05 按 05/后端契约不发送 `Idempotency-Key`（票面「写请求都带」与实现不符）；MAT-06 的「恢复自动流转（`manualStatus=false`）」未在本票做，留待后续；创建名额默认值仍取 04 系统默认 30/4/2，未读取 SET-02 实时配置。
- 未执行：无后端/DB/schema/Flyway 改动；未 push；已按批准重放 `test-data.sql` 清理本地 E2E 比赛与分组状态。
