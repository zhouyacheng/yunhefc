---
name: java-rules
description: Spring Boot 后端 Java 编码、分层、事务、持久化、安全、事件与测试规范
---

# Java 后端编码规范

## 1. 目的与适用范围

本规范约束 `backend/` 单部署单元内的 Java 代码，适用于 `common`、`contracts`、`infra`、`query`、`stats` 与各业务领域包（auth/player/match/registration/attendance/event/lineup/grouping/notification/audit/settings）。

设计依据：`docs/design/03.整体架构设计.md`、`04.数据库设计.md`、`docs/rules/database-rules.md`、`api-rules.md`、`kafka-rules.md`、`logging-rules.md`。

一句话总纲：**依赖方向单一、事务边界清晰、领域规则在领域层、外部依赖走网关、日志与安全不越界。**

## 2. 技术基线

| 项 | 约定 |
|---|---|
| Java | 21 |
| 框架 | Spring Boot 3.5.x、Spring Security 6 |
| 持久化 | MyBatis-Plus + Flyway；复杂 SQL 放 XML |
| 辅助库 | Lombok（样板代码）、Hutool（通用工具） |
| 消息 | Kafka + Outbox；具体规则见 `kafka-rules.md` |
| 定时任务 | Quartz JDBC JobStore |
| 缓存/会话 | Redis + Spring Session |
| 文件 | 阿里云 OSS，经 `OssGateway` |
| 测试 | JUnit 5 + Testcontainers + ArchUnit |

不擅自引入新的基础框架或替换既有选型；确需引入时先在设计文档中确认。

### 2.1 Spring Boot Starter 依赖

以下依赖由 Spring Boot BOM 统一管理版本，不在业务模块中单独指定版本：

| 依赖 | 用途 | 约束 |
|---|---|---|
| `spring-boot-starter-web` | Web、内嵌 Tomcat | 默认使用 Tomcat；不额外引入 Jetty，除非明确替换并验证 |
| `spring-boot-starter-validation` | Jakarta Validation（`jakarta.validation`） | Controller 入参使用 `@Valid` / `@Validated`；服务端必须校验 |
| `spring-boot-starter-data-redis` | Redis 客户端 | 使用默认 Lettuce；**不要引入 Jedis** |
| `spring-boot-starter-aop` | AOP | 用于权限、审计、幂等等横切；不把业务规则写进切面 |
| `spring-boot-starter-actuator` | 健康检查与监控端点 | 生产必须开启；敏感端点需鉴权或仅内网暴露 |
| `spring-boot-starter-test` | 测试 | JUnit 5 Jupiter；**JUnit 4 彻底不使用** |
| `spring-boot-starter-logging` | 默认 Logback | 使用 Logback；**不要混用 log4j2**，除非明确迁移并统一配置 |

注意：

- 不手工管理上述 starter 的版本；升级随 Spring Boot 版本统一进行。
- 排除传递依赖时需说明原因，避免同时出现两套日志/JSON/Redis 客户端。
- Actuator 只暴露必要端点；生产不公开 `/env`、`/beans` 等敏感端点。
- 测试依赖只在 `test` scope 使用；集成测试用 Testcontainers，不用 H2 替代 MySQL。

## 3. 包结构与依赖方向

### 3.1 领域包

每个领域包内部统一分层：

```text
com.yunhefc.<domain>/
├── controller/        REST 接口与请求/响应 DTO
├── application/       用例编排、事务边界、Outbox 写入
├── domain/            实体、值对象、状态机、领域服务
├── infrastructure/    本域 Mapper、Repository、外部适配
└── dto/               请求/响应对象（也可放 controller 同包）
```

### 3.2 全局包

- `common`：统一响应、异常、分页、时间、权限注解；禁止业务实体与跨域逻辑。
- `contracts`：领域事件 DTO 与事件类型常量；不依赖业务实现。
- `infra`：共享技术网关（`MessagePublisher`、`OssGateway`、`IdempotencyStore`、`CacheStore` 等），不暴露 KafkaTemplate/RedisTemplate。
- `query`：跨域只读组合查询；只依赖各域 read port，不写业务表。
- `stats`：统计汇总任务与查询；只读依赖各域 read port，允许 `query` 与 `grouping` 通过只读接口依赖，其他业务域不得依赖。

