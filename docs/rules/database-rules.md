---
name: database-rules
description: MySQL 表结构、命名、枚举、伪删除、索引、约束与 Flyway 迁移规范
---

# 数据库设计规范

## 1 目的与适用范围

本规范约束 yunhefc 的 MySQL 8.4 表结构、字段、索引、约束与 Flyway 迁移，适用于 `scripts/sql/schema.sql`、`scripts/sql/quartz.sql` 及后续所有迁移脚本。

设计依据：`docs/design/03.整体架构设计.md`、`docs/design/04.数据库设计.md`、`docs/design/05.API接口设计.md`。

一句话总纲：**命名稳定、枚举有编码、字段有注释、业务表无物理外键、永不使用 CHECK、迁移可重复、查询走索引**。

---

## 2 命名与类型

1. 数据库对象统一小写 `snake_case`；表名使用单数，例如 `team`、`player`、`team_match`。
2. 避开 MySQL 保留字；已用 `team_match`、`app_user`、`app_role`、`match_grouping`。
3. 主键统一 `id BIGINT UNSIGNED AUTO_INCREMENT`；关联字段使用 `{实体}_id`，例如 `team_id`、`player_id`、`match_id`。
4. 关联表使用复合主键，例如 `user_role(user_id, role_id)`、`role_permission(role_id, permission_id)`。
5. 字段类型准确表达业务含义：
   - 时间：`DATETIME(3)`，统一 UTC。
   - 布尔：`TINYINT(1)`，0/1，`COMMENT` 写明含义。
   - 枚举：`TINYINT UNSIGNED`，`COMMENT` 写数字映射。
   - 标识/哈希：哈希用 `CHAR` + `ascii_bin`（如 `token_hash`、`request_hash`）；可变长度标识用 `VARCHAR` + `ascii_bin`（如 `openid`）。
   - 结构化数据：`JSON`，例如副位置数组、前后快照、事件载荷。
   - 金额：`DECIMAL`；本期无账本表，二期再引入。
6. 不使用逗号、分号拼接多值；多值使用 JSON 或关联表。
7. 字符集统一 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`；大小写敏感字段显式 `ascii_bin`。

---

## 3 主键、关联与完整性

1. 每张表必须有主键；主键唯一、非空、稳定标识记录。
2. 业务上必须唯一的值使用唯一约束，不依赖应用层判重。
3. 业务表之间使用逻辑关联字段，由应用服务维护关联完整性；**业务表不创建物理外键**。
4. Quartz 官方 11 张表的 5 个框架外键是唯一例外，保持官方结构，不删除、不扩散到业务表。
5. 多球队预留：按球队隔离的聚合根带 `team_id`；查询的 `teamId` 来自登录上下文，不信任客户端传入。
6. **永不使用数据库 CHECK**；状态/类型枚举、范围与跨字段规则全部由应用层强校验，完整清单见 `04.数据库设计.md` §8.1。至少覆盖：
   - 换人事件字段完整性、进球助攻/点球约束；
   - 外挂不得候补；
   - 时间、容量、赛季、分组权重、坐标范围；
   - 预置/自定义阵型归属、报名容量上限。
7. 唯一性、非空与默认值仍由数据库保证；业务规则不得依赖数据库异常，应用层必须返回明确业务错误码。
8. 约束命名统一 `uk_`、`idx_` 前缀；本项目永久禁用 `CHECK`/`chk_`，不提供例外，也不要创建此类约束。

---

## 4 枚举、COMMENT 与伪删除

1. 所有字段必须带中文 `COMMENT`；枚举/布尔字段的 COMMENT 同时写明数字与含义。
2. 枚举编码以 `04.数据库设计.md` §2.4 为唯一事实源；API 返回 code + `*Label`，请求只接受数字 code。
3. 27 张业务表均包含：

```sql
is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-未删除;1-伪删除'
```

4. 查询默认过滤 `is_deleted=0`；伪删除不物理删除，也不自动释放唯一约束。
5. `registration.active` 为 `TINYINT UNSIGNED` 严格 0/1（1-有效；0-无效）；`active_unique` 为生成列（有效=1；无效=NULL），由唯一键 `UNIQUE(match_id, player_id, active_unique)` 保证“仅一条有效报名”，同时允许多条取消/移除历史。
6. 事件删除同时写 `is_deleted=1` 与 `deleted_at/deleted_by`；查询同时过滤两者。
7. 账号、比赛、报名等业务生命周期仍用状态字段表达；`is_deleted` 只表示伪删除，不替代业务状态。

---

## 5 索引

1. 高频查询、分页排序、逻辑关联字段建立索引；索引以实际查询与执行计划为依据。
2. 列表分页优先使用 `(过滤列, 排序列)` 组合索引，并带稳定 tie-breaker。
3. 唯一约束由唯一索引实现；不要为同一列重复建普通索引。
4. 业务表现有 58 条 KEY/UNIQUE 定义；复核未发现完全重复或前缀可覆盖项，保持现状，不重复建可覆盖索引。
5. `is_deleted` 作为通用过滤条件，不单独建低选择性索引；查询在现有索引前缀后附加 `is_deleted=0`。
6. Quartz 官方索引随框架保留，不做业务索引收敛。
7. 索引变更通过 Flyway 迁移；不在生产环境手工 `CREATE INDEX`。

---

## 6 审计、时间与保留

1. 时间统一 `DATETIME(3)` UTC；业务边界（报名截止 00:00、锁定 10:00、签到窗口、缺勤 +6h）先在 Asia/Shanghai 推导，再转 UTC。
2. 业务主表包含 `created_at`；可变表包含 `updated_at`；关联表、子表与事件去重表可使用业务时间字段替代（如 `registered_at`、`recorded_at`、`used_at`、`processed_at`）；关键写操作记录 `created_by/updated_by` 等操作人字段。
3. 事件修改历史写 `event_history`；审计日志不删除，年度导出 OSS 冷备。
4. 保留策略：
   - 通知 90 天；
   - 已发送 Outbox 7 天，FAILED 保留排查；
   - 过期邀请 30 天；
   - 幂等键/消费去重按 TTL 清理；
   - 事件历史永久保留。
5. 账号、球员、比赛、报名、考勤、事件不做物理删除；使用状态、`is_deleted` 或 `deleted_at`。

---

## 7 Flyway 迁移

1. 所有 DDL 通过 Flyway 迁移，随应用启动执行；禁止手工改表。
2. 基线顺序：
   - `V1__baseline_schema.sql`：27 张业务表；
   - `V2__quartz_schema.sql`：11 张 Quartz 表；
   - `V3__seed_team_roles_permissions_settings.sql`：球队、角色、28 权限、9 项配置；
   - `V4__seed_formation_templates.sql`：8 套阵型、64 个槽位。
3. 迁移保持小步、向后兼容；采用 expand/contract：先加兼容字段，再切读写，最后清理旧字段。
4. 不做逆向迁移；回滚优先回滚代码，不兼容变更按备份/PITR 恢复。
5. 启动健康检查覆盖迁移结果；迁移失败阻断启动。
6. 迁移脚本不得包含 `DROP DATABASE`、`USE`、生产凭证或环境相关硬编码。

---

## 8 查询与写入约定

1. 禁止 `SELECT *`；显式列名，复杂查询放 XML。
2. 使用 `#{}` 绑定参数；动态排序字段走白名单，禁止 `${}` 拼接。
3. 避免 N+1；批量查询/写入使用批量 upsert/foreach。
4. 唯一约束冲突转换为明确业务错误码，不把 SQL 异常抛给前端。
5. 乐观锁使用 `version` 条件更新；更新行数为 0 视为版本冲突。
6. 事务默认只修改一个聚合；三个例外：报名生命周期、注册建档、账号生命周期编排（INV-02 复用审核/转正编排）。
7. 跨模块查询通过 read port，不直接访问对方表。
8. 所有写入前必须调用领域校验服务；批量导入、测试数据与 Quartz 任务复用同一校验，不得绕过。数据库只兜底 `NOT NULL`、默认值与唯一键。

