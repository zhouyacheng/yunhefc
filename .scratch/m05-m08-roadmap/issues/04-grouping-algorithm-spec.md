# 04 — M08 分组算法规格

Type: grilling
Status: resolved

## Question

把「门将/位置蛇形分配 + 局部搜索 + 随机因子/随机重启」落成可实现、可测试的规格：

- 自动分公式：出场率（分母=本赛季已结束且未取消的球队比赛数）、场均进球、场均助攻如何在本场候选池归一化到 1–5 星。
- 合成分：手动分与自动分 70/30（全局可配）、自动分内部 50/25/25（可配）、少于最少出场时优先手动分。
- 位置与人数：8 人制的门将/后卫/中场/前锋名额、蛇形分配顺序、两队人数差。
- 门将约束：P-06 的 0/1 名提示、≥2 名时每队至少 1 名如何在校验与搜索中表达。
- 局部搜索：邻域动作（交换/移动）、迭代次数或时间上限、终止条件（总分差不再下降）。
- 随机性：随机因子加在初始排序还是起始队伍、随机种子如何记录、如何测试「连续重新生成不应总是同一结果」。
- 输出：分组草稿的持久化字段与不可变性（生成→拖拽→保存快照）。

事实指针：需求 GROUP-02/03/06/07、`docs/design/01.功能模块梳理.md:248-250`、`docs/design/03.整体架构设计.md:381`、`docs/design/02.页面结构与交互设计.md` §6.8/§7.6、`docs/design/04.数据库设计.md` `match_grouping`。

## Answer

**结论：不设固定位置名额；按 GK→DF→MF→FW 逐位置蛇形（`A,B,B,A` 循环）+ 局部搜索；自动分在候选池内归一化；随机因子只作用于初始排序与每位置起始队，种子不落库；分组两表采用覆盖写/物理删除；GRP-04 锁定候选池，保存后调整需 confirm 并回到草稿。**

以下规则均可直接写成测试断言。

### 1. 候选池、赛季与接口边界

- 比赛仅 `type ∈ {INTERNAL(1), TRAINING(2)}`；生成/调整/保存/清除仅 `status = LOCKED(2)`；其他状态分组只读且入口禁用。
- 候选 = 本场 `registration.status = CONFIRMED(0)` 且 `is_deleted = 0` 的报名，按 `playerId` 去重。
- 球员必须存在且属于 `match.teamId`；`PlayerReadPort.findByIds(teamId, ids)` 天然过滤跨队与不存在 ID。
- `match.includeGuests = false` 时，`registration.type = GUEST(0)` 或 `player.type = GUEST(0)` 任一成立即排除（兼容转正后报名类型仍为 GUEST 的情况）。
- 账号必须 `app_user.status = ACTIVE(1)`，用 `UserAccountReadPort.findByIds` 批量过滤。
- 赛季 = `match.season`；为空时取 `start_at` 在 `Asia/Shanghai` 的年份。不按当前日历年取赛季。
- 出场率分母 = 同球队、同赛季、`status = FINISHED(4)` 的比赛数；`CANCELLED/POSTPONED` 天然不计；分母为 0 时按 1 处理。
- 候选 < 2 **仅在 GRP-03 生成时**返回 `42201`；GRP-01/02/04/05/06 不受候选数限制——名单缩到 1 人时仍可查看、保存、清除旧分组。

### 2. 分数计算（纯函数，`grouping/domain`）

- `manual = clamp(player.skill, 1, 5)`；`player.skill` NOT NULL、默认 3，所以 manual 恒有值。「两者都缺失默认 3 星」保留为不可达的防御分支。
- 统计来自 `StatsQueryService.seasonStats(teamId, season)`；无汇总行按 `appearances=goals=assists=0`。
- `goalsPerGame = appearances > 0 ? goals / appearances : 0`；`assistsPerGame` 同理。
- 归一化：`min = min(0, poolMin)`、`max = max(0, poolMax)`；`max - min < 1e-9` 时取 `0.5`，否则 `(v - min) / (max - min)`。统计非负，因此 min 锚定 0：全体为 0 → 0.5；全体为同一正值 → 1。
- `auto = appearances > 0 ? 1 + 4 × (appearanceWeight×出场率 + goalWeight×进球归一 + assistWeight×助攻归一) : null`。
- **appearances = 0 即使 minAppearances = 0 也强制 manual-only**，不参与自动分。
- `final`：`appearances = 0` 或 `appearances < minAppearances` → `manual`（`manualOnly = true`）；否则 `manual × manualRatio + auto × autoRatio`。
- 权重：主权重、子权重都按各自和归一为比例；主权重非法（<0 或和≠100）整组回退 70/30；子权重非法（<0 或和≤0）整组回退 50/25/25；缺失键逐键回退默认；`minAppearances` 缺失或负数回退 3。
- 精度：`manual_score` 1 位小数；`auto_score/final_score` 2 位小数，`HALF_UP`。**先舍入，再参与排序、目标函数、汇总与落库**，保证「排序 = 展示 = 存储」。
- 落库 `match_grouping.manual_weight/auto_weight` 用配置原值（0–100、和=100）；非法配置回退后写 70/30。

