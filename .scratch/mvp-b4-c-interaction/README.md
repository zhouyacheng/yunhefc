# B4 C-B 互动与微信 — 实施规格

Status: ready-for-agent
Type: spec

事实源：`.scratch/mvp-release/batches.md` §B4/§1/§4/§6；决策票 `.scratch/mvp-release/issues/11-grilling-c-end-pages.md`、`14-grilling-sms-wechat.md`、`20-grilling-b02-depth.md`、`21-grilling-wechat-binding.md`；`docs/design/02.页面结构与交互设计.md` §2.1/§5.3/§5.5/§5.6/§5.8、§7.1–§7.3；`docs/design/05.API接口设计.md` REG-01/02/04/05、ATT-01/02/03、INV-03、MAT-12/13、AUTH-07/08/09；`.scratch/mvp-b3-c-public/README.md` 交付结果；`frontend/apps/web` 现状。

本规格只定义 B4 的实施边界与验收口径，不写业务代码、不改后端契约、不改 `schema.sql` / Flyway / Quartz DDL。

---

## Problem Statement

B3 已交付 C-A 公开页，但 C 端的互动闭环还差两步：

- C03 比赛详情只有「我的报名」展示与签到：没有报名/取消按钮，没有全队名单与考勤，没有邀请队友、分享与管理员状态下拉；球员无法在 C 端完成「报名 → 取消」闭环，管理员改状态还得跳 B02。
- 微信网页授权只有后端 AUTH-07/08/09：C 端没有 `/auth/wechat/callback` 落地页、登录页没有微信入口、失败没有站内提示；微信内用户无法完成静默授权与首次绑定手机。
- 结果：B4 是 cutover 前最后一个功能批次；两票完成后 112 条 MVP 验收才具备可演示条件。

## Solution

按 2 张实施票补齐 C-B；两票在代码上无共享文件，可各自独立完成，按批内票序先做 C03，再做微信：

| 票 | 内容 | 前置 | 主要交付 |
|---|---|---|---|
| 01 | C03 报名闭环、名单与分享 | 无（B3 已完成） | REG-01/02、名单/考勤、INV-03、分享、MAT-12、404 |
| 02 | 微信授权回调与绑定 | 无（建议排在 01 后） | AUTH-08 HTML 302、mock 授权页、C08 落地页、C05 微信入口、C06 绑定按钮、失败提示 |

**批次完成门**

- C03 报名→取消闭环、分享、管理员状态变更的 Playwright 黄金路径全绿，无 console error。
- 微信授权在 dev/mock 下全链路可跑通；真机证据（静默 `snsapi_base`、首次绑定手机、老用户登录、管理员解绑后重绑、缺 code/state 提示页）作为外部门禁挂 release-checklist，cutover 前必须补齐。
- 112 条 MVP 验收矩阵中与 C-B 相关的条目有页面级证据。
- `pnpm -r build/typecheck/test` 全绿；无契约变更：AUTH-07/08/09 路径、权限码、错误码与 JSON 响应形态不变，不改 schema/Flyway。允许票 02 的 AUTH-08 HTML 分支、mock client 与 dev-only mock 页。

## Implementation Decisions

### C03 结构与权限

- 维持 4 个 Tab：概览/事件/阵容/分组；报名合并进概览，不恢复独立报名 Tab。
- 概览：作废横幅、本人报名卡（状态、名额进度、报名/取消/签到及禁用原因）、名单与考勤。
- 名单可见性：正式球员与管理员看全队；外挂只看本人；访客与待审核只读或登录引导。
- 名单组成：以本场有效报名为基准，按「正式确认 → 正式候补（带顺序）→ 外挂确认」分段；REG-05 与 ATT-03 按 `playerId` 左连接，缺考勤记录显示「未记录」。外挂无 `attendance:view:any`，不调用 REG-05/ATT-03。
- 报名 `REG-01`、取消 `REG-02` 均带 `Idempotency-Key`；取消 CONFIRMED 时展示真实递补结果（`promotedPlayerId/promoted`）。
- 名单用 `REG-05`（正式/管理员），考勤用 `ATT-03`，按 `playerId` 合并；管理员移除同页跳 B03（`?section=registrations&match={id}`），完成后返回 C03 重载并展示真实递补结果；C 端名单只读。
- 邀请队友：仅本场正式球员/管理员显示，`INV-03`，有效期默认 7 天、次数默认不限；链接只展示一次。
- 分享：稳定 URL `/matches/{id}`；支持 `navigator.share` 时调起系统分享；微信内提示右上角分享；不接 JS-SDK。
- MAT-12：管理员状态下拉展示全部状态，但只允许选择后端接受的目标：0–4 恒可（含从终态恢复）；延期 5 仅源状态为报名中/已截止/锁定/进行中且当前账号有 `match:edit:any`；取消 6 在源状态为已结束/已取消时禁用。延期/取消在 C 端弹窗完成并复用 MAT-07/08 规则；带 `version` 与幂等键。
- 不存在或已删除比赛统一进 `/404`。

