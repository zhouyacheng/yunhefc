# 03 — M06/M08 分组校验集成契约

Type: grilling
Status: resolved

## Question

EVENT-12 要求「有分组时主队只能选橙队分组成员、客队只能选兰队分组成员」，但架构依赖图没有 M06→M08 边。需要确定：

- 校验归属：event application 通过 contracts 只读端口查询分组，还是 grouping 暴露校验接口；ArchUnit 允许的依赖方向是什么。
- 权威来源：使用已保存的分组快照，还是当前报名 ∩ 分组；分组名单与报名不一致时拒绝、提示还是回退到报名名单。
- 分阶段改动面：M06 第一阶段按 CONFIRMED+ACTIVE 报名名单校验；M08 落地后回填分组校验，具体涉及 EVT-02/03/06/07 的哪些字段与测试。
- 是否需要在设计文档（01 依赖表、03 依赖图）补 M06→M08 的边，或明确走 contracts 端口避免新增依赖。

事实指针：需求 EVENT-12、`docs/design/05.API接口设计.md` EVT-02/03、`docs/design/03.整体架构设计.md` 模块依赖图、`docs/design/01.功能模块梳理.md:366`。

## Answer

**结论：已保存分组快照 + CONFIRMED/ACTIVE 报名双前置；`event` 经 contracts 只读端口读取分组；EVT-02/03/06/07 统一校验、管理员不豁免；M08 落地后回填。**

### 1. 权威来源与校验顺序

- 报名前置：`playerId/assistPlayerId/inPlayerId/outPlayerId` 必须是本场 CONFIRMED 报名（`registration.status=0`）且账号 `app_user.status=ACTIVE`。
- 分组前置：存在**已保存**分组快照（`match_grouping.saved_at` 非空）时，球员还必须属于 `event.team` 对应的分组队；仅草稿（`saved_at` 为空）不约束。
- 任一不满足即拒绝，**不回退报名名单**。分组与报名不一致时，前端提示并引导到 B06 重新生成/保存分组。
- 校验顺序：比赛状态（40905）→ 字段规则 → 报名前置（失败 42201）→ 分组归属（失败 42203）。
- PATCH（EVT-03/07）按「现有事件 + 请求字段」合并后的有效 `team` 与球员字段校验。

### 2. 归属与依赖

- 新增 `contracts/grouping/GroupingReadPort`：
  ```java
  Optional<SavedGroupingSnapshot> findSavedByMatch(Long matchId);
  ```
- `SavedGroupingSnapshot(Long matchId, Instant savedAt, Map<Long, Integer> playerTeams)`；`playerTeams` 值 0=A、1=B，与 `match_grouping_player.team` 一致。
- 适配器 `grouping/application/GroupingReadAdapter`（`@Component`），查询 `match_grouping.saved_at IS NOT NULL AND is_deleted = 0` 与 `match_grouping_player`；未生成、仅草稿、已清除均返回 `Optional.empty()`。
- `event` 只依赖 `contracts`，不访问 grouping application/表。M08 回填时补 ArchUnit 定向规则：`com.yunhefc.event..` 不得依赖 `com.yunhefc.grouping..`。
- 03 依赖图用带标签虚线 `Event -.->|只读 port（M08 后）| Grouping`；虚线图例扩展为「异步领域事件或 contracts 只读端口」，均不表示应用服务/数据表依赖。

### 3. 覆盖范围与字段语义

- EVT-02/03/06/07 全部校验四个球员字段；管理员不豁免；EVT-06/07 的「任意事件」仅指事件类型与对象，不豁免选人校验。
- `HOME=0=橙队=A 组`，`AWAY=1=兰队=B 组`（与 LIN-03 一致）。
- OWN_GOAL 球员按 `event.team`（乌龙方）校验，比分仍记给对方；EVENT-05 的「我方球员」在队内/训练赛语境下即事件所属队球员。
- `includeGuests=false` 的已确认外挂不在快照中，不能被选入事件；需要记录时先在比赛/分组中包含外挂（与 B05 一致）。

### 4. 错误码

- 05 §2.3 新增 `42203 GROUPING_MISMATCH`（HTTP 422），message 含违规字段与 playerId。
- 报名前置失败沿用 `42201 BUSINESS_RULE_VIOLATION`；42203 只表示「已保存分组归属不匹配」。
- 不复用 40911（分组指纹冲突）或 40001（字段格式）。

### 5. 分阶段与回填面

- M06：只做报名校验，不建分组端口。现有 `MatchRosterPort.isActiveFormalPlayer` 只覆盖正式球员且按 userId，需新增按 playerId、覆盖正式+外挂的报名只读方法。
- M08：新增 `GroupingReadPort` + 适配器，回填 EVT-02/03/06/07 与测试。回填面 = event 共享校验器 1 处 + 4 个 controller 路径 + IT。
- `match_event` 不新增 grouping fingerprint 列；分组生成/调整/保存/清除不回溯校验或改写已有事件。

### 6. 文档同步

- 05 v0.21：EVT-02/03/06/07 规则、42203、`EventCreate/EventUpdate` 字段说明、事件选人校验说明。
- 01 v0.13：§4.7 选人限制与依赖、§4.9 不回溯、§5.1 M06/M08 依赖、§5.2 说明。
- 02 v0.14：C03、B04、B06。
- 03 v1.12：§4.2 依赖图与说明、§4.3/§6.2 只读 port 规则。
- 06 v1.36：本决策记入 §39。
- `docs/rules/java-rules.md`：§3.2/§3.3/§15 补 contracts 只读 port。
- `prototype/assets/js/events.js`：仅 `savedAt` 非空时按分组限制。
- 不改需求、04、`schema.sql`。

### 7. 测试清单（M08 回填时）

- 四个写接口 × `playerId/assistPlayerId/inPlayerId/outPlayerId` 错队 → 42203。
- OWN_GOAL 球员按 `event.team` 通过；对方队分组球员被拒。
- 仅草稿分组 → 回退报名校验，不产生 42203。
- 无分组 → 只校验报名。
- 管理员（EVT-06/07）错队 → 42203。
- 分组内但报名已取消/账号非 ACTIVE → 报名错误优先，不返回 42203。
- PATCH 只改 `team` 导致原球员跨队 → 42203。
- 分组生成/保存/清除后，已有事件不变。
