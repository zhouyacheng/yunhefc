# 12 — 低优先级加固清单

**What to build:** 收口 M01 评审中不影响主流程、但上线前应处理的细节。

**Blocked by:** 07、10 — 依赖认证写入与 Redis 清理的落地.

**Status:** ready-for-agent

- [ ] 接入阿里云短信，按 purpose 使用模板发送；生产不再依赖 mock
- [ ] 短信发送按手机号 + IP 限流
- [ ] 验证码校验与删除原子化，避免并发复用
- [ ] `WechatAuthService` 唯一键冲突分类去掉异常文本依赖，并补测试
- [ ] 评估“手机号已存在”时避免白耗短信验证码，同时保留手机号枚举防护
- [ ] Redis 限流 INCR/EXPIRE 原子化
- [ ] Outbox `NOW(3)` 时区、租约与顺序策略复核
- [ ] 微信 real 模式错误码、双重编码与登录写 `last_login_at`
