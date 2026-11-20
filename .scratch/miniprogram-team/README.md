# P-79 小程序球队页对齐 C04

## 事实源

- 需求 v1.32；02 v0.58 §5.4/§5.8；06 §103（P-79）
- Web C04：`frontend/apps/web/src/pages/TeamPage.vue`、`features/team/{merge-stats,query}.ts`
- 现有小程序：`frontend/apps/miniprogram/miniprogram/pages/team/`

## 范围

- 页头 SQUAD / 球队 / 赛季下拉，选项来自 MAT-14 并强制包含当前上海年份与已选赛季。
- PLR-01（`all=true&season`）+ STA-01（`season`）按 playerId 合并；统计允许延迟，失败降级。
- 前锋 → 中场 → 后卫 → 门将分组，组头人数。
- 球员卡：头像（签名 URL，失败回落首字）、姓名、号码、位置、地区、星级、出场/进球/助攻。
- 空态、加载、失败重试；未登录可浏览。
- 不做：球员详情页、额外筛选、后端/API/DB/权限码变更。

## 票

| 票 | 文件 | 状态 |
|---|---|---|
| 01 | `issues/01-team-page.md` | ready-for-human |

## 验证

`pnpm --filter @yunhefc/miniprogram typecheck/test`、`pnpm lint`；`team-view`/`skill` 单测；开发者工具与 iOS/Android 真机。
