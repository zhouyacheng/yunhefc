# 03 DTO 与纯函数（TDD）

Status: ready-for-human
Blocked by: 02

## 目标

按 TDD 移植 Web/shared 的纯逻辑，先写失败测试再实现，锁定两端口径。

## 验收

- [ ] `types/api.ts` 补全报名/考勤/事件/状态/阵容/分组 DTO；`MatchDetail.version/cancelLock` 必填，`MatchEvent.version` 必填。
- [ ] 新增 `registration`（signupState/cancelState/promotionText）。
- [ ] 新增 `match-status`（statusOptions、上海时区转换、延期/取消校验）。
- [ ] 新增 `match-window`（签到窗口、事件录入/编辑窗口）。
- [ ] 新增 `roster`（emptyRoster/buildRoster）。
- [ ] 新增 `event-form`（eventTeamRoster、校验、payload、编辑谓词）。
- [ ] 新增 `lineup-view`、`grouping-view` 只读 view model。
- [ ] 扩展 home-cta（canSignup）、profile-view（取消按钮）、match-detail-view、presentation（考勤文案）。
- [ ] 测试覆盖匿名/待审核/正式/外挂、截止/锁定边界、7 状态、24h 窗口、冲突文案。
- [ ] `pnpm --filter @yunhefc/miniprogram typecheck` 与 `test` 通过。

## Comments

- 2026-11-20 实施 P-78。