### 3.3 依赖规则

- 依赖方向：controller → application → domain；infrastructure 实现 domain/application 定义的接口。
- controller 不得直接调用 Mapper/Repository，不得包含业务规则。
- domain 不得依赖 Spring Web、MyBatis、Kafka、Redis、OSS SDK。
- 跨领域只调用对方 application 服务或消费事件，不直接访问对方 Mapper/表。
- 一个本地事务默认只修改一个聚合；三个显式例外：报名生命周期（Match + Registration）、注册建档（User + PlayerProfile）与账号生命周期编排（审核/转正/注销：User + Player + UserRole + Registration）。例外集中在应用服务层，不得扩散。
- 以上规则由 ArchUnit 在 CI 固化；不得用注释或约定替代。

## 4. 命名与代码风格

### 4.1 命名

| 类型 | 规则 | 示例 |
|---|---|---|
| Controller | `<Domain>Controller` | `MatchController` |
| 应用服务 | `<UseCase>ApplicationService` | `RegistrationApplicationService` |
| 领域服务 | `<Domain>Service` | `GroupingService` |
| Mapper | `<Entity>Mapper` | `TeamMatchMapper` |
| Repository | `<Aggregate>Repository` | `LineupRepository` |
| 网关 | `<Capability>Gateway` | `OssGateway` |
| 定时任务 | `<Task>Job` | `MatchStatusJob` |
| 消费者 | `<Event>Consumer` | `NotificationEventConsumer` |
| 请求/响应 | `<Action>Request` / `<View>Response` | `MatchCreateRequest` |
| 事件 | `<Aggregate><Action>Event` | `RegistrationPromotedEvent` |
| 异常 | `<Domain>Exception` / `<Reason>Exception` | `CapacityFullException` |

- 不用 `ServiceImpl`、`Helper`、`Util2` 等含糊命名。
- 权限码、状态码、事件类型使用常量类或枚举，禁止散落字符串字面量。

### 4.2 风格

- 使用 Lombok `@RequiredArgsConstructor` + `private final` 字段实现构造器注入；禁止字段 `@Autowired`，不再手写构造函数。
- 字段尽量 `final`；DTO/事件优先使用 `record`；实体使用类 + Lombok。
- 不使用通配符 import；不写无意义注释；注释解释“为什么”，不是“做了什么”。
- 优先 Java 21 语法：`record`、`switch` 表达式、`var`（局部且类型明显时）。
- 集合返回空集合，不返回 `null`；查找结果用 `Optional`，不要把 `Optional` 放进字段或参数。
- 时间点统一 `Instant`；通过注入的 `Clock` 获取当前时间，禁止 `LocalDateTime.now()` 直接作为业务时间。持久化层显式按 UTC 转换。

### 4.3 Lombok 与 Hutool 边界

- Lombok：`@Getter`、`@Builder`、`@RequiredArgsConstructor`、`@Slf4j` 可用；实体避免 `@Data`（equals/hashCode 风险）；禁用 `@SneakyThrows`。
- Hutool：字符串、日期、加解密、HTTP 工具可用；业务规则不得放进工具类；能用 JDK 清晰表达的，不额外包装。
- 不在 DTO 上使用 Lombok `@Data` 暴露可变集合；需要时返回不可变副本。

## 5. REST 接口层

- 路径遵循 `api-rules.md`：`/api/v1/c/**`、`/api/v1/b/**`；C/B 会话由路径前缀决定。
- Controller 只做：参数绑定、`@Valid` 校验、调用 application 服务、组装统一响应。
- 请求/响应 DTO 与数据库字段一一对应；计算字段（如 `confirmedCount`）在响应 DTO 中明确命名，不写入实体。
- 统一返回 `ApiResponse<T>`：`{code,message,data,requestId}`；成功 `code=0`，失败为业务数字错误码。
- 全局异常处理用 `@RestControllerAdvice`：
  - `BusinessException` → 业务错误码 + HTTP 状态；
  - 参数校验异常 → `VALIDATION_FAILED` + 字段 details；
  - 未知异常 → `INTERNAL_ERROR`，记录日志，不返回堆栈。
- 不把实体、Mapper 查询结果直接序列化给前端；必须转 DTO。
- 写接口支持 `Idempotency-Key` 的，由统一过滤器/拦截器处理，Controller 不重复实现。

