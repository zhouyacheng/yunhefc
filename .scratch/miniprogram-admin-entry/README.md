# P-81 小程序首页管理后台入口

## 事实源

- 需求 v1.34；02 v0.60 §5.8/§5.9；06 §105（P-81）；05 AUTH-18
- Web 参考：`frontend/apps/web/src/pages/HomePage.vue`、`features/admin-handoff/admin-handoff.ts`、`packages/permissions/src/index.ts`

## 范围

- 首页 Hero 动作区新增「管理后台」，仅具备任一后台权限时显示（排除 6 个 C 端专属权限）。
- 点击调用 AUTH-18 签发一次性票据，复制 `https://<domain>/admin/#ticket=...` 到剪贴板并弹窗提示到浏览器打开（60 秒有效）。
- 票据签发失败回退复制后台首页链接，由 B 端手机号 + 密码登录。
- 个人主体小程序不支持 web-view，不内嵌后台；比赛详情等其他页面不加后台入口。
- 不做：后端、API、DB、权限码与错误码变更。

## 票

| 票 | 文件 | 状态 |
|---|---|---|
| 01 | `issues/01-admin-entry.md` | ready-for-human |
