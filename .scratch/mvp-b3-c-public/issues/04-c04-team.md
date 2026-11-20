# 04 — C04 球队页

Type: implementation
Status: resolved
Blocked by: 01 — 四 Tab 壳层、错误页与公共基座

**What to build:** 访客在 `/team` 按前锋/中场/后卫/门将查看球队名单，每组默认 10 人、可展开看到全部；每名球员显示头像、姓名、号码、位置、星级、地区与本赛季出场/进球/助攻。

## Scope

- 替换 `TeamPage.vue` stub：球队名称、四个位置组、球员卡、组内展开/收起、空态。
- `PLR-01` 与 `STA-01` 的合并逻辑与测试。
- 头像占位与签名 URL 失败降级。

## Owned files

- `frontend/apps/web/src/pages/TeamPage.vue`
- `frontend/apps/web/src/features/team/merge-stats.ts`
- `frontend/apps/web/src/features/team/merge-stats.test.ts`
- `frontend/e2e/web-team.spec.ts`

## API dependencies

- `PLR-01 GET /c/team/players?limit=10`：首次加载；`all=true` 首次展开时获取完整名单。
- `STA-01 GET /c/team/stats?season=`：按 `playerId` 合并出场/进球/助攻。
- 两者均为公开接口。

## UI states

- 加载中：Loading。
- 失败：错误文案 + 重试。
- 空：无球员时 Empty。
- 每组：标题（位置 + 人数）、球员卡、组内「展开更多 / 收起」。
- 统计缺失：出场用 PLR-01 的 `appearances`，进球/助攻显示「—」。
- 头像为空或非 http(s)：姓名首字占位。

## Implementation notes

- `PLR-01` 返回**扁平** `items`，已按 前锋→中场→后卫→门将 排序，并在每组内按 出场降序 → 号码升序；`total` 是全队人数，不是当前返回条数。前端按 `position` 自行分组，不得重排组内顺序。
- 每组默认显示 10 人；当 `items.length < total` 时显示展开入口。首次展开请求 `all=true` 并缓存完整 `items`，之后组内展开/收起只做本地切片，不再请求。
- `STA-01` 的 `items` 按 `playerId` 建 Map；合并时缺失项降级为 `appearances = PLR-01.appearances`、`goals/assists = null`。
- 位置文案使用 `positionLabel`；不在前端硬编码枚举中文，避免与后端不一致。
- 不展示手机号、外挂球员（后端已过滤）、详情页入口、筛选。
- 无新接口；不请求 `PLR-05` 或管理端字段。

## Test obligations

- Vitest `merge-stats.test.ts`：按 `playerId` 合并；缺失统计降级；组顺序稳定；组内顺序不被前端改写。
- Playwright `web-team.spec.ts`：
  - `/team` 显示四个组标题；
  - 中场组默认 10 人，点击「展开更多」后出现种子球员（1030–1035）之一；
  - 球员卡不含手机号样式/文本，外挂球员不出现；
  - 无 console error。
- 若种子不足导致无法区分展开前后，票 01 的 1030–1035 必须存在。

## Cross-ticket prerequisites

票 01 的 `/team` 路由、Tab 与 B3 种子（1030–1035 让中场组超过 10 人）。

## Result

- 交付：`a0caa14`（`TeamPage` 替换 stub：四位置组、头像/首字占位、号码/位置/地区/星级/出场进球助攻、组内 10 人预览与展开收起、加载/失败/空态；`merge-stats.ts` 合并 STA-01、固定组顺序、预览切片、展开判定、头像 URL 与首字纯函数；`web-team.spec.ts` 3 条）。
- 验证：`pnpm -r typecheck`、`pnpm -r test`（web 9 文件 78 项，其中 team 11 项）、`pnpm lint`、改动文件 Prettier 通过；Playwright `web-team.spec.ts` 3/3 通过（2026-11-12）；首次展开断言请求 `limit=10` → `all=true`，收起不再请求；统计延迟 1.5s 不阻塞名单、失败降级为「—」、脱敏手机号不外泄。
- 评审处理：统计请求改为不阻塞首屏；公开请求传 `redirectOnUnauthorized:false`；组人数预览期显示「10+」；展开失败文案就近显示；加载态只作用于被点击组；头像失败在重新签发 URL 后重试；`avatarInitial` 按码点切分；复用 `@yunhefc/shared` 的 `PlayerPublic`；星级改为 5 星刻度。
- 偏差：后端 `positionLabel` 为英文枚举名（`FORWARD`/`MIDFIELDER`），页面改用既有共享 `positionText(code)` 输出中文；组人数在 `all=true` 前无法确定，预览期以「10+」表示。
- 未执行：无后端、`schema.sql`、Flyway、Quartz、05/04 契约改动；未改共享类型与种子。全量 web E2E 在本票并入前为 22/30，失败项均属当时并行进行中的 03/05/06 票（登录页、Playwright `test-results` 被并发抢占），与本票无关；本票 3 条在单独运行与全量运行中均绿。E2E 用例中 `出场 1`/`进球 0` 依赖种子固定 `2026` 赛季（后端按当前年份取赛季），2027 年起需同步种子。
