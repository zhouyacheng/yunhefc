---
name: logging-rules
description: 后端 Java 日志级别、字段、异常记录与敏感信息脱敏规范
---

# 日志编码规范

## 1 目的与适用范围

本规范约束后端 Java 代码中的日志写法，适用于 `backend` 单部署单元内的所有模块，包括 `common`、`contracts`、`infra`、`query` 与各业务领域包。

日志的格式、级别、滚动与输出位置由 `logback-spring.xml` 统一配置；日志保留与可观测性约定见 `docs/design/03.整体架构设计.md` 3.4、7。本规范只约束**代码里怎么写日志**。

---

## 2 强制规则

### 2.1 统一使用 SLF4J API

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
```

禁止：

- `System.out.println`、`System.err.println`
- `e.printStackTrace()`
- 直接使用 `org.apache.log4j.Logger`、`java.util.logging.Logger`
- 在业务代码中拼接日志输出流

### 2.2 使用占位符，禁止字符串拼接

```java
// 正确
log.info("registration created, matchId={}, playerId={}", matchId, playerId);

// 错误
log.info("registration created, matchId=" + matchId + ", playerId=" + playerId);
```

- 占位符数量必须与参数数量一致。
- 不在日志中做字符串格式化、JSON 序列化等耗时操作，除非日志级别确实开启。

### 2.3 不直接打印大对象或复杂对象

```java
// 错误：可能触发懒加载、序列化耗时、泄露敏感字段
log.info("player={}", player);

// 正确：只打印必要字段
log.info("player updated, playerId={}, status={}", player.getId(), player.getStatus());
```

- 禁止直接打印实体、聚合根、DTO、请求/响应对象、集合。
- 需要记录复杂对象时，只打印标识、状态、数量、耗时等关键字段。
- 如确需序列化，使用白名单日志 DTO，并在序列化前脱敏。
- 禁止 `log.info(JSON.toJSONString(obj))` 这类写法。

### 2.4 循环与高频路径减少日志

```java
// 错误：逐条打印，容易形成日志风暴
for (Match match : matches) {
    log.info("match loaded, id={}", match.getId());
}

// 正确：批量结束后汇总
log.info("matches loaded, count={}, durationMs={}", matches.size(), durationMs);
```

- 循环内只记录失败项，且设置上限或采样。
- Kafka 单条消息、Quartz 定时任务、健康检查与心跳不打印 INFO。
- 高频事件使用 DEBUG 或指标统计，生产环境默认关闭 DEBUG。

### 2.5 异常打印保留堆栈

```java
try {
    registrationService.confirm(matchId, playerId);
} catch (Exception e) {
    log.error("confirm registration failed, matchId={}, playerId={}", matchId, playerId, e);
}
```

禁止：

```java
log.error(e.getMessage());
log.error("confirm registration failed: " + e.toString());
e.printStackTrace();
```

- 异常对象必须作为最后一个参数传入，Logback 才会输出堆栈。
- 同一异常只在一个边界层记录，避免每层重复打印。
- 记录后继续抛出时，不要丢失原始异常。

### 2.6 敏感信息脱敏

**禁止直接打印**：

- 手机号、身份证号、邮箱、地址、银行账号
- 密码、短信验证码、会话 ID、CSRF token
- 微信 openid / unionid
- OSS、微信、短信、ACR 等外部凭证

注：身份证号、邮箱、地址、银行账号当前 MVP 数据模型未包含，按预留敏感字段一并保护；二期账本上线后必须沿用。

**正确做法**：

```java
log.info("user login, phone={}", MaskUtils.maskPhone(user.getPhone()));
// 输出：user login, phone=138****2049
```

- 优先在调用点使用 `MaskUtils` 脱敏，再写入日志。
- 统一脱敏工具放在 `com.yunhefc.common.util.MaskUtils`。
- 禁止把未脱敏对象交给日志框架后再指望全局替换。

**MessageConverter 兜底**：

- 在 `logback-spring.xml` 注册自定义 `MessageConverter`，对文本 Pattern 输出做二次脱敏：

```xml
<conversionRule conversionWord="maskedMsg"
                converterClass="com.yunhefc.infra.logging.SensitiveMessageConverter"/>
<pattern>%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%X{traceId:-}] %logger{36} - %maskedMsg%n</pattern>
```

- **JSON 输出注意**：`LogstashEncoder` 不会经过 Pattern 的 `MessageConverter`。因此：
  - JSON 日志必须在调用点完成脱敏；
  - 如需编码层兜底，使用自定义 `JsonProvider` / `JsonFactoryDecorator` 对 `message` 字段做同样的脱敏；
  - 不允许只配置 `MessageConverter` 就认为 JSON 日志已安全。

### 2.7 日志消息结构化

日志的 JSON 结构由编码器生成，**禁止手工拼接 JSON 字符串**。

```java
import static net.logstash.logback.argument.StructuredArguments.kv;

