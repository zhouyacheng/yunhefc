# MVP 复核阻断项修复

Status: resolved（2026-11-14）

事实源：`aaa382a..HEAD` 两轴复核结果（Standards 4 条硬违规 + Spec B02 默认值缺陷）；用户确认范围「只修阻断项」、Spec 偏差「改 deploy，其余记账」。

## 票序

- `01-blocker-fixes.md` — 全部阻断项，无依赖。
- `02-ci-e2e-java.md` — e2e job 的 JDK 版本，无依赖。

## 约束

- 不新增接口、权限码、错误码；不改 05/04 契约与 27 张业务表 DDL。
- 日志/脱敏规则：`docs/rules/logging-rules.md` §2.4/§2.6；多队查询：`docs/rules/java-rules.md` §7。
- 提交前需用户确认；本批完成后重跑 `mvn test`、`pnpm -r typecheck/test`。
