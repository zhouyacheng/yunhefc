# 06 — M11 配置项、生产种子与设置页

Type: grilling
Status: resolved
Blocked by: —

## Question

现有 V3 已种子默认角色/权限与 9 项配置，settings 只有两个读适配器，SET-01/02 无写路径。需要定：

- 配置项键、取值与校验范围：报名默认 30/4/2、分组手动/自动 70/30、自动分内部 50/25/25、最少出场 3；哪些进设置页、哪些固定。
- 保存后的生效边界：只影响新建比赛/后续分组，不回写已创建比赛与已保存分组。
- SET-01/02 的响应结构、Idempotency-Key、配置变更审计。
- B 端系统设置页形态（单表单保存 vs 分组保存）与权限 `settings:view:any` / `settings:manage:any`。
- 生产空库时默认角色、权限、配置种子的来源（Flyway 种子 vs 初始化脚本）与幂等；首位管理员不在此票（见「部署拓扑、发布与回滚」）。

事实指针：需求 CONFIG-01/02、GROUP-02/04、05 §3.12、04 `system_setting`/角色权限表、`V3__seed_team_roles_permissions_settings.sql`、`settings/application/*Adapter.java`、02 后台「系统设置」。

## Answer

- **配置面**：V3 的 9 个键全部可编辑，无固定项；值均为整数。
- **校验**：`maxPlayers 1–99`；`maxGuests/maxWaitlist 0–99`；`minAppearances 0–99`；主权重 `manual+auto` 必须=100；子权重各 0–100 且合计>0。与 `GroupingWeights.resolve` 同源，SET-02 拒绝非法值而不是让分组层静默回退。
- **契约**：沿用 05 §4 的扁平 `Settings` 9 字段；SET-01 返回当前有效配置（缺失键按 30/4/2、70/30、50/25/25、3）；SET-02 为部分合并 PATCH，未传键保持原值，按合并后的完整 9 键校验，非法返回 `VALIDATION_FAILED`（40001）；沿用 `Idempotency-Key`，不带 `version`。
- **生效边界**：只影响之后的比赛创建与分组生成；不回写已创建比赛、不回算已保存分组分数；已保存分组展示的权重标签为当前有效配置（维持现状）。
- **B09**：单表单一次保存，报名与分组两个区块共用一个「保存」按钮，一次 PATCH 提交；前端校验与服务端同源；不新增「恢复默认」。
- **审计**：每次 PATCH 记一条 `SETTINGS_UPDATE`，`targetType=SETTINGS`，`targetId=teamId`，before/after 为完整 9 键配置；无 PII，不脱敏。
- **生产种子**：仅 Flyway V1–V5；V3 是球队、默认角色、28 个权限码与 9 项配置的唯一来源；迁移不可变、禁止手工重跑；不新增初始化 SQL；首位管理员留给「部署拓扑、发布与回滚」票。
- **同步**：05 v0.23、02 v0.15、06 §42 v1.39；04/`schema.sql`/Flyway/后端不改；原型 `admin.js` 仅同步校验与提示。
