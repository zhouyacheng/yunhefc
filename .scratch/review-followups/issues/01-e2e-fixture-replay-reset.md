# 01 本地 E2E 种子重放复位

Status: resolved
Blocked by:

## 问题

本地全量 admin E2E 复跑时 B03/B05/B06 等用例超时。根因是 `scripts/sql/test-data.sql` 的重放不幂等：

- 1001/1002 使用 `INSERT IGNORE`，重放只更新 1001 的时间，不恢复 `status/manual_status/version`。
- 1003/1004 的 `ON DUPLICATE KEY UPDATE` 恢复 `status/manual_status`，但不复位 `version`。
- 实测漂移：1001 `status=3`（应为 2/LOCKED，version 18）；1004 在重放后由 B3 1b 段按设计改为 4/FINISHED，但 version 仍残留 9；1005 重放后恢复 0/SIGNUP_OPEN。

B03 依赖「橙队VS兰队 · 已锁定」的 1001，状态漂移后选择器找不到目标场次，直接超时。

## 修复

只改 `scripts/sql/test-data.sql` 的重放复位语句：

- 1001 的既有 UPDATE 增加 `status=2, manual_status=1, version=0, is_deleted=0`。
- 新增 1002 的复位 UPDATE：`status=4, manual_status=0, version=0, is_deleted=0`。
- 1003/1004 的 `ON DUPLICATE KEY UPDATE` 增加 `version=0`（1005 已有，1004 最终由 B3 1b 段改为 FINISHED）。
- 不动业务数据、报名/考勤复位与其它 fixture。

## 验收

- 在已跑过 E2E 的本地库重放种子后，1001–1005 的状态/版本回到种子定义值。
- 无需 `DROP/CREATE`，`pnpm test:e2e --project=admin e2e/admin.spec.ts` 不再因 fixture 漂移失败。
- 连续重放两次，结果一致。

## 验证

```bash
docker exec -i yunhefc-mysql mysql -uyunhefc -pyunhefc yunhefc < scripts/sql/test-data.sql
docker exec -i yunhefc-mysql mysql -uyunhefc -pyunhefc -N -e \
  "SELECT id,status,manual_status,version FROM yunhefc.team_match WHERE id BETWEEN 1001 AND 1005 ORDER BY id"
cd frontend && pnpm test:e2e --project=admin e2e/admin.spec.ts
```

## Comments

- 2026-11-17 已实现：`test-data.sql` 的 1001 UPDATE 补 `status/manual_status/version/is_deleted` 复位，1003/1004 的 `ON DUPLICATE KEY UPDATE` 补 `version=0`；1002 已有 B4 段复位，仅补 `is_deleted=0`，未新增重复 UPDATE。连续重放两次后 1001–1005 状态/版本一致（1001=2/1/0，1004 由 B3 1b 段改为 4/FINISHED）；`pnpm test:e2e --project=admin e2e/admin.spec.ts` 25 passed。
