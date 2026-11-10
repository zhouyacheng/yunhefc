# 08 — 报名接口限流（05 §2.7 / 03 AQ-20）

**What to build:** 对 `REG-01` 报名接口按账号 + 客户端 IP 做严格限流，超限返回 429 并带 `Retry-After`，避免用新幂等键反复取比赛行锁、拉长正常报名等待。

**Blocked by:** 01 — 报名与候补主流程（REG-01/09）

**Status:** ready-for-agent

- [ ] 读 05 §2.7、03 §5（AQ-20）、06 认证限流结论、`common/ratelimit/RedisRateLimiter`。
- [ ] 复用 `RedisRateLimiter.check`；key 为 `registration:<userId>:<clientIp>`，阈值 10 次/分钟（与短信 5/分钟、登录同量级）；`RequestContext.clientIp()` 取真实客户端 IP。
- [ ] 超限由既有 `GlobalExceptionHandler` 返回 `42901` / HTTP 429，并带 `Retry-After`；不新增错误码。
- [ ] 限流先于幂等占位执行（Servlet Filter 或前置 Aspect，Order 早于 `IdempotentAspect`）：同一幂等键重放也计入窗口；不要放在 Controller 方法体内，否则重放会被幂等直接短路；失败请求同样计数。
- [ ] 不同账号、不同 IP 的窗口互不影响；Redis 不可用时限流降级放行，不阻塞报名主流程（与 03 §5 缓存降级一致）。
- [ ] 测试：窗口内超限 429 + `Retry-After`、不同账号互不影响、同幂等键重放计入限流、Redis 故障不阻塞报名。
