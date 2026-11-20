# 02 — 邀请生命周期正确性

**What to build:** 入队邀请接受只替换 GUEST 为 PLAYER，不删除自定义角色；比赛邀请在一次注册/接受流程中只消费一次；已有手机号绑定微信时 `position` 可省略；INV-05 必须显式传 `type`；过期邀请由每日任务落为 `status=2`，管理端可按状态筛选。

**Blocked by:** 01 — 认证与身份授权加固.

**Status:** done — 7e9923a

- [x] INV-02 接受入队邀请后，仅移除 GUEST、授予 PLAYER，保留其他自定义角色
- [x] 同一比赛邀请在“邀请注册 + 接受”链路中只增加一次 `used_count`
- [x] AUTH-09 已有手机号绑定时不要求 `position`；新账号仍要求 `name`/`position`
- [x] INV-05 缺失 `type` 返回 400，不再默认 TEAM
- [x] 每日任务把过期邀请写为 `status=2`；INV-04 `status=2` 可查到
- [x] 集成测试覆盖以上分支

> 注：`InviteExpiryJob` 当前用 `@Scheduled` 落地每日过期处理；按决策将在 issue 05/06 统一迁移为严格 Quartz JDBC。
