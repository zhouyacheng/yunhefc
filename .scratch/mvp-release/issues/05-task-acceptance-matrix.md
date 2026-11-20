# 05 — 112 条 MVP 验收矩阵与测试覆盖对账

Type: task
Status: resolved
Blocked by: —

## Question

从 `docs/云河FC-需求文档.md` 抽取 112 条 MVP 验收项，逐条标注：

- 可自动验证（Playwright / Vitest / JUnit）、需人工演示、当前无覆盖。
- 现有 Playwright 黄金路径与 Vitest/JUnit 基线覆盖了哪些，缺口在哪。
- 产出一份验收矩阵骨架：编号、验收点、验证方式、现状、备注；供「验收与上线检查单」定稿。

只做抽取与对账，不决定上线范围，不改需求文档。事实指针：需求文档 MVP/验收章节、`frontend/e2e`、`frontend/**/*.test.ts`、`backend/src/test`、`CLAUDE.md` 验证清单。

## Answer

- 112 条 MVP 验收项已逐条编号（1–112），与需求文档 `✅ MVP` 行数一致；二期 MATCH-14、NOTIFY-04 与 11 条 Q 不计入。
- 验证方式统计：可自动化 100 条（Playwright 11、Vitest 1、JUnit 88）；需人工演示 3 条（MATCH-09、EVENT-15、LINEUP-07）；当前无覆盖 9 条（PLAYER-05、TEAM-01~05、MATCH-13、NOTIFY-02、NOTIFY-03）。
- 最大缺口：球队页整页缺失导致 5 条 TEAM 验收无法端到端演示；分享链接、通知已读/未读整块缺失；头像 OSS 上传无代码；B 端球员/角色/设置/审计/邀请仍为占位页。
- 自动化基线：Playwright 14 条、Vitest 3 个文件、JUnit 69 个文件；黄金路径覆盖登录、赛程、签到、事件/比分、B03、B04、B05、B06 与权限 403。
- 完整矩阵与缺口： [research/05-acceptance-matrix.md](../research/05-acceptance-matrix.md)