---

## 9 常见坑

- ❌ 业务表建物理外键，导致迁移、清理、分库分表受限。
- ❌ 枚举继续用 VARCHAR，或只改 TINYINT 不写 COMMENT。
- ❌ 新增字段不写 COMMENT，或 COMMENT 不写数字映射。
- ❌ 使用数据库 CHECK/`chk_` 约束；本项目永久禁用，业务规则只能由应用层强校验。
- ❌ 依赖数据库约束异常做业务校验，或批量导入绕过应用层校验。
- ❌ 新增业务表漏加 `is_deleted`，查询不过滤伪删除。
- ❌ 认为伪删除会释放唯一键，删除后直接复用手机号/球衣号/编号。
- ❌ `active` 用 0/1 但没有 `active_unique`，多次取消触发唯一冲突。
- ❌ 事件只写 `deleted_at` 不写 `is_deleted`，或查询只过滤其中一个。
- ❌ 为 `is_deleted` 单独建低选择性索引，或重复建已有索引前缀。
- ❌ 手工改生产表；Flyway 迁移不可重复或依赖环境。
- ❌ 使用 `SELECT *`、`${}` 拼接、循环单条查询。
- ❌ 时间用本地时区写入，或 `DATETIME` 不带毫秒精度。

---

## 10 审查清单

- [ ] 表名/字段 snake_case、单数、避开保留字
- [ ] 主键/关联字段类型与命名正确，关联表使用复合主键
- [ ] 枚举字段为 TINYINT UNSIGNED，COMMENT 含数字映射
- [ ] 所有字段有中文 COMMENT
- [ ] 27 张业务表均有 `is_deleted`，查询默认过滤
- [ ] `registration.active` 0/1，`active_unique` 参与唯一键
- [ ] 业务表 0 物理外键；Quartz 5 框架外键保留
- [ ] 数据库无 CHECK（永久禁用）；应用层校验清单（04 §8.1）已实现并被测试覆盖
- [ ] 索引覆盖查询，无重复/可覆盖索引；`is_deleted` 不单独建索引
- [ ] 时间 UTC、`DATETIME(3)`；业务边界按 Asia/Shanghai 推导
- [ ] 唯一约束、`version` 乐观锁、批量写入正确
- [ ] Flyway V1–V4 顺序正确、可重复、无环境硬编码
- [ ] 未执行生产 DDL、未手工改表
