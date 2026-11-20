# 02 MP07 对齐 C06

Status: ready-for-human
Blocked by: none

## 目标

小程序「我的」页功能与风格和 C 端 C06 一致。

## 验收

- [x] 身份卡改用五格满/半/空星级，头像/姓名/元信息字号对齐 C06；移动端换行、编辑按钮独占一行。
- [x] 头像签名 URL 失败回落姓名首字。
- [x] 通知保留全部/未读与加载更多；未读为 0 时「全部已读」禁用；单条已读刷新角标与计数。
- [x] 我的报名保留进行中/历史、加载更多与进行中取消；文案与 C06 一致。
- [x] 账号与安全行高/字号对齐；退出登录样式一致。
- [x] `pnpm --filter @yunhefc/miniprogram typecheck/test`、`pnpm lint` 通过。
- [ ] 开发者工具/iOS/Android 真机人工验收。

## Comments

- 2026-11-20 实施 P-80；自动化检查通过，人工验收待完成。
- 2026-11-20 复审修复：头像 URL 校验复用 `utils/avatar.ts`；时间格式改为 `YYYY/MM/DD HH:mm`。
