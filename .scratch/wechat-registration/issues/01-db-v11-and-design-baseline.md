# 01 — 数据库与设计基线（V11 + 文档）

**What to build:** 让 `app_user.phone` 允许为空，微信注册账号可以不持有手机号；同步 04/06/schema.sql 与迁移记录，使后续后端与前端可以在同一契约上开发。

**Blocked by:** None — can start immediately.

**Status:** wontfix（2026-11-20 微信注册已移除）

- [ ] 新增 `V11__wechat_registration.sql`，`phone` 改为 `VARCHAR(20) NULL` 并保留列注释
- [ ] `scripts/sql/schema.sql` 与 04 §5.2 同步为可空，`uk_user_phone` 不变
- [ ] 空库 Flyway V1→V11 成功；`phone.is_nullable=YES`；27 张业务表、0 CHECK、0 业务外键
- [ ] `FlywayMigrationIT` 断言迁移数与可空性