### 3. 初始分配

- 种子：`SecureRandom.nextLong()` 取种子，`new SplittableRandom(seed)` 驱动分配；`log.debug("grouping generate matchId={} seed={}")`。同种子 + 同候选池 + 同配置 + 同统计 ⇒ 同结果。
- 固定抽取顺序：按 GK(0)→DF(1)→MF(2)→FW(3) 遍历四个位置，空位置也消耗一次 `nextBoolean()`；每个位置先 `startTeam = rng.nextBoolean()`，再按 `playerId` 升序对组内每人抽一个 `nextDouble()` 作为噪声，然后排序。
- 位置内排序键 `final + (noise - 0.5) × 1.2` 降序；tie-break `jersey` 升序、`id` 升序。
- 蛇形定义：组内第 i 人（0 基），`i % 4 ∈ {0,3}` 分到起始队，`i % 4 ∈ {1,2}` 分到另一队；起始队为 B 时整体交换。即 `A,B,B,A` 循环。
- 人数修平：全部位置分配完后，若 `|A| - |B| > 1`，反复从人多队移走综合分最低的**外场**球员到人少队（并列取 `id` 小；若只剩门将则移门将）；偶数候选人最终差 0，奇数最终差 1。
- 门将修复：`gkPool ≥ 2` 且某队 0 门将时，从门将多的队取 `playerId` 最小的门将，与另一队 `final` 最低的外场球员（并列取 `id` 小）交换，只修一次。
- 顺序固定：初始分配 → 人数修平 → 门将修复 → 局部搜索。
- 不设固定名额；`secondary_positions` 不参与均衡，只按 `player.position` 主位置。

### 4. 局部搜索与目标

- 目标元组（字典序最小）：`[gkPool ≥ 2 且某队缺门将 ? 1 : 0, |A|-|B| > 1 ? 1 : 0, |sumA - sumB|, Σ|posCountA - posCountB|]`；最后一项只统计外场 DF/MF/FW。
- 语义：门将、人数是可行性优先；二者满足后最小化总分差，再最小化位置差。这是 GROUP-06「目标总分差最小」在 P-06 约束下的实现口径。
- 邻域：所有跨队成对交换 `(i ∈ A, j ∈ B)`；best-improvement；只接受严格更优元组；并列最优取 `(i, j)` 字典序最小；无改进或达到 `max(20, n²)` 轮停止（n = 候选数，轮数不是评估数）。
- 只交换、不移动，人数差保持不变；随机性只在第 3 节，局部搜索确定。
- 复杂度：n ≤ 30 时每轮 ≤ 225 次交换、每次目标 O(n)，总评估量约 10⁶ 级，单次生成可接受。

### 5. 警告（不落库，每次读取按当前数据重算）

固定顺序、去重后返回 `string[]`：

1. 人数差 > 1；
2. `gkPool = 0` →「名单中没有门将」；
3. `gkPool = 1` →「名单中仅 1 名门将；0/1 名只提示不阻断」；
4. `gkPool ≥ 2` 且某队 0 门将 →「有球队缺少门将」；
5. 每个外场位置：候选人数 < 2 →「X 位置仅 N 人，位置均衡可能受限」；`|countA - countB| > 1` →「X 人数差 d」。

警告永不阻断生成/调整；GRP-05 保存时存在任何警告或名单变化，均需 `confirm=true`（与 02 §6.8、05 §3.9 一致）。

### 6. 指纹与持久化

