# M02 球员档案 — 实施票

事实源：`docs/云河FC-需求文档.md`（PLAYER/TEAM）、`docs/design/01` §4.3、`02` §5.4/5.6/6.9、`04` `player`/`player_stats_summary`、`05` §3.3/§4。

按阻塞边顺序执行，每票单独会话：

1. `issues/01-player-admin-read-model.md` — PLR-05/06
2. `issues/02-self-profile.md` — PLR-03（依赖 01）
3. `issues/03-team-page.md` — PLR-01 + STA-01（依赖 01）
4. `issues/04-approve.md` — PLR-07（依赖 01）
5. `issues/05-convert.md` — PLR-08（依赖 01）
6. `issues/06-disable.md` — PLR-09（依赖 01；含 registration 取消/递补端口）

约束：单队 MVP；跨域只经 contracts 读端口；枚举响应带 `*Label`；写操作审计；P-05 事务例外仅 PLR-07/08/09；不改 `schema.sql`，不执行 SQL。
