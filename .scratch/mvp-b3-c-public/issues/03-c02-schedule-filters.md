# 03 — C02 赛程完整筛选

Type: implementation
Status: resolved
Blocked by: 01 — 四 Tab 壳层、错误页与公共基座

**What to build:** 访客在 `/matches` 按比赛类型、地点、状态、年份、月份筛选，点搜索生效、点重置清空；筛选与分页写入 URL，刷新或后退可恢复；列表遵守 `Page<T>` 契约并处理加载、失败、空态。

## Scope

- 替换现有仅状态筛选的 `SchedulePage.vue`：筛选区/移动端底部抽屉、搜索结果、分页、空态。
- 查询参数与 URL 双向同步的纯函数与测试。
- 保留现有卡片信息与状态样式；管理员/球员不因本页新增权限判断。

## Owned files

- `frontend/apps/web/src/pages/SchedulePage.vue`
- `frontend/apps/web/src/features/schedule/query.ts`
- `frontend/apps/web/src/features/schedule/query.test.ts`
- `frontend/e2e/web-schedule.spec.ts`

## API dependencies

- `MAT-02 GET /c/matches`：`type, venue, status, season, month(YYYY-MM), page, pageSize`。
- 固定 `scope=all`、`sort=startAt:desc`，不作为用户可见筛选项。
- 后端 `venue` 为模糊匹配，`season` 为精确字符串，`month` 为 `YYYY-MM` 范围。

## UI states

- 加载中：Loading。
- 失败：错误文案 + 重试。
- 空：Empty + 「重置筛选」入口。
- 筛选：类型（0 正式/1 队内/2 训练/3 友谊）、地点（文本）、状态（0–6）、年份、月份；月份与年份互斥。
- 分页：默认 10，可选 20/50/100；筛选/搜索/重置回第 1 页。

## Implementation notes

- `query.ts` 导出解析与序列化：从 `route.query` 读取合法值，非法值忽略；序列化只写非默认值；使用 `router.replace`，避免污染历史。
- 筛选表单在点击「搜索」时才应用；「重置」清空全部条件并立即请求。
- 月份存在时只传 `month`，不传 `season`；月份清空后才使用年份。年份选项可由后端数据或当前年附近生成，默认「全部年份」。
- 请求构建：`scope=all`、`sort=startAt:desc` 固定；`page/pageSize` 始终传；空字符串参数不发送。
- 空态文案与重置入口按 02 §5.2；卡片字段与现状一致（日期/星期、主客队、比分或 VS、类型、地点、场次编号、胜方样式）。
- 移动端筛选使用底部抽屉（Vant `Popup` + 现有控件），不引入新视觉体系。
- 不新增后端参数或聚合接口。

## Test obligations

- Vitest `query.test.ts`：
  - 默认值不写入 URL；
  - 完整参数往返一致；
  - 月份与年份互斥；
  - 非法 type/status/month 被忽略；
  - 筛选变化回到第 1 页。
- Playwright `web-schedule.spec.ts`：
  - 打开 `/matches`，按状态筛选并搜索，列表数量/内容变化且 URL 含 `status`；
  - 刷新后筛选仍生效；后退恢复上一组条件；
  - 重置后 URL 清空并回到全部；
  - 地点无匹配时显示空态与重置入口；
  - 分页切换 pageSize 后回到第 1 页；
  - 无 console error。

## Cross-ticket prerequisites

票 01 的路由、Tab 与种子（4+1 场比赛提供可筛选数据）。

## Result

- 交付：`03c770e`。五类筛选、搜索/重置、URL 双向同步（`router.replace`）、分页与移动端底部抽屉；`features/schedule/query.ts` 纯函数 + `query.test.ts`；`e2e/web-schedule.spec.ts` 8 条。
- 验证：`pnpm -r typecheck/test/build` 全绿（web 74 tests）；改动文件 ESLint/Prettier 通过；`web-schedule.spec.ts` 8/8 通过（2026-11-12，`--trace=off`）。两轴评审后修正：URL 已默认时重置仍清空表单草稿（新增 E2E）；枚举校验改用共享 `MATCH_*_TEXT` 键；序列化/请求共用同一筛选键值；删除因移除 `DropdownMenu` 而孤儿的 `.filters` 样式。
- 说明：`e2e/web.spec.ts` 的「已锁定」断言限定到 `.match-card`，避免匹配新增状态筛选的 `<option>`；E2E 状态筛选使用稳定的 `status=4`，规避「报名中→报名截止」定时流转。经用户确认已重跑 `scripts/sql/test-data.sql` 复位测试状态。
- 未完成：完整 `--project=web` 套件受同一工作区并行会话影响（运行期间其他票新增 `web-auth.spec.ts`/`web-team.spec.ts`，并并发清理 Playwright 产物目录导致 trace 写入 ENOENT）。关闭 trace 后本票 8 条全绿；其余用例未在本会话完整复跑。
- 未执行：后端、`schema.sql`、Flyway、Quartz DDL 改动。