## 6. 应用层与事务

- `@Transactional` 只放在 application 服务的方法上；Controller、Mapper、domain 不放。
- 查询方法使用 `@Transactional(readOnly = true)`；写方法明确回滚异常类型。
- 默认一个事务只修改一个聚合；跨聚合一致性通过领域事件与 Outbox。
- Outbox 写入必须与业务写入同一事务；事件发布由 `MessagePublisher` 在事务提交后/独立投递器完成。
- 不在事务内做远程调用、文件上传、Kafka 同步发送、长时间计算。
- 捕获异常后要么处理并记录，要么原样抛出；禁止静默吞异常或只打印消息。
- 自调用不会触发 Spring 事务/权限代理；需要事务的方法不要在同类的内部调用中绕过代理。

## 7. 领域层

- 状态机、名额规则、权限窗口、版本冲突等业务规则放 domain/application，不写在 Controller 或 SQL 里。
- 状态、类型使用枚举；数据库存 TINYINT 时通过 TypeHandler/统一转换映射，API 返回 code+Label；禁止魔法字符串。
- 实体方法维护自身不变量（如报名状态迁移、事件字段校验）；应用服务负责编排与持久化。
- 领域异常使用明确的 `BusinessException` 子类或错误码；不抛裸 `RuntimeException`。
- 领域层不依赖 Spring 注解；需要时间/ID/外部能力时通过参数或接口注入。
- 多队预留：所有按球队隔离的查询必须带 `teamId`，且 `teamId` 来自登录上下文，不信任客户端传入。

## 8. 持久化（MyBatis-Plus / MySQL）

- 禁止 `SELECT *`；显式列名，复杂查询写 XML。
- MyBatis 使用 `#{}` 绑定参数，禁止 `${}` 拼接 SQL；动态排序字段必须走白名单。
- 分页统一使用 MyBatis-Plus 分页插件；列表接口逻辑响应为 05 §2.2 的 `items/total/page/pageSize/totalPages`，Java 用 `PageResponse<T>` 包装 MyBatis-Plus `Page<T>`。
- 唯一约束冲突（报名、球衣号、场次编号等）转换为明确业务错误码，不把 SQL 异常直接抛给前端。
- 乐观锁：阵容、比赛状态、事件修改使用 `version` 条件更新；更新行数为 0 时抛版本冲突。
- 所有业务表使用 `is_deleted` 伪删除（查询过滤 `is_deleted=0`，唯一键不自动释放）；事件另保留 `deleted_at`；账号/比赛/报名同时使用状态字段表达业务生命周期。
- 时间字段统一 UTC；实体使用 `Instant`，Mapper 显式转换；不要依赖 JVM 默认时区。
- 不建物理外键；关联完整性由应用服务校验，查询依赖索引。
- 批量写入使用批量 upsert/foreach，避免循环单条 SQL。

## 9. 安全与权限

- Spring Security 会话认证；C/B 会话由路径前缀选择 `YHFC_C`/`YHFC_B`。
- 权限判断使用权限码，不按角色代码硬编码；提供 `PermissionChecker`/注解统一校验。
- B 端接口额外校验后台分区权限；无权限返回 403，未登录返回 401。
- 密码使用 BCrypt；不记录密码、验证码、会话 ID、CSRF token、openid、手机号明文。
- CSRF token 从响应体获取，写请求通过 `X-XSRF-TOKEN`；登录后轮换会话与 token。
- 文件上传经后端校验类型/大小后写 OSS；不在 domain 直接使用 OSS SDK。
- 任何外部输入都要服务端校验；数据库永久禁用 CHECK，业务规则由应用层强校验，唯一约束/NOT NULL 只作最后防线。

## 10. 事件、Kafka 与 Outbox

- 事件 DTO 放 `contracts`；生产方与消费方都依赖 contracts，不反向依赖业务包。
- 业务事务内写 `outbox_event`；`MessagePublisher` 负责发布；domain 不直接使用 KafkaTemplate。
- 消费者遵循 `kafka-rules.md`：
  - 关闭自动提交；业务成功后提交 offset；
  - 按事件 ID + 消费组去重；Redis 两阶段标记 `kafka::{topic}::{group}::{partition}::{offset}`；
  - 失败删除标记、不提交 offset；超过阈值进死信；
  - 单条消息处理日志用 DEBUG，循环内不逐条 INFO。
