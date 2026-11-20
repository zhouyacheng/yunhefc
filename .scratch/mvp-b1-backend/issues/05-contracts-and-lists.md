# 05 — 契约收口：wechatBound / sections / 列表校验

Type: implementation
Status: resolved

**What to build:** 前后端共享契约与两个管理端列表的参数校验收口：用户与球员管理响应能直接判断是否绑定微信（不暴露 openid）；B 端 `sections` 不再返回已下线的 dashboard；邀请列表与账号列表的枚举参数非法时返回 40001，账号列表支持按姓名或手机号关键字选人。

**Blocked by:** None — can start immediately

**Status:** resolved

- [x] `UserView` 增加 `wechatBound: boolean`，由 `app_user.openid` 是否为空派生；AUTH-03/04/09/10/11/15/17 的 `user` 自动携带；始终返回布尔值，任何响应都不返回 openid。
- [x] `PlayerAdminView` 增加 `wechatBound`，经 `UserAccountSnapshot` 传递布尔值；PLR-05/06/07/08/09 自动携带；无账号球员为 false。
- [x] `UserSummary` 不加该字段；`MeView` 不新增顶层字段。
- [x] `AuthService.sections` 移除 dashboard，其余分区与权限映射不变；同步修正现有断言 dashboard 存在的集成测试。前端 `ADMIN_SECTIONS` 与路由清理属 B2，本票不修改。
- [x] INV-04 校验 `type∈{0,1}`、`status∈{0,1,2}`，非法返回 40001；分页保持 `PageQuery` 白名单。
- [x] AUTH-13 校验 `status∈{0,1,2}`；新增可选 `keyword`：去首尾空格、空串不过滤、长度 >64 返回 40001，对球员姓名或账号手机号模糊匹配；分页与固定排序不变。
- [x] `frontend/packages/shared` 的 `User` 与 `PlayerAdmin` 增加 `wechatBound`；本票不做 UI。
- [x] 集成测试覆盖：绑定/未绑定/解绑后两侧字段、无账号球员 false、openid 不出现在响应、sections 不含 dashboard、INV-04/AUTH-13 非法枚举、`keyword` 的 trim/空串/超长/姓名命中/手机号命中；`mvn verify` 全绿。

## Comments

- 2026-11-12 交付：提交 `a504529`（feat: close contract gaps for wechatBound, sections and list validation）。
- 验证：`mvn verify` BUILD SUCCESS（132 单测 / 298 IT / 0 failures，JDK 21）；`pnpm -r typecheck` 通过。
- 评审：Standards/Spec 双轴评审已完成；按 Standards 修正跨域查询（AUTH-13 姓名搜索改走 `PlayerReadPort.findUserIdsByNameContaining`，不再直查 `player` 表），并抽出 `AppUser#isWechatBound` 消除两处重复；两轴均指出 LIKE 通配符未转义，新增 `LikePattern` 对 `%`/`_`/`\` 按字面量转义。
- 实现位置：`UserViewAssembler`、`UserAccountReadAdapter`、`PlayerAdminApplicationService`、`AuthService.listUsers/sections`、`InviteService.list`、`RoleController` GET `/b/users`、`PlayerReadPort` 与 `PlayerReadAdapter`、`common.util.LikePattern`、`frontend/packages/shared/src/auth.ts`。
- 文档：本票无契约/DDL 变更（05 §3.1/§4 已于设计同步中定稿）；06 追加 §57 实施记录，README 回填交付表。前端 `ADMIN_SECTIONS`/路由清理按票面留 B2。
