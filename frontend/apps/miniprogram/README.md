# 云河FC 微信小程序

原生小程序 + Vant Weapp。范围：微信登录、绑定手机号、首页 / 比赛（对齐 C02 筛选与卡片）/ 比赛详情（对齐 C03：报名/取消/候补递补、签到、名单与考勤、事件录入与本人编辑、阵容/分组只读、管理员状态修改）/ 球队（对齐 C04：赛季、头像/地区/星级、出场/进球/助攻）/ 我的（对齐 C06：五星半星身份卡、通知、我的报名与取消、账号安全、退出登录）/ 活动（对齐 C11/C12：范围与类型/状态筛选、详情报名/取消、家属与备注、分享卡片、活动通知落点）/ 账本（对齐 C13：汇总、流水、方向/分类/区间筛选，仅正式球员只读）。比赛列表支持类型/地点/状态/月份/自定义时间筛选、下拉刷新与上拉加载；首页 CTA 可直接报名/加入候补。首页对具备后台权限的账号显示「管理后台」，复制带 AUTH-18 一次性票据的链接（60 秒有效）到浏览器打开；个人主体小程序不支持 web-view，不做内嵌。不做比赛邀请/复制链接、比赛分享、订阅消息、账号密码登录/找回密码与 B 端专属操作（活动详情支持微信分享卡片）。

## 结构

```text
frontend/apps/miniprogram/
├── scripts/prepare-vant.mjs     # 把 @vant/weapp 平铺到 miniprogram_npm（见下）
├── tests/                       # Vitest：请求/上传封装、会话、展示/CTA/筛选/详情/个人中心纯函数、跨页失效、设计 token
└── miniprogram/
    ├── app.ts|json|wxss
    ├── styles/{tokens,controls,layout,match-card}.wxss  # --yh-* 变量、跨页按钮/pill、状态布局、比赛卡片
    ├── templates/match-card.wxml      # MP03/MP06 共用的 C02 卡片结构
    ├── types/api.ts
    ├── utils/{config,request,api-client,session,notifications,datetime,presentation,paged-feed,guarded-load,invalidation,schedule-query,home-cta,home-view,match-view,match-detail-view,profile-view,idempotency,permissions,admin-entry,api-errors,registration,match-status,match-window,roster,event-form,lineup-view,grouping-view,skill,team-view,activity-view,ledger-query}.ts
    │   ├── request.ts      # 传输层：Cookie jar、CSRF、ApiError
    │   └── api-client.ts   # 唯一请求入口：按域暴露函数，路径/分页/幂等键集中于此
    ├── components/
    │   ├── status-sheet/        # 管理员状态操作与延期/取消表单
    │   └── event-form-sheet/    # 事件录入/本人编辑弹层
    └── pages/
        ├── home/                # tab：首页（镜像 C01，真实报名）
        ├── schedule/            # tab：比赛（C02 筛选、富卡片、下拉/上拉）
        ├── team/                # tab：球队（C04 赛季、球员卡与赛季统计）
        ├── profile/             # tab：我的（通知/我的报名与取消/账号）
        ├── profile-edit/        # 编辑资料与头像
        ├── match-detail/        # 比赛详情（C03 四 Tab：概览/事件/阵容/分组）
        ├── login/               # wx.login → AUTH-20
        ├── phone-bind/          # 手机号 + 短信 → AUTH-21
        ├── leaderboard/         # 排行榜（MP09：单行五列表格）
        ├── activity/            # 活动列表（MP10：范围/类型/状态筛选）
        ├── activity-detail/     # 活动详情（MP11：报名/取消、分享、通知落点）
        ├── ledger/              # 账本（MP12：汇总与流水只读）
        └── spike/               # Phase 0 会话实验页，仅用于真机验证
```

## 会话模型（关键）

`wx.request` **不会**自动保存和回放 `Set-Cookie`。后端仍是 Cookie 会话（`YHFC_C` + `X-XSRF-TOKEN`），所以小程序端在 `utils/request.ts` 维护一个**内存 Cookie jar**：从响应头提取 `YHFC_C`，后续请求手动写入 `Cookie` 头。

- 冷启动后 jar 清空，重新 `wx.login` 即可恢复；凭证不写 storage。
- 权限码随登录、绑定与 `GET /c/me` 保存到内存 `session`，页面用 `hasPermission()` 控制报名/名单/事件/状态/阵容/分组入口；权限只是渲染依据，服务端仍强校验。
- 写请求先 `ensureCsrf()`，高风险接口带每次操作新生成的 `Idempotency-Key`；状态与事件编辑再带 `version`。
- 结论与实验记录见 `.scratch/miniprogram-login/spike.md`。
- `pages/spike` 保留用于真机复验，验证完成后可删除。

## 本地开发

```bash
cd frontend
pnpm install
pnpm --filter @yunhefc/miniprogram prepare:vant
```

微信开发者工具导入 `frontend/apps/miniprogram`，填写小程序 AppID。

`utils/config.ts` 默认指向生产域名；本地联调改为 `'local'`，并在 DevTools「详情 → 本地设置」勾选「不校验合法域名」。

> 不要用 DevTools 的「构建 npm」：pnpm 的符号链接布局会让它失败。统一跑 `prepare:vant`。

## 后端要求

- `WECHAT_MINI_ENABLED=true`，dev 下 `WECHAT_MINI_MODE=mock`（mock 客户端把 code 映射为 `mock-mp-openid-<code>`）。
- 生产 `WECHAT_MINI_MODE=real` + `WECHAT_MINI_APP_ID` / `WECHAT_MINI_APP_SECRET`。
- 小程序后台「request 合法域名」需登记 `https://www.<domain>`。

## 验证

```bash
pnpm --filter @yunhefc/miniprogram typecheck
pnpm --filter @yunhefc/miniprogram test
```

真机验收：`wx.login` → 首次绑定手机号 → 首页 CTA 报名/加入候补并刷新队列 → 管理后台入口（无权限不显示；有权限复制票据链接到浏览器兑换）→ 比赛（首屏无月份参数、筛选合并/互斥/清空、抽屉与选择层、下拉/上拉、返回不丢分页）→ 详情（头部元信息、报名/取消/递补、签到、名单考勤、事件录入与本人编辑、阵容/分组只读、管理员状态 0–6、冲突刷新、取消/延期横幅、404）→ 球队 → 我的（通知已读、我的报名取消、编辑资料、头像）→ 退出登录 → 冷启动重登。iOS、Android 各一台。
