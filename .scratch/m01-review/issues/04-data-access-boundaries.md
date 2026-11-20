# 04 — 数据访问与模块边界

**What to build:** 业务域不再直接依赖其他域的 Mapper；跨域只读数据通过 `contracts` 中的 read port 获取；新增 ArchUnit 规则阻止跨域 infrastructure 访问；移除 `SELECT *`。这是宽重构，按 expand–contract 执行。

**Blocked by:** 01 — 认证与身份授权加固；02 — 邀请生命周期正确性；03 — 可靠消息与审计一致性.

**Status:** done — 70411da

- [x] 先新增 player/notification/match 的只读 port 与适配器，旧调用保持可用
- [x] 按模块迁移 auth/invite/wechat 等调用方到 read port
- [x] 删除跨域 Mapper 直接依赖，业务域只依赖本域 infrastructure
- [x] 新增 ArchUnit 测试：controller 不访问 mapper、领域不跨域访问 infrastructure、common/contracts 不依赖业务域
- [x] 移除 M01 范围内的 `SELECT *`
- [x] 全量测试通过
