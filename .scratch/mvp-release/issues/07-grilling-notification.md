# 07 — 通知矩阵定稿

Type: grilling
Status: resolved
Blocked by: 02

## Question

基于「通知触发矩阵与现有 handler 差距」的盘点定稿：

- 哪些源事件发通知；05 中 `MATCH_CREATED` / `MATCH_SIGNUP_CLOSED` / `MATCH_LOCKED` / `MATCH_STARTED` / `MATCH_FINISHED` 是启用还是移除类型。
- 收件人范围、文案与敏感信息边界（不含手机号等）、赛程变更的报名者广播。
- 分享链接（NOTIFY-02）的前端实现范围。
- 未读角标轮询策略（不做 WebSocket）、通知保留 90 天的执行归属。
- NTF-01~04 与 C 端通知页的接口/页面改动清单。

产出可直接实施的矩阵与改动清单；契约变化先改 05/02/04 并记 06。事实指针：02 答案、需求 NOTIFY-01~05、DQ-12、05 §3.10、02 个人中心通知。

## Answer

七项决策已定（2026-11-11）。文档已同步：05 v0.24、02 v0.16、04 v0.18、06 v1.40（§43）。

### 1. 最终触发矩阵

| 源事件 | 类型 | 收件人 | 文案要点 | MVP |
|---|---|---|---|---|
| `REGISTRATION_CONFIRMED` | 0 | 报名本人 | 报名成功，留意赛前通知 | 已实现 |
| `WAITLIST_PROMOTED` | 1 | 递补本人 | 候补递补成功 | 已实现 |
| `MATCH_POSTPONED`（`rescheduled=false`） | 7 | 该场 CONFIRMED + WAITLISTED | 比赛延期至新时间 | 已实现 |
| `MATCH_POSTPONED`（`rescheduled=true`） | 7 | 同上 | 比赛恢复报名/重新排期 | 已实现 |
| `MATCH_CANCELLED` | 8 | 同上 | 比赛取消 + 原因 | 已实现 |
| `ROLE_CHANGED`（ROL-05/ROL-07） | 9 | 角色下 ACTIVE 用户 / 被调整本人 | 角色已更新 | 已实现 |
| `ACCOUNT_APPROVED` | 10 | 被审核本人 | 账号审核通过 | 已实现 |
| `ACCOUNT_DISABLED` | 11 | 被注销本人 | 账号已注销，历史数据保留 | 已实现（不可读可接受） |
| PLR-08 转正 | 9 | 被转正本人 | 角色已更新 | 新增事件发布 |
| 2–6 比赛状态 | 2–6 | — | — | 预留，不触发 |

不产生站内信：注册进入 PENDING、INV-02 邀请接受、考勤、比赛事件、阵容、分组、管理员移除报名。

### 2. 决策

1. **口径**：只保留 7 类触发；2–6 保留枚举/DB 编码但不实现。需求 §9.2 状态表“通知”列不构成站内信强制项；02 §6.4 普通状态重置只写审计。
2. **角标**：实现 NTF-02；登录态由 AUTH-10 初始化（AUTH-04 不含 `unreadCount`，登录成功后立即调一次 NTF-02），之后每 60s 轮询，`visibilitychange` 与路由切换刷新；不做 WebSocket。
3. **分享**：复制稳定 URL `/matches/{id}`；`navigator.share` 可用时调用；微信内提示右上角；不接 JS-SDK、不新增接口。
4. **90 天保留**：扩展现有 `DataRetentionService` 每日任务，按 `created_at` 物理删除；必须自定义 `@Delete`（`@TableLogic` 会把 `delete(wrapper)` 改成软删）；不加索引、不改 schema。
5. **待审核**：注册响应 + 页面横幅，不发站内信，不新增 `NotificationType`。前端当前无注册页，注册响应消费由 C 端页面批次补，18 切批标依赖。
6. **邀请/转正**：INV-02 不发；PLR-08 在同一事务写 `ROLE_CHANGED`，收件人为被转正本人，角色取转正后快照。不放在 `PlayerProfileApplicationService.convertToFormal`（其他流程共用）。
7. **注销可达性**：继续写 type=11；DISABLED 不可登录、不可读，声明为可接受，不改认证契约。

### 3. 实施改动清单

后端：
- 新增 `notification/controller/NotificationController`、`notification/application/NotificationApplicationService`、`notification/dto/NotificationDtos`（`NotificationView/UnreadCountView/ReadView/ReadAllView`）。
- `NotificationMapper` 继承 `BaseMapper`，列表/已读用 `LambdaQueryWrapper/LambdaUpdateWrapper`；`typeLabel` 用 `NotificationType.label()`。分页按 `PageQuery`（10/20/50/100），排序 `created_at DESC, id DESC`；NTF-03 按 `id + userId` 查，非本人/不存在 40401；NTF-04 返回 `updated`，0 也 200。PENDING 白名单已就绪。
- `AccountLifecycleApplicationService.convert` 增加 `ROLE_CHANGED` Outbox；`DataRetentionService` 增加通知 90 天物理删除。
- 测试：新增 `NotificationReadIT`；扩展 `PlayerConvertIT`、`DataRetentionIT`。

前端：
- `packages/shared` 增加通知类型并导出；session 增加 `unreadCount`，登录后立即刷新；`AppShell` 启动轮询（仅登录态、60s、可见性/路由刷新、防重入、401 静默）并在“我的”Tab 显示 badge。
- `AccountPage` 增加分页通知区块（单条/全部已读、空态/失败态、`MATCH` 跳转、`USER` 只标记已读）与 PENDING 横幅。
- `MatchDetailPage` 增加分享按钮（稳定 URL；微信提示；`navigator.share`/复制；注意 `.match-hero__top .muted:last-child` 右对齐依赖）。
- 测试：shared 纯函数 Vitest；Playwright 通知列表→已读→角标归零、分享、PENDING 横幅；`scripts/sql/test-data.sql` 补通知种子与可登录 PENDING 用户。

### 4. 风险与依赖

- 前端无注册页：待审核只能先落 `AccountPage` 横幅；注册页与响应由 C 端页面批次补。
- 90 天清理按 `created_at` 无前导索引，单队 MVP 接受，不改 schema。
- 通知分页需“加载更多”，不能只拉第一页。
- 未登录/公开页不得启动轮询，避免 401 与 console error 污染 Playwright。

### 5. 验证

- 05 仍 92 接口、28 权限码；`schema.sql` 仍 27 表、0 业务外键；本票未改 SQL、未写业务代码、未改 Flyway。
- 文档表格与原型 JS 检查通过；本票仅文档与 tracker 改动。
