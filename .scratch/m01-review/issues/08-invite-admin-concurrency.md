# 08 — 邀请与管理员并发保护

**What to build:** 撤销/过期邀请在接受并发时不可被消费；ROL-05/ROL-07 的最后管理员保护在并发下不可绕过。

**Blocked by:** None — can start immediately.

**Status:** done — 4c08c1c

- [x] `incrementUsedCount` 条件包含 `status=ACTIVE` 与未过期，撤销/过期后无法消费
- [x] 接受邀请在事务内锁定邀请行或使用条件更新，校验状态与次数
- [x] INV-06 撤销使用定向更新，不覆盖并发 `used_count`
- [x] ROL-05/ROL-07 最后管理员校验使用锁定读或串行化（如锁定球队/管理员行）
- [x] 并发测试覆盖“撤销 vs 接受”和“两个管理员互相降权”
- [x] 保持 40910 `INVITE_UNAVAILABLE` 与 40913 `LAST_ADMIN_PROTECTED` 语义