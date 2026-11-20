# 02 — 通知触发矩阵与现有 handler 差距

Type: research
Status: resolved
Blocked by: —

## Question

盘点通知模块的触发现状与设计差距：

- 对照需求 NOTIFY-01~05、DQ-12 与 06 已确认决策，列出必须触达的业务变化。
- 对账 `NotificationEventHandler` / `NotificationType` / `EventTypes` 当前覆盖的事件（已知 7 类：报名成功、候补递补、账号审核、账号注销、角色变更、比赛延期、比赛取消），确认 05 中 `MATCH_CREATED` / `MATCH_SIGNUP_CLOSED` / `MATCH_LOCKED` / `MATCH_STARTED` / `MATCH_FINISHED` 等类型是否应有触发、是否已发布对应 Outbox 事件。
- 输出「源事件 → 通知类型 → 收件人 → 文案要点 → 现状（已实现/缺 handler/缺事件）」矩阵。
- 标出站内消息接口（NTF-01~04）、分享链接（NOTIFY-02）、未读角标、保留 90 天、实时逐条不聚合等约束的缺口。

只给事实与差距，不做取舍。事实指针：需求 NOTIFY-01~05、DQ-12、`docs/design/05.API接口设计.md` §3.10、`notification/application/NotificationEventHandler.java`、`notification/domain/NotificationType.java`、`contracts/messaging/EventTypes.java`、报名/考勤/比赛的状态事件发布点。

## Answer

- 现有 7 类事件全部经 Outbox→Kafka 实现：报名成功(0)、候补递补(1)、延期(7)、取消(8)、角色变更(9)、审核通过(10)、注销(11)；文案与收件人见矩阵。
- `NotificationType` 的 2–6（创建/截止/锁定/开赛/结束）只有枚举与 DB 编码：无 `EventTypes` 常量、无发布点、无 handler；relay 遇未配置类型会抛异常。
- 需求 §9.2 / MATCH 规则 8 / 02 §6.4 与 NOTIFY-01 / 12.5 规则 26 / 06 §41 对“普通状态变更是否通知”口径冲突，待 07 定稿。
- NTF-01~04 无 controller/mapper；仅 AUTH-10 返回 `unreadCount`；C 端无通知区块、未读角标、轮询，session 丢弃 `unreadCount`。
- NOTIFY-02 仅原型有复制链接，正式前端无分享按钮；NOTIFY-04 为二期外范围。
- 通知 90 天清理未实现（`DataRetentionService` 只清幂等/去重/Outbox）；现有 7 类满足实时逐条且不含手机号。
- 相邻待决策：注册待审核提示载体、INV-02 邀请接受、PLR-08 转正、注销用户通知可达性。

完整矩阵与 `path:line` 证据见 [research/02-notification-matrix.md](../research/02-notification-matrix.md)。
