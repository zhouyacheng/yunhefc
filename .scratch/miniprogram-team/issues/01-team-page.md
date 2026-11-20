# 01 MP05 对齐 C04

Status: ready-for-human
Blocked by: none

## 目标

小程序球队页功能与风格和 C 端 C04 一致。

## 验收

- [x] 页头 SQUAD / 球队 / 赛季下拉；选项去重倒序并包含当前与已选赛季。
- [x] PLR-01 名单 + STA-01 统计按 playerId 合并；统计延迟/失败不阻塞名单。
- [x] 前锋 → 中场 → 后卫 → 门将分组，组头展示人数。
- [x] 球员卡：头像/首字回落、姓名、号码、位置、地区、五星半星、出场/进球/助攻。
- [x] 空态、加载、失败重试；未登录可浏览。
- [x] `pnpm --filter @yunhefc/miniprogram typecheck/test`、`pnpm lint` 通过。
- [ ] 开发者工具/iOS/Android 真机人工验收。

## Comments

- 2026-11-20 实施 P-79；自动化检查通过，人工验收待完成。