- 指纹：候选池 `playerId` 去重、十进制升序、逗号连接（无空格、无尾逗号），UTF-8 后 SHA-256 小写十六进制 64 位。
- **GRP-04 锁定候选池**：要求「当前候选池指纹 = 存储指纹」，否则 `40911`。因此 PATCH 不引入新球员、不重算分数；名单变化一律走 GRP-03 重新生成。这同时避免「子权重/minAppearances 未持久化、归一化基池变化导致分数不可解释」。
- GRP-03 生成：事务内先 `SELECT ... FOR UPDATE` 锁 `team_match` 行（新增 `MatchReadPort.lockForGrouping(matchId, teamId)`，仿 M04 报名锁序）串行化本场分组写；`match_grouping` 按 `match_id` 查含伪删除行，存在则 UPDATE（权重、指纹、`generated_at = now`、`created_by = 当前管理员`、`saved_at = NULL`、`is_deleted = 0`），不存在则 INSERT；球员集合物理删除后整批 INSERT（`manual/auto/final` 快照）。
- 已存在分组（草稿或已保存）时 `confirm != true` → `42202`；覆盖丢弃已保存快照。
- GRP-04 调整：`teams.A/B` 必须是当前候选池的无重复、无遗漏划分；结构非法 `40001`，未知/非候选/重复/遗漏 `42201`；`expectedFingerprint` 与存储不符 → `40911`；无分组 → `42201`；已保存（`saved_at` 非空）且 teams 有变化时 `confirm != true` → `42202`；确认后原地更新 `team` 并 `saved_at = NULL`；teams 与现状完全相同则空操作，保留 `saved_at`、不写审计。
- GRP-05 保存：无分组 → `42201`；`expectedFingerprint` 不符 → `40911`；当前候选池指纹 ≠ 存储指纹（名单变化）**或** warnings 非空，且 `confirm != true` → `42202`；保存**保留生成时指纹**（快照描述生成时名单），只写 `saved_at = now`，不重算分数；重复保存刷新 `savedAt` 并写审计。
- GRP-06 清除：LOCKED 才可；事务内锁比赛行，物理删除球员行后删除主行；写审计；无分组时幂等返回 `{cleared:true}`，不写审计。
- 生成/调整/保存/清除写审计；动作键 `grouping.generate/adjust/save/clear`、目标 `team_match:{matchId}`，精确枚举由 M10 统一；物理删除前取旧行作为 before 快照。
- 并发：比赛行锁串行化同场写，避免 `UK(match_id)` 插入冲突；`expectedFingerprint` 只保护名单、不保护 teams，两个管理员并发改 teams 为串行后的后写覆盖（last-write-wins），单队 MVP 接受，记录为已知限制。
- 幂等：沿用现有 `IdempotencyService`；同 key + 同 body 返回首结果，同 key + 不同 body → `40906`；失败（42202/40911 等）不落幂等记录，可用同 key 重试。

### 7. 读接口语义

- GRP-01：未生成返回 `{teams:{A:[],B:[]}, weights:{...当前配置...}, diff:0, warnings:[], savedAt:null}`。
- GRP-02：`scores = {A: Σfinal, B: Σfinal, diff: |A-B|}`；`rosterFingerprint/generatedAt/savedAt` 未生成时为 `null`；`ungroupedPlayerIds` = 当前候选 − 已分组球员行（未生成时 = 全部候选）；`rosterDiff` 以 `match_grouping_player.player_id` 为保存侧：`addedPlayerIds` = 当前候选 − 已分组行，`removedPlayerIds` = 已分组行 − 当前候选，`changed` = 当前指纹 ≠ 存储指纹。字段名用 05 的 `addedPlayerIds/removedPlayerIds`。
- `weights` 结构统一为 `{manualWeight, autoWeight, appearanceWeight, goalWeight, assistWeight, minAppearances}`，与设置键同名。
- `teams` 与 `ungroupedPlayerIds` 按 `playerId` 升序返回，保证稳定。
- 时间统一为 UTC `Instant`（ISO-8601 `Z`），DB 用 `DATETIME(3)` + `Clock`。
- 已保存后名单变化仍持续提示（06 保留项），不自动重算。
- B06 展示「所有确认报名球员/不参与球员」需组合 `REG-06`：GRP-02 的 `ungroupedPlayerIds` 只覆盖候选池；被过滤的外挂/非 ACTIVE 账号由报名名单补充展示，不进分组。

