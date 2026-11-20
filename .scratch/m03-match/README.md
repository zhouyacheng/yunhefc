# M03 比赛模块 — 实施票

事实源：`docs/云河FC-需求文档.md`（MATCH）、`docs/design/01` §5.3、`02` §5.2/5.3/6.4、`04` `team_match`、`05` §3.4。

按阻塞边顺序执行，每票单独会话，票间 `/clear`：

1. `issues/01-create-admin-read.md` — MAT-04/09/10
2. `issues/02-public-schedule-detail.md` — MAT-02/03（依赖 01）
3. `issues/03-edit.md` — MAT-05（依赖 01）
4. `issues/04-status-machine.md` — MAT-06/12 普通状态（依赖 01）
5. `issues/05-postpone-reschedule-cancel.md` — MAT-07/08/11 + MAT-12 对应分支（依赖 04）
6. `issues/06-auto-status.md` — MATCH-05 自动流转（依赖 04）

约束：单队 MVP；跨域只经 contracts 端口；状态与时间规则放 domain/application；写操作审计；自动流转必须 Quartz JDBC（禁止 `@Scheduled`）；不改 `schema.sql`，不执行 SQL。`derivedScore` 留 M06，已报名者通知留 M04。
