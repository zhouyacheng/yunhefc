# 05 深化两个表单 sheet 的接线

Status: needs-triage
Blocked by: none

## 目标

把事件/状态 sheet 里真正易错的接线移入纯 module；sheet 退化为 adapter，只绑定事件与渲染。

## 现状与摩擦

- `utils/event-form.ts`、`utils/match-status.ts` 的纯规则有测试，且 earns its keep。
- 但 `components/event-form-sheet/index.ts` 仍自己维护 picker index ↔ playerId 映射、选项重建、`version` 回退；`components/status-sheet/index.ts` 的 `splitLocal` 缺省回退当前时间。
- 24 个测试文件全部只 import `utils/`，没有测试跨到这些真实 bug 面。

## 深化方向

- 一个 form-state module：由表单状态与球员候选生成选项、索引与提交 payload；版本回退与默认时间也在这里。
- 两个 sheet 只保留渲染、事件转发与 `triggerEvent`。
- interface 先设计再实现；避免把 WeChat 事件对象传入 module。

## 收益

- 测试命中 picker 映射与 version 回退。
- sheet 退化为 adapter。
- 选项重建一处，两个表单共用。
- locality：表单 bug 集中。

## 范围

- 改 `components/event-form-sheet/index.ts`、`components/status-sheet/index.ts`、`utils/event-form.ts`、`utils/match-status.ts` 与测试。
- 保持现有表单字段、校验文案与提交流程不变。

## 不做

- 不改 EVT/MAT 接口契约。
- 不改弹层视觉。
- 不引入组件测试框架；纯 module 测试即可。

## 验收

- [ ] picker 选项重建、index ↔ playerId、version 回退有 module 级测试。
- [ ] 状态 sheet 的默认时间回退有测试。
- [ ] sheet 内不再包含可由纯 module 承担的规则。
- [ ] 现有事件录入/编辑、延期/取消表单行为不变。
- [ ] `typecheck`、`test`、`pnpm lint` 通过。

## 风险

- WeChat 自定义组件的数据观测顺序（`show, editing`）不能改，否则会重置正在编辑的表单；只迁移纯计算。

## Comments

- 2026-11-20 架构扫描开票（Worth exploring）。
