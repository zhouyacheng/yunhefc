# 08 — 评审遗留处理（C01 后续）

Type: implementation
Status: resolved
Blocked by: 02 — C01 首页与报名 CTA（已 resolved）

**What to build:** 处理票 02 `/code-review` 的判断性遗留：抽取首页/赛程共用的 MatchCard 与展示纯函数、AppShell Tab 单表化、首页截止时间与刷新重试、CTA 输入收敛。只改前端，不改接口、DB 或设计契约。

## Scope

- 新建 `features/matches/presentation.ts`：`matchTagType(status)`、`scoreText(score)`；首页、赛程、详情复用。
- 新建 `components/MatchCard.vue`：首页近期赛程与赛程页共用，复用全局 `.match-card*` 样式。
- `AppShell.vue`：`TABS` 单表同时驱动路径、图标、文案、激活与角标；首页精确匹配。
- 首页：30 秒定时刷新 `now`，页面停留跨过截止时间后 CTA 自动禁用；报名后静默刷新失败时提供「重试刷新」。
- CTA：`queue: HomeCtaQueue` 取代六个平铺字段；复用 `isPending`；报名状态使用共享 `REGISTRATION_STATUS`。
- E2E：补首页近期赛程、Tab 激活、报名后刷新失败→重试路径。

## Owned files

- `frontend/apps/web/src/features/matches/presentation.ts`
- `frontend/apps/web/src/features/matches/presentation.test.ts`
- `frontend/apps/web/src/components/MatchCard.vue`
- `frontend/apps/web/src/components/AppShell.vue`
- `frontend/apps/web/src/pages/HomePage.vue`
- `frontend/apps/web/src/pages/SchedulePage.vue`
- `frontend/apps/web/src/pages/MatchDetailPage.vue`
- `frontend/apps/web/src/features/home/cta.ts`
- `frontend/apps/web/src/features/home/cta.test.ts`
- `frontend/packages/shared/src/match.ts`
- `frontend/packages/shared/src/index.test.ts`
- `frontend/e2e/web-home.spec.ts`
- `.scratch/mvp-b3-c-public/README.md`（交付表）

## Non-goals

- 不改 05/04/`schema.sql`/Flyway/Quartz/后端，不新增接口、权限码或错误码。
- 不追加 06 记录：无设计/契约变更。
- 不引入 jsdom、`@vue/test-utils` 或组件单测；MatchCard 渲染由 Playwright 覆盖。
- 不为 `playerType=null` 增加票据未定义的产品分支。

## Implementation notes

- `MatchTagType` 为 `'primary' | 'success' | 'warning' | 'danger'`；`scoreText` 保持 `scoreHome == null → VS`。
- 详情页保留 `displayedScore` 优先，`displayedScoreText()` = 推导/事件比分 → 存储比分 → `VS`。
- `HomeCtaQueue = Pick<MatchDetail, 'capacity' | 'guestCapacity' | 'waitlistCapacity' | 'confirmedCount' | 'guestCount' | 'waitlistCount'>`；HomePage 直接传 `queue: detail`。
- `REGISTRATION_STATUS = { CONFIRMED: 0, WAITLISTED: 1, CANCELLED: 2, REMOVED: 3 }` 放在 `packages/shared/src/match.ts`。
- 定时器用 `ReturnType<typeof setInterval>`，`onUnmounted` 清理。
- `refreshFailed` 只在静默刷新失败时置真；成功加载仅在该标记为真时清 `actionError`，不得吞掉报名业务错误。

## Test obligations

- `presentation.test.ts`：状态 0/1/2/null/undefined → primary，3 → warning，4 → success，5/6 → danger；比分 null/undefined、2:1、2:null、0:0。
- `cta.test.ts`：17 项分支断言保持通过（fixture 改为 `input(overrides, queueOverrides)`）。
- `packages/shared/src/index.test.ts`：`REGISTRATION_STATUS` 映射断言。
- Playwright `web-home.spec.ts`：访客看到 3 张近期卡片且首张跳 `/matches/1005`，Tab 激活正确；报名后静默刷新失败显示「重试刷新」，点击后恢复「查看详情」与 `1 / 30`；无 console error。
- 完整 web E2E 与 `pnpm -r typecheck/test/lint` 通过。

## Cross-ticket prerequisites

票 03（C02 赛程筛选）也拥有 `SchedulePage.vue`。若 03 在 08 之后执行，重写时必须保留 `<MatchCard>` 与 `match-card-link` 外壳。

## Result

- 交付：`050898d`（MatchCard + `presentation` 纯函数、AppShell TABS 单表、首页 30s 截止刷新与刷新失败重试、CTA `queue`/`REGISTRATION_STATUS`；E2E 补近期卡片、Tab 激活与刷新重试）。
- 验证：`pnpm -r typecheck`、`pnpm -r test`（web 26 项、shared 13 项）、`pnpm lint`、改动文件 Prettier 通过；Playwright `--project=web` 15 条全绿（2026-11-12，含 `web-home.spec.ts` 4 条与新增断言）；E2E 前重置 `test-data.sql`。
- 未执行：后端/schema/Flyway/API/06 设计文档改动；未做组件单测（未引入 jsdom/@vue/test-utils）。
