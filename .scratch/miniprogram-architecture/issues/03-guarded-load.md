# 03 四个 loader 收进一个 guarded-load module

Status: resolved
Blocked by: none

## 目标

把“迟到响应丢弃 + loading/error/finally + 空态回退”收进一个 guarded-load module；详情页四个 loader 只提供 fetcher、映射与空态。

## 现状与摩擦

- `pages/match-detail/index.ts` 的 `loadRoster`、`loadEvents`、`loadLineup`、`loadGrouping` 各写一遍 seq 比对、loading/error 重置、finally 收尾与空态回退。
- `loadLineup` 还额外校验 `lineupSide`，`loadEvents` 有独立的错误文案。
- stale-response 不变量没有单一 seam，也没有集成测试；改一处容易漏掉另一处。

## 深化方向

- 一个 guarded-load module：调用方给 key、fetcher、成功映射与空态回退；module 负责代次、loading、error、finally。
- 先设计 interface，再迁移四个 loader；尽量保持页面 data 字段名不变。
- 30 秒 `syncWindowState` 定时器只重算窗口状态，不触发请求。

## 删除测试

删掉 module 后，代次守卫会回到四个 loader 并继续重复——复杂度集中而不是消失，说明这个 seam 值得存在。

## 收益

- 不变量只测一次。
- 新 tab 直接复用 interface。
- loader 退化为 fetcher + 映射。
- locality：竞态修复集中在一处。

## 范围

- 改 `pages/match-detail/index.ts` 与新增 module、对应测试。
- 保留现有错误文案、空态与 loading 标记。

## 不做

- 不改请求函数与响应类型。
- 不改 tab 可见性或权限判断。
- 不引入页面级状态管理库。

## 验收

- [x] 四个 loader 不再各自实现 seq/loading/error/finally。
- [x] 新增测试覆盖迟到响应丢弃、错误回退、loading 复位。
- [x] 阵容主客快速切换仍不会被乱序响应覆盖。
- [x] 页面 data 字段名与 WXML 绑定不变。
- [x] `typecheck`、`test`、`pnpm lint` 通过。

## 风险

- `loadGrouping` 被 `ensureEventGrouping` 在开弹层前等待；迁移时保留可等待语义，不要改成 fire-and-forget。

## Comments

- 2026-11-20 架构扫描开票（Worth exploring）。
- 2026-11-20 实施完成（TDD）：
  - 新增 `utils/guarded-load.ts`：`createGuardedLoads(setData)` 按 `key` 维护代次；`run({ key, fetch, map, fallback, begin, errorText, after })` 负责开跑时的 `loading`/`error` 重置、迟到响应丢弃、失败空态回退与 `finally` 的 loading 复位。`key` 决定写入 `${key}Loading`/`${key}Error`，与页面既有字段名一致。
  - `pages/match-detail/index.ts` 四个 loader 退化为 fetcher + 映射：`loadRoster` 的三种来源（空态/本人视角/完整名单）收进 `fetch` 返回的 `RosterLoad`，`eventPlayers` 去重抽为 `eventPlayersOf`；`loadLineup` 的主客切换由代次取代 `lineupSide` 比对（`lineupSide` 改由 `begin` 与 loading 同一次写入）。
  - `load()` 开始与 `onUnload` 调用 `invalidateAll()`，保持“重载/卸载后旧响应不落地”的既有语义；页面 `seq` 只再用于详情请求自身的代次。`loadGrouping` 仍返回 Promise，`ensureEventGrouping` 继续 await。
  - `actions.ts` 的 `MatchActionPage` 去掉不再使用的 `seq`，`loadRoster/loadGrouping/loadEvents` 改为无参。
  - 新增 `tests/guarded-load.test.ts`（10 例）：loading/映射/复位顺序、`begin` 合并、失败回退与默认文案、逐 key 空态回退、迟到响应丢弃、阵容主客乱序、旧请求不提前复位 loading、`invalidateAll`、`after` 只对最新响应执行。
  - 验证：`pnpm --filter @yunhefc/miniprogram typecheck`、`test`（26 文件 / 224 用例）、`pnpm lint`、`prettier --check`（改动文件）通过；开发者工具/真机走查见下。
- 2026-11-21 code-review（Standards / Spec 两轴）：
  - 已改：`EMPTY_ROSTER_VIEW` 统一 data 初始化、重载与 404 三处旧字面量；页面字段改名 `loadRegistry`；`frontend/apps/miniprogram/README.md` 的 utils 清单补 `guarded-load`；补两例测试（阵容主客乱序 + `begin.lineupSide`、逐 key 空态回退）；`invalidateAll` 注明“只让在途过期、不写 loading”的契约。
  - 保留：`key: string` 而非字面量联合——module 是通用接口，票面要求新 tab 直接复用；代次实现与 `paged-feed` 保持各自独立——后者的代次与分页 state 绑定，抽取共用代价大于收益。
  - 行为差异（有意，README“行为保持不变”的唯一例外）：同一 key 的并发运行现在一律 `last-request-wins`；旧代码只在阵容主客不同时丢弃旧响应，同目标重复重试是 `last-settled-wins`。仅影响双击重试等罕见竞态，且与“迟到响应丢弃”不变量一致。
  - `actions.ts` 的 `MatchActionPage` 去 `seq` 是接口改动的必要涟漪，未触碰 ticket 04 的 actions 深化。
  - `CONTEXT.md` 新增 guarded-load 术语，按批次 README 的“事实源”约定登记。
- 2026-11-21 人工走查（开发者工具/真机）：清单 8 项全部通过，票 03 收口。

## 走查清单（开发者工具/真机，2026-11-21 人工走查通过）

本机 WeChat DevTools 服务端口未开启（`cli islogin` 报“服务端口已关闭”），无法自动化驱动 GUI，以下由人工执行；2026-11-21 用户人工走查，8 项全部通过。

1. 冷启动进入任意比赛详情：概览与事件内容正常，名单/事件 loading 各自出现后消失。
2. 下拉刷新：数据更新，名单/事件/阵容/分组 loading 全部复位，无残留“加载中”。
3. 错误路径（断网或后端 5xx）：名单、事件、阵容、分组分别显示原文案（名单/事件/阵容/分组加载失败，请稍后重试），loading 复位，点重试可恢复。
4. 阵容 tab：在主/客之间快速切换 5 次，最终阵容与最后选中一侧一致，无旧响应覆盖。
5. 分组 tab：管理员点“录入事件”，弹层内分组已就绪（`ensureEventGrouping` await 生效）。
6. 详情加载中直接返回：控制台无 `setData` 页面已卸载类告警。
7. 不存在的比赛 id：显示未找到态。
8. 全程控制台无 error。