log.info("registration created, registrationId={}",
        registrationId,
        kv("bizType", "registration"),
        kv("bizId", registrationId),
        kv("matchId", matchId),
        kv("playerId", playerId),
        kv("durationMs", cost));
```

- `message` 保持简洁、可读，描述“发生了什么”。
- 业务字段通过 `StructuredArguments.kv` 输出为 JSON 顶层字段。
- 字段命名使用 lowerCamelCase，例如 `bizType`、`bizId`、`matchId`、`registrationId`、`playerId`、`eventId`、`durationMs`、`result`。
- 不要覆盖编码器的标准字段：`timestamp`、`level`、`logger`、`thread`、`message`、`exception`、`app`、`env`、`traceId`（启用链路追踪时）、`userId`。
- 敏感字段先脱敏再放入 `kv`。
- 控制台为文本 Pattern，结构化字段以 JSON 文件为准；不要为了控制台再手工拼 JSON。

---

## 3 日志级别约定

| 级别 | 使用场景 | 示例 |
|---|---|---|
| ERROR | 需要人工处理的失败、数据不一致、外部依赖不可用 | 报名落库失败、Outbox 投递连续失败、通知消费连续重试失败 |
| WARN | 可恢复的异常、降级、重试、业务规则拦截 | 站内通知写入失败、OSS 上传失败、短信发送失败 |
| INFO | 关键业务节点、状态流转、批量结果 | 建赛、报名成功、名单锁定、比赛结束、通知消费批次汇总 |
| DEBUG | 排障细节、单条数据处理、循环内信息 | 单场分组计算细节、Kafka 单条消费、SQL 参数 |
| TRACE | 不在生产使用 | — |

规则：

1. 业务关键节点用 INFO，排障细节用 DEBUG。
2. 生产环境默认 INFO，禁止长期开启 DEBUG。
3. ERROR 必须带异常堆栈；WARN 视情况带堆栈。
4. 不要用 ERROR 记录可恢复的校验失败。

---

## 4 结构化字段建议

| 字段 | 说明 |
|---|---|
| `bizType` | 业务类型，如 `match`、`registration`、`attendance`、`lineup`、`event`、`notification` |
| `bizId` | 业务主键 |
| `playerId` | 球员 ID |
| `matchId` | 比赛 ID |
| `registrationId` | 报名记录 ID |
| `eventId` | 比赛事件 ID |
| `durationMs` | 耗时 |
| `result` | 业务结果，如 `success`、`failed`、`retry` |

- 只放必要的键值，不要把所有字段都塞进日志。
- 不把集合、对象、JSON 字符串放进 `kv`。

---

## 5 代码审查与自动检查

### 5.1 代码审查清单

- [ ] 没有 `System.out` / `System.err` / `printStackTrace`
- [ ] 没有字符串拼接日志
- [ ] 没有直接打印实体、DTO、集合、请求/响应对象
- [ ] 循环与高频路径没有逐条 INFO
- [ ] 异常日志带异常对象
- [ ] 敏感字段已脱敏
- [ ] JSON 字段使用 `kv`，没有手工拼 JSON
- [ ] 日志级别与场景匹配

### 5.2 CI 检查

建议在 CI 中增加静态检查：

```bash
# 禁止标准输出与直接使用 Log4j
grep -rnE 'System\.(out|err)\.print|printStackTrace|org\.apache\.log4j' backend --include='*.java' || true

# 禁止日志字符串拼接（简单规则，需人工复核）
grep -rnE 'log\.(trace|debug|info|warn|error)\([^)]*\+' backend --include='*.java' || true
```

- 可进一步接入 Checkstyle / PMD / ArchUnit 自定义规则。
- 检查失败应阻断合并，而不是只告警。

---

## 6 正反例汇总

| 场景 | 错误写法 | 正确写法 |
|---|---|---|
| 普通日志 | `log.info("id=" + id)` | `log.info("id={}", id)` |
| 对象 | `log.info("player={}", player)` | `log.info("player updated, playerId={}", player.getId())` |
| 循环 | 循环内逐条 `log.info` | 循环结束后汇总数量与耗时 |
| 异常 | `log.error(e.getMessage())` | `log.error("confirm registration failed, matchId={}", matchId, e)` |
| 敏感信息 | `log.info("phone={}", phone)` | `log.info("phone={}", MaskUtils.maskPhone(phone))` |
| 结构化 | `log.info(JSON.toJSONString(obj))` | `log.info("match created, matchId={}", matchId, kv("bizId", matchId))` |
| 输出 | `System.out.println(...)` | `log.info(...)` |
