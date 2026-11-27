---
name: messaging-rules
description: Outbox、进程内事件分发、线程池、幂等与失败处理规范
---

# 异步消息与 Outbox 规范

## 1 目的与适用范围

本规范约束 yunhefc 的领域事件异步投递：业务事务写 `outbox_event`，Quartz 中继认领后交给进程内有界线程池，由分发器调用审计/通知处理器落库。统计汇总由 Quartz 完成，不走事件通道。

适用范围：`infra/outbox`（Outbox 写入、中继、分发、去重）、`audit` 与 `notification` 的事件处理器，以及后续新增的事件生产方与处理器。

设计依据：`docs/design/03.整体架构设计.md` 2.1、5.3、7；AQ-08、AQ-16、P-167。

一句话总纲：**Outbox 与业务同事务、处理器幂等、线程池有界、失败有上限、超时小于租约**。

---

## 2 事件与 Outbox

1. 生产者只写 Outbox：业务事务内调用 `OutboxService.append/appendBatch`，不得在事务内直接调用处理器或做远程投递。
2. 事件 DTO 放 `contracts/messaging`，事件类型常量集中维护在 `EventTypes`。
3. `outbox_event` 状态机：`0-PENDING → 1-SENT`；发送失败 `attempts+1`、按退避重试，第 5 次失败置 `2-FAILED`。
4. 中继（`OutboxRelay`）按 `status + next_retry_at` 取批（20 条），用 `lease_owner/lease_until`（60 秒）条件认领，避免多实例重复发送。
5. PENDING 不清理；SENT 按 `sent_at`、FAILED 按 `created_at` 满 7 天物理删除；FAILED 删除前 WARN 计数。
6. 事件间**不保证顺序**：同一批事件由线程池并发处理，同一聚合的连续事件也可能乱序。审计按 `operated_at`、通知按 `created_at` 展示；不得让业务正确性依赖事件到达顺序。如未来出现强顺序需求，再单独引入按键串行化。

---

## 3 分发与线程池

1. `OutboxEventDispatcher.dispatch` 按事件类型路由：`AUDIT_RECORDED` → `contracts.messaging.AuditEventSink`（由 `AuditEventHandler` 实现）；`EventTypes.NOTIFICATION_EVENTS` → `NotificationEventSink`（由 `NotificationEventHandler` 实现）；未知类型抛 `IllegalArgumentException`，由 Outbox 记失败。分发器只依赖 contracts 端口，不反向依赖业务域 application 包。
2. `@Async` 方法必须返回 `CompletableFuture`：返回 `void` 会把异常吞进 `AsyncUncaughtExceptionHandler`，导致 Outbox 永不进入 FAILED。
3. 分发器必须是独立 Spring Bean，不得在调用方 `new` 后调用，否则 `@Async` 失效；分发器自身不加 `@Transactional`，事务边界在处理器。
4. 线程池固定为有界池：core 4、max 8、queue 32、线程前缀 `outbox-dispatch-`、拒绝策略 `CallerRunsPolicy`、关闭时等待 20 秒。容量 40 按单批 20 设计，正常不触发拒绝；未来调大批次或并发时由 `CallerRunsPolicy` 回落到中继线程同步执行，形成背压而不丢事件。
5. 中继等待整批 Future 的共享截止时间由 `yunhefc.outbox.dispatch-timeout-ms` 控制，默认 30000ms，**必须大于 0 且小于 60 秒租约**；启动时由 `OutboxRelay` 校验，非法配置直接失败。
6. 超时不取消 Future（中断可能破坏正在提交的事务）；重复投递由处理器去重兜底。

---

## 4 幂等与去重

1. 至少一次投递；处理器幂等是最终防线，不得假设事件只投递一次。
2. 每个处理器在**同一事务**内先写 `consumed_event` 去重记录，再写业务数据；业务失败时两者一起回滚。
3. 去重键：
   - 主键 `(consumer_group, event_id)`；
   - 位点键 `(topic, consumer_group, partition_no, offset_no)`。
