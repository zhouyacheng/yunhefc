# 05 — 报名名单读取（REG-05/06）

**What to build:** 正式球员与管理员能按类型/状态分页查看某场比赛的报名名单；外挂只能看到自己的报名。

**Blocked by:** 01 — 报名与候补主流程（REG-01/09）

**Status:** ready-for-agent

- [ ] 读 02 §5.3/§6.5、05 REG-05/06 与 `RegistrationRow`。
- [ ] `GET /api/v1/c/matches/{id}/registrations` 与 `GET /api/v1/b/matches/{id}/registrations`，权限 `attendance:view:any`；外挂/无权限返回 40301。
- [ ] 查询 `type,status,page,pageSize`；默认 10、最大 100；返回 `Page<RegistrationRow>`。
- [ ] 每行 `id,player:{id,name,jersey?,position},type,status,registeredAt,cancelledAt?`，枚举带 `*Label`。
- [ ] 排序按 `registered_at` 升序、`id` 升序；比赛不存在或跨队返回 40401。
- [ ] 名单只读，不触发写操作；不返回手机号等敏感字段。
- [ ] 跨域球员信息经 contracts 只读端口批量读取，避免 N+1；读模型放 `query`。
- [ ] 测试：C/B 两端、正式球员可见、外挂 403、type/status 过滤、分页、空名单、跨队 404。
