# 07 — M10 审计查询与脱敏

Type: implementation
Status: resolved

**What to build:** 管理员可以按操作人、动作、对象与时间筛选并分页查看操作日志；响应中的前后快照与 IP 已脱敏，数据库仍保留原文；微信绑定与邀请到期两个缺口补上审计留痕。

**Blocked by:** None — can start immediately

**Status:** resolved

- [x] AUD-01 `GET /b/audit-logs`（`audit:view:any`）支持可选 `actor,action,targetType,targetId,from,to,page,pageSize`，全部 AND 组合。
- [x] `actor` 纯数字按用户 ID 精确匹配，否则按操作人姓名模糊匹配（长度 ≤64）；`targetId ≥1`；`from`/`to` 为 ISO-8601 UTC，`from > to` 返回 40001。
- [x] 固定 `created_at DESC, id DESC`；`pageSize` 走 `PageQuery`；空结果返回空 Page；无权限 40301；按当前球队过滤。
- [x] 响应为 `id,actor:{id?,name?},action,targetType,targetId?,beforeRedacted?,afterRedacted?,requestId?,ipMasked?,createdAt`；系统操作为 `id=null,name="系统"`；快照是脱敏后的 JSON 对象。
- [x] `MaskUtils` 为纯函数，递归对象/数组、键名不区分大小写：`phone` 前 3 后 4（长度 <7 为 `***`）；`openid` / `passwordHash` / `tokenHash` / `token` 替换为 `***`（非字符串值同样替换）；IP 由 `maskIp` 处理（IPv4 掩去末段、IPv6 保留前 3 组并追加 `::*`）；`region` / `reason` / `avatarUrl` 不脱敏。
- [x] `audit_log` 与 Outbox payload 仍写原文；脱敏只发生在 AUD-01 响应组装；服务层不暴露 update/delete。
- [x] 补审计点：已登录绑定微信与手机号绑定两条路径写 `WECHAT_BIND`（`targetType=USER`，快照 openid 为 `***`，操作人为本人）；邀请到期置失效写 `INVITE_EXPIRE`（系统操作人，`targetType=INVITE`，status 0→2）。既有 `WECHAT_UNBIND` 不变。
- [x] 测试覆盖：`MaskUtils` 单测（手机号、IPv4/IPv6、递归、大小写、非字符串、null）；AUD-01 集成测试（各过滤组合、from>to、非法分页、403、空结果、响应脱敏与库内原文）；两个补点各一条集成测试；`mvn verify` 全绿。
