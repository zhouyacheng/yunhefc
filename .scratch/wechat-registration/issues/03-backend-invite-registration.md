# 03 — 微信注册支持入队邀请免审

**What to build:** 微信内通过入队邀请注册时，AUTH-09 携带 `inviteToken` 可让账号直接成为 ACTIVE + PLAYER 并消费邀请；比赛邀请只引导、不改变状态。

**Blocked by:** 02

**Status:** wontfix（2026-11-20 微信注册已移除）

- [ ] AUTH-09 请求增加可选 `inviteToken`
- [ ] 可用 TEAM 邀请 → ACTIVE+PLAYER、消费邀请、player.type=PLAYER；与 AUTH-03 判定一致
- [ ] 比赛邀请 / 无效邀请按现有语义处理，无效返回 40910
- [ ] 邀请消费与建档同事务，失败整体回滚
- [ ] IT：带 TEAM 邀请注册 → ACTIVE+PLAYER；重复使用受 `used_count` 保护
