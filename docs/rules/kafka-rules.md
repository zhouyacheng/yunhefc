---
name: kafka-rules
description: Kafka Topic、分区、生产者、消费者、Broker 与可靠性语义规范
---

# Kafka 使用规范

## 1 目的与适用范围

本规范约束 yunhefc 中 Kafka 的 Topic 设计、生产、消费与 Broker 配置。Kafka 仅用于领域事件异步投递（通知、审计）；统计汇总由 Quartz 完成，不依赖 Kafka。

适用范围：`infra` 消息适配、Outbox 投递器、通知与审计消费者，以及后续新增的 Kafka 生产方与消费方。

设计依据：`docs/design/03.整体架构设计.md` 2.1、5.3、7；AQ-08、AQ-16。

一句话总纲：**分区提前规划、生产者开启幂等、消费者关闭自动提交、业务层幂等兜底、严控 rebalance**。

---

## 2 Topic 与分区设计

### 2.1 Topic

1. 副本 `replication.factor=1`、`min.insync.replicas=1`：本项目为单 broker 部署，只能使用单副本；这不具备高可用能力，可靠性由 Outbox 重试、消费端幂等与积压监控兜底。后续扩容多 broker 时必须提升副本数。
2. 按业务域拆分 topic；通知与审计使用独立 topic 和独立消费组，不共用 group。topic 命名、分区数与保留期在详细设计阶段固化。
3. 禁止自动建 topic：`auto.create.topics.enable=false`。
4. 保留策略按业务设置 `retention.ms`，不要全部使用默认 7 天；可同时按大小 `retention.bytes` 限制。长期数据以 MySQL 与 OSS 归档为准，Kafka 只承担投递与短期重放。
5. 清理策略 `log.cleanup.policy`：普通事件用 `delete`；状态变更、CDC 等只保留每个 key 最新值的场景用 `compact`。

### 2.2 分区

1. 分区决定消费并行度：消费线程数 ≤ 分区数，超过分区数的 consumer 空闲。
2. 单分区参考写入上限约 10MB/s，按预估流量预留 2~3 倍余量；**分区只能增加不能减少**，不要一次性开过大分区，避免 broker 元数据与 Controller 压力。
3. MVP 默认 3 个分区，最终以详细设计为准；多 broker 时尽量取 broker 数量的倍数，便于 leader 均衡。
4. 顺序保证：**分区内有序、全局无序**。全局顺序只能使用单分区，吞吐低；业务事件按聚合 ID（如 `matchId`、`playerId`、`eventId`）哈希到同一分区，保证同 key 有序，同时避免 key 分布不均造成热点分区。
5. 消息大小：单条尽量 <1MB；超大消息走 OSS，消息只传引用。
6. 单节点部署下重点监控分区倾斜、消息堆积与磁盘使用率；多节点后再做 leader 均衡与副本分布。

---

## 3 生产者

### 3.1 核心配置

```properties
acks=all
enable.idempotence=true
retries=3
max.in.flight.requests.per.connection=5
compression.type=lz4
batch.size=131072
linger.ms=10~50
buffer.memory=67108864
delivery.timeout.ms=120000
```

- `retries` 不要设得过小；示例 3 可按重试预算调整，通常应配合 `delivery.timeout.ms` 覆盖可接受的重试窗口。
- 吞吐优先可评估 `acks=1`，但本项目通知/审计事件优先可靠性，默认 `acks=all`。
- 低延迟场景 `linger.ms=5` 左右，不要设为 0，避免大量小请求。
- 压缩优先 lz4/zstd，snappy 次之，减少网络 IO。

### 3.2 发送方式

1. 使用异步发送 + Callback 捕获异常；不要盲目同步 `send()` 阻塞业务线程。
2. Callback 中记录失败消息、告警并投递死信；避免失败被静默吞掉。
3. Producer 线程安全，全局单例复用；禁止频繁创建销毁。
4. 发送前确保业务事务与 Outbox 写入同事务提交，由 Outbox 投递器负责 Kafka 发布。

### 3.3 事务

- 消费-处理-转发、流处理等场景可使用幂等生产者 + Kafka 事务：设置 `transactional.id`，消费者 `isolation.level=read_committed`。
- 业务处理、`send` 与 `sendOffsetsToTransaction` 一起提交。
- 本项目通知/审计默认采用“手动提交 offset + 事件 ID 去重”，不引入 Kafka 事务；事务成本高，收益有限。

