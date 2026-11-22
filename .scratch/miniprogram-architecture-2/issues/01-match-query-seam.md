# 01 比赛筛选模型与传输门面的依赖反转

Status: open
Blocked by: none

## 目标

`api-client` 不再依赖页面筛选表单模型；筛选串编码只有一份实现，依赖方向为 `api-client → match-query ← schedule-query`。

## 现状与摩擦

- `utils/schedule-query.ts` 同时承担三件事：筛选表单模型（`ScheduleForm` ↔ `ScheduleFilters`）、非法输入归一化（`normalize`，91-113）、请求串编码（`scheduleFeedKey`，142-160）。
- `utils/api-client.ts:38` 直接 import `DEFAULT_PAGE_SIZE`、`scheduleFeedKey`、`ScheduleFilters`；`listMatches`（151-161）用 `scheduleFeedKey` 拼 query。
- 结果：传输门面依赖 UI 表单模型。新增列表筛选要同时改两处；`scheduleFeedKey` 名义是 feed key、实际又被当作 query suffix 使用，语义重叠。

## 深化方向

新建中立 module `utils/match-query.ts`：

- 迁入 `ScheduleFilters`、`DEFAULT_PAGE_SIZE`。
- 迁入 `normalize`（保持校验规则与互斥逻辑逐字不变）。
- 迁入编码实现，命名为 `filterQueryString(filters)`（输出必须与现有 `scheduleFeedKey` 逐字节一致）。

`schedule-query.ts` 只保留表单转换（`toScheduleForm`/`fromScheduleForm`/`sameForm`）、选项与 `normalize` 的调用方；`scheduleFeedKey` 改为调用 `filterQueryString` 的薄包装（或直接 re-export），`ScheduleFilters` 从 `match-query` re-export 给现有调用方。

`api-client.ts` 改为只从 `match-query` 引入。

## 删除测试

不能删除 `schedule-query`：表单模型仍然真实存在。本票修的是依赖方向，不是消灭 module。若 `match-query` 落地后 `schedule-query` 只剩 3 个函数与选项表，说明它已退化为薄适配层——可接受，但不再承担编码职责。

## 收益

- leverage：一个编码实现同时服务列表请求与 PagedFeed key。
- locality：筛选归一化/编码与 UI 表单分离，测试可只测纯函数。
- 方向：`api-client` 不再知道“表单”这一概念。

## 范围

- 新增 `utils/match-query.ts`；改 `utils/api-client.ts`、`utils/schedule-query.ts`。
- 新增 `tests/match-query.test.ts`；`tests/schedule-query.test.ts` 与 `tests/api-client.test.ts` 按新边界调整。
- `pages/schedule/index.ts` 的 import 可保持不变（经 re-export）。

## 不做

- 不改筛选 API 参数、默认值、编码顺序或当前上海月份的默认逻辑。
- 不改 `paged-feed`。
- 不引入通用 query builder。

## 验收

- [x] `grep "from './schedule-query'" utils/api-client.ts` 无结果。
- [x] `api-client` 只 import `match-query` 的筛选类型与编码函数。
- [x] `filterQueryString` 与旧 `scheduleFeedKey` 对同一输入的输出逐字节一致（用现有用例锁住）。
- [x] `schedule-query` 的归一化用例（非法日期、月份与区间互斥、venue trim、编码）全部保留并通过。
- [x] `typecheck`、`test`、workspace `pnpm lint` 通过。
- [ ] 开发者工具手点：比赛页筛选（类型/状态/场地/月份/日期区间）切换后列表与分页不回归。

## 风险

- `scheduleFeedKey` 同时是 PagedFeed 的 key：输出顺序或编码变化会让 feed 误判为新 key 并重置列表。迁移时不要“顺手”重排字段或改变 `encodeURIComponent` 的位置。
- re-export 会让类型有两个 import 路径；在注释里标明 `match-query` 是唯一事实源，`schedule-query` 的 re-export 只为兼容现有调用方。

## Comments

- 2026-11-22 架构扫描开票（Strong）。
- 2026-11-22 实施：新增 `utils/match-query.ts`（`ScheduleFilters`、`DEFAULT_PAGE_SIZE`、`normalize`、`filterQueryString`）；`schedule-query` 只保留表单转换/选项并 re-export 类型与常量，`scheduleFeedKey = filterQueryString`；`api-client` 改依赖 `match-query`。新增 `tests/match-query.test.ts`。验证：`pnpm --filter @yunhefc/miniprogram typecheck`、`test`（29 文件 / 284 用例）、`pnpm exec eslint apps/miniprogram`、`prettier --check` 通过。开发者工具筛选手点待做。
