# 08 — 审计年度导出

Type: implementation
Status: resolved

**What to build:** 每年 1 月 1 日把上一自然年的全部审计日志导出为 JSONL.gz，写入私有 OSS 作为在线永久保留之外的冷备；按年幂等，开关默认关闭，失败只记日志、不删除在线数据。

**Blocked by:** 01 — M13 OSS 网关与头像上传；07 — M10 审计查询与脱敏

**Status:** resolved

- [x] Quartz 任务每年 1 月 1 日 Asia/Shanghai 触发，导出上一自然年（本地时区口径）的全部 `audit_log`。
- [x] 输出为 JSONL.gz：一行一条，按 `id` 升序；快照为 JSON 对象；保留原文，不做脱敏。
- [x] 对象写 `audit/export/{year}/audit-{year}.jsonl.gz`，经 `OssGateway` 写入私有 Bucket；对象已存在则跳过（按年幂等）。
- [x] 开关 `yunhefc.audit.export.enabled` 默认 false；关闭时不执行，也不产生错误。
- [x] 失败记录 ERROR 日志，不自动重试、不删除在线审计；不设生命周期或对象删除逻辑。
- [x] 测试用内存 OSS 网关断言：对象键、gzip 可解、行数与 `id` 顺序、快照为对象、重复执行不覆盖、关闭时不写对象；断言 Quartz JobDetail/CronTrigger 的 key 与表达式；`mvn verify` 全绿。

## Comments

- 2026-11-12 交付：`5cb399a`。实现 `AuditExportApplicationService` + `AuditExportSerializer` + `AuditExportJob`/`AuditExportQuartzConfig`；查询走 `AuditLogMapper.selectForExport`（显式导出全部行，不经 `@TableLogic` 过滤）。`mvn verify` 132 单测 + 276 集成测试全绿。
