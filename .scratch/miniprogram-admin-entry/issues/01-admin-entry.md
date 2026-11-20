# 01 小程序首页管理后台入口

Status: ready-for-human
Blocked by: none

## 目标

小程序首页与 C 端 C01 一致提供「管理后台」入口。

## 验收

- [x] 仅 `hasBackendPermission` 为真时显示 Hero 动作区「管理后台」按钮。
- [x] 点击签发 AUTH-18 票据，复制带 `#ticket=` 的后台链接并弹窗提示浏览器打开。
- [x] 签发失败回退复制后台首页链接（B 端手机号 + 密码登录）。
- [x] `admin-entry` / `permissions` 单测；`pnpm --filter @yunhefc/miniprogram typecheck/test` 通过。
- [ ] 开发者工具/iOS/Android 真机人工验收（含票据在浏览器 60 秒内兑换）。

## Comments

- 2026-11-21 实施 P-81；自动化检查通过，人工验收待完成。
