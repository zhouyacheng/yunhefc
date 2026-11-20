# P-78 小程序比赛详情对齐 C03 写操作

## 事实源

- 需求 v1.31；02 v0.57；06 §102（P-78）
- Web C03：`frontend/apps/web/src/pages/MatchDetailPage.vue`
- Web 纯逻辑：`frontend/apps/web/src/features/match-detail/{registration,status-options,roster}.ts`
- 共享纯逻辑：`frontend/packages/shared/src/{match,event,lineup}.ts`
- 现有小程序：`frontend/apps/miniprogram/miniprogram/pages/match-detail/`、`utils/`、`types/api.ts`

## 范围

- MP04 对齐 C03：概览（报名/取消/候补递补、签到、名单与考勤）、事件（录入/本人编辑）、阵容只读、分组只读。
- 管理员状态操作：0–4 直接提交（0 即开启报名）、5 延期、6 取消。
- MP06 首页 CTA 真实报名/加入候补；MP07 我的报名进行中可直接取消。
- 不做：邀请/复制链接、分享、订阅消息、账号密码登录/找回密码、B 端专属操作、名单快照 diff、所有「去管理后台」入口。

## 票序与依赖

| 票 | 文件 | 依赖 | 状态 |
|---|---|---|---|
| 01 | `issues/01-docs-p78.md` | 无 | ready-for-human |
| 02 | `issues/02-request-session-api.md` | 01 | ready-for-human |
| 03 | `issues/03-types-pure-functions.md` | 02 | ready-for-human |
| 04 | `issues/04-match-detail-shell-overview.md` | 03 | ready-for-human |
| 05 | `issues/05-overview-writes-status.md` | 04 | ready-for-human |
| 06 | `issues/06-events-lineup-grouping.md` | 04 | ready-for-human |
| 07 | `issues/07-home-profile-actions.md` | 03 | ready-for-human |
| 08 | `issues/08-acceptance.md` | 05、06、07 | ready-for-human（待开发者工具/真机） |

实施记录（2026-11-20）：`pnpm --filter @yunhefc/miniprogram typecheck`、`test`（21 文件 187 项）、`pnpm lint`、`pnpm -r typecheck`、`pnpm -r test` 均通过；开发者工具与真机验收待票 08。

## 约束

- 无后端、API、DB、权限码、错误码变更；接口仍 95、权限码 28。
- 不引 `@yunhefc/shared`；纯函数本地移植并用 Vitest 锁定 Web 同口径。
- 实施使用 TDD：先写失败测试，再实现纯函数。
- 不执行 SQL、不改 CI/CD、不提交 git。

## 验收矩阵

- 角色：匿名 / 待审核 / 正式球员 / 外挂 / 管理员。
- 比赛状态：0 报名中、1 报名截止、2 已锁定、3 进行中、4 已结束、5 已延期、6 已取消。
- 时间窗口：报名截止前后、取消锁定前后、签到窗口前/中/后、事件赛后 24h。
- 冲突：40901 版本冲突、40902 重复报名、40903 名额满、40905 比赛状态冲突、42203 分组不匹配。
- 真实设备：iOS、Android 各一台，覆盖报名→取消→签到→改状态→事件→阵容/分组。
