# 02 让 typed client 成为唯一 seam

Status: needs-triage
Blocked by: none

## 目标

所有 C 端请求从 typed client module 进入；页面不再出现 endpoint 字符串、手拼 query 或直接调用 `request()`。

## 现状与摩擦

- `utils/match-api.ts` 已是深 adapter：路径、分页、幂等键、CSRF 都在里面。
- 但 `pages/home/index.ts`、`pages/profile/index.ts`、`pages/team/index.ts`、`pages/profile-edit/index.ts` 仍有 8 处内联 `request()` 与手拼 query（通知分页、我的报名、球队赛季/球员/统计等）。
- seam 未被强制，页面可随时绕过；路径与错误契约的 locality 被打散。

## 深化方向

- 一个 typed c-client module，按域暴露函数（比赛、报名、通知、我的、球队、资料）；页面只调用函数。
- interface 先设计再实现；分页参数、幂等键、CSRF 与错误归一化留在 implementation。
- 上传若必须用 `wx.uploadFile`，作为 client 内部 adapter，不暴露给页面。

## 删除测试

删除 `match-api` 会让 4 个页面变复杂，它 earns its keep；问题不是删除，而是让这个 seam 成为唯一入口。

## 收益

- leverage：一个 client interface 覆盖全部页面。
- locality：路径、分页、幂等、错误处理集中。
- 测试：以 request adapter 为替身测 client，不在页面测 URL 字符串。

## 范围

- 改 `utils/match-api.ts`（或拆成同层域 module）、上述页面与对应测试。
- 保持现有请求参数、分页语义、错误提示不变。

## 不做

- 不改后端路径或响应结构。
- 不重写 `utils/request.ts` 的 Cookie/CSRF 机制。
- 不引入新的 HTTP 依赖。

## 验收

- [ ] 页面不再出现 `/notifications`、`/me/registrations`、`/team/*`、`/matches/seasons` 等 endpoint 字符串。
- [ ] 页面不再手拼 `page`/`pageSize`/`scope`/`season` query。
- [ ] 请求入口只剩 typed client（上传 adapter 可在 client 内）。
- [ ] client 层测试覆盖分页、幂等键与错误归一化。
- [ ] `typecheck`、`test`、`pnpm lint` 通过。

## 风险

- 页面现有错误提示各自定制；迁移时保留 `ApiError` 语义与 fallback 文案，不要统一成一条泛化提示。

## Comments

- 2026-11-20 架构扫描开票（Strong）。