### 微信网页授权

- 回调地址保持 `https://www.<domain>/api/v1/c/auth/wechat/callback`（同源 www，host-only Cookie）。微信浏览器跳回后，后端按 `Accept` 判定：含 `text/html` 且不含 `application/json` 时走 302——成功直接回 `redirect`；需绑手机回 `/auth/wechat/callback?bind=1&redirect=…`；失败回 `/auth/wechat/callback?error=<code>&redirect=…`。其余请求仍返回现有 JSON，契约不变。
- `redirect` 只接受站内相对路径：`/` 开头、非 `//`、不含 `://` 或 `\`；否则回首页。
- scope `snsapi_base` 静默授权；只使用 openid，不获取/存储昵称头像。
- 失败口径：非微信 UA → `42201`「请在微信内打开」；缺 code / state 失效 / 微信 `40029/40163` → `40001`，落地页显示「微信授权未完成」并提供重试/手机号登录；AppID/Secret/系统错误 → `50001`；日志只记 errcode。重试重新发起 AUTH-07，不刷新已失效的回调 URL。
- 已登录用户回调即绑定 openid 到当前账号；openid 已绑其他账号 → `42201`；换绑只能先由管理员在 B07 解绑。
- C05 微信入口仅在微信内置浏览器显示；C06 绑定状态取自 `AUTH-10` 的 `user.wechatBound`，页面进入与路由切换时刷新。
- C06 账号与安全区新增「绑定微信」按钮：仅微信内置浏览器且 `wechatBound=false` 时显示；点击走 AUTH-07（`redirect=/profile`）；无自助解绑。
- dev/test 用 mock 完成 E2E：新增专用 mock 授权页 `GET /api/v1/c/auth/wechat/mock-authorize?state=…`（仅 mock 模式注册，不计入 92 接口），`MockWechatOAuthClient` 返回该页地址，页面「确认授权」后跳后端回调，模拟微信完整链路。真机验收依赖外部 AppID/Secret/授权域名与 `MP_verify_*.txt`，作为外部门禁挂在 release-checklist，不阻塞本票关闭。

---

## Out of Scope

- B0 基座与 CI/CD、收口检查单。
- B 端页面与后端契约；`schema.sql` / Flyway / Quartz DDL。
- 微信公众号模板消息、JS-SDK 自定义分享、昵称头像获取。
- 微信支付、订阅通知、多公众号支持。
- M14 二期能力、多队/平台超管。

---

## Further Notes

### 实施票序

| 票 | 文件 | Blocked by |
|---|---|---|
| 01 | `issues/01-c03-completion.md` | — |
| 02 | `issues/02-wechat-auth-and-binding.md` | —（建议排在 01 后） |

建议每票单独会话、票间 `/clear`。两票无共享文件；`02` 的外部真机验收可在代码完成后单独执行，不阻塞 `01`。

### 完成标准

- 2 票全部 resolved；每票在 `## Result` 记录提交与验证。
- C03 黄金路径与微信 mock E2E 证据留档；真机验收未完成时在 `## Result` 明确写出，由 release-checklist 在 cutover 前补齐。
- 无契约变更与 schema/Flyway 改动；票 02 的 AUTH-08 HTML 分支、mock client、dev-only mock 页属允许范围。未执行生产 SQL、未 push。

### 外部门禁与跨批事项

- 微信真机验收（静默授权、首次绑定、老用户登录、解绑后重绑、缺 code/state 提示）可在票 02 代码完成后单独执行；外部配置未就绪时票仍可 resolved，但须在 `## Result` 列出未验证项，并由 release-checklist 在 cutover 前补齐证据。
- B02 管理状态下拉当前未按合法转换禁用，与本批 C 端口径不一致；另见 `.scratch/mvp-b2-admin/issues/11-b02-status-options.md`，B4 不修改 B02。
