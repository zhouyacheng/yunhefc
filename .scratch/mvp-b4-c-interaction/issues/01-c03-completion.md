# 01 — C03 报名闭环、名单与分享

Type: implementation
Status: resolved
Blocked by: 无（B3 已完成）

**What to build:** 球员在比赛详情页完成报名与取消（含候补与真实递补提示），查看全队名单与考勤；正式球员可创建比赛邀请并分享比赛；管理员可在 C 端直接修改比赛状态；不存在的比赛进入 404。

## Scope

- 概览（保持 4 Tab，不恢复报名 Tab）：
  - 作废场次横幅与历史条数；已取消比赛显示「报名作废」，隐藏取消/签到/名单操作。
  - 本人报名卡：状态徽标、名额进度、REG-01 报名与 REG-02 取消，均带 `Idempotency-Key`；成功后刷新本人状态、名单、名额与考勤。
  - 禁用原因：访客引导登录；待审核只读；外挂仅本人；截止/锁定/进行中/结束/延期/取消；正式满进候补、外挂满拒绝；取消 CONFIRMED 时提示真实递补结果。后端不返回 eligibility，前端从 `MatchDetail` 的 `status/signupDeadline/cancelLock/capacity/guestCapacity/waitlistCapacity/confirmedCount/guestCount/waitlistCount/version/myRegistration` 推导；复用 `features/home/cta.ts` 的判定思路与 shared 的 `checkInWindowState`。
- 名单与考勤：REG-05 拉全部有效报名（`pageSize=100`，超出则循环取页），按「正式确认 → 正式候补（带顺序）→ 外挂确认」分段；ATT-03 按 `playerId` 左连接，缺记录显示「未记录」并展示来源。外挂无 `attendance:view:any`，不调用 REG-05/ATT-03，只看 REG-04 本人；C 端名单只读。管理员移除同页跳 B03（`?section=registrations&match={id}`），返回 C03 后重载名单并展示真实递补结果。
- 邀请队友：仅本场正式球员或管理员显示；INV-03 + `Idempotency-Key`；有效期 1–30 默认 7、次数留空不限；创建成功只展示一次链接，支持复制。
- 分享：稳定 URL `/matches/{id}`；支持 `navigator.share` 时调起系统分享，否则复制；微信内置浏览器提示点击右上角分享；不接 JS-SDK。
- MAT-12 管理员状态下拉：展示全部状态（含延期/取消），但按后端合法集禁用：0–4 恒可（含从终态恢复）；延期 5 仅当前状态为 0–3 且当前账号有 `match:edit:any`；取消 6 在当前状态为 4/6 时禁用。延期复用 MAT-07、取消复用 MAT-08，在 C 端弹窗完成（延期需 `startAt/endAt`，取消需 `reason`）；带 `version` 与 `Idempotency-Key`；`40905/40901` 展示真实原因并刷新。
- 比赛不存在或已删除（40401）统一跳 `/404`；加载失败可重试。

## Owned files

- `frontend/apps/web/src/pages/MatchDetailPage.vue`
- `frontend/apps/web/src/features/match-detail/**`（新增的纯逻辑/子组件）
- `frontend/packages/shared/src/match.ts`（仅追加分享/状态 helper 与类型，如需要）
- `frontend/e2e/web-match-detail.spec.ts`（新建）

## API

- REG-01 `POST /c/matches/{id}/registrations`
- REG-02 `DELETE /c/matches/{id}/registrations/me`
- REG-04 `GET /c/matches/{id}/registrations/me`
- REG-05 `GET /c/matches/{id}/registrations`
- ATT-03 `GET /c/matches/{id}/attendance`
- INV-03 `POST /c/matches/{id}/invites`
- MAT-12 `POST /c/matches/{id}/status`

## Test obligations

- Playwright：正式球员报名 → 状态与名额更新 → 取消 → 状态变更；管理员状态下拉修改一次并回到原状；分享复制稳定 URL；邀请队友创建后出现一次性链接；不存在的比赛进入 404；无 console error。
- 纯逻辑用 Vitest 覆盖报名/取消按钮状态与禁用原因、递补提示文案、分享降级。
- 待审核、外挂、访客三种身份的可见性各至少一条断言。
- `pnpm -r test`、`pnpm -r typecheck`、`pnpm -r build` 全绿。
- 复用现有种子：1005 用于报名→取消（报名/考勤每次清空）、1001/1002 用于名单与考勤合并、1021 用于待审核外挂；如需要新增种子，按区块追加且可重复执行。
- 管理员移除链路断言 B03 链接带 `matchId`，返回后名单重新拉取；状态下拉断言延期/取消在非法源状态下禁用。
- 不改后端契约、权限码、错误码；不执行 SQL。

## Result

已完成（2026-11-13），提交 `9111e53`：

- C03 概览：本人报名卡（状态、正式/外挂/候补进度、REG-01 报名 / REG-02 取消、禁用原因、签到）、作废场次横幅与历史条数、待审核只读、外挂仅本人、访客登录引导。
- 名单与考勤：REG-05（`status=0`/`1` 分开取，`pageSize=100` 循环）+ ATT-03 按 `playerId` 合并；正式确认 → 正式候补（顺位）→ 外挂确认；缺考勤显示「未记录」与来源；管理员 B03 链接、返回重载与真实递补提示。
- 邀请队友：INV-03 + `Idempotency-Key`，默认 7 天、次数留空不限；一次性链接只展示一次并支持复制。
- 分享：稳定 URL `/matches/{id}`；`navigator.share` → 微信右上角提示 → 剪贴板降级。
- MAT-12：管理员下拉展示全部状态并按合法集禁用；延期/取消弹窗分别复用 MAT-07/MAT-08 规则；带 `version` 与幂等键；40901/40905 展示真实原因并刷新。
- 40401 统一 `/404`；加载失败可重试。
- 新增 `frontend/apps/web/src/features/match-detail/` 纯逻辑与组件（registration/roster/status-options/share/invite + 4 组 Vitest）；`packages/shared/src/match.ts` 仅追加 `MatchStatusUpdate`、`CancellationResult`。
- 种子：`test-data.sql` 追加可重复执行的 B4 区块（外挂 1016 登录密码、1005/1002 的候补 1011、1002 状态与报名复位、外挂类型复位）。

验证：

- `pnpm -r test`：web 15 文件 / 166 用例通过。
- `pnpm -r typecheck`、`pnpm -r build`、`pnpm lint`、本票文件 `prettier --check` 全绿。
- 重放 `scripts/sql/test-data.sql`（本地 dev 库，用户已确认）后 `pnpm test:e2e`：75 passed（web 52 + admin 23），无 console error。
- 首轮 `/code-review` 的问题已修复：邀请入口仅 CONFIRMED、邀请在途禁止关闭、复制状态一致、静默刷新加载态收尾、名单快照按用户隔离且仅全队可见者消费、递补人名以 `promotedPlayerId` 为准、签到响应先回填、B03 链接加载完成后再显示、ATT-03 并行、冲突码命名、Prettier 格式化。

已知限制 / 后续项：

- `RegistrationsPage` 当前不读取 `?match=`，C03 链接已带 `match` 但 B03 需手动选场；B03 不在本票 Owned files，未修改。
- `signupState` 与 `homeCta`、上海时间转换、报名状态文案存在重复，涉及 B3/admin owned 文件，留待后续收敛。
- 未执行生产 SQL、未 push。
