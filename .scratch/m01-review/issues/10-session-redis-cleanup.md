# 10 — 会话、Redis 敏感值与清理任务

**What to build:** 会话 Cookie 实现滑动过期；Redis key 不出现明文手机号；幂等、消费去重与 Outbox 增加过期读取与定期清理。

**Blocked by:** None — can start immediately.

**Status:** done — 待提交

- [x] Cookie Max-Age 随请求续期，符合 api-rules §7.1 的 C 7 天/B 2 小时滑动窗口
- [x] 登录失败、短信、限流 key 使用手机号哈希；抽出统一 `Hashing` 工具
- [x] `idempotency_key` 读取过滤 `expires_at`，过期记录不再 replay
- [x] Quartz 清理任务覆盖 `idempotency_key`、`consumed_event`、已发送 `outbox_event`
- [x] 测试覆盖过期重放与清理