# 03 home / team 迁入 guarded-load，扩展静默语义

Status: open
Blocked by: none

## 目标

首页与球队页共用 `guarded-load` 的代次守卫；两页删除手写 `requestSeq` 与 try/catch，同时逐字保留现有失败语义（静默刷新只 toast、统计迟到降级）。

## 现状与摩擦

- `utils/guarded-load.ts` 已提供按 key 的代次守卫，但失败路径固定写 `fallback` 与 `${key}Error`，无法表达两种真实需求：
  - `pages/home/index.ts:82-126`：静默刷新失败时，若已有内容则只 toast，不清空 hero/recent。
  - `pages/team/index.ts:65-94`：统计允许迟到，失败时降级为「—」，不阻断名单。
- 因此 `home`（17、83、89、102、114）与 `team`（9、66、75、80、85）各自手写 `requestSeq`。
- `team` 的 `requestSeq` 是 module 级变量且无 `onUnload` 复位，页面实例重建后可能残留。

## 深化方向

先扩 `GuardedLoadSpec`（interface 先行，用测试锁住）：

```ts
export interface GuardedLoadSpec<T> {
  key: string;
  fetch: () => Promise<T>;
  map: (value: T) => Record<string, unknown>;
  fallback?: Record<string, unknown>;
  begin?: Record<string, unknown>;
  errorText: string;
  after?: () => void;
  /** 静默：开始时不写 loading/error，也不写 begin。 */
  silent?: boolean;
  /** 失败时保留页面已有数据：不写 fallback，也不写 error 字段。 */
  keepOnError?: boolean;
  /** 失败且响应仍为最新时回调，页面据此 toast 或做空态处理。 */
  onError?: (message: string) => void;
}
```

迁移：

- `home`：一个 `fetch` 内先 `listRecentMatches()`、取首条再 `fetchMatchDetail(id)`，返回 `{ page, detail }` 一次性 `map`；`silent: true` + `keepOnError: true` + `onError`（仅 `hasLoaded` 时 toast）对齐现有行为。
- `team`：拆两个 key——`players`（`listTeamPlayers`）与 `stats`（`fetchTeamStats`，`silent: true` + `keepOnError: true`）；`players.after` 触发 `stats` 并在成功时二次 `setData` 合并。
- 两页删除 `requestSeq`、手写 catch 与 `hasLoaded`/timer 之外的临时变量；`onUnload`/`onHide` 继续调用 `invalidateAll()`。

## 删除测试

如果删掉 `guarded-load`，两页会回到各自实现代次守卫——正是本票要消除的重复。module earns its keep，问题是它不够深（表达不了静默/保留），扩展后成为唯一加载 seam。

## 收益

- leverage：一个 loader interface 覆盖首页、球队、详情页。
- locality：代次比较、loading/error 复位只在一处。
- 测试：扩展语义可在 `guarded-load.test.ts` 内穷举，不依赖页面。

## 范围

- `utils/guarded-load.ts`、`tests/guarded-load.test.ts`。
- `pages/home/index.ts`、`pages/team/index.ts`。

## 不做

- 不迁移 `profile`/`schedule`（已由 `paged-feed` 覆盖）。
- 不改下拉刷新包装（`onPullDownRefresh` 里调用 loader 的写法保留）。
- 不改首页 30s CTA 定时器与球队页赛季选择逻辑。
- 不统一计时器（另案，见批次 README 的“不在本批”）。

## 验收

- [ ] `home`/`team` 不再出现 `requestSeq`。
- [ ] `guarded-load.test.ts` 新增用例：`silent` 不写 loading/begin；`keepOnError` 不写 fallback 与 error 字段；`onError` 收到 `errorMessage` 结果；迟到响应仍被丢弃；`after` 仅在最新响应后执行。
- [ ] 首页：首次加载失败显示整页错误；已有内容时静默刷新失败只 toast 且内容保留；空列表仍正确清空 hero/recent。
- [ ] 球队页：名单失败显示整页错误并清空分组；统计失败时名单照常渲染、统计列为「—」；快速切赛季不乱序。
- [ ] 页面实例重建后不再残留跨实例守卫状态。
- [ ] `typecheck`、`test`、workspace `pnpm lint` 通过。
- [ ] 开发者工具手点：首页下拉刷新（断网/恢复）、球队页切赛季（统计接口失败）。

## 风险

- `guarded-load` 的 `finally` 目前只在“仍为最新”时写 `loading=false`；扩展后要保持这一条件，否则旧请求会提前关掉新请求的 loading。
- 首页把两次请求合成一个 `fetch` 后，第一次请求成功、第二次失败会整体走失败路径；现有实现同样如此（102-104 后进入 catch），行为一致，不要顺手改成部分渲染。
- `keepOnError` 与 `fallback` 同时传入属于调用方错误；interface 注释需写明优先级。

## Comments

- 2026-11-22 架构扫描开票（Strong）。
