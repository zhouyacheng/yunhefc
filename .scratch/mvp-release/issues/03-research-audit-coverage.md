# 03 — 审计留痕覆盖与脱敏现状

Type: research
Status: resolved
Blocked by: —

## Question

盘点审计模块的留痕覆盖与脱敏现状：

- 从需求 §6.1 / §11.1 / §12.6 与 06 决策提取必须留痕的写操作清单（角色权限、审核/注销、比赛状态、移除报名、考勤标记、事件变更、阵容/分组保存、邀请变更等）。
- 对账 `AuditRecorder` 的实际调用点（据初步盘点约 32 处），输出「动作 → 目标类型 → 调用点 → before/after 快照字段 → 脱敏风险」矩阵。
- 标出未覆盖写点、快照中可能含手机号/地区/证件/地址等敏感字段、AUD-01 的查询/脱敏/分页缺口、审计永久保留与年度导出 OSS 的边界。

只给事实与差距，不做取舍。事实指针：需求 §6.1 / §11.1 / §12.6、05 §3.11、`audit/application/AuditRecorder.java`、`audit/application/AuditEventHandler.java`、各域 controller/application 的写路径、04 `audit_log` 表。

## Answer

- 生产代码 `AuditRecorder.record*` 共 32 个调用点，RoleService helper 展开后为 36 个动作发射点。
- 需求 §11.1 的 8 类硬性操作均已覆盖：角色/权限、审核/注销、比赛状态（含自动流转）、管理员移除报名、管理员考勤、事件变更、阵容/分组保存、邀请变更。
- 未覆盖：微信绑定、系统配置修改（SET-02 整体未实现）、自助报名/取消、自助签到、邀请自动过期、阵型模板 CRUD（后三项是否属强制范围待决策）。
- AUD-01 查询接口完全未实现：无 controller/service/DTO、无过滤/分页、无 `beforeRedacted/afterRedacted/ipMasked` 脱敏映射；`audit_log` 原样存 `before_json/after_json/ip`。
- 年度 OSS 导出任务缺失（03 §5.4、04 §7、需求 §11.4 有要求）；在线永久保留已满足，`DataRetentionService` 不清理 `audit_log`。
- 敏感字段：`PLAYER_UPDATE` 含 `region`、`avatarUrl`；`INVITE_ACCEPT` 含 `inviter.phoneMasked`；`reason` 为自由文本；Outbox/Kafka 另存一份明文快照。
- 完整矩阵、证据行号与待决策项见 [research/03-audit-coverage.md](../research/03-audit-coverage.md)。
