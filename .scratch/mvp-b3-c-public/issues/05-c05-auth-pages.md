# 05 — C05 登录/注册/找回密码

Type: implementation
Status: resolved
Blocked by: 01 — 四 Tab 壳层、错误页与公共基座

**What to build:** `/login` 支持登录、注册、找回密码三种模式；注册可带邀请上下文并携带 `inviteToken`；新用户注册后看到「账号待审核」提示并按白名单跳转；错误与限流文案可读。

## Scope

- 扩展现有 `LoginPage.vue`：Tab 或 `mode=login|register|reset`，默认登录，保留现有登录表单与选择器。
- 注册：手机号、验证码、密码、姓名、主位置；AUTH-02 取码；AUTH-03 提交；`inviteToken` 从 query 透传。
- 找回密码：AUTH-02 `purpose=RESET` + AUTH-06。
- 邀请上下文展示（INV-01）；无效邀请的降级提示。
- 前端校验与错误映射。

## Owned files

- `frontend/apps/web/src/pages/LoginPage.vue`
- `frontend/apps/web/src/features/auth/validation.ts`
- `frontend/apps/web/src/features/auth/validation.test.ts`
- `frontend/apps/web/src/stores/session.ts`（仅新增 `register()`，与票 01 的会话方法协调）
- `frontend/e2e/web-auth.spec.ts`

## API dependencies

- `AUTH-02 POST /c/auth/sms-codes`：`{phone, purpose}`，purpose 为 `REGISTER|RESET|BIND`。
- `AUTH-03 POST /c/auth/register`：`{phone, code, password, name, position, inviteToken?}`；响应 `SessionResponse {user, player, csrfToken}`，**不含 permissions**。
- `AUTH-04 POST /c/auth/login`：现有逻辑。
- `AUTH-06 POST /c/auth/password/reset`：`{phone, code, newPassword}`。
- `INV-01 GET /c/invites/{token}`：邀请上下文。
- 错误码：40001、40102、40907、40910、42901、40303。

## UI states

- 模式切换：登录/注册/找回密码；`mode` 写入 URL query，可直达。
- 提交中：按钮 loading，禁用重复提交。
- 短信码：发送后 60 秒倒计时，按钮禁用；mock 环境提示可用码。
- 字段错误：手机号格式、验证码 6 位、密码 8–64 位且含字母和数字、姓名必填、主位置必选。
- 邀请上下文：类型、球队/比赛、邀请人、有效期；无效时提示但不阻断普通注册。
- 注册成功：显示「账号待审核」提示，再按 `redirect` 白名单跳转；无 redirect 回 `/`。
- 错误：手机号已注册、邀请不可用、限流、登录失败；登录失败不区分账号不存在与密码错误。

## Implementation notes

- 路由 `/login` 使用 `mode` query；默认 `login`。切换模式时保留 `redirect` 与 `inviteToken`。
- 密码规则：8–64 位且同时含字母和数字（前后端同源校验）；手机号 `^1\d{10}$`；验证码 6 位数字。
- 注册成功后 `stores/session.ts` 的 `register()`：提交 AUTH-03、保存 `csrfToken`、写入返回的 `user/player`、设置本地会话提示，然后调用 `refresh()` 补 `permissions/unreadCount`（AUTH-10）。
- 注册请求携带 `inviteToken`（query 或表单隐藏字段）；`redirect` 只接受站内白名单，复用 `safeRouteRedirect`，注册默认 `/`。
- 邀请上下文只在 `inviteToken` 存在时调用 INV-01；404/40910 时展示「邀请不可用」并允许继续自助注册。
- 找回密码成功后切回登录模式并提示成功；不自动登录。
- 微信入口不实现（C08/B4）。

## Test obligations

- Vitest `validation.test.ts`：手机号、验证码、密码强度、邀请 token 透传、错误码到文案映射。
- Playwright `web-auth.spec.ts`：
  - `mode=register`：用 mock 码 `123456` 注册新手机号 → 显示「账号待审核」→ 落在 `/`；
  - 1021 / `Passw0rd!` 登录 → 按默认回到 `/matches`；再访问 `/profile` 可进入并显示待审核横幅（横幅由票 06 提供；若票 06 未合入，断言 `/profile` 可访问）；
  - 找回密码表单：填手机号 → 发送验证码 → 提交成功提示；
  - 错误：错误密码显示统一文案；重复手机号注册显示 40907 文案；
  - 无 console error。
- 登录成功默认仍回 `/matches`（保留现有 `web.spec.ts`）。

## Cross-ticket prerequisites

- 票 01：路由、会话提示、`/profile` 与种子 1021。
- 票 07：消费本票的 `redirect` + `inviteToken` 契约；本票需保证两参数在模式切换与跳转中不丢。

## Result

- 交付：登录/注册/找回密码三模式与 `mode` query；AUTH-02 短信码（按手机号+用途 60 秒倒计时、dev mock 码提示）、AUTH-03 注册、AUTH-06 重置；INV-01 邀请上下文（含 404/失效降级，`team/inviter` 可空）与请求代次保护；`session.register()`（保存 CSRF、写入会话、`refresh()` 补权限/未读数，刷新失败保留 `loaded=false` 供守卫重试）；`features/auth/validation.ts` 字段校验、邀请透传与错误码文案映射；Vitest 与 Playwright 用例。注册成功提示按 `user.status` 判定（待审核/已入队），延迟跳转前校验路由与模式，避免离开后强制跳回。
- 验证：`pnpm --filter @yunhefc/web test` 75 项、`typecheck`、`eslint`、改动文件 Prettier 通过；`api-client` test/typecheck 通过；Playwright `--project=web e2e/web-auth.spec.ts` 6 条全绿（2026-11-12），注册/重置/邀请上下文/登录用例均无 console error；同批复跑 `web.spec.ts`、`web-shell.spec.ts`，登录相关用例通过。
- 说明：`ERROR_CODES` 补充 40907/40910 常量；新增 `stores/session.test.ts` 注册透传用例。全量 `--project=web` 中 `web.spec.ts` 的签到与录事件 2 条因本地种子窗口/并发会话改动未通过，均发生在登录成功后的比赛详情页，与本票无关。验证期间有并行会话同时运行 Playwright 清理 `test-results/`，个别失败为环境干扰，单文件已复跑确认。
- 未执行：后端、schema、Flyway、Quartz DDL 改动；微信入口（C08/B4）；票 06 的待审核横幅断言（仅断言 `/profile` 可访问）；未重放 `test-data.sql`。
