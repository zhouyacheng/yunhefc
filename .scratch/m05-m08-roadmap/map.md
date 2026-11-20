# M05–M08 实施路线图

Status: resolved

## Destination

产出一张决策完备的路线图：M05 考勤、M06 事件与比分、M07 阵容、M08 分组的实施顺序、依赖、模块完成标准与交接点。路线走完后，可按模块用 to-tickets 生成实施票；本 map 不写业务代码。

## Notes

- 设计事实源：需求 v1.15；design 01 §4.5–4.8、02 §6.5–6.8、03 §4–§5、04、05 M05–M08。
- 顺序：M05 ∥ M06 → stats → M08 → M07。M06 先以 CONFIRMED+ACTIVE 报名名单校验；M08 落地后回填分组归属校验。
- 前端后端先行，页面随模块；`frontend/` 尚未创建，首个模块页面前先定脚手架与首个纵向切片。
- M04 余票（04 我的报名、05 名单、06/07 通知、08 限流）是前置/并行工作，由既有 M04 票跟踪，不在本 map。
- 跟踪约定见 `docs/agents/issue-tracker.md`；模块边界、DB/API 规则见 `CLAUDE.md` 与 `docs/rules/*`。

## Decisions so far

- [M05–M08 模块完成标准与交接方式](issues/01-module-done-and-handoff.md) — 后端门 + 页面门双门；每模块一条 Playwright 黄金路径；四票关闭后一次 to-spec、按模块出票；stats 独立切片位于 M06 与 M08 之间。
- [前端工程脚手架与首个纵向切片](issues/02-frontend-scaffold-first-slice.md) — 首个切片走通骨架：登录 + 双壳层 + 各一屏真实只读数据；api-client 用 fetch + 内存 CSRF；admin 路由 `BASE_URL` 驱动；Node 24；Playwright 打真实后端。
- [M06/M08 分组校验集成契约](issues/03-event-grouping-integration.md) — 已保存快照 + CONFIRMED+ACTIVE 双前置；contracts 只读端口；四写接口统一校验、无管理员豁免；42203；M08 回填，不记指纹不回溯。
- [M08 分组算法规格](issues/04-grouping-algorithm-spec.md) — 逐位置蛇形 + 局部搜索；候选池归一化自动分；SecureRandom 不落库；分组两表物理删除/覆盖写；PATCH 锁定候选池、保存后调整需 confirm。

## Not yet specified

- 各模块的实施票拆分与后端/前端日历（已由 `.scratch/m05-m08-build/README.md` 实施规格接续；下一步 to-tickets）。

## Out of scope

- M09 通知、M10 审计查询、M11 配置、M12 检索、M13 公开站点/个人中心——超出本路线终点。
- M14 二期功能（排行榜/身价、账本、视频等）。