---

## 4 消费者

### 4.1 核心配置

```properties
enable.auto.commit=false
max.poll.records=50~200
max.poll.interval.ms=300000
session.timeout.ms=25000
heartbeat.interval.ms=8000
fetch.min.bytes=100000
fetch.max.wait.ms=500
partition.assignment.strategy=CooperativeStickyAssignor
```

- `heartbeat.interval.ms` 约为 `session.timeout.ms` 的 1/3。
- `CooperativeStickyAssignor` 用于增量再平衡，减少 stop-the-world；Kafka 2.4+ 可用。

### 4.2 消费与提交

1. 生产环境必须关闭自动提交；业务处理成功后手动提交 offset，失败不提交，由下次 poll 重拉。
2. 消费线程不要执行无界阻塞操作（慢查询、长同步调用）。Spring Kafka 监听容器按分区并发消费，长耗时处理如需异步化，必须使用有界线程池与队列，并明确 offset 提交时机，防止消息堆积打爆内存。
3. 消费组尽量只订阅一个 topic；不同业务、不同 SLA 使用独立 group。
4. 滚动发布使用 `group.instance.id` 静态成员，分批重启，减少 rebalance。

### 4.3 幂等与失败处理

1. Kafka 只能保证至少一次投递；业务幂等是最终防线。
2. 通知/审计消费者除事件 ID 去重（去重表/唯一约束）外，按下文 Redis 标记做快速跳过；重复消息不得产生重复通知或审计记录。
3. 可结合唯一索引、Redis 去重、状态机条件更新兜底；不得只依赖“消息不会重复”的假设。
4. 消费持续失败（解析异常、脏数据）不得无限重试；超过阈值投递死信 topic 或死信表，单独排查，避免阻塞分区。
5. 监控消费延迟与死信数量；失败重试次数与死信处理策略在详细设计中固化。

### 4.4 Redis 消费标记（两阶段）

1. 消息唯一标识使用 `topic + group + partition + offset`；Redis key 为 `kafka::{topic}::{group}::{partition}::{offset}`。不同消费组各自维护标记，避免通知与审计相互跳过。
2. 消费开始时用 Lua 脚本或 `SET NX` 原子写入处理中标记：`SET key PROCESSING NX EX 60`。
   - 写入成功：进入业务处理。
   - key 已存在且值为 `DONE`：该消息已成功消费，跳过业务处理并提交 offset。
   - key 已存在且值为 `PROCESSING`：其他实例正在处理，跳过本次投递；不得视为成功，也不得被后续累计提交跳过该位点（暂停/重试该分区或等待标记过期）。
3. 消费成功后执行 `SET key DONE EX 43200`，覆盖处理中标记并保留 12 小时。
4. 消费失败时删除 key，且不提交 offset，允许 Kafka 重投后重新处理。
5. `PROCESSING` 的 1 分钟 TTL 必须大于单条消息的正常处理时间；处理可能超时的场景需要续期，否则标记过期后可能被重复处理。
6. 该标记不能替代业务幂等：事件 ID 去重表、唯一约束、状态机仍是最终防线。与设计文档的消费者去重表并存；若要以 Redis 标记替代事件 ID 去重，需先同步修改设计文档。
7. Redis 不可用时不得阻塞或回滚业务，降级为业务幂等；标记写入失败由业务幂等兜底，恢复后按 offset 正常消费。
8. `DONE` 的 12 小时 TTL 必须覆盖 Kafka 重投与消费滞后窗口；超过窗口的重放依赖业务幂等兜底。

---

## 5 Broker 与部署

1. 版本与形态：Kafka 3.7.2，单节点 KRaft，Docker Compose 部署。
2. 磁盘：使用 SSD，日志目录尽量与系统盘分离；监控磁盘使用率与 IO 等待。
3. JVM：堆 1~2G，不要给超大堆；使用 G1GC，监控 GC 停顿与 Full GC。
4. 网络：broker 使用内网；单节点阶段不配置机架感知，多 broker 扩容时再配置 rack 与副本跨机架。
5. 限流：配置 producer/consumer 带宽 Quota，防止单个客户端打满集群。
6. 分区与 leader：单节点阶段关注分区倾斜与积压；多节点后定期检查 leader 分布，必要时执行 preferred leader election。
7. 禁止大量小分区、长期空 topic 不清理。

