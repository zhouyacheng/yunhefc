# 03 — PLR-01 球队页预览与 STA-01 统计

**What to build:** 实现公开球队页数据：正式且 ACTIVE 的球员按位置分组，组内本赛季出场降序 → 号码升序；每组默认前 10、可展开全部；提供 STA-01 本赛季统计供前端按 playerId 合并。

**Blocked by:** 01 — 球员域读模型与 PLR-05/06.

**Status:** ready-for-agent.

- [ ] 读 01 §4.3、02 §5.4、需求 TEAM-01~05、05 PLR-01/STA-01。
- [ ] `GET /c/team/players` 公开：`limit` 每组上限（默认 10，≤100）、`all=true` 返回各组全部；不使用 page/pageSize；返回 `{items,total}`。
- [ ] 分组顺序：前锋(3) → 中场(2) → 后卫(1) → 门将(0)；只含 `player.type=1` 且 `app_user.status=1`。
- [ ] 组内排序：本赛季出场降序 → 号码升序；`items` 含 `appearances`，字段按 `PlayerPublic`。
- [ ] `GET /c/team/stats` 公开：返回 `{items, season}`，数据读 `player_stats_summary` 当前赛季，允许 10 分钟延迟。
- [ ] 统计缺失时出场/进球/助攻按 0，不因无汇总行漏球员。
- [ ] 测试：分组顺序、组内排序、limit/all、注销/待审核/外挂排除、空态、统计缺失。