4. 进程内投递的填值：`topic` 使用通道标识 `outbox.audit` / `outbox.notification`；`partition_no` 固定 `0`；`offset_no` 取 `outbox_event.id`。
5. **禁止复用历史 Kafka topic 字符串**：切换后 7 天保留期内，历史行的 `offset_no` 可能与新 outbox id 相同，导致新事件被 `INSERT IGNORE` 静默吞掉。
6. 通知与审计使用独立消费组，互不影响；去重记录 7 天清理。
7. 唯一约束、状态机条件更新仍是业务幂等的必要补充；不得只依赖去重表。

---

## 5 失败处理与可观测性

1. 处理器异常或分发超时 → 中继 `markFailed`：`attempts+1`、`next_retry_at = now + min(300, 30 × attempts)` 秒、清空租约、记录 `last_error`（截断 500 字符）。
2. 第 5 次失败置 FAILED；不引入死信队列/表，FAILED 即终态，通过 `outbox_event` 查询与保留任务 WARN 计数处理。
3. 进程重启时未 `markSent` 的行租约过期后被重新认领；若处理器已提交但未标记，去重表抑制重复业务数据。
4. 日志：单条事件处理用 DEBUG；中继失败用 WARN，带 `outboxId/eventId/eventType/attempts/elapsedMs/error`；同一异常的堆栈只在处理器边界记录一次，中继不重复打印堆栈；禁止在循环内逐条 INFO。
5. 监控：`outbox_event` 中 PENDING 积压与 FAILED 数量；线程池队列满时应排查处理器慢查询或批次过大。
6. 处理器应短小：不做远程调用、不长时间计算；大批量广播类通知需关注耗时是否接近分发超时。

---

## 6 测试要求

1. 分发器单测覆盖：审计/通知路由、通道标识与位点参数、未知类型拒绝、消息 JSON 序列化。
2. 中继单测覆盖：成功 `markSent`、失败 `markFailed`、分发超时、租约被抢时跳过、关闭开关。
3. 线程池配置测试覆盖：core/max/queue/线程前缀/拒绝策略，以及关闭时等待运行中任务。
4. 集成测试覆盖：审计与通知落库、重复投递去重、处理器失败事务回滚且去重记录回滚、未知类型 5 次后 FAILED。
5. 不得用 H2 替代 MySQL 验证去重与事务；集成测试沿用 Testcontainers MySQL/Redis。

## 7 常见坑

- ❌ 事务内直接调用处理器或远程投递，业务回滚后事件已发出。
- ❌ `@Async` 返回 `void`，异常丢失，Outbox 永远重试而不进入 FAILED。
- ❌ 使用无界队列或 `AbortPolicy`：前者打爆内存，后者把拒绝当失败消耗重试次数。
- ❌ 分发超时大于租约，批次未完成就被其他实例抢走。
- ❌ 去重记录与业务写入不在同一事务，失败后留下“已消费”假象。
- ❌ 复用历史 Kafka topic 作为通道标识，位点键与新 outbox id 碰撞。
- ❌ 把去重表当作唯一幂等方式，忽略唯一约束与状态机条件更新。
- ❌ 依赖事件到达顺序实现业务正确性。

---

## 8 审查清单

- [ ] 事件写入与业务写入同事务；事务内无远程调用
- [ ] 新事件类型已加入 `EventTypes` 并明确路由通道
- [ ] 处理器 `@Transactional` 内先写去重记录再写业务数据
- [ ] 分发方法返回 `CompletableFuture`，且由独立 Bean 调用
- [ ] 线程池有界、线程名前缀明确、拒绝策略为 `CallerRunsPolicy`
- [ ] `dispatch-timeout-ms` 小于 60 秒租约
- [ ] 通道标识为 `outbox.audit` / `outbox.notification`，`offset_no` 取 outbox id
- [ ] 失败有 5 次上限并置 FAILED；日志级别与字段符合规范
- [ ] 不依赖事件顺序；新增顺序敏感场景需先修订设计
