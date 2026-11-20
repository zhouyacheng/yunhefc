# 07 — M08 分组生成

**What to build:** 管理员在锁定的队内赛/训练赛一键生成 A/B 两队，看到总分差、位置分布与警告；球员在 C03 分组 Tab 只读查看；连续重新生成不总是同一结果。

**Blocked by:** 06 — stats 汇总切片.

**Status:** resolved

- [ ] `GRP-01 GET /c/matches/{id}/grouping`：权限 `grouping:view:any`；未生成返回空结构（空 teams、`diff=0`、空 warnings、`savedAt=null`）。
- [ ] `GRP-02 GET /b/matches/{id}/grouping`：权限 `grouping:view:any`；返回 `scores:{A,B,diff}`、`rosterFingerprint`、`generatedAt`、`savedAt`、`ungroupedPlayerIds`、`rosterDiff`；未生成时相应字段为 null/空。
- [ ] `GRP-03 POST /b/matches/{id}/grouping/generate`：权限 `grouping:generate:any`，`{confirm?}` + `Idempotency-Key`；仅队内/训练赛且 `LOCKED`；候选 < 2 返回 42201；已有草稿或已保存分组时 `confirm!=true` 返回 42202；覆盖后 `saved_at=NULL`；生成即持久化草稿。
- [ ] 候选：本场 CONFIRMED、账号 ACTIVE、属于本队、按 `includeGuests` 过滤、按 playerId 去重；赛季取比赛赛季字段。
- [ ] 评分：`manual=player.skill`；出场率分母为同赛季 `FINISHED` 场次（0 按 1）；进球/助攻在候选池 min-max 锚 0 归一；`auto=1+4×(0.50×出场率+0.25×进球归一+0.25×助攻归一)`；`appearances=0` 或 `<minAppearances` 时 `final=manual`，否则 70/30；权重默认 70/30/50/25/25/3，缺失或非法按规格回退；先舍入再排序与落库。
- [ ] 分配：GK→DF→MF→FW 逐位置；位置内 `final+(rand-0.5)×1.2` 降序、`A,B,B,A` 蛇形、每位置随机起始队；人数修平 → 门将修复 → 局部搜索；目标字典序 `[缺门将, 人数差>1, 总分差, 外场位置差]`，best-improvement 跨队交换，无改进或 `max(20,n²)` 轮停止。
- [ ] 随机性：`SecureRandom` 取种子、`SplittableRandom` 驱动、种子不落库、DEBUG 记录；不做随机重启。
- [ ] 警告固定顺序：人数差 → 门将 0/1/≥2 → 各外场位置人数/位置差；永不阻断。
- [ ] 指纹：候选 playerId 升序逗号连接后 SHA-256 小写十六进制；生成时写入；分组写操作锁比赛行串行化；球员集合物理替换。
- [ ] 错误：非队内/训练赛/候选不足 42201；非 LOCKED 40905；需确认 42202；结构错误 40001。
- [ ] B06：比赛选择器、生成/重新生成（已有分组先确认）、A/B 两列、总分差、位置分布、警告、本场报名两列、未分组/不参与单列、空状态；C03 分组 Tab 只读。
- [ ] 单元测试：评分/归一化/蛇形/人数与门将修复/搜索目标与终止/固定种子可复现/生产 RNG 多次生成不全同/警告顺序/指纹；IT：权限、状态门、生成、覆盖确认、空结构。
- [ ] Playwright：B06 生成分组 + C03 只读查看；无 console error。
- [ ] 不改 `schema.sql`、不新增 Flyway；`mvn verify` 全绿。

## Result

- 交付：`240c5ae`。
- 验证：批次收尾 `mvn verify` 全绿（107 单元 + 250 集成）；前端 lint/typecheck 通过；Playwright 14 条黄金路径通过（2026-11-11）。
