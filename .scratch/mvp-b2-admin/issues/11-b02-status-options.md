# 11 — B02 状态下拉按后端合法集禁用

Type: implementation
Status: resolved
Blocked by: 无

**What to build:** B02 管理状态下拉当前列出全部状态且不置灰，用户可选中后端必然拒绝的目标（延期有源状态限制、取消有终态限制）。改为按后端合法集禁用并给出原因，与 B4 的 C03 口径一致。

## Scope

- `MatchesPage.vue` 状态下拉：普通状态 0–4 恒可（含从终态恢复）；延期 5 仅当前状态为 0–3 且当前账号有 `match:edit:any`；取消 6 在当前状态为 4/6 时禁用；当前状态保持选中。
- 禁用项通过 `title` 给出原因：`需要编辑比赛权限` / `当前状态不可延期` / `当前状态不可取消`。
- 判定逻辑抽为纯函数（放 `frontend/packages/shared/src/match.ts` 或 admin 本地模块），Vitest 覆盖 7 个当前状态 × 7 个目标的最小矩阵。
- 不改后端、不改接口/权限码/错误码、不改 02/05 契约。

## Owned files

- `frontend/apps/admin/src/pages/MatchesPage.vue`
- `frontend/packages/shared/src/match.ts`（若放共享层）
- `frontend/packages/shared/src/match.test.ts` 或 admin 对应单测
- `frontend/e2e/admin.spec.ts`

## Test obligations

- Vitest：SIGNUP_OPEN/SIGNUP_CLOSED/LOCKED/IN_PROGRESS 下延期可选；FINISHED/CANCELLED/POSTPONED 下延期禁用；FINISHED/CANCELLED 下取消禁用；无 `match:edit:any` 时延期禁用；0–4 恒可选。
- Playwright：B02 黄金路径补一条断言——已结束比赛延期选项禁用、已取消比赛取消选项禁用，且页面无 console error。
- `pnpm -r test`、`pnpm -r typecheck`、`pnpm -r build` 全绿。

## Comments

- 2026-11-14 交付：状态合法集抽为 `@yunhefc/shared` 的 `matchStatusCommandOptions({currentStatus, canEditAny, canChangeStatus})`，返回 `{status,label,disabled,reason}`；`MatchesPage.vue` 用它渲染下拉，禁用项带 `title` 原因。
- 偏差：保留原实现「普通状态 0–4 与取消需 `match:status:any`」的门禁，缺权限时原因文案为 `需要状态管理权限`（票面只列了 3 条状态类原因）。移除该门禁会让仅有 `match:edit:any` 的账号点到后端必然 403 的项，属回归，故保留。
- 未动 C03：`apps/web/src/features/match-detail/status-options.ts` 仍有同构实现，文案为 `已结束或已取消的比赛不可取消`（B 端按票面用 `当前状态不可取消`）；按票面 Owned files 不触碰，如需合并另开票。
- 验证：`pnpm -r test` 168 passed（新增 `matchStatusCommandOptions` 7 项，含 7×7 矩阵）；`pnpm -r typecheck`、`pnpm -r build` 全绿；`playwright test e2e/admin.spec.ts --project=admin` 23 passed，其中 B02 黄金路径补「已结束不可延期 / 已取消不可再取消」断言（禁用项 `title` 文案）且无 console error。
- 未执行 SQL、未改后端、未改接口/权限码/契约。
