# 03 四个 loader 收进一个 guarded-load module

Status: needs-triage
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

- [ ] 四个 loader 不再各自实现 seq/loading/error/finally。
- [ ] 新增测试覆盖迟到响应丢弃、错误回退、loading 复位。
- [ ] 阵容主客快速切换仍不会被乱序响应覆盖。
- [ ] 页面 data 字段名与 WXML 绑定不变。
- [ ] `typecheck`、`test`、`pnpm lint` 通过。

## 风险

- `loadGrouping` 被 `ensureEventGrouping` 在开弹层前等待；迁移时保留可等待语义，不要改成 fire-and-forget。

## Comments

- 2026-11-20 架构扫描开票（Worth exploring）。
