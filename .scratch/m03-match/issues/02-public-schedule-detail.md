# 02 — 公开赛程与比赛详情（MAT-02/03）

**What to build:** 匿名访客浏览赛程并打开比赛详情；列表支持筛选、排序、分页与 scope，卡片含场次编号、比分、胜负文本与报名队列计数；详情返回比赛完整信息，登录用户额外看到本人报名。`derivedScore` 暂留空，M06 补齐。

**Blocked by:** 01 — 建赛与后台读取.

**Status:** ready-for-agent

- [ ] 读 05 MAT-02/03、02 §5.2/5.3、需求 MATCH-09/11/12/15。
- [ ] `GET /api/v1/c/matches` 公开：`type,venue,status,season,month(YYYY-MM),scope=upcoming/history/all,sort=startAt:asc/desc,page,pageSize`；默认 all + startAt desc；upcoming=未结束且未取消（含 IN_PROGRESS/POSTPONED），history=FINISHED/CANCELLED；返回 `Page<MatchSummary>`。
- [ ] `MatchSummary` 含 `seasonMatchNo,totalMatchNo,resultText,confirmedCount,guestCount,waitlistCount`；`resultText` 按 `ourSide` 视角给出胜/负/平，`ourSide` 为空时给出比分文本，无比分为 null。
- [ ] 报名队列计数与本人报名经 registration 只读端口读取；没有报名记录时 `myRegistration` 为 null；匿名也为 null。实际报名数据由 M04 产生。
- [ ] `GET /api/v1/c/matches/{id}` 公开：返回 `MatchDetail`（`MatchSummary + format,competition?,signupDeadline,cancelLock,capacity,guestCapacity,waitlistCapacity,version`）；不存在 40401。
- [ ] `derivedScore` 暂返回 null，比分展示使用存储值；M06 事件域接入后改为事件推导值。
- [ ] 分页默认 10、可选 20/50/100；非法参数返回 40001。
- [ ] 测试：筛选/scope/month/sort/分页、详情 404、匿名与登录的 myRegistration、队列计数、resultText 与 ourSide 派生、公开可访问。
