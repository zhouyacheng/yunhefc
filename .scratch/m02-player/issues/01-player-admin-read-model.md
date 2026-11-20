# 01 — 球员域读模型与 PLR-05/06

**What to build:** 建立 `player` 域的管理端读模型与编辑能力，作为 M02 的 tracer bullet。实现 PLR-05 分页列表与 PLR-06 编辑；补齐 `PlayerAdmin` 组装所需的账号状态、角色、脱敏手机号读取，跨域数据只经 contracts 读端口获取。

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent.

- [ ] 读 01 §4.3、02 §6.9、04 `player`/`app_user`/`user_role`、05 §3.3/§4，确认字段与筛选口径。
- [ ] PLR-05 `GET /b/players`：`name,region,type,admin,status,position,skill,sort,page,pageSize`；默认号码升序；分页默认 10，可选 20/50/100；`admin` 表示默认 ADMIN 角色或含 `role:manage:any`；`status` 取 `app_user.status`。
- [ ] 返回 `Page<PlayerAdmin>`：`PlayerPublic + userId,phoneMasked,status,roles,createdAt`，枚举带 `*Label`。
- [ ] PLR-06 `PATCH /b/players/{id}`：`name,jersey,region,position,secondaryPositions,skill,avatarUrl`；正式球员号码唯一，冲突返回 40904 `DUPLICATE_JERSEY`。
- [ ] 写操作只更新目标列；写审计 `PLAYER_UPDATE`。
- [ ] 权限 `player:view:any` / `player:edit:any` 走 `@PreAuthorize`。
- [ ] 跨域读取走 contracts 读端口，不在 player 域 JOIN `app_user`/`user_role`。
- [ ] 集成测试覆盖：筛选、排序、分页白名单、号码冲突、无权限、编辑字段不可越权（type/jersey 边界按 05）。
