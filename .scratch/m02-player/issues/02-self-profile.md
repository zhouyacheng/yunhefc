# 02 — PLR-03 自助资料

**What to build:** 实现 C 端球员自助编辑姓名、地区、主/副位置与头像 URL；号码、星级、类型与角色不可自助修改。

**Blocked by:** 01 — 球员域读模型与 PLR-05/06.

**Status:** ready-for-agent.

- [ ] 读 01 §4.3、02 §5.6、05 PLR-03/§2.4。
- [ ] `PATCH /c/me/player`，权限 `player:edit:own`；请求 `{name, region, position, secondaryPositions, avatarUrl?}`。
- [ ] 仅更新本人档案的目标列；拒绝 `jersey/skill/type/roles` 等未声明字段（按 05 只接受数字位置编码，副位置数组 0–3）。
- [ ] 头像只接受已上传的 `avatarUrl`；FIL-01 不在本票实现，测试直接写 URL。
- [ ] PENDING 账号可调用（05 §2.4 白名单已允许）。
- [ ] 写审计；返回 `Player`。
- [ ] 测试：字段更新、非法位置/副位置、越权字段忽略或拒绝、PENDING 可写、未登录 401。
