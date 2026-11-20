# 小程序端架构深化候选

2026-11-20 架构扫描结果。来源：`/improve-codebase-architecture` 对 `frontend/apps/miniprogram` 的抽查（近期热点：MP04、我的、首页、球队、分页 Feed、request/session、两个弹层）。

## 事实源

- `CONTEXT.md`（分页 Feed module 等既有术语）
- `frontend/apps/miniprogram` 当前实现
- 第三轮两轴复审（2026-11-20，固定点 `9b488c8`）的 Standards / Spec 结论
- Web C 端对应页面与样式，仅作行为与视觉口径参考，不作为小程序结构模板

## 候选与票序

| 票 | 文件 | 候选 | 强度 | 主要文件 |
|---|---|---|---|---|
| 01 | `issues/01-registration-eligibility.md` | 报名资格收拢为一个 deep module | Strong | `utils/registration.ts`、`utils/home-cta.ts`、`utils/home-view.ts`、`pages/match-detail/index.ts` |
| 02 | `issues/02-typed-c-client.md` | 让 typed client 成为唯一 seam | Strong | `utils/match-api.ts`、`pages/{home,profile,team,profile-edit}/index.ts` |
| 03 | `issues/03-guarded-load.md` | 四个 loader 收进一个 guarded-load module | Worth exploring | `pages/match-detail/index.ts` |
| 04 | `issues/04-actions-seam.md` | 删除或深化 actions seam | Worth exploring | `pages/match-detail/{index,actions}.ts` |
| 05 | `issues/05-form-sheet-wiring.md` | 深化两个表单 sheet 的接线 | Worth exploring | `components/{event-form-sheet,status-sheet}`、`utils/{event-form,match-status}.ts` |
| 06 | `issues/06-invalidation-seam.md` | 用显式 invalidation 取代隐藏 dirty 布尔 | Speculative | `utils/registration-signal.ts`、`pages/profile/index.ts` |

## 依赖与并行

- 建议顺序：01 → 02 → 03 → 04 → 05 → 06。
- 01 与 04 都改 `pages/match-detail`，不要并行；01 先落地，04 再按新的报名决策形状设计。
- 02 会碰首页、我的、球队、编辑资料，03/04 会碰详情页；同一文件不要同时开工。
- 06 是 Speculative，只有在 01/02 落地后仍觉得跨页失效协议碍事才做。

## 约束

- 行为保持不变；这是结构深化，不是功能批次。
- 不改后端、API 契约、DB、权限码、错误码。
- 不引 `@yunhefc/*`；小程序继续本地移植纯函数。
- 每个 module 的 interface 先设计再实现；测试只跨 interface，不测页面内部。
- 实施使用 TDD；完成后跑 `pnpm --filter @yunhefc/miniprogram typecheck`、`test`、`pnpm lint`。
- 不提交 git；涉及页面编排的改动需开发者工具/真机走查。

## 完成标准

- 候选对应的重复实现只剩一处，或浅 seam 被删除。
- 新增测试锁定迁移前行为，原有 24 文件 / 198 用例保持通过。
- 票内验收项全部勾选，`## Comments` 记录实施日期与验证命令。
