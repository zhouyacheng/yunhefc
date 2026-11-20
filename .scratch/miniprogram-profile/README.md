# P-76 小程序个人中心与四 tab

## 事实源

- 需求：`docs/云河FC-需求文档.md` v1.29
- 页面：`docs/design/02.页面结构与交互设计.md` §4.1.1、§4.3、§5.8、§5.9
- 决策：`docs/design/06.设计方案评审结果.md` §100（P-76）
- Web C06：`frontend/apps/web/src/pages/ProfilePage.vue` 及 ProfileEditPopup / NotificationList / MyRegistrations

## 范围

- tabBar 改为 首页 / 比赛 / 球队 / 我的；赛程页文案统一为「比赛」。
- MP07 我的：身份卡、通知（全部/未读、已读、全部已读、比赛跳转、未读角标）、我的报名（进行中/历史，只读）、账号与安全、退出登录。
- MP08 编辑资料：姓名、地区、主位置、副位置（≤3）、头像上传（FIL-01）与保存（PLR-03）。
- 待审核用户：可读通知，不可编辑资料，不展示我的报名。
- 不做报名/取消报名/签到/分享/订阅消息；首页报名类 CTA 继续引导网页版。

## 票序

1. `issues/01-profile.md` —— 四 tab 与个人中心实现（已实现，待人工验收）。
2. `issues/02-c06-parity.md` —— 按 P-80 对齐 C06：五星半星身份卡、通知未读为 0 禁用、移动端排版（已实现，待人工验收）。

## 约束

- 不改后端/DB/API/权限码/错误码；复用 AUTH-05/10、NTF-01~04、REG-03、PLR-01/03、FIL-01。
- 原生 tabBar 保持纯文字（不新增图标资源）。
- 验证：`pnpm --filter @yunhefc/miniprogram typecheck/test`、`pnpm lint`；开发者工具真机/模拟器验收。
