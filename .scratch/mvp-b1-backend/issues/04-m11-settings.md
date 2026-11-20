# 04 — M11 系统配置读写

Type: implementation
Status: resolved

**What to build:** 管理员可以在 B 端读取并部分修改 9 项系统配置（报名默认容量与分组评分权重）。保存按合并后的完整配置校验，非法值被拒绝；每次保存留一条完整前后快照的审计，且只影响之后的建赛与分组。

**Blocked by:** None — can start immediately

**Status:** resolved

- [x] SET-01 `GET /b/settings`（`settings:view:any`）返回扁平 `{settings}` 的 9 个键；缺失或不可解析的键逐项回退 30/4/2、70/30、50/25/25、3。
- [x] SET-02 `PATCH /b/settings`（`settings:manage:any` + `Idempotency-Key`）支持任意子集，未传键保持原值；按合并后的完整 9 键校验。
- [x] 校验规则：`maxPlayers 1–99`；`maxGuests` / `maxWaitlist` / `minAppearances 0–99`；`manualWeight` 与 `autoWeight` 各 0–100 且合计=100；三个子权重各 0–100 且合计>0；非法值返回 40001，不依赖分组层回退。
- [x] 写入 `system_setting` 的值是 JSON 数字（不是字符串），`value_type=NUMBER`；按 `(team_id,setting_key)` upsert；不新增缓存。
- [x] 每次 PATCH 写一条 `SETTINGS_UPDATE` 审计：`targetType=SETTINGS`、`targetId=teamId`、before/after 为完整 9 键。
- [x] 不带 `version`，后写生效；配置只影响之后创建的比赛与后续分组，不回写已创建比赛、不回算已保存分组。
- [x] 集成测试覆盖：默认值、部分合并、每个范围的上下界、权重合计、非法值、幂等键、审计快照、保存后新建比赛采用新默认值；`mvn verify` 全绿。

## Comments

- 2026-11-12 交付：实现 SET-01/02，新增 `SettingsController`、`SettingsApplicationService`、`SettingsDtos`、`SettingsPatchDeserializer` 与 `SystemSettingMapper.upsertBatch`；`SettingsIT` 10 项。
- 校验：9 项只接受 JSON 整数，小数/数字字符串/布尔/越界整数由自定义反序列化器与合并后完整校验统一返回 40001；主权重合计 100、子权重合计 >0 与 `GroupingWeights.resolve` 保持同一口径（该规则保留在 grouping 领域内，settings 侧内联同条件，避免 contracts 承载业务规则）。
- 存储：`CAST(? AS JSON)` 写入 JSON 数字，`value_type=NUMBER`，`(team_id,setting_key)` upsert；`updated_by` 记录当前用户，无缓存。
- 审计：同事务写一条 `SETTINGS_UPDATE`，前后快照为完整 9 键；幂等重放不重复写。
- 验证：`mvn verify` BUILD SUCCESS（132 单测 / 294 IT / 0 failures，JDK 21）；覆盖默认值、不可解析回退、部分合并、全部上下界、权重合计、非整数、幂等、审计快照、权限与新建比赛默认值。
- 评审：两轴评审完成；已修复静默吞异常（改为 DEBUG 记录）、移除 contracts 中的业务规则类、补非整数与边界用例。未知字段仍按 Jackson 默认忽略（规格未定义）。
- 同步：仅本票与 README；05/04/`schema.sql`/Flyway/Quartz/原型/前端不改，无新接口、权限码或错误码。
