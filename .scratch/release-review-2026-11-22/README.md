# 发版前两轴复审遗留项（2026-11-22）

## 事实源

- 复审范围：`main...qua`，按四个切片分别跑 Standards + Spec 两轴：miniprogram、backend、deploy/scripts。
- 设计依据：`docs/design/02`–`06`、`docs/rules/*`、`.scratch/mvp-release/release-checklist.md`、`deploy/RUNBOOK.md`。
- 已修（P0）：小程序 MP03 首屏 month、退出登录被撤销、管理员禁用原因、spike 页、CONTEXT.md 失真；时区脚本 `operated_at` 与防重跑、发布向导迁移门禁、回滚判定、向导目录名与默认 sha7、清理脚本 1040–1059、`release.sh` 冒烟/预检、审计原文快照、Kafka 日志级别、CI 证据口径。

## 票序

1. `issues/01-auth-contract-deviations.md` — 票据 Redis 10 分钟一次性、短信 IP 限流（cutover 前；`ready-for-agent`）
2. `issues/02-deploy-script-hardening.md` — 验收向导事实错误、gitleaks 历史、ssh-keyscan、infra manifest、冒烟断言（本批；`ready-for-agent`）
3. `issues/04-wizard-shared-lib.md` — 五向导共享库与 rsync `--delete`（RC 后；blocked by 02）
4. `issues/03-code-smells.md` — 小程序与后端重复代码/命名/分页（RC 后；`ready-for-agent`）

## 2026-11-22 收口裁定

- 发布入口：仅 Actions `deploy`；SSH 手工发布为 break-glass。
- 硬门槛：检查单 §0.1/§1 去掉 §10 已接受项；证据分级见 §0.4。
- 迁移门禁：后端 prod 启动自检（ADR 0002）；冒烟删除 wechat 端点断言。
- CSRF 豁免与保留任务 INFO 口径已落文档（api-rules §7、logging-rules、06 §107）。

## 约束

- 设计冻结：不改 DB/API 契约、权限码、错误码与接口数量；契约偏离要么补文档进 06，要么按 05 修代码。
- 已发布的 Flyway 迁移不可修改。
- `.scratch/` 默认不提交 git。
