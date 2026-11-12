# 07 — C07 邀请落地

Type: implementation
Status: resolved
Blocked by: 01 — 四 Tab 壳层、错误页与公共基座（软依赖 05）

**What to build:** 访客或球员打开 `/invite/:token` 时看到邀请类型、球队/比赛、邀请人、有效期与剩余次数；未登录先登录/注册并保留邀请上下文；已登录可接受入队邀请成为正式球员，或接受比赛邀请后进入比赛详情；不存在/过期/撤销/用尽分别有明确结果。

## Scope

- 替换 `InvitePage.vue` stub：INV-01 判定与展示、登录/注册入口、INV-02 接受、比赛邀请跳转、失效/404 状态。
- 本地 `InviteLandingView` 类型（不改共享 `Invite` 类型）。
- 邀请状态推导纯函数与测试。

## Owned files

- `frontend/apps/web/src/pages/InvitePage.vue`
- `frontend/apps/web/src/features/invite/invite-state.ts`
- `frontend/apps/web/src/features/invite/invite-state.test.ts`
- `frontend/apps/web/src/features/invite/types.ts`
- `frontend/e2e/web-invite.spec.ts`

## API dependencies

- `INV-01 GET /c/invites/{token}`：公开；返回 `type/typeLabel/team/matchId/match/inviter/expiresAt/maxUses/usedCount/remainingUses/status/statusLabel/exhausted`。
- `INV-02 POST /c/invites/{token}/accept`：已登录；返回 `{player, matchId?}`。
- `AUTH-10 GET /c/me`：登录态下判断是否已是成员（`player.type=1`）。
- 错误码：40401、40910、40101、40303。

## UI states

- 加载中：Loading。
- 404：token 不存在 → `router.replace('/404')`。
- 失效：`status=2（已过期）`、`status=1（已撤销）` 或 `exhausted=true` → 失效说明与返回首页；不显示接受按钮。
- 有效入队邀请：球队名称、邀请人、有效期、剩余次数、「加入球队」。
- 有效比赛邀请：比赛摘要（时间、地点、名额）、邀请人、「去比赛」；接受后进入详情。
- 访客：登录/注册按钮，带 `redirect=/invite/{token}` 与（注册时）`inviteToken`。
- 已登录已是成员：`player.type=1` → 「你已是球队成员，无需重复加入」。
- 接受中/成功/失败：按钮 loading；成功刷新会话并更新页面；40910 显示失效。

## Implementation notes

- `InviteLandingView` 按后端实际响应定义：`team` 为 `{id,name,shortName}`，`inviter` 为 `{id,name,...}`（`UserSummary`），`match` 可按 `MatchSummary` 读取 `id/startAt/venue/capacity/...`；不修改 `packages/shared/src/auth.ts`。
- 状态推导纯函数输入 INV-01 响应，输出 `notFound | invalid | team | match | member`；`notFound` 由调用方跳 404；`invalid` 显示失效文案。
- 访客入口：
  - 登录：`/login?redirect=/invite/{token}`；
  - 注册：`/login?mode=register&redirect=/invite/{token}&inviteToken={token}`。
  - 登录/注册返回邀请页后不自动接受，必须由用户点击「加入球队」。
- 入队接受：调用 INV-02，成功后 `session.refresh()`，更新为「已是成员」；若 INV-02 返回 `matchId` 则视为比赛邀请，直接 `router.push('/matches/{matchId}')`，不自动报名。
- 比赛邀请接受前展示比赛是否已满/截止；接受本身只消耗邀请并返回 `matchId`，不改账号状态、不创建报名。
- 40401 → `/404`；40910 → 失效说明；401 → 登录；PENDING 接受入队邀请在白名单内（可成功转正），比赛邀请同样允许。
- 复制链接/分享不新增接口；本页只消费 URL token。

## Test obligations

- Vitest `invite-state.test.ts`：404/过期/撤销/用尽/有效入队/有效比赛/已是成员。
- Playwright `web-invite.spec.ts`：
  - 访客打开 `B3-INVITE-TEAM`，看到球队与邀请人；点击登录 → `/login?redirect=/invite/B3-INVITE-TEAM`；
  - 1021 / `Passw0rd!` 登录后打开该邀请并接受 → 页面显示已是成员（种子重放复位）；
  - 已登录 1002 打开 `B3-INVITE-MATCH` 并接受 → 进入 `/matches/{id}`；
  - 不存在的 token → `/404`；
  - 过期/撤销邀请显示失效说明；
  - 无 console error。

## Cross-ticket prerequisites

- 票 01：`/invite/:token` 路由与种子 5001–5005（含 1021）。
- 票 05：登录/注册页消费 `redirect` 与 `inviteToken`；本票生成入口。
- 种子重放必须清理 1021 的 `invite_use` 并复位状态/角色，否则接受用例不可重复。

## Result

- 交付：`fc8b38f`（`InvitePage` 替换 stub：加载/失败重试、失效、已是成员、入队、比赛五态；INV-01 状态推导、INV-02 接受、访客登录/注册透传 `redirect` 与 `inviteToken`、40401→`/404`、40910→失效、40303 文案；比赛邀请额外读取公开 `GET /c/matches/{id}` 展示名额与截止/满员提示；`features/invite/types.ts`、`invite-state.ts` 与 15 项 Vitest；`web-invite.spec.ts` 4 条 + 排序最后的 `web.z-invite-accept.spec.ts` 1 条）。
- 验证：`pnpm -r test`（web 10 文件 93 项，invite 15 项）、`pnpm -r typecheck`、`pnpm lint`、`pnpm -r build` 通过；重放 `test-data.sql` 后 Playwright `--project=web` 39/39 全绿（2026-11-12，31.9s），其中 C07 5 条覆盖访客登录/注册 query、1021 接受比赛邀请进入 1005、1021 转正、404、过期/撤销/用尽失效文案；除 404 用例按既有惯例不断言 console error 外均无 console error。
- 偏差：INV-01 嵌套 `match` 实际不含 `capacity/confirmedCount/signupDeadline`，故比赛邀请额外请求公开比赛详情；请求失败时名额显示 `—`，不影响接受。1021 转正用例拆到文件名排序最后的 `web.z-invite-accept.spec.ts`，避免共享种子下影响 `web-profile` 的待审核断言与 `web-team` 的名单人数；比赛邀请用例改用 1021（待审核）验证比赛邀请对 PENDING 同样放行且不改变账号状态，避开 1002/1001 在共享套件内触发每手机号 10 次/分钟登录限流。
- 未执行：后端、DB、`schema.sql`/Flyway、共享类型与登录页改动；未新增接口、权限码或错误码。
