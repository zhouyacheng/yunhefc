# 05 — 前端类型、会话封装与 C05 微信注册入口

**What to build:** 前端共享类型允许 `phoneMasked` 为空；C05 注册模式在微信内提供「微信注册」入口，非微信浏览器给出降级提示。

**Blocked by:** 02

**Status:** done

- [ ] `User.phoneMasked` 与 `UserSummary.phoneMasked` 改为 `string | null`
- [ ] `session` 新增微信注册与补绑手机号的调用封装（处理 csrfToken 与刷新）
- [ ] C05 注册模式显示微信注册入口（仅微信 UA）；非微信显示「请在微信内打开」
- [ ] 注册页提示「已是成员请先用手机号登录再绑定微信」
- [ ] Vitest：类型/入口可见性/会话封装
