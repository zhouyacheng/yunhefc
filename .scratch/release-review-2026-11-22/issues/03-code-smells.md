# 复审坏味道清理

Type: task
Status: ready-for-agent
Blocked by: 03, 04, 05（`.scratch/miniprogram-architecture-2/issues/`，收拢后多数自动消失）

## 问题

小程序：

- 报名/取消三页各写一份（home/profile/match-detail）；`registration-writes.ts` 尚不存在。
- `isOpponentSide` 与 `isOpponentLineupSide` 逐字相同。
- 两个 `attendanceText` 同名不同义。
- `session.ts` 三处重复赋值；`request.ts#uploadFile` 复制 envelope/401 处理。
- 比赛状态 5/6 字面量散落，已有 `isCancelledStatus/isPostponedStatus` 未用。
- `phone-bind` 本地 `POSITION_LABELS` 重复 `profile-edit` 导出。

后端：

- 内存分页绕过 MyBatis-Plus 插件（`PlayerAdminApplicationService`）。
- 赛季 `\d{4}` 正则三处重复；默认星级 `new BigDecimal("3.00")` 重复。
- `MatchQueryParsing` 未使用 import；`GroupingScore` 保留恒为 null 的字段。
- INV-02 未走 `AccountLifecycleApplicationService` 编排。

## 需求

- 先完成 architecture-2 票 03/04/05，再逐项清理；每项补或改对应单测。
