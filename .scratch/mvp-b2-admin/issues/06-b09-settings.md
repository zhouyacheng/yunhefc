# 06 — B09 系统设置

Type: implementation
Status: resolved
Blocked by: 无

**What to build:** 按 02 §6.11 交付单表单系统设置页：9 项配置同页展示，一次 PATCH 保存；前端校验与服务端 40001 一致。

## Scope

- 路由：`sectionComponent('settings')` 指向新 `SettingsPage.vue`。
- 入口 `settings:view:any`；保存按钮需 `settings:manage:any`，否则只读禁用并提示。
- SET-01 读取 `{settings}` 9 键；SET-02 一次 PATCH 全部 9 键 + `Idempotency-Key`。
- 两张卡片：报名默认值（`maxPlayers` 1–99、`maxGuests` 0–99、`maxWaitlist` 0–99）；分组参数（`groupingManualWeight`/`groupingAutoWeight` 各 0–100 且合计=100；`groupingAppearanceWeight`/`groupingGoalWeight`/`groupingAssistWeight` 各 0–100 且合计>0；`groupingMinAppearances` 0–99）。
- 9 项均为整数，使用 `el-input-number`；保存前前端校验，非法不发请求并展示字段级提示。
- 保存成功后用响应刷新表单并提示「设置已保存」；加载失败可重试；空/缺失键由后端回退默认值。
- 页面说明生效边界：只影响新建比赛与后续分组，不回写已建比赛与已保存分组。

## Owned files

- `frontend/apps/admin/src/router/index.ts`
- `frontend/apps/admin/src/pages/SettingsPage.vue`
- `frontend/packages/shared/src/settings.ts`
- `frontend/packages/shared/src/index.ts`
- `frontend/e2e/admin.spec.ts`

## API

- SET-01 `GET /b/settings`
- SET-02 `PATCH /b/settings`

## Test obligations

- Vitest（shared）：每个字段上下界、主权重合计、自动三项合计、非整数。
- Playwright：加载 → 修改报名上限与权重 → 保存 → 刷新仍生效；权重合计非法时提示且不发 PATCH。
- `pnpm -r test`、`pnpm -r typecheck`、`pnpm -r build`。

## Result

- 交付：`SettingsPage.vue`（SET-01 加载、两张卡片 9 项 `el-input-number`、SET-02 一次 PATCH 全 9 键 + `Idempotency-Key`、响应刷新表单、加载失败重试、`settings:manage:any` 缺失时只读禁用并提示、生效边界说明）；路由 `settings` 指向新页；shared 新增 `settings.ts`（`SystemSettings`/`SettingsForm`、字段规则、合计校验、`toSettingsPayload`）并导出。
- 决策：`SettingsFieldRule` 同时保存展示标签与 `messageLabel`，后者与后端 `SettingsApplicationService.requireRange` 标签逐字对齐，保证字段级提示与 40001 文案一致；缺失键回退默认值完全由后端负责，前端不重复维护默认值；合计错误挂到参与字段，便于逐项标红。
- 测试：shared 新增系统设置用例（9 键分组、非权重字段上下界、主权重合计=100、自动三项合计>0、9 字段空值/小数、payload 构造）；Playwright 新增 B09 保存后刷新仍生效、权重合计非法时不发 PATCH 两条。
- 验证：`pnpm -r test`（shared 37、permissions 9、api-client 21、web 93）、`pnpm -r typecheck`、`pnpm -r build`、`pnpm lint`、B09 Playwright 2/2 通过。
- 全量 Playwright：admin 19/20，仅既有「B02 只读权限」因多轮 E2E 累积比赛把「橙队VS兰队」挤出第 1 页而失败；web 套件存在既有测试数据漂移（比赛 10 张而非 5 张、首页队列已有报名），均与本票无关。
- 未执行：无后端/DB/Flyway 改动；未执行 SQL；未 push。
