# 04 — 我的报名（REG-03/04）

**What to build:** 登录用户能查看自己的“进行中/历史”报名，以及单场比赛的本人报名状态，供个人中心与比赛详情报名卡使用。

**Blocked by:** 01 — 报名与候补主流程（REG-01/09）

**Status:** ready-for-agent

- [ ] 读 01 §4.5、02 §5.6、04 `registration`、05 REG-03/04 与 `MyRegistration`/`RegistrationRow`。
- [ ] `GET /api/v1/c/me/registrations`，已登录即可访问；`scope=active/history`、`page`、`pageSize`；默认 active、默认 10、最大 100；返回 `Page<MyRegistration>`。
- [ ] 每场比赛只返回最新一条报名记录（`registered_at,id` 降序）；active = 比赛非 FINISHED/CANCELLED 且报名 CONFIRMED/WAITLISTED；history = 其余（含已取消、已移除、比赛已结束/已取消）。
- [ ] 比赛已取消时“已作废”由 `match.status` 派生，不修改报名 status。
- [ ] `MyRegistration = {registration: RegistrationRow, match: MatchSummary}`，字段与 05 §4 一致；比赛摘要经 contracts 只读端口获取，不在报名域复制比赛展示规则。
- [ ] `GET /api/v1/c/matches/{id}/registrations/me`，已登录即可访问；返回 `{registration?, attendance?}`；未报名 `registration=null`；M05 落地前 `attendance=null`。
- [ ] 跨域只读组合放 `query` 读模型包，只依赖各域 read port；业务域不反向依赖 `query`。
- [ ] 测试：进行中/历史分组、默认 active、每场取最新记录、比赛取消派生已作废、未报名 null、分页与排序、跨队比赛 404。
