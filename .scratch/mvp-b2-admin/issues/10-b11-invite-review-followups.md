# 10 — B11 邀请管理评审后续项

Type: implementation
Status: resolved
Blocked by: 无

来源：`/code-review` 对 B11（`abf5f26`）的 15 条发现；其中 3 条高危（token 丢失/幂等键/路由卸载）已修复，见 09 的 Comments。本票收剩余中低问题，不改变 INV-04/05/06 契约。

## Scope

### 列表与创建交互
- 创建成功后把 `page` 重置为 1 再刷新；否则第 2 页及以后看不到新增行（MatchesPage 已有同款处理）。
- 比赛下拉不要永久缓存：每次打开「比赛邀请」强制刷新，或增加显式刷新；仍只保留报名中/截止/锁定状态。后端 INV-05 若允许，补「已结束/已取消比赛不可邀请」校验。
- `createDialog.error` 在相关字段变化时清空，避免修正字段后仍显示旧错误。

### 共享代码去重
- `frontend/packages/shared/src/invite.ts`：合并 `inviteStatusText`/`inviteStatusTone` 为一次 `inviteStatusMeta(invite, now)`；删除与 `apps/web/src/features/invite/invite-state.ts` 重复的 `inviteTypeText`，web 改为从 shared 导入。
- `inviteMatchTitle` 去掉 `as InviteMatchRef` 强转，并把 INV-04 的 match/inviter/team 形状与可空性统一声明到 `shared/auth.ts`（后端 `InviteView` 的 inviter/team 可为 null）。
- 把页面内 4 处内联 `isApiError(caught) ? caught.message : fallback` 与 MatchesPage/PlayersPage 的 `describeError` 收敛到 api-client 或 shared。
- 评估抽取 admin 第 7 份分页样板为 `usePagedList`（loading/error/requestSeq/rows/total），仅当能明显减少重复时再做。

### E2E 稳定性
- B11 创建用例：POST 完成后再等待包含新 id 的列表 GET，避免消费初始加载的旧响应；或直接断言行内容而非依赖首行。
- 429 重试会留下 console error：`collectConsoleErrors` 对限流重试路径做过滤（参考现有 409 过滤），或降低 B11 登录次数。
- 比赛邀请用例 `.dialog-error` 选择器会同时命中 `matchesError` 与 `createDialog.error`，改为限定在提交错误区域。

### 配置默认值
- `application.yml` 的 `yunhefc.public-base-url` 默认 `http://localhost:5173` 与 dev 前端 5273 不一致；`application-prod.yml` 也未强制 `PUBLIC_BASE_URL`。确认是否把 prod 改为必填并补启动自检；dev 默认值与 CORS 的 5173/5174 一并核对。

## Test obligations

- 上述交互项有对应 E2E 或组件级用例；共享去重后 `pnpm -r test`/`typecheck`/`build`/`lint` 通过。
- E2E 在套件末尾连续运行两次均通过（覆盖登录限流窗口）。

## Out of Scope

- 已修复的 3 条高危：创建成功弹窗不可误关、幂等键+请求体原样重试、创建在途阻止路由离开。
- INV-04/05/06 接口契约与后端邀请表结构。

## Result

- 列表与创建：创建成功后 `page=1` 再刷新；比赛下拉不再缓存，每次切入「比赛邀请」都重新请求 MAT-02，仍只保留报名中/截止/锁定；`createDialog.error` 在类型/比赛/有效期/上限变化时清空（结果未知的冻结态除外）。
- 共享代码：`shared/auth.ts` 声明 INV-04 的 `InviteMatchSummary`，`team`/`inviter` 按后端改为可空，`match` 改为强类型；`invite.ts` 删除 `InviteMatchRef` 强转，`inviteStatusText`/`inviteStatusTone` 合并为 `inviteStatusMeta(invite, now)`；web `invite-state.ts` 删除重复的 `inviteTypeText`，页面改从 shared 导入。
- 错误文案：api-client 新增 `describeError`，InvitesPage 4 处内联、MatchesPage/PlayersPage 的本地函数与剩余 3 处内联统一收敛。
- 分页样板：评估后不抽 `usePagedList`——7 份实现各自带筛选/排序/时间刷新与不同的请求参数，抽出的泛型会把差异挤进参数，未明显减少重复，保持现状。
- E2E：B11 创建用例先读取初始 `total`，只等待 `total+1` 的列表响应，避免消费初始加载旧响应；提交错误改用 `.create-error` 限定区域；只有登录确实发生 429 重试时才忽略对应资源错误。B11 连续运行两次通过。
- 配置：dev `public-base-url` 与 CORS 默认值改为实际端口 5273/5274；prod `PUBLIC_BASE_URL` 改为必填并纳入 `ProdStartupValidator`（必须与 CORS 的 `https://www.<domain>` 完全一致、拒绝首尾空白），补充单测；`release.sh` 发布前预检；03/06 与 `deploy/RUNBOOK.md` 同步。
- 评审修正：比赛下拉刷新后若已选比赛不再可邀请则清空 `matchId`；`AppProperties` 移除与 `application.yml` 重复的默认值。
- 未做：后端 INV-05 未新增「已结束/已取消比赛不可邀请」校验——该规则未写入 05/02，属于契约外的业务规则变更，按「先确认再改」留待确认；未执行 SQL、未 push。
- 验证：`pnpm -r test`（shared 47、api-client 23、web 92、permissions 9）、`pnpm -r typecheck`、`pnpm -r build`、`pnpm lint` 通过；`mvn test` 202/202（JDK 21）；admin Playwright 22/23，B08「外挂转正」因本地测试数据残留（测试外挂01 缺少外挂球员角色）失败，与本票改动无关；B11 两条连续两轮 4/4 通过。
