# 05 — 代码规范清理

**What to build:** M01 代码符合 `java-rules`/`database-rules`/`api-rules` 的剩余规范项：时间、枚举、配置、日志、HTTP 超时、实体、批量写入、测试命名、分页校验。

**Blocked by:** 04 — 数据访问与模块边界.

**Status:** done — 485bd84

- [x] 时间统一 `Instant` 语义，注入 `Clock`，去掉散落 `LocalDateTime.now()`
- [x] 状态/类型/位置使用枚举，移除 `Labels` 中的领域 switch 与散落角色码
- [x] `@Value` 收敛为 `@ConfigurationProperties`
- [x] 异常与消费者失败有结构化日志，不吞异常
- [x] `RestClient` 配置连接/读取超时
- [x] 实体移除 Lombok `@Data`，改为显式/受控访问器
- [x] 角色授权、权限替换、通知发送改批量写入
- [x] 集成测试命名改为 `*IT`
- [x] `pageSize` 白名单（10/20/50/100）与分页校验集中实现
- [x] `OutboxRelay`、`InviteExpiryJob` 等 `@Scheduled` 任务迁移到 Quartz JDBC JobStore（严格 Quartz，见 03 §5.4）
