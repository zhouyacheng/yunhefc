# 02 — C01 首页与报名 CTA

Type: implementation
Status: resolved
Blocked by: 01 — 四 Tab 壳层、错误页与公共基座

**What to build:** 访客和球员打开 `/` 时看到下一场比赛 Hero、近期 3 场赛程与按身份/状态分支的报名 CTA；未满时可直接报名，满额时进候补或看到明确拒绝原因；没有待进行比赛时显示空态。

## Scope

- C01 首页：Hero（类型、状态、主客队、时间、场地、场次编号、报名队列）、近期 3 场、球队火力二期占位、空态。
- 报名 CTA 状态机与 `REG-01` 直接报名；登录引导、待审核提示、已报名查看详情、截止/锁定/进行中/结束/延期/取消的禁用原因。
- 加载中、失败重试、空态；报名成功后刷新 Hero 与列表。
- 执行时在 `docs/design/06.设计方案评审结果.md` 追加实现记录：C01 用 MAT-02 取列表 + MAT-03 取 Hero 详情，接口数不变。

## Owned files

- `frontend/apps/web/src/pages/HomePage.vue`
- `frontend/apps/web/src/features/home/cta.ts`
- `frontend/apps/web/src/features/home/cta.test.ts`
- `frontend/e2e/web-home.spec.ts`
- `docs/design/06.设计方案评审结果.md`（仅追加实现记录）

## API dependencies

- `MAT-02 GET /c/matches?scope=upcoming&sort=startAt:asc&pageSize=3`：Hero 候选与近期赛程。
- `MAT-03 GET /c/matches/{id}`：Hero 的 `capacity/guestCapacity/waitlistCapacity/signupDeadline/myRegistration`。
- `REG-01 POST /c/matches/{id}/registrations` + `Idempotency-Key`：直接报名。
- `AUTH-10` 经会话读取身份、账号状态、球员类型。

## UI states

- 加载中：骨架或 Loading。
- 失败：错误文案 + 重试。
- 空：无 upcoming → 「暂无待进行比赛」+ 去赛程；不回退历史。
- Hero 正常：信息 + 队列 + CTA。
- 近期赛程：卡片进入 `/matches/{id}`。
- 火力：标题 + 「二期上线」，不请求数据。

## Implementation notes

CTA 为纯函数，输入 `authenticated`、`user.status`、`player.type`、`match.status`、`now`、`signupDeadline`、`capacity/guestCapacity/waitlistCapacity`、`confirmedCount/guestCount/waitlistCount`、`myRegistration`，输出 `{ label, action, disabled, reason }`：

1. 访客 → 「登录后报名」→ `/login?redirect=/`。
2. PENDING（`user.status=0`）→ 禁用「账号待审核，通过后可报名」。
3. 已报名（`myRegistration.status=0` 或 `1`）→ 「查看详情」+ 状态文案。
4. `status=0` 且未报名：
   - `now >= signupDeadline` → 禁用「报名已截止」；
   - 正式球员（`player.type=1`）且 `confirmedCount >= capacity`：
     - 候补未满 → 「加入候补」；
     - 候补已满 → 禁用「名额与候补已满」；
   - 外挂（`player.type=0`）且 `guestCount >= guestCapacity` → 禁用「外挂名额已满」；
   - 其他 → 「报名」。
5. `status=1` → 禁用「报名已截止」；`status=2` → 「报名已锁定」；`status=3` → 「比赛已开始」；`status=4` → 「比赛已结束」；`status=5` → 「比赛已延期」；`status=6` → 「比赛已取消」。

其他：

- `scope=upcoming` 由后端定义为未结束且未取消，含进行中与已延期；Hero 取第 1 条，不自行过滤。
- 报名成功后重新拉取 MAT-02 + MAT-03；失败按 40902/40903/40905/42901 展示真实 message，不吞错。
- 幂等键每次用户点击生成；重复点击进行中禁用按钮。
- 登录态判定只用 `session`；访客不调用 `/c/me`（票 01 已保证）。
- 不新增聚合接口；不展示事件统计。

## Test obligations

- Vitest `cta.test.ts`：覆盖访客、PENDING、已报名、正式未满、正式满可候补、正式与候补全满、外挂满、截止、锁定、进行中、结束、延期、取消；断言 label/disabled/action。
- Playwright `web-home.spec.ts`：
  - 访客首页看到 Hero，点击 CTA 进入 `/login` 且带 `redirect=/`；
  - 1002 登录后在 1005 报名，CTA 变为「查看详情」，队列计数更新；
  - 加载失败分支可重试（可用不存在场景或不强制）；
  - 无 console error。
- 文档：`06` 实现记录存在，说明 MAT-02 + MAT-03 取数。

## Cross-ticket prerequisites

票 01 的路由、会话、种子（1005 与 1002 已有可报名状态）与 E2E helper。

## Result

- 交付：`0c3969b`（C01 首页 Hero、近期 3 场、火力占位、CTA 状态机、REG-01 报名、E2E、06 §60；另修复 AppShell Tab 激活与导航——Vant `route` 模式在子路由下会把「首页」判为激活）。
- 验证：`vue-tsc`、`eslint`、改动文件 Prettier 通过；`pnpm -r test` 全绿（web 23 项，其中 `cta.test.ts` 17 项）；Playwright `--project=web` 15 条全绿（2026-11-12，含 `web-home.spec.ts` 4 条），有 console error 断言；E2E 前重置 `test-data.sql`。
- 未执行：后端/schema/Flyway/DDL 改动；真实设备与生产验收。
