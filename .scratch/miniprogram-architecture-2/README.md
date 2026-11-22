# 小程序端架构深化候选（批次 2）

2026-11-22 架构扫描结果。来源：`/grill-with-docs` 会话 + 对 `frontend/apps/miniprogram` 的全量扫描（页面、utils、测试、跨页状态）。

## 事实源

- `CONTEXT.md`（分页 Feed module、guarded-load module、跨页 invalidation module、写操作 outcome）
- 第一轮架构深化 `.scratch/miniprogram-architecture/`（01–06 已闭环）
- 复审跟进 `.scratch/review-followups/miniprogram-c-end-parity.md`
- `frontend/apps/miniprogram` 当前实现

## 候选与票序

| 票 | 文件 | 候选 | 强度 | 主要文件 |
|---|---|---|---|---|
| 01 | `issues/01-match-query-seam.md` | 比赛筛选模型与传输门面依赖反转 | Strong | `utils/schedule-query.ts`、`utils/api-client.ts` |
| 02 | `issues/02-seam-hygiene.md` | seam 卫生包：死资源与错误文案重复 | Medium | `utils/invalidation.ts`、`pages/{login,phone-bind,profile-edit}/index.ts` |
| 03 | `issues/03-guarded-load-adoption.md` | home/team 迁入 guarded-load，扩展静默语义 | Strong | `utils/guarded-load.ts`、`pages/{home,team}/index.ts` |
| 04 | `issues/04-registration-writes.md` | 报名/取消写流程收成共享 module | Strong | `pages/match-detail/actions.ts`、`pages/{home,profile}/index.ts` |
| 05 | `issues/05-match-detail-state.md` | 详情页派生状态装配外提 | Worth exploring | `pages/match-detail/index.ts` |

## 依赖与并行

```
01 ─┐
02 ─┴─ 可并行开工
03 ──→ 04 ──→ 05
```

- 03/04 都改 `pages/home/index.ts`；04/05 都改 `pages/match-detail/index.ts`，必须串行。
- 01 碰 `api-client` 与 `schedule-query`；02 碰登录/绑手机/编辑资料；与 03–05 无文件重叠。
- 04 落地前不要动详情页写操作；05 落地前不要动详情页派生逻辑。

## 约束

- 行为保持不变；这是结构深化，不是功能批次。
- 不改后端、API 契约、DB、权限码、错误码。
- 不为拆页面改 WXML；确需改动时必须有开发者工具/真机走查作证。
- 不引 `@yunhefc/*`；小程序继续本地移植纯函数。
- 每个 module 的 interface 先设计再实现；测试只跨 interface，不测页面内部。
- 实施使用 TDD；每票跑 `pnpm --filter @yunhefc/miniprogram typecheck`、`test` 与 workspace `pnpm lint`。
- 涉及页面编排的票在微信开发者工具手点关键路径；整批完成后真机走一遍报名/取消/详情四个 tab。
- 实施时机：qua→main 发布与生产 RC 之后。票可以提前写，代码不提前改。

## 不在本批

- `pages/spike`：硬编码 `localhost`、绕过 client/session，且仍注册在 `app.json:11`。属发布卫生，单独开票。
- C6 下拉刷新/`paged-feed.onChange` 接线去重：各页刷新语义（静默、保留内容、卸载守卫）不一致，抽 helper 的收益低于间接层成本。
- C7 定时器生命周期统一：home 30s、详情 30s、角标 60s、倒计时 1s 语义差异大，通用 poller 只会增加跳转。
- lint 工具链：workspace `pnpm lint` 已覆盖 `apps/miniprogram/**/*.ts` 与 spike 的 `.js`，无需新增。

## 完成标准

- 候选对应的重复实现只剩一处，或浅 seam 被删除。
- 新增测试锁定迁移前行为；`01`、`03`、`04`、`05` 完成后原有测试保持通过；`02` 删除 2 个失去意义的 invalidation 隔离用例，需在票内说明。
- 每票验收项全部勾选，`## Comments` 记录实施日期、验证命令与走查结论。
- 整批完成后：页面与组件中不再出现 `requestSeq`、`signupMatch`/`cancelMyRegistration` 的直接调用，以及 `(error as Error).message`。
