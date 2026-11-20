# 11 — M07 阵容板

**What to build:** 管理员为一场比赛的一方选择阵型、排出首发 8 人与替补并保存，版本冲突时拒绝覆盖；队内/训练赛有已保存分组时橙队只能排 A 组、兰队只能排 B 组；对外比赛对手方仅记录阵型与空槽位；正式球员只读、外挂不可见；可另存当前阵型为模板。

**Blocked by:** 08 — M08 分组调整、保存与清除；10 — M07 阵型模板.

**Status:** resolved

- [ ] `LIN-01 GET /c/matches/{id}/lineups/{side}`：权限 `lineup:view:any`；对手方仅阵型与空槽位；历史阵容按 `formation_template_id` 显式包含已软删除模板。
- [ ] `LIN-02 GET /b/matches/{id}/lineups/{side}`：权限 `lineup:view:any`；含 version；未保存时返回默认阵型、空首发、全部可排球员进替补、version=0；B05 由 REG-06 + GRP-02 + LIN-02 组合。
- [ ] `LIN-03 PUT /b/matches/{id}/lineups/{side}`：权限 `lineup:edit:any`，`LineupSave + Idempotency-Key`；我方首发 8 人且每槽 `playerId` 必填；对手方允许空槽位；版本冲突 40901 且返回 `data.currentVersion`；终态或 `POSTPONED` 40905；保存成功版本 +1 并写审计 `LINEUP_SAVE`。
- [ ] 可排名单：队内/训练赛存在已保存分组时，HOME=A 组、AWAY=B 组；首发/替补只能来自对应分组 ∩ 本场 CONFIRMED ∩ 账号 ACTIVE；跨队/已取消/已注销 42201；无已保存分组时来自本场确认报名；对外对手方仅阵型与空槽位。
- [ ] `LIN-04 POST /b/matches/{id}/lineups/{side}/template`：权限 `lineup:edit:any`，`{name}`；只写模板目录，不改变本场阵容版本。
- [ ] B05：比赛选择器、主/客切换、阵型下拉（预置 + 自定义）、球场拖拽/点击安排、替补加入/移出、首发为 0 时提示、分组两列与切队、名单变化警告、未分组/不参与单列、版本冲突提示刷新重试、终态/延期禁用保存、对手方仅阵型与空槽位、模板管理入口、加载中/失败/空数据可用。
- [ ] C03 阵容 Tab 只读；正式球员可看、外挂不可见。
- [ ] IT/单元：新建默认值、首发 8 人、替补持久化、版本冲突、终态/延期、预置与自定义模板、历史已删模板、对手方空槽位、队内/训练赛分组约束与跨队 422、无分组时确认报名、名单变化警告、另存模板不改阵容。
- [ ] Playwright B 端黄金路径：登录 → B05 → 选阵型 → 排 8 人首发 → 保存 → C03 只读查看；无 console error。
- [ ] 不改 `schema.sql`、不新增 Flyway；`mvn verify` 全绿。

## Result

- 交付：`aaa382a`。
- 验证：批次收尾 `mvn verify` 全绿（107 单元 + 250 集成）；前端 lint/typecheck 通过；Playwright 14 条黄金路径通过（2026-11-11）。
