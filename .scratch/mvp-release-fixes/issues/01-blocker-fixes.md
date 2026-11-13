# 01 — 复核阻断项修复

Type: implementation
Status: resolved
Blocked by: None

**What to build:** 修复 `aaa382a..HEAD` 两轴复核确认的上线阻断项：4 条 Standards 硬违规 + B02 新建比赛默认名额未读取 SET-01；并补齐 deploy 同步口径、记录其余 Spec 偏差。

**验收标准**

- [x] `PlayerReadPort.findUserIdsByNameContaining` 增加 `teamId` 参数，`PlayerReadAdapter` 按 `team_id` 过滤，`AuthService.listUsers` 传当前球队；`RoleManagementIT` keyword 用例仍绿。
- [x] `DataRetentionService.cleanup` 成功日志降为 DEBUG（Quartz 任务不打 INFO），`DataRetentionServiceTest` 断言同步改为 DEBUG。
- [x] 新增 `com.yunhefc.infra.logging.SensitiveMessageConverter`，`logback-spring.xml` 注册 `maskedMsg` 并用于非 prod 文本 console pattern；单测覆盖手机号/邮箱/身份证号。
- [x] `WechatAuthService` 两处日志改用 `MaskUtils.maskPhone`；`AuthService` 两处同类日志一并统一。
- [x] B02 新建弹窗通过 `GET /settings`（`settings:view:any`）预填 `maxPlayers/maxGuests/maxWaitlist`，无权限或失败回退 30/4/2；shared 纯函数 + 单测。
- [x] `deploy.yml` rsync `deploy/`（排除 `.env`）到 release 目录；`RUNBOOK.md` 手工命令同步。
- [x] Spec 偏差记账：`schema.sql` 注释改动（B1）、`setup-ci-external.sh` 与 `AUTH_LOGIN_RATE_LIMIT_*`（B0）；修正 B1 票 07/08 正文 Status。
- [x] `mvn verify`、`pnpm -r typecheck`、`pnpm -r test` 通过。

## Result

- 后端：`mvn verify` BUILD SUCCESS（单测 + 311 IT，0 failures，JDK 21）。
- 前端：`pnpm -r typecheck` 全绿；`pnpm -r test` 全绿（shared 50 / web 168）。
- 代码：`PlayerReadPort` 增加 `teamId`；`MaskUtils.maskText` + `SensitiveMessageConverter` + logback `maskedMsg`；`DataRetentionService` DEBUG；日志统一 `MaskUtils`；`capacityDefaults` + MatchesPage 读取 SET-01；`deploy.yml` 全量 rsync。
- 记账：B0/B1 README 偏差记录；B1 票 07/08 正文状态修正。
- 未提交 git，等用户确认。

## Comments

- 2026-11-14 开工：范围与口径经用户确认；不改设计契约。
