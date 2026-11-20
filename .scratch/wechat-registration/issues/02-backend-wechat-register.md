# 02 — 后端微信注册最小闭环

**What to build:** 微信授权未命中 openid 时，AUTH-09 只凭姓名与主位置创建 phone 为空的 PENDING+GUEST 账号并直接登录；AUTH-08 继续返回 `needBindPhone=true` 引导补资料。

**Blocked by:** 01

**Status:** wontfix（2026-11-20 微信注册已移除）

- [ ] AUTH-09 允许 `phone/code` 缺省，校验 name/position 并建档（phone=NULL、password_hash=NULL）
- [ ] 新账号授予 GUEST、player.type=GUEST，审计 `WECHAT_REGISTER`
- [ ] 手机号存在时保留原「绑定已有账号」语义
- [ ] `loginAttemptService.clear(null)` 与所有 `sha256(phone)` 调用点不抛 NPE
- [ ] 无手机号建档按 `sha256(openid)` 限流，超限 42901 + Retry-After
- [ ] IT：无手机号注册 → 200、status=0、phoneMasked=null、wechatBound=true；PENDING 写接口 40303
