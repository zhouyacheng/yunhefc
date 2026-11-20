# 09 — 数据保留任务

Type: implementation
Status: resolved

**What to build:** 一个统一的每日保留任务，在 03:30 Asia/Shanghai 依次物理清理通知、失效邀请、Outbox、消费去重与幂等键；保留天数可配置，每类分批删除且互不阻断，失败可观测。

**Blocked by:** 03 — M09 通知读接口与转正通知

**Status:** resolved

- [x] 单一 `DataRetentionJob` 每日 03:30 Asia/Shanghai 执行，编排各域清理；`InviteExpiryJob` 保持 03:00 只把到期邀请置失效，两者不合并。
- [x] 通知按 `created_at` 满 90 天物理删除；失效邀请满 30 天删除（`status=2` 且 `expires_at < now-30d`，或 `status=1` 且 `revoked_at < now-30d`），同事务先删使用记录再删邀请。
- [x] Outbox SENT 按 `sent_at` 满 7 天、FAILED 按 `created_at` 满 7 天删除（删除前 WARN 数量），PENDING 不清理；消费去重满 7 天；幂等键按 `expires_at`。
- [x] 新增 `yunhefc.retention.{notification-days,invite-days,outbox-days,consumed-event-days}`，默认 90/30/7/7，正数校验，可由环境变量覆盖，无管理界面。
- [x] 每类每轮最多删除 1000 行并循环到不足一批；批大小是代码常量。
- [x] 单类失败记 ERROR 并继续其他类；成功记 INFO 与删除数量；任务重复执行幂等。
- [x] 物理删除必须自定义 `@Delete` 绕过 `@TableLogic`；不新增索引、不改 schema。
- [x] 测试覆盖：每类删除与保留边界、FAILED 的 WARN 计数、PENDING 不清、邀请先 use 后 link、每轮批大小与多轮循环、单类失败不影响其他类、重复执行幂等；断言 Quartz Trigger 的 cron 与时区；`mvn verify` 全绿。

## Comments

- 2026-11-12 交付：提交 `61842e8`（feat: add scheduled data retention cleanup）。
- 验证：`mvn verify` BUILD SUCCESS（138 单测 / 306 IT / 0 failures，JDK 21）；新增 `DataRetentionServiceTest` 3 项、`RetentionPropertiesTest` 3 项、`DataRetentionIT` 9 项。
- 实现位置：`infra.maintenance.DataRetentionService`（按 `@Order` 编排、1000 行批循环、单类失败隔离）、`common.retention.RetentionTask`、六个适配器（notification/invite/outbox-sent/outbox-failed/consumed-event/idempotency）、`common.config.RetentionProperties`、mapper 自定义 `@Delete`/`LIMIT`；`DataRetentionQuartzConfig` 显式 Asia/Shanghai。
- 评审：两轴评审已完成。Standards 指出 `RetentionTask` 属写端口，不应放只读 `contracts`，已移至 `common.retention`；单元测试已按包镜像放到 `infra/maintenance`、`common/config`；IT 改用固定 Clock 并补精确边界用例。Spec 无阻断项；已补边界断言（严格 `<`，幂等键 `<=`）。
- 文档：本票无契约/DDL 变更；06 追加 §58 实施记录，README 回填交付表。
