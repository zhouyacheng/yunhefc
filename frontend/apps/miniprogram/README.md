# 云河FC 微信小程序

原生小程序 + Vant Weapp。首发范围：微信登录、绑定手机号、浏览赛程 / 比赛详情 / 球队页；不提供报名、通知、分享与订阅消息。

## 结构

```text
frontend/apps/miniprogram/
├── scripts/prepare-vant.mjs     # 把 @vant/weapp 平铺到 miniprogram_npm（见下）
├── tests/                       # Vitest：请求封装、Cookie jar、设计 token 一致性
└── miniprogram/
    ├── app.ts|json|wxss
    ├── styles/tokens.wxss       # 与 web 的 --yh-* 变量一致
    ├── types/api.ts
    ├── utils/{config,request,session}.ts
    └── pages/
        ├── schedule/            # tab：赛程
        ├── team/                # tab：球队
        ├── match-detail/        # 比赛详情（只读）
        ├── login/               # wx.login → AUTH-20
        ├── phone-bind/          # 手机号 + 短信 → AUTH-21
        └── spike/               # Phase 0 会话实验页，仅用于真机验证
```

## 会话模型（关键）

`wx.request` **不会**自动保存和回放 `Set-Cookie`。后端仍是 Cookie 会话（`YHFC_C` + `X-XSRF-TOKEN`），所以小程序端在 `utils/request.ts` 维护一个**内存 Cookie jar**：从响应头提取 `YHFC_C`，后续请求手动写入 `Cookie` 头。

- 冷启动后 jar 清空，重新 `wx.login` 即可恢复；凭证不写 storage。
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

真机验收：`wx.login` → 首次绑定手机号 → 浏览赛程/详情/球队 → 冷启动重登。iOS、Android 各一台。
