# 02 — CI e2e job 使用 Java 21

Type: implementation
Status: resolved
Blocked by: None

**What to build:** 修复 main 分支 `e2e` job 在 Java 17 上启动 Java 21 boot jar 导致 `UnsupportedClassVersionError`、健康检查 180s 超时的问题。

**验收标准**

- [x] `ci.yml` 的 `e2e` job 在 checkout 后配置 `actions/setup-java@v6`（temurin 21），与 `backend` job 一致。
- [x] 不改动 e2e 的其他步骤与触发条件。
- [ ] 合并到 main 后，`main` 的 e2e job 通过（待合并后验证）。

## Result

- 2026-11-14：已在 `qua` 添加 setup-java 21；PR 合并后由 main push 触发 e2e 验证。
