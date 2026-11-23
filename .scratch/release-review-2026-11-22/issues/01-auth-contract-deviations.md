# 认证契约偏离（cutover 前修复）

Type: task
Status: ready-for-agent
Blocked by: —

## 问题

1. **AUTH-20/21 票据未按 05 §2.4 实现**：`MiniAuthService` 把原始票据放 HttpSession（全局 7 天），未用 Redis `sha256(ticket)` + 10 分钟；绑手机失败时恢复票据，破坏一次性语义。
2. **短信缺 IP 维度限流**：`AuthController.sms` 只有「号码 5 次/分」，缺 03 §5 的「10 次/小时/IP」。

## 需求

- 1 改为 Redis 一次性票据（`sha256(ticket)`，10 分钟 TTL，兑换即删），绑手机失败不恢复票据；补并发兑换与过期用例。
- 2 按 03 §5 补 IP 计数与小时窗口；取真实 IP 走既有 Nginx `X-Forwarded-For` 链路；补限流用例。
- 两项完成后部署到生产 RC，复验小程序真机登录与绑手机。

## 已裁定（2026-11-22，不在本票内）

- CSRF 豁免：`/c/auth/mini/login`、`/c/auth/mini/bind-phone` 记为窄例外，已写入 `docs/rules/api-rules.md` §7 与 06 §107。
- 保留任务日志级别：成功 INFO + 删除数量，已写入 `docs/rules/logging-rules.md` 与 06 §107。

## Comments

- 2026-11-22：两项已实现。票据改为 `MiniBindTicketStore`（Redis 键 `sha256(ticket)`，10 分钟 TTL，`GETDEL` 消费，失败不恢复）；短信增加 10 次/小时/IP（`yunhefc.auth.sms-rate-limit.ip-max-per-hour`，默认 10，环境变量 `AUTH_SMS_IP_RATE_LIMIT_MAX_PER_HOUR`）。`MiniAuthServiceTest`、`MiniBindTicketStoreTest`、`MiniProgramAuthIT` 已更新；后端全量 `mvn verify` 通过。待部署后复验真机登录与绑手机。
- 2026-11-22 复审补：openid 以票据派生 AES-GCM 密钥加密后存 Redis（明文不落库，java-rules §12）；补并发兑换、过期/已消费、篡改密文用例。
