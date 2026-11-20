# 21 — C06 微信绑定状态与 AUTH-10 契约

Type: grilling
Status: resolved
Blocked by: —

## Question

02 §5.6 要求个人中心「账号与安全」展示微信绑定状态（解绑仅管理员操作），但 AUTH-10 `GET /c/me` 的响应只有 `user/player/roles/permissions/unreadCount`，`User` 不含绑定标记，前端无法判断当前账号是否已绑定微信。11 号票已将微信相关能力后置到 C-B（随 C08 与 14 号票）。

确定：

- 是否扩展 AUTH-10（及 AUTH-04/AUTH-15 的 `user`）返回 `wechatBound`，还是仅在 C06 展示静态说明、不显示状态，或从 02 移除该展示。
- 若扩展契约：字段命名、返回位置（`user` 内或 `MeView` 顶层）、登录/解绑后的刷新口径；同步 05/02/06，不新增接口。
- 与 14 号票（真实微信配置、解绑与重新授权）的边界，避免同一契约在两票重复决定。

事实指针：02 §5.6、05 §3.1 AUTH-10/AUTH-04/AUTH-15、05 §4 `User`、`frontend/packages/shared/src/auth.ts`、`backend/.../auth/application/AuthService.java`、11 号票答案、14 号票。

## Answer

状态：resolved（2026-11-12）。决策：扩展共享 `User`，同时补 B07 需要的 `PlayerAdmin` 字段；只定读契约，不新增接口。

### 1. 契约

- `User` 增加 `wechatBound: boolean`，由 `app_user.openid IS NOT NULL` 派生；始终返回，未绑定为 `false`；**不返回 openid**。因 `UserView` 被复用，AUTH-03/04/09/10/11/15/17 的 `user` 自动携带该字段。
- `PlayerAdmin` 增加 `wechatBound: boolean`；PLR-05/06/07/08/09 响应自动携带；无账号的球员（`userId` 为空）为 `false`。
- `UserSummary`（AUTH-13/B08 选人）不加；`MeView` 不新增顶层字段，避免与 `user` 重复。
- 无新接口、无新权限码、无新错误码；AUTH-07/08/09/12 的请求/响应形状不变。

### 2. 读取与刷新

- 每次响应实时从库派生，不加 Redis/会话缓存、不做推送。
- 登录（AUTH-04）、绑手机（AUTH-09）响应立即反映最新值。
- 管理员解绑（AUTH-12）后：C 用户下次 `GET /c/me` 生效（C06 在页面进入与路由切换时刷新）；B07 重新拉取 PLR-05 生效。
- 不新增会话失效或强制下线逻辑；解绑只清 openid，不影响登录态。

### 3. 页面

- **C06**：账号与安全展示「微信：已绑定/未绑定」，取自 `AUTH-10` 的 `user.wechatBound`；解绑仅管理员操作，C 端不提供入口。展示随 C-B（保持 11 号票边界）。
- **B07**：表格新增「微信」列（已绑定/未绑定）；「解绑微信」入口仅在已绑定时显示，确认后调用 AUTH-12 并留痕；未绑定不显示该操作。

### 4. 批次归属（交 18 号票）

- 本票只冻结契约；`PlayerAdmin.wechatBound` 随 B07 所在首批落地，`User.wechatBound` 随 C-A/M13 的后端改动落地（无 UI），C06 的可视状态随 C-B（与 C08 微信授权同批）。避免同一契约两次修改。

### 5. 与 14 号票边界

- 14 号票负责真实微信 AppID/Secret、`snsapi_base` 授权、失败口径与管理员解绑/重新授权流程；本票只决定「绑定状态如何被读取」，不重复决定授权与解绑语义。

### 6. 实现要点（不在本票写代码）

- `UserViewAssembler.toView` 直接读 `AppUser.openid` 派生 `wechatBound`，无需额外查询。
- `PlayerAdminView` 经 `UserAccountSnapshot` 组装：给快照增加 `wechatBound`（不暴露 openid），`PlayerAdminApplicationService` 映射。
- 前端 `frontend/packages/shared/src/auth.ts` 的 `User` 与 `PlayerAdmin` 各加一个布尔字段；无新请求。

### 7. 文档与原型同步

- 05 v0.29：§4 `User`、`PlayerAdmin` 增加字段与说明。
- 02 v0.21：§5.6 C06 账号与安全、§6.9 B07 列与解绑入口显隐。
- 06 v1.49：新增 §52。
- 原型：`data.js` 增加少量 demo 绑定标记，`admin.js` B07 微信列与解绑入口，`public.js` C06 绑定状态；`README.md` 补一句演示说明。
- 需求/01/03/04/`schema.sql`/Flyway 不改（`app_user.openid` 已存在）。

### 8. 未执行

未写业务代码、未执行 SQL、未运行后端/前端测试；仅文档、原型与 tracker 改动。
