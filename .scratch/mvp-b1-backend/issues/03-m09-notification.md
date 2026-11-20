# 03 — M09 通知读接口与转正通知

Type: implementation
Status: resolved

**What to build:** 球员可以分页读取自己的站内信、查看未读数、标记单条或全部已读；外挂转正时被转正本人收到一条角色变更通知；通知具备按保留期物理删除的能力，供后续保留任务调度。

**Blocked by:** None — can start immediately

**Status:** resolved

- [x] NTF-01 `GET /c/notifications`：`unread` 可省略，`true` 仅未读；`PageQuery` 分页；固定 `created_at DESC, id DESC`；PENDING 可读。
- [x] NTF-02 `GET /c/notifications/unread-count`：返回 `{count}`，与 AUTH-10 的初始值一致。
- [x] NTF-03 `POST /c/notifications/{id}/read`：只按 `id + userId` 查询；非本人或不存在统一 40401；重复已读返回原 `readAt`，不覆盖时间。
- [x] NTF-04 `POST /c/notifications/read-all`：只更新本人未读，返回 `{updated}`，`updated=0` 也返回 200。
- [x] 通知响应为 `id,type,typeLabel,title,body,refType?,refId?,readAt?,createdAt`；`typeLabel` 取现有枚举名。
- [x] PLR-08 转正在同一事务补 `ROLE_CHANGED` Outbox，收件人为被转正本人，角色取转正后快照；通知类型为 9。
- [x] 新增按 `created_at` 物理删除通知的 mapper 方法，自定义 `@Delete` 绕过 `@TableLogic`；默认 90 天保留期由 09 号票接入调度。
- [x] `frontend/packages/shared` 新增通知类型并在入口导出（`Notification`、未读数、已读结果、全部已读结果）；本票不做 UI。
- [x] NTF-03/04 不要求 `Idempotency-Key`；PENDING 白名单沿用现有配置，不改安全规则。
- [x] 集成测试覆盖：分页与未读过滤、权限与 PENDING、非本人 40401、重复已读、全部已读 0/非 0、PLR-08 通知只产生一条；`mvn verify` 全绿。

## Comments

- 2026-11-12 交付：提交 `ead23d6`（feat: add M09 notification read APIs）。
- 验证：`mvn verify` BUILD SUCCESS（132 单测 / 284 IT / 0 failures，JDK 21）；新增 `NotificationIT` 8 项。
- 评审：两轴评审（Standards / Spec）已完成；补 `unreadCount` 只读事务、`now()` 复用、分页边界用例，并将单条已读改为 `id + user_id` 的 `FOR UPDATE` 当前读以消除并发重复已读返回 `readAt=null` 的路径；列表/全部已读仍用 MyBatis-Plus 条件构造器（均带本人 `user_id` 且由 `@TableLogic` 过滤），与 README 的“自定义 SQL”建议有实现差异但不改变契约。
- 实现位置：`notification.controller.NotificationController`、`notification.application.NotificationApplicationService`、`notification.dto.NotificationDtos`；`NotificationMapper.deleteCreatedBefore`；`AccountLifecycleApplicationService.convert` 追加 `ROLE_CHANGED` Outbox；`frontend/packages/shared/src/notification.ts`。
- 文档：本票无契约/DDL 变更；06 追加实施记录，README 回填交付表。
