# 04 — 已登录补绑手机号（AUTH-20）

**What to build:** 微信注册的无手机号账号可在个人中心用短信验证码补绑手机号；已有手机号的账号仍只能由管理员换绑。

**Blocked by:** 01

**Status:** wontfix（2026-11-20 微信注册已移除）

- [ ] 新增 `POST /c/me/phone`，请求 `{phone, code}`，响应 `{user}`
- [ ] 仅 `phone IS NULL` 可绑定；已有手机号拒绝；占用返回 40907
- [ ] 校验 BIND 短信码；审计 `PHONE_BIND`；PENDING 白名单放行
- [ ] `UserViewAssembler` 对 null 手机号返回 `phoneMasked=null`，name 双空回退「微信用户」
- [ ] IT：补绑成功、已有手机号拒绝、被占用 40907、验证码错误 40001
