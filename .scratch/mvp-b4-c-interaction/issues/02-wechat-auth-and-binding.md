# 02 — 微信授权回调与绑定

Type: implementation
Status: resolved
Blocked by: 无（建议排在 01 后；真机验收依赖外部公众号配置）

**What to build:** 微信内用户从登录页发起静默授权，回调页完成登录或绑定手机后回到站内；老用户直接登录，已登录用户绑定当前账号；失败时有站内提示页可重试或改用手机号登录；C06 展示绑定状态并可主动绑定。后端补 AUTH-08 HTML 导航 302 与 dev/test mock 授权页，JSON 契约不变。

## Scope

- C05：微信授权入口仅在微信内置浏览器显示；点击走 AUTH-07，`redirect` 取当前站内路径（`/` 开头、非 `//`、不含 `://`/`\`），非法回首页；非微信浏览器不显示入口。
- 后端 AUTH-08 落地（票内改动，JSON 契约不变）：
  - `Accept` 含 `text/html` 且不含 `application/json` 时按结果 302：成功 → `redirect`；需绑手机 → `/auth/wechat/callback?bind=1&redirect=<encoded>`；失败 → `/auth/wechat/callback?error=<code>&redirect=<encoded>`。
  - 其余请求保持现有 JSON 响应与错误码；现有 `WechatAuthIT` 的 JSON 断言不变。
- C08 `/auth/wechat/callback`：
  - `bind=1` → 绑定手机号表单（手机号、验证码、姓名、主位置），走 AUTH-09；手机号已存在则绑定到已有账号；成功后按 `redirect` 跳转并刷新会话、权限与未读角标。
  - `error=<code>` 或无参数直达 → 站内提示页「微信授权未完成」，提供重试与「使用手机号登录」；重试重新发起 AUTH-07 并保留 `redirect`，不刷新已失效的当前 URL。
  - 成功路径由后端直接 302 到 `redirect`，不经过前端页。
- C06：微信绑定状态取自 `user.wechatBound`，页面进入与路由切换时刷新；账号与安全区新增「绑定微信」按钮（仅微信内置浏览器且未绑定时显示，点击走 AUTH-07，`redirect=/profile`）；解绑入口不出现（仅管理员在 B07）。
- dev/test mock：新增 `GET /api/v1/c/auth/wechat/mock-authorize?state=…`，仅 mock 模式注册；页面含「确认授权」链接跳 `/api/v1/c/auth/wechat/callback?code=mock-code&state=…`；`MockWechatOAuthClient` 返回该页地址。该页不计入 92 接口、不写入 05 契约，prod 不注册。
- 不获取、不展示昵称/头像；不接 JS-SDK 与模板消息。

## Owned files

- `frontend/apps/web/src/router/index.ts`
- `frontend/apps/web/src/pages/WechatCallbackPage.vue`（新建）
- `frontend/apps/web/src/pages/LoginPage.vue`
- `frontend/apps/web/src/pages/ProfilePage.vue`（绑定状态刷新 + 绑定按钮）
- `frontend/packages/shared/src/auth.ts`（仅新增微信相关类型，若需要）
- `frontend/e2e/web-wechat.spec.ts`（新建）
- `backend/src/main/java/com/yunhefc/auth/controller/WechatAuthController.java`（HTML 分支与 302）
- `backend/src/main/java/com/yunhefc/auth/application/WechatAuthService.java`（结果到落点的映射）
- `backend/src/main/java/com/yunhefc/auth/wechat/MockWechatOAuthClient.java`（指向 mock 页）
- `backend/src/main/java/com/yunhefc/auth/wechat/MockWechatAuthorizeController.java`（新建，仅 mock 模式）
- `backend/src/test/java/com/yunhefc/WechatAuthIT.java`（补 HTML/JSON 双分支）

## API

- AUTH-07 `GET /c/auth/wechat/authorize`
- AUTH-08 `GET /c/auth/wechat/callback`
- AUTH-09 `POST /c/auth/wechat/bind-phone`

## Test obligations

- dev/mock 下 Playwright：登录页微信入口显隐；点击入口 → mock 授权页 → 后端回调 302 → 站内 `redirect` 闭环；`bind=1` 表单校验；`error` 页重试（重新发起 AUTH-07）与手输登录入口；C06 绑定按钮仅微信内未绑定时显示；无 console error。
- 直接访问 `/auth/wechat/callback` 无参数 → 失败页；非微信 UA、非法 `redirect`、缺 code、state 失效各覆盖一条分支。
- 后端 IT：`Accept: text/html` 下成功/绑定/失败三分支的 302 Location（参数正确编码）；`Accept: application/json` 下响应与错误码不变；mock 授权页只在 mock 模式注册、prod 不可达。
- 真机验收（外部前置）：微信内静默授权无确认页、首次绑定手机、老用户登录、管理员解绑后重绑；证据为真机截图或录屏。
- `mvn test`、`pnpm -r test`、`pnpm -r typecheck`、`pnpm -r build` 全绿。
- 不改 AUTH-07/08/09 路径、权限码、错误码与 JSON 响应形态；不执行 SQL；真实 AppID/Secret/授权域名由外部任务提供。

## Result

已完成（2026-11-13），提交 `7e6432e`。

**后端（AUTH-08 HTML 分支；JSON 契约不变）**

- `WechatAuthController`：`Accept` 含 `text/html` 且不含 `application/json` 时 302——成功 → `redirect`，需绑手机 → `/auth/wechat/callback?bind=1&redirect=…`，失败 → `?error=<code>&redirect=…`；`code`/`state` 改为可选，缺参走失败页。其余请求仍返回原 JSON envelope。
- `WechatAuthService.callbackLanding`：结果到落点的映射；`WechatStateStore.peek` 在 state 被消费前保留 redirect，失败页重试不丢目标；成功落点按 URI 组件编码非 ASCII/空格/裸 `%`，失败与绑定落点对 redirect 做白名单校验与 URL 编码。
- `MockWechatOAuthClient` 返回 `/api/v1/c/auth/wechat/mock-authorize?state=…`；新增 `MockWechatAuthorizeController`（`@Profile("!prod")` + `mode=mock`，页面「确认授权」跳 `code=mock-code`）；`SecurityConfig` 与 `PendingAccountFilter` 放行该只读页（prod 无控制器，路径 404）。
- `WechatAuthIT` 增 4 条：HTML 绑定/缺 code 保留 redirect/state 失效/JSON 缺参分支、已登录绑定与老用户登录、非 ASCII redirect 编码、mock 页 HTML 内容；新增 `WechatAuthorizeUaIT`（`require-wechat-ua=true` 下非微信 UA → 42201、微信 UA 放行）与 `MockWechatAuthorizeControllerTest`（prod 与 real 模式不注册）。

**前端**

- 新增 `pages/WechatCallbackPage.vue`（路由 `/auth/wechat/callback`）：`bind=1` 绑定手机表单（手机号、BIND 验证码、姓名、主位置），成功后写入 CSRF、`markSessionHint()`、`refresh()` 补权限与未读角标并按 redirect 跳转；`error`/无参数显示「微信授权未完成」+「重新授权」（重新 AUTH-07、保留 redirect）+「使用手机号登录」。
- C05 登录页：仅微信内置浏览器显示「微信登录」；redirect 经 `safeRouteRedirect` 归一（非法回首页），发起前写会话提示，后端 302 落到公开页时可恢复登录态。
- C06：账号与安全区新增「绑定微信」（仅微信内且 `wechatBound=false`），点击 AUTH-07 `redirect=/profile`；进入个人中心刷新 `/me`；无自助解绑入口。
- `features/auth/wechat.ts` 统一 `isWechatBrowser` 与 AUTH-07 请求（三个页面不再各自拼请求）；`packages/shared/src/auth.ts` 仅新增 AUTH-07/09 响应类型。
- `e2e/web-wechat.spec.ts` 5 条：微信入口显隐、非法 redirect、mock 授权→回调 302→站内闭环、首绑表单校验、新设备老用户登录与头部身份恢复、C06 绑定按钮、失败页/缺 code/错误码分支；`support.ts` 登录按钮改为 `exact` 避免与「微信登录」歧义。

**验证**

- `./mvnw verify`：unit 203 + IT 311 全部通过（含 `WechatAuthIT` 7 条、`WechatAuthorizeUaIT` 1 条）。
- `pnpm -r typecheck`、`pnpm -r build`、`pnpm lint` 全绿；`pnpm -r test`：permissions 9 + shared 47 + api-client 23 + web 168 全通过。
- `web-wechat.spec.ts` 连续多轮 5/5 通过；用例开头用管理员解绑 mock openid，可重复执行。
- 全量 `pnpm test:e2e`：59 passed / 21 failed；失败均为本地 dev 库残留（期望 5 场比赛实得 6、`B3首页联调赛` 已被后续比赛顶替、13910000021 已被上一轮入队邀请提为 PLAYER、球队人数 +1），与本票改动无关。按既有约定需先重放 `scripts/sql/test-data.sql` 再跑全量；本票未执行 SQL。

**未执行 / 待外部**

- 真机验收（静默授权、首次绑定、老用户登录、管理员解绑后重绑）依赖真实 AppID/Secret/授权域名与 `MP_verify_*.txt`，未执行，由 release-checklist 在 cutover 前补齐证据。
- 票面 Owned files 之外的必要改动：`WechatStateStore.peek`、`SecurityConfig`/`PendingAccountFilter` 放行 mock 页、`features/auth/wechat.ts`、`e2e/support.ts` 与两份新增测试；均无契约变化。
- 已知重复：C08 绑定表单的验证码倒计时/位置选择与 C05 登录页同形，暂未抽 composable（避免改动稳定登录表单），留待后续收敛。
- 未改 AUTH-07/08/09 路径、权限码、错误码与 JSON 响应形态；未改 schema/Flyway/Quartz；未 push。
