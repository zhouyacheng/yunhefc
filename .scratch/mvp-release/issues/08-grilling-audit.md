# 08 — 审计覆盖、脱敏与日志页边界

Type: grilling
Status: resolved
Blocked by: 03

## Question

基于「审计留痕覆盖与脱敏现状」定稿：

- 哪些未覆盖写点必须补审计，哪些不属于 §6.1 / §11.1 范围。
- before/after 快照的脱敏字段与规则（手机号、地区、证件、地址等），存储与返回是否同一份。
- AUD-01 的筛选、分页、响应契约；B 端操作日志页范围。
- 审计永久保留与年度导出 OSS 的边界：自动化任务还是 runbook（备份机制见「备份与数据保留」）。
- 补点清单与测试口径。

契约变化先改 05/02/04 并记 06。事实指针：03 答案、需求 §6.1 / §11.1 / §12.6、05 §3.11、02 后台「操作日志」。

## Answer

- **补点**：新增 `WECHAT_BIND`（已登录绑定与手机号绑定两条路径；`targetType=USER`；快照仅 `openid:"***"`；操作人为本人）与 `INVITE_EXPIRE`（系统操作人、`targetType=INVITE`、`{status:<原值>}` → `{status=EXPIRED}`）。`SETTINGS_UPDATE` 沿用票 06。自助报名/取消、自助签到、阵型模板 CRUD 不写 `audit_log`：分别由 `registration`、`attendance` 行与 `LINEUP_SAVE` 留痕；登录/登出、短信、通知已读不留痕。
- **脱敏**：`audit_log` 与年度 OSS 冷备保留原文；AUD-01 响应层统一经 `MaskUtils` 脱敏。仅处理 `phone`（前 3 后 4，<7 位为 `***`）、`openid/passwordHash/tokenHash/token`（`***`）与 IP（IPv4 末段、IPv6 前 3 组）；递归对象/数组、键名不区分大小写。`region`/`reason`/`avatarUrl` 不脱敏；`avatarUrl` 只存 OSS 对象键（依赖票 15）。
- **AUD-01**：`GET /api/v1/b/audit-logs`、`audit:view:any`；参数全部可选且 AND；`actor` 纯数字按 ID、否则姓名模糊；时间 ISO-8601 UTC，`from > to` 返回 40001；固定 `created_at DESC, id DESC`；`pageSize` 10/20/50/100；空 Page；只读不可变；按球队过滤；无权限 40301。响应 `Page<AuditLog>`，字段与脱敏规则按 05 §3.11/§4。
- **B10**：首批交付完整页面——筛选（时间/操作人/动作/对象类型/对象ID）、时间倒序分页、前后快照详情、空态/失败重试/无权限；不做导出入口。需求 §12.6 #30 的“界面后续完善”不作为首批豁免。
- **年度导出**：Quartz 每年 1 月 1 日导出上一自然年（Asia/Shanghai）为 JSONL.gz（一行一条、`id` 升序、快照为 JSON 对象）到私有 OSS `audit/export/{year}/audit-{year}.jsonl.gz`；按年幂等、不自动删除、保留原文；失败 ERROR 日志 + runbook 手工补导；开关 `yunhefc.audit.export.enabled` 默认关闭。Outbox/Kafka 审计载荷各保留 7 天，Kafka 保留期在部署票落地。
- **测试口径**：`MaskUtils` 单测（手机号/IP v4/v6/递归/非字符串/null）；`AuditQueryIT`（过滤、分页边界、`from > to`、403、空结果、响应脱敏、库内原文）；补点 IT（微信首次绑定/换绑、邀请过期）；导出任务用假 OSS 客户端验证内容与按年幂等；Playwright B10 筛选 + 详情断言手机号掩码且无 console error；无 schema 改动，空库 Flyway 幂等不变。
- **同步**：05 v0.25、02 v0.17、03 v1.13、04 v0.19、06 v1.41；需求/01/`schema.sql`/Flyway/后端不改；原型 `admin.js` 已同步 B10 筛选交互。
- **未执行**：未执行 SQL、未写业务代码、未运行后端测试；AUD-01/`MaskUtils`/导出任务/B10 Vue 页面留在 M10 实施批次。
