# 02 请求、会话权限与 API 编排层

Status: ready-for-human
Blocked by: 01

## 目标

让小程序具备 C03 写操作所需的基础能力：自定义请求头、幂等键、权限快照、统一 API 路径与冲突错误码。

## 验收

- [ ] `request()` 支持 `headers` 与 `idempotencyKey`；GET 不带幂等键；Cookie/CSRF/401 行为不变。
- [ ] 新增 `newIdempotencyKey()`，不依赖 `crypto.randomUUID`，长度 ≤64。
- [ ] 新增 `permissions` 工具与权限码常量；session 在 login/bindPhone/restore 保存 `permissions`，401/退出清空；暴露 `hasPermission()`。
- [ ] 新增 `api-errors`：40901/40902/40903/40905/42203 与 `shouldRefreshAfterConflict()`。
- [ ] 新增 `match-api`：MAT-03、REG-04、REG-05+ATT-03（全页）、REG-01/02、ATT-01、MAT-12、EVT-02/03、LIN-01、GRP-01；写函数内部 `ensureCsrf()` + 幂等键。
- [ ] Vitest 覆盖 request header/幂等键、session 权限保存与清理、幂等键格式。

## Comments

- 2026-11-20 实施 P-78。
