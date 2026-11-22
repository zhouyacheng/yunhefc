# 05 详情页派生状态装配外提

Status: open
Blocked by: 04

## 目标

把比赛详情页“详情 + 报名 + 会话 + 时间 → 权限位/窗口状态/报名决策”的纯逻辑抽成可单测 module；页面只应用 patch 与处理事件，`data` 字段名与 WXML 不变。

## 现状与摩擦

`pages/match-detail/index.ts`（919 行、70 个 data key）里混着三类逻辑：

- 权限派生（333-408）：`canX` 一组布尔、管理员/PENDING 分支。
- 窗口状态（537-614，`syncWindowState`）：签到窗口、事件录入/编辑窗口、取消/报名决策，30s 重算一次。
- 事件处理与 UI 编排（633-919）：写操作分发、弹层开关、事件表单、tab 切换。

前两类是纯函数候选，却被写在 Page 对象里，只能靠开发者工具验证；详细页也是后续功能最常落地的地方，页面继续膨胀会推高每次接入成本。

## 深化方向

新建 `utils/match-detail-state.ts`：

```ts
export interface MatchDetailStateInput {
  detail: MatchDetail;
  myRegistration: MyMatchRegistration | null;
  session: {
    authenticated: boolean;
    pending: boolean;
    playerType: number | null;
    permissions: string[];
  };
  now: number;
}

export interface MatchDetailState {
  /** 写入页面 data 的字段；键名与现有 data 完全一致。 */
  patch: Record<string, unknown>;
  /** 页面后续事件需要的派生态（报名决策、取消决策、窗口标志）。 */
  derived: { /* 形状由实现前的 interface 设计确定 */ };
}

export function matchDetailState(input: MatchDetailStateInput): MatchDetailState;
```

规则来源不变：复用现有 `registrationDecision`、`match-window`、`permissions`、`presentation`，module 只做“组装 + 映射”，不重写判定规则。

页面改造：`syncWindowState` 只调用 module 并 `setData(patch)`；权限派生与报名决策不再在页面内联；`applyWriteOutcome`、四个 loader、事件处理、sheet 开关保持原位。

## 删除测试

删除本 module 后，派生逻辑会回到 Page 对象——不可单测、无法在接入新功能时复用。它 earns its keep：输入是四个领域值，输出是页面 patch 与决策。

## 收益

- locality：权限/窗口/决策规则集中，30s 重算与首屏加载走同一条路径。
- 测试：边界（截止时间前后、PENDING、管理员、取消锁）可在 vitest 里穷举。
- 接入成本：详情页新增字段只需扩展 module 的 map，不再往 900 行 Page 里加分支。

## 范围

- 新增 `utils/match-detail-state.ts`、`tests/match-detail-state.test.ts`。
- 改 `pages/match-detail/index.ts`；不改 `.wxml`/`.wxss`。
- 预期页面从 919 行降到约 700 行（以实际迁移为准，不为了行数拆逻辑）。

## 不做

- 不拆四个 loader（已被 `guarded-load` 收拢）。
- 不拆子组件、不改 WXML。
- 不改 30s 定时器节奏。
- 不改事件表单/状态弹层组件。

## 验收

- [ ] `data` 字段名与现有 WXML 绑定逐一对齐，WXML 零改动。
- [ ] 权限矩阵可单测：管理员、PENDING、普通球员 × 报名/取消/签到/录事件/看阵容/看分组。
- [ ] 窗口边界可单测：签到窗口开启前/中/后、事件录入窗口、取消锁。
- [ ] 报名/取消决策与迁移前一致（复用 `registrationDecision`，不复制规则）。
- [ ] `applyWriteOutcome` 与 loader 行为不变；30s 重算后页面表现与现在一致。
- [ ] `typecheck`、`test`、workspace `pnpm lint` 通过。
- [ ] 开发者工具手点：详情页四个 tab、签到窗口倒计时、管理员状态修改、本人事件编辑、取消报名。

## 风险

- 70 个 data key 里有一部分是 loader 写入、一部分是派生写入；迁移时必须先列一张“字段 → 写入方”表，避免把 loader 的字段也塞进 patch 导致互相覆盖。
- `syncWindowState` 依赖 `now`：module 必须接收时间，不要在内部调 `Date.now()`，否则测试不可控。
- 这是一次“行为等价搬运”；任何看似顺手的行为改进（例如统一禁用原因文案）都不属于本票。

## Comments

- 2026-11-22 架构扫描开票（Worth exploring → 定为 5 票之一）。