---

## 6 可靠性语义

| 语义 | 方案 | 适用场景 |
|---|---|---|
| At most once | 自动提交 / 不重试 | 不允许用于通知、审计等业务事件 |
| At least once | `acks=all` + 手动提交 + 业务幂等 | 本项目默认：Outbox 投递、通知/审计消费 |
| Exactly once | 幂等生产者 + Kafka 事务 + `read_committed` | 消费-转发、流处理；MVP 不启用 |

结论：**不要迷信 Kafka 事务**。本项目采用“至少一次投递 + 消费端按事件 ID 去重/业务幂等”，并用 Redis 两阶段标记 `kafka::{topic}::{group}::{partition}::{offset}` 快速跳过重复消息；仅在消费-处理-转发等确有原子性要求的场景再引入事务。

**本项目保障要点**：

- 顺序：按聚合 ID 哈希分区，分区内有序。
- 可靠：Outbox 与业务同事务，投递失败重试并监控积压。
- 幂等：Redis 两阶段标记（`PROCESSING` 60s、`DONE` 12h，失败删除）+ 事件 ID 去重，重复消息不产生重复业务数据。
- 失败：重试有上限，超限进入死信 topic 或死信表。
- 单节点：接受 RF=1 的单点风险；通知延迟不得阻塞主流程。

---

## 7 常见坑

- ❌ 多 broker 环境仍使用副本=1，broker 宕机丢数据；本项目单节点只能 RF=1，需用 Outbox 与积压监控兜底。
- ❌ `min.insync.replicas` 大于可用副本数，写请求全部失败；单节点必须为 1。
- ❌ `enable.auto.commit=true`，业务失败但 offset 已提交，造成消息丢失或重复。
- ❌ poll 线程阻塞，触发频繁 rebalance，形成雪崩。
- ❌ key 分布不均导致单分区热点，吞吐上不去。
- ❌ 消息超大，超过 socket 缓冲区或 broker 限制，发送失败。
- ❌ 大量空 topic、海量分区，Controller 压力过大。
- ❌ 消费重试无限循环，脏数据卡死分区。
- ❌ 一个 group 订阅多个 topic，故障与再平衡相互影响。
- ❌ Redis key 不含 group，通知与审计消费组互相跳过。
- ❌ `PROCESSING` 命中被当作已成功提交 offset，导致消息漏处理。
- ❌ `PROCESSING` TTL 短于业务处理时间，标记提前过期导致重复处理。
- ❌ 消费失败未删除 key，重投被误判为处理中或已处理。
- ❌ `DONE` TTL 短于重投窗口，重复消费。
- ❌ 把 Redis 标记当作唯一幂等方式，忽略事件 ID 去重与唯一约束。

---

## 8 审查清单

- [ ] 未开启自动建 topic；topic 命名、分区数、保留期已记录
- [ ] 单节点 `replication.factor=1` 且 `min.insync.replicas=1`；已评估单点风险
- [ ] 生产者开启幂等、`acks=all`、全局单例、Callback 记录失败
- [ ] 消费者关闭自动提交，业务成功后提交 offset
- [ ] 消费端按事件 ID 去重，重复消息不产生重复业务数据
- [ ] Redis key 为 `kafka::{topic}::{group}::{partition}::{offset}`，包含 group
- [ ] 处理中写 `PROCESSING`（TTL 60s），成功后写 `DONE`（TTL 12h），失败删除 key
- [ ] `PROCESSING` 命中不视为成功，且不会被后续累计提交跳过
- [ ] `PROCESSING` TTL 大于正常处理时间，超长处理有续期
- [ ] `DONE` TTL 覆盖 Kafka 重投与消费滞后窗口
- [ ] Redis 不可用时降级为业务幂等，不阻塞或回滚业务
- [ ] 消费失败有重试上限与死信处理
- [ ] 通知/审计使用独立消费组
- [ ] 已配置 rebalance 策略与滚动发布方式
- [ ] 单条消息 <1MB，超大内容走 OSS
- [ ] 监控消费延迟、磁盘、GC、错误率与死信数量
