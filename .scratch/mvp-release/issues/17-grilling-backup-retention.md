# 17 — 备份与数据保留

Type: grilling
Status: resolved
Blocked by: 07, 08, 12

## Question

用户明确不做恢复演练，但备份与清理机制仍须定义：

- MySQL（阿里云 RDS 8.4）：云自动备份 + binlog/PITR、备份窗口与保留 7 天；核实实例规格/存储/备份保留，决定是否仍需本地或 OSS 副本（票 12 已确认不使用 mysqldump 容器）。
- Redis AOF everysec + RDB；内存监控。
- 数据保留：通知 90 天、已投递 Outbox 7 天、失效邀请 30 天、审计永久保留并每年导出 OSS。
- 清理任务的调度归属：Quartz 任务 vs 部署脚本；失败处理与监控。
- 年度审计导出的自动化 vs 手工 runbook（与 08 定稿一致）。
- 明确恢复演练不在本次范围，但备份策略须可验证：RDS 备份任务成功、可恢复时间点、备份产物在控制台可见。

产出配置与脚本范围。事实指针：03 §5 数据保留/备份、需求 DQ-13、07 / 08 / 12 答案。

## Comments

- 票 15 遗留（2026-11-12）：头像上传未保存会产生孤儿对象（`avatar/{userId}/{ulid}.{ext}`），本批不清扫；是否纳入保留/生命周期策略由本票判断。审计年度导出复用 `infra.storage.OssGateway`。

## Answer

状态：resolved（2026-11-12）。以下为已确认决策；本票不写配置、脚本或清理代码，实施归 18 号票切片后的批次。

### 1. MySQL / RDS 与主机文件

- 只用 RDS 自动备份 + binlog/PITR，保留 **7 天**；不做 OSS/本地逻辑副本，不做手动快照，不做 ECS 磁盘快照。
- 迁移不兼容时按 PITR 恢复；发布前确认 PITR 可恢复时间点覆盖回滚窗口，覆盖不了则推迟发布，不以快照替代。
- 主机文件只把 `.env` 放入密码管理器离线保管、仅用于灾难恢复；运行时仍从 ECS `/opt/yunhefc/.env`（600）读取；TLS 证书/私钥丢失后重新签发；部署文件由 git/ACR 重建。
- 不做备份恢复演练（用户已明确排除）；备份新鲜度只在每次发布前人工检查（RDS 控制台：自动备份开启、保留 7 天、binlog 开启、最近备份与 PITR 范围可见），不新增云监控告警，不设固定周检。

### 2. Redis / OSS

- Redis 仅保留 AOF everysec + RDB 命名卷；不做独立备份、RDB 上传 OSS、主从或异地副本。丢卷仅会话/缓存丢失，用户重新登录。
- OSS 不做版本控制、跨区域复制或生命周期规则；头像孤儿对象接受不清扫；审计导出对象永久保留、不自动删除。
- 审计导出继续用同一私有 Bucket 的 `audit/export/{year}/audit-{year}.jsonl.gz`；RAM 策略需同时授权 `avatar/` 与 `audit/export/` 前缀。

### 3. 数据保留与清理

| 数据 | 保留 | 删除方式 |
|---|---|---|
| notification | 90 天 | 按 `created_at` 物理删除；票 07 已定，不加索引 |
| invite_link + invite_use | 失效后 30 天 | `status=2` 且 `expires_at < now-30d`，或 `status=1` 且 `revoked_at < now-30d`；同一事务内先删 `invite_use` 再删 `invite_link` |
| outbox_event SENT | 7 天 | 按 `sent_at` 物理删除 |
| outbox_event FAILED | 7 天 | 按 `created_at` 物理删除；删除前 WARN 记录数量；PENDING 不清理 |
| consumed_event | 7 天 | 按 `processed_at` 物理删除 |
| idempotency_key | 过期即清 | 按 `expires_at` 物理删除 |
| audit_log | 在线永久 | 年度 JSONL.gz 导出 OSS（票 08 已定） |

- 邀请清理不删除审计记录；`audit_log` 保留原文与 `target_id`。
- 不新增清理专用索引、不改 schema；接受必要的扫描，单队数据量可接受。物理删除必须自定义 `@Delete` 或绕过 `@TableLogic`。

### 4. 配置与调度

- 新增 `application.yml` 配置键（`@ConfigurationProperties` 正数校验、环境变量 relaxed binding 可覆盖、无管理界面）：
  - `yunhefc.retention.notification-days=90`
  - `yunhefc.retention.invite-days=30`
  - `yunhefc.retention.outbox-days=7`
  - `yunhefc.retention.consumed-event-days=7`
- 批量删除每轮最多 1000 行，循环到不足一批；批大小为代码常量，不开放配置。
- 单一 `DataRetentionJob` 每日 **03:30 Asia/Shanghai** 执行，编排各模块 retention port；单任务失败不阻断其他任务，成功计数 INFO、失败 ERROR。
- `InviteExpiryJob` 保持每日 03:00，只负责把到期邀请置 EXPIRED；30 天后的物理删除归 `DataRetentionJob`，两者不合并。
- 不用部署 cron、不用 Spring `@Scheduled`；保留 Quartz JDBC JobStore。
- 审计导出沿用票 08：Quartz 每年 1 月 1 日、上一自然年 Asia/Shanghai、JSONL.gz、按年幂等、`yunhefc.audit.export.enabled` 默认 false、失败 ERROR + runbook 手工补导。

### 5. 运维与验证

- 清理/导出失败只写 ERROR 日志，由发布前检查与日常排障发现；不新增告警。
- 实施批次创建 `deploy/RUNBOOK.md`，覆盖：RDS PITR 恢复、`.env` 恢复、证书重签、审计补导、FAILED Outbox 处置、清理失败检查。
- 上线检查项写入票 19，不在本票执行。

### 影响面

- 03 v1.18：§2.1、§5.4、§6.1/§6.4、§7、§8、§9、§10、§11.1（新增 AQ-45/46）。
- 04 v0.21：§2.3、§6.1、§6.2、§10 DB-13；不新增索引，`schema.sql`/Flyway 不改。
- 06 v1.48：新增 §51；注明取代 §49 的 RAM/孤儿对象口径与 §44 的 Outbox 保留口径。
- `docs/rules/database-rules.md` §6 保留策略同步。
- 票 12/15/16/19 补 Comment；05/02/需求/01/原型不改。
- 18 号票：本票关闭后阻塞项减少一项；清理实现、配置类、`deploy/RUNBOOK.md` 归实施批次。

### 未执行

- 未执行 SQL、未改 Flyway、未写业务代码、未创建 Compose/Nginx/CI/云监控配置、未运行后端/前端测试。
- 未创建 `deploy/RUNBOOK.md`，未做真实 RDS/Redis/OSS 控制台核验；上线检查归票 19。
