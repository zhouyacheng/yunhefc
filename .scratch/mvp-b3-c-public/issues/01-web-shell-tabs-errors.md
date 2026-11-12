# 01 — 四 Tab 壳层、错误页与公共基座

Type: implementation
Status: resolved
Blocked by: None

**What to build:** C 端具备四个底部 Tab（首页/赛程/球队/我的）、`/profile` 与旧 `/account` 重定向、403/404 独立页、未读角标与 60 秒轮询、api-client 的 multipart 能力；同时补齐 B3 全部测试种子与 E2E 骨架，使后续 6 张页面票可以独立构建和落测。

## Scope

- 路由表按 README「路由与壳层」落地；`HomePage/TeamPage/ProfilePage/InvitePage` 先创建最小 stub，由票 02/04/06/07 替换。
- `AppShell` 改为四 Tab，品牌链接首页，右侧随登录态显示姓名或登录；「我的」挂未读角标（`99+` 上限）。
- `stores/session.ts`：保存 `unreadCount`、`refresh()`、`refreshUnreadCount()`、本地会话提示。
- `composables/useNotificationPolling.ts`：60 秒 + `visibilitychange` + 路由切换刷新；访客不启动；401 静默。
- `packages/api-client` 支持 FormData：不设置 JSON `Content-Type`，保留 CSRF、credentials、幂等键与 403 重试。
- C09：`/403`、`/404`、通配路由、`MatchDetailPage` 40401 → `/404`。
- E2E：`e2e/support.ts`、`playwright.config.ts` testMatch、更新 `web.spec.ts`、新增 `web-shell.spec.ts`。
- `scripts/sql/test-data.sql` 追加 B3 种子区块（比赛 1005、账号 1021、球员 1030–1035、通知 4001–4012、邀请 5001–5005）。

## Owned files

- `frontend/apps/web/src/router/index.ts`
- `frontend/apps/web/src/components/AppShell.vue`
- `frontend/apps/web/src/stores/session.ts`
- `frontend/apps/web/src/composables/useNotificationPolling.ts`
- `frontend/apps/web/src/pages/ForbiddenPage.vue`
- `frontend/apps/web/src/pages/NotFoundPage.vue`
- `frontend/apps/web/src/pages/HomePage.vue`（stub）
- `frontend/apps/web/src/pages/TeamPage.vue`（stub）
- `frontend/apps/web/src/pages/ProfilePage.vue`（stub）
- `frontend/apps/web/src/pages/InvitePage.vue`（stub）
- `frontend/apps/web/src/pages/MatchDetailPage.vue`（仅 40401 分支）
- `frontend/apps/web/src/features/auth/user-status.ts`
- `frontend/packages/api-client/src/client.ts`、`frontend/packages/api-client/src/index.test.ts`
- `frontend/e2e/support.ts`、`frontend/e2e/web-shell.spec.ts`、`frontend/e2e/web.spec.ts`
- `frontend/playwright.config.ts`
- `scripts/sql/test-data.sql`（仅 B3 区块）

## API dependencies

- `AUTH-10 GET /c/me`：登录态初始化，含 `unreadCount`。
- `NTF-02 GET /c/notifications/unread-count`：轮询角标。
- 无新增接口、权限码、错误码。

## UI states

- 角标：0 隐藏；1–99 显示数字；≥100 显示 `99+`。
- 顶部：未登录「登录」；已登录姓名 → `/profile`。
- `/403`：所需权限说明 + 返回首页 + 切换账号（退出后去登录）。
- `/404`：返回上一页 + 首页。
- 公开页：无 `requiresAuth`，不跳登录。

## Implementation notes

- 路由：`/`、`/matches`、`/matches/:id`、`/team`、`/profile`（requiresAuth）、`/invite/:token`、`/403`、`/404`；`/account` 用 `redirect: '/profile'`；通配改 `/404`。
- 登录成功仍默认回 `/matches`；注册成功由票 05 按 `redirect` 白名单处理。
- 会话提示键如 `yunhefc.c.sessionHint=1`：登录/注册成功写入；`clearSession()` 与 401 清除。`AppShell` 仅在提示存在或路由 `requiresAuth` 时调用 `ensureLoaded()`，保证访客不发 `/c/me`。
- `apply()` 必须保存 `unreadCount`（缺省 0）；`refresh()` 即使 `loaded` 也重取 `/me`；`refreshUnreadCount()` 用 `redirectOnUnauthorized:false`。
- 轮询 composable：登录后立即一次；`setInterval(60_000)`；`document.visibilitychange` 可见时刷新；`router.afterEach` 或 `watch(route.fullPath)` 刷新；请求进行中跳过；`onUnmounted`/退出清理；访客与 `!session.isAuthenticated` 不启动。
- api-client：新增 `postForm<T>(path, formData, options)`（或等价 `formData` 选项）。`body instanceof FormData` 时不设置 `Content-Type`，由浏览器带 boundary；仍设置 `Accept`、CSRF、`Idempotency-Key`；403 重取 token 后重试同一 FormData。
- `MatchDetailPage`：捕获 `ERROR_CODES.NOT_FOUND`（40401）时 `router.replace('/404')`；其他错误保持行内。
- E2E 配置：`testMatch: /web.*\.spec\.ts/`，保持 admin 项目不变；`workers: 1`、`fullyParallel: false`。
- 现有 `web.spec.ts`：比赛卡预期 4 → 5；未登录 `/account` 的 redirect 断言改为 `/profile`；新增已登录 `/account` → `/profile`。
- 种子按 README「测试数据与环境」执行；区块必须幂等，重复执行能复位 1021 状态/角色/`invite_use`、1005 报名与考勤、通知 `read_at/is_deleted`、邀请计数/状态/过期时间。

## Test obligations

- Vitest（`packages/api-client`）：FormData 透传、无 JSON `Content-Type`、CSRF 头、403 重试、错误 envelope。
- Vitest（web）：`user-status` 常量；会话 `apply` 保留 `unreadCount`（可在 store 单测或轻量组合式测试中覆盖）。
- Playwright `web-shell.spec.ts`：
  - 访客打开 `/matches` 不产生 `/api/v1/c/me` 请求与 401；
  - `/unknown` → `/404`；`/403` 可访问；
  - 未登录 `/account` → `/login?redirect=%2Fprofile`；登录后 `/account` → `/profile`；
  - 登录 1002 后「我的」角标显示种子未读数；无 console error。
- 现有 `web.spec.ts` 全绿。

## Cross-ticket prerequisites

无。本票发布后续票所需的：路由与 stub、会话方法、轮询 composable、multipart API、E2E helper、全部 B3 种子。

## Result

- 交付：`9929a23`（四 Tab 壳层、`/profile`、403/404、会话角标与 60s 轮询、api-client FormData、E2E helper、B3 种子）。
- 验证：`pnpm -r typecheck/test/build`、`pnpm lint` 与改动文件 Prettier 通过；`test-data.sql` 连续执行两次幂等；Playwright `--project=web` 11 条全绿（2026-11-12），访客 `/matches` 无 `/c/me` 请求/401，角标与 NTF-02 一致。
- 说明：B3 区块把 1004 改为已结束以退出 upcoming，确保 1005 成为首页 Hero；1004 赛后 24h 内事件黄金路径仍通过。`AccountPage.vue` 已无引用，按删除红线保留。全局 `pnpm format:check` 仍会因既有 `apps/admin/src/pages/GroupingPage.vue` 报错，与本票无关。
- 未执行：后端/schema/Flyway/Quartz 改动；E2E 使用本地 dev backend 与 mock 短信/微信/OSS。
