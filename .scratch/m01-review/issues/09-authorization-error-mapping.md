# 09 — 授权白名单与错误映射

**What to build:** PENDING 白名单按 05 §2.4 显式匹配路径与方法；AUTH-17 `/b/me` 校验任一后台权限；框架 4xx（405/415 等）不再落为 500。

**Blocked by:** None — 405/415 需先在 05 补错误码契约.

**Status:** done — 待提交

- [x] PENDING 白名单改为显式路径/方法列表，移除“全部 GET/HEAD/OPTIONS 放行”
- [x] PENDING 允许 PLR-03/FIL-01/NTF-03/04 等已定义写接口；未实现接口先登记
- [x] `/b/me` 校验任一后台权限，无权限返回 403
- [x] `HttpRequestMethodNotSupportedException`/`HttpMediaTypeNotSupportedException` 等返回 405/415，不再 500
- [x] 测试覆盖 PENDING 读写、`/b/me`、405/415