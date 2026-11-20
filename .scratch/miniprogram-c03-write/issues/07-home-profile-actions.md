# 07 首页报名与我的报名取消

Status: ready-for-human
Blocked by: 03

## 目标

让 MP06 Hero 与 MP07 我的报名具备真实写操作。

## 验收

- [ ] 首页 CTA 的 `register` 调 REG-01，成功提示“报名成功/已加入候补”并刷新 Hero；删除网页版提示。
- [ ] 首页 CTA 纳入 `registration:create:own`；40902/40903 刷新并提示。
- [ ] 我的报名进行中支持取消：先 MAT-03 校验 `cancelLock`，再 REG-02；成功提示递补并刷新列表。
- [ ] 取消按钮 `catchtap` 不触发进入详情；待审核不显示。
- [ ] `typecheck`/`test`/`lint` 通过。

## Comments

- 2026-11-20 实施 P-78；typecheck/test/lint 通过，人工验收见票 08。
