# 05 — PLR-08 外挂转正

**What to build:** 管理员将 ACTIVE 外挂转为正式球员：`player.type=1`、移除 GUEST、授予 PLAYER；可选设置正式号码并保证唯一。

**Blocked by:** 01 — 球员域读模型与 PLR-05/06.

**Status:** ready-for-agent.

- [ ] 读 05 PLR-08、06 P-05、01 §4.3、02 §6.9。
- [ ] `POST /b/players/{id}/convert`，权限 `player:edit:any`，要求 `Idempotency-Key`；请求 `{jersey?}`。
- [ ] 仅 ACTIVE 且 `player.type=0`（GUEST）可转正；已是 PLAYER 时幂等返回。
- [ ] 号码：未传沿用现有 `jersey`；与正式球员冲突返回 40904 `DUPLICATE_JERSEY`。
- [ ] 同一事务内改 `player.type`、移除 GUEST、授予 PLAYER、写审计（P-05 例外）；保留自定义角色。
- [ ] 返回 `PlayerAdmin`。
- [ ] 测试：成功转正、号码冲突、非外挂冲突、幂等、角色替换只动 GUEST/PLAYER、审计、无权限。