### 8. 模块归属、端口与错误码

- `grouping/domain`：`GroupingScoreCalculator`、`GroupingAlgorithm`、`GroupingObjective`、`GroupingWarnings`、`RosterFingerprint`，纯函数、无 Spring。
- `grouping/application`：`GroupingApplicationService` 编排；`GroupingReadAdapter` 实现 ticket 03 已定的 `contracts/grouping/GroupingReadPort`。
- `grouping/infrastructure` + `grouping/controller`（GRP-01..06）。
- 需补的内部契约（不改 05 对外契约，除 GRP-04/05 规则与 `weights` 说明外）：
  - `MatchSnapshot` 增加 `type/season/includeGuests`；`MatchReadPort` 增加 `countFinishedInSeason(teamId, season)` 与 `lockForGrouping(matchId, teamId)`。
  - `RegistrationReadPort` 增加本场 CONFIRMED 名单（playerId + registrationType）。
  - `PlayerReadPort` 提供 `position/skill/type/jersey`；账号 ACTIVE 用现有 `UserAccountReadPort.findByIds`。
  - 新增 `contracts/settings/GroupingSettingsPort` + `settings` 适配器（仿 `RegistrationDefaultsAdapter`），默认 `70/30/50/25/25/3`。
  - stats 经 `StatsQueryService.seasonStats(teamId, season)` 读取，不直读汇总表。
- 错误优先级：比赛不存在 `404` → 非队内/训练赛 `42201` → 非 LOCKED `40905` → 无分组 `42201` → 请求结构 `40001` → 集合语义 `42201` → 指纹 `40911` → confirm `42202`。
- 依赖方向：`grouping` 不访问 `event/attendance` 表；`event` 只经 `contracts.grouping` 读已保存快照（ticket 03）。

### 9. 测试清单

- 分数：分母口径、出场 0 强制 manual、min-max 锚 0、全 0 取 0.5、全同正值取 1、低于最少出场优先手动、先舍入后排序。
- 分配：逐位置蛇形 `A,B,B,A`、随机起始队、空位置消耗随机数、奇数/偶数人数、2 人候选必然 1/1、门将 0/1/≥2、门将修复、人数修平。
- 搜索：目标字典序、best-improvement、只接受严格更优、并列取最小索引、无改进即停、轮数上限、给定种子结果可复现。
- 随机性：固定种子单测；生产 RNG 连续生成 20 次，断言至少两种不同 A/B 结果。
- 指纹：排序、去重、无空格、64 位、同池同值。
- IT：状态门 40905、非队内 42201、候选<2 仅生成报错、confirm 42202（生成覆盖/已保存调整/保存警告或名单变化）、指纹 40911、PATCH 名单变化 40911、幂等 40906/40915、清除后重新生成、无分组 clear 幂等、重复 save 刷新 savedAt、PATCH 保存后需 confirm 且 saved_at 回 NULL、审计落库、并发 generate 由行锁串行化。

### 10. 文档同步与原型差异

文档同步：

- 06 追加 `§40 M08 分组算法规格（2026-11-11）`：五个决策、候选池锁定、目标元组口径、无固定名额/副位置不参与、物理删除与 B06 组合口径。
- 05 GRP-04 补「teams 必须是当前候选池的无重复划分；名单变化返回 40911，须重新生成」；GRP-05 补「保存保留生成时指纹；警告或名单变化需 confirm」；§3.9 补 `weights` 六字段结构。
- 04 把「伪删除」全局条款与 DB-13 限定为「分组两表例外：覆盖写 + 物理删除，因唯一键不含 is_deleted」；§5.19/5.20 同步说明；保留 `is_deleted` 列但分组查询不依赖它。
- 不改需求、01、02、`schema.sql`。

原型差异（原型非事实源，实施以本规格为准）：

- 原型蛇形为逐人交替 `A,B,A,B`，本规格为 `A,B,B,A`。
- 原型统计赛季取当前日历年，本规格取 `match.season`（空则开赛年）。
- 原型生成/汇总两套警告顺序，本规格固定为人数→门将→位置；`rosterDiff` 字段名以 05 的 `addedPlayerIds/removedPlayerIds` 为准。
