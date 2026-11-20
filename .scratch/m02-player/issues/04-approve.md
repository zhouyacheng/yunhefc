# 04 — PLR-07 审核通过

**What to build:** 管理员审核待审核账号：仅 PENDING 可审核，改为 ACTIVE，角色保持 GUEST；通知与审计在同一事务内落地。

**Blocked by:** 01 — 球员域读模型与 PLR-05/06.

**Status:** ready-for-agent.

- [ ] 读 05 PLR-07、06 P-03/P-05、03 §4.3 事务例外、04 `app_user`/`player`/`notification`。
- [ ] `POST /b/players/{id}/approve`，权限 `user:approve:any`，要求 `Idempotency-Key`。
- [ ] 仅 `app_user.status=PENDING` 可审核；置 ACTIVE；不改变角色（保持 GUEST）；不改 `player.type`。
- [ ] 已在 ACTIVE 时幂等返回当前 `PlayerAdmin`，不重复写通知/审计。
- [ ] 同一本地事务内完成账号状态、审计、Outbox 通知（`notification.type=10`，P-05 例外）。
- [ ] 返回 `PlayerAdmin`；错误码按 05。
- [ ] 测试：审核成功、非 PENDING 冲突、幂等重放、通知 Outbox、审计、无权限 403。