- 消费者只做“解析 → 调用 application 服务 → 记录结果”，不在消费者里堆业务规则。
- 通知与审计使用独立消费组，不共用 group。

## 11. 定时任务（Quartz）

- Job 只做调度入口：获取参数、调用 application 服务、记录汇总日志。
- 任务必须幂等：按主键/状态条件更新；重复执行不产生副作用。
- 自动流转只前进不后退；跳过手动覆盖、已取消、已延期、已结束。管理员手动重置（MATCH-06/MAT-12）不受此限制。
- 统计汇总任务通过各域 read port 读取，不直读业务表；全量重算 + 批量 upsert。
- 不在 Job 中直接写复杂 SQL；复用应用服务/查询服务。

## 12. Redis 与缓存

- Key 统一带业务前缀与环境前缀，例如 `yunhefc:{env}:permission:{userId}`。
- 会话、权限缓存、限流、名额锁、幂等键、Kafka 消费标记按架构约定使用；Redis 不作为事实源。
- 分布式锁必须设置 TTL、唯一 value，并在 `finally` 中校验 value 后释放。
- 缓存读取失败可降级到数据库；不得因缓存故障阻塞主流程。
- 禁止缓存用户敏感信息明文（手机号、openid、token）。

## 13. 配置与外部依赖

- 配置使用 `@ConfigurationProperties` 分组绑定；禁止散落 `@Value`。
- 环境通过 Spring profile + 环境变量区分 local/prod；生产凭证不入库、不入 Git。
- OSS、短信、微信、ACR 等凭证从环境变量读取；日志中脱敏。
- 外部 HTTP 调用设置连接/读取超时与重试上限；失败返回明确业务错误。

## 14. 日志

- 遵循 `logging-rules.md`：SLF4J 占位符、结构化 `kv`、异常带堆栈、敏感信息脱敏。
- 不直接打印实体、DTO、集合、请求/响应对象；只打印 ID、状态、数量、耗时。
- 循环与高频路径不逐条 INFO；Kafka 单条消费、Quartz、健康检查不打印 INFO。
- 日志包含 `requestId`；关键业务节点 INFO，排障细节 DEBUG。

## 15. 测试

- 单元测试：JUnit 5，覆盖领域规则、状态机、名额/递推、权限窗口。
- 集成测试：Testcontainers 启动 MySQL/Redis/Kafka；禁止用 H2 替代 MySQL 验证 SQL。
- 架构测试：ArchUnit 校验 controller 不访问 mapper、领域不跨域访问 infrastructure、common/contracts 不依赖业务域、query/stats 只依赖 read port。
- 测试命名：`<Class>Test`（单元）、`<Class>IT`（集成）；使用固定 Clock 与测试数据构建器。
- 每个业务错误码至少有一个失败路径测试；唯一约束、版本冲突必须有测试。

## 16. 禁止事项

- `System.out` / `System.err` / `printStackTrace`。
- 字段注入、`@Transactional` 放 Controller、Controller 直接调用 Mapper。
- 字符串拼接 SQL、`SELECT *`、`${}` 拼接参数。
- 把实体直接作为 API 响应；把数据库字段随意改名。
- 静默吞异常、只记录 `e.getMessage()` 不带堆栈。
- 在 domain 直接使用 Kafka/Redis/OSS/Spring Web。
- 按角色代码硬编码权限；在日志中输出手机号、openid、token。
- 为一次性需求增加通用抽象或配置开关。

## 17. 代码审查清单

- [ ] 依赖方向符合包结构；无跨域 Mapper 访问
- [ ] Controller 无业务规则；DTO 与数据库字段对应
- [ ] `@Transactional` 在 application 层；Outbox 同事务
- [ ] 状态/类型使用枚举或常量；无魔法字符串
- [ ] MyBatis 使用 `#{}`；分页、索引、乐观锁正确
- [ ] 权限码校验；C/B 会话与 CSRF 正确
- [ ] 事件 DTO 在 contracts；消费者幂等与 offset 提交正确
- [ ] Job 幂等；统计通过 read port
- [ ] 日志无敏感信息；异常带堆栈
- [ ] 单元/集成/ArchUnit 测试覆盖关键路径与失败路径
