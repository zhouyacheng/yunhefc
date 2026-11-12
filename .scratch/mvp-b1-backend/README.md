# B1 后端与契约补齐 — 实施规格

Status: ready-for-agent
Type: spec

事实源：需求 v1.15；`docs/design/01.功能模块梳理.md`–`06.设计方案评审结果.md`；`docs/rules/*`；`.scratch/mvp-release/batches.md`；已关闭决策票 `.scratch/mvp-release/issues/06`、`07`、`08`、`09`、`10`、`14`、`15`、`17`、`21`；现状盘点 `.scratch/mvp-release/research/01`–`04`。

本规格只定义 B1 的实施边界与验收口径，不写业务代码、不改动 `schema.sql` / Flyway / Quartz DDL。

---

## Problem Statement

M01–M08 已让球队完成建赛、报名、考勤、事件比分、统计、分组与阵容，但上线所需的后端能力仍缺最后一层：

- 后端只有 83/92 个接口。缺失的 9 个恰好集中在 M09 通知（NTF-01~04）、M10 审计（AUD-01）、M11 系统配置（SET-01/02）、M13 文件（FIL-01/02）。
- 球员收不到也读不到站内信：通知写入链路存在，但没有列表、未读计数、单条已读与全部已读接口；外挂转正也没有产生 `ROLE_CHANGED` 通知。
- 管理员无法查询操作日志：审计已异步落库，但没有过滤、分页、脱敏与年度导出。
- 管理员无法查看或修改 9 项系统配置；建赛与分组只能读种子默认值。
- 头像没有上传与签名读：`player.avatar_url` 仍被当作普通 URL 原样读写，私有 OSS 对象键与 15 分钟签名 URL 尚未落地。
- 生产环境的短信只有 mock 分支，关闭 mock 即拒绝；微信真实模式缺少失败分类与启动自检。生产一上线，注册、找回密码、绑定手机与微信登录都会断。
- 契约未收口：`User` / `PlayerAdmin` 没有 `wechatBound`，B 端 `sections` 仍返回已下线的 dashboard，INV-04 / AUTH-13 未校验枚举，AUTH-13 没有 `keyword`。

结果：B2/B3/B4 的任何页面批次都无法开工；MVP 的「可上线」停在最后一公里。

## Solution

按 9 张实施票补齐后端与契约，票间按依赖边执行；每票以后端门禁收口：

| 票 | 内容 | 前置 | 主要交付 |
|---|---|---|---|
| 01 | M13 OSS 网关与头像上传 | 无 | `OssGateway`（real + in-memory）、FIL-01/02、对象键与文件校验 |
| 02 | 头像签名读与对象键写校验 | 01 | 五个读路径现签 URL、PLR-03/06 只接受对象键 |
| 03 | M09 通知 | 无 | NTF-01~04、PLR-08 通知、90 天删除方法、shared 类型 |
| 04 | M11 系统配置 | 无 | SET-01/02、部分合并、与分组同源校验、`SETTINGS_UPDATE` |
| 05 | 契约与列表收口 | 无 | `wechatBound`、`sections`、INV-04/AUTH-13 校验与 `keyword` |
| 06 | 真实短信与微信 | 无 | `SmsSender` + `MarketplaceSmsSender`、prod fail-fast、微信失败映射 |
| 07 | M10 审计查询与脱敏 | 无 | AUD-01、`MaskUtils`、`WECHAT_BIND` / `INVITE_EXPIRE` |
| 08 | 审计年度导出 | 01、07 | 年度 JSONL.gz 导出、按年幂等、开关默认关 |
| 09 | 数据保留任务 | 03 | 单一 `DataRetentionJob`、保留期配置、批量物理删除 |

依赖边：`01 → 02`（签名读复用网关）；`01、07 → 08`（导出复用网关与审计查询）；`03 → 09`（通知清理方法）。`01`、`03`、`04`、`05`、`06`、`07` 可并行。

**完成门（整批）**

- 05 的 92 个接口全部实现；无新接口编号、无新权限码、无新错误码。
- `mvn verify` 全绿（含 ArchUnit 与既有 250 条集成测试基线）；空库 Flyway 迁移幂等。
- 无 `schema.sql` / Flyway / Quartz DDL 改动；空库与既有库结构一致。
- 新增集成测试覆盖本规格「Testing Decisions」逐项清单。
- dev/test 默认 mock 可启动；prod 在 OSS/短信/微信缺少真实配置或错误开启 mock 时启动失败（fail-fast）。
- 审计写入继续经 Outbox 异步，不阻塞接口响应。

---

## User Stories

### M13 OSS 与头像

1. 作为已登录球员，我想上传一张头像并拿到对象键与签名 URL，以便在资料页预览后再保存。
2. 作为待审核球员，我想仍能上传头像，以便完善资料而不被账号状态阻断。
3. 作为球员，我想上传 JPEG/PNG/WebP，以便常见手机照片都能用。
4. 作为球员，我想超过 2MiB 的图片被拒绝并看到原因，以便知道压缩后重试。
5. 作为球员，我想伪装成图片的非图片文件被拒绝为不支持的类型，以便服务端不只信扩展名。
6. 作为球员，我想空文件或损坏文件被拒绝并指明原因，以便重选。
7. 作为球员，我想上传本身不修改我的档案，以便取消时头像不被动改。
8. 作为管理员，我想为指定球员代传头像，以便协助维护资料。
9. 作为管理员，我想代传时目标球员不存在返回 40401，以便区分错误对象。
10. 作为前端开发者，我想读响应里的 `avatarUrl` 已经是 15 分钟签名 URL，以便直接用作图片地址而无需二次请求。
11. 作为后端，我想写接口只接受 FIL 返回的对象键，以便数据库与审计快照永远不存签名参数。
12. 作为运维，我想 dev/test 可用 in-memory OSS，prod 缺凭证或仍为 mock 时启动失败，以便不会带着假存储上线。

### M09 通知

13. 作为球员，我想分页查看自己的通知，以便按时间了解报名、比赛与角色变化。
14. 作为球员，我想只看未读通知，以便集中处理。
15. 作为球员，我想把单条通知标记为已读，以便角标更新。
16. 作为球员，我想重复标记同一条已读时拿到原来的 `readAt`，以便网络重试安全。
17. 作为球员，我想一键把全部未读标记已读，以便快速清空角标。
18. 作为球员，我想在全部已读时也拿到 `updated=0` 的 200 响应，以便前端不用区分空操作。
19. 作为球员，我想随时读取未读数，以便登录后立即显示角标。
20. 作为球员，我想访问别人的通知时得到 40401，以便不泄露他人数据。
21. 作为待审核球员，我想读取与标记自己的通知，以便审核期间也能看到账号动态。
22. 作为外挂转正后的球员，我想收到一条角色变更通知，以便知道身份已变化。
23. 作为系统，我想在通知满 90 天后物理删除，以便在线数据不无限增长。
24. 作为前端开发者，我想 shared 包导出通知与未读结果类型，以便页面批次直接消费。

### M10 审计

25. 作为管理员，我想按操作人、动作、对象类型、对象 ID 与时间范围筛选操作日志，以便定位一次变更。
26. 作为管理员，我想在操作人输入纯数字时按用户 ID 查、否则按姓名模糊查，以便一个输入框两种用法。
27. 作为管理员，我想 `from > to` 得到 40001，以便时间范围错误被明确拒绝。
28. 作为管理员，我想日志固定按时间倒序分页，以便最新操作总在前面。
29. 作为管理员，我想空结果返回空 Page 而不是 404，以便列表页正常展示空态。
30. 作为无权限用户，我想得到 403，以便不误以为没有日志。
31. 作为管理员，我想前后快照里的手机号、openid、密码哈希、token 与 IP 被脱敏，以便展示日志不泄露敏感信息。
32. 作为审计员，我想数据库仍保留原文，以便年度冷备可追溯。
33. 作为管理员，我想系统操作显示 `id=null,name="系统"`，以便区分人工与定时任务。
34. 作为审计员，我想微信绑定与管理员解绑留痕，以便账号安全可追溯。
35. 作为审计员，我想邀请到期置为失效时留痕，以便邀请生命周期完整。
36. 作为运维，我想每年把上一自然年的审计导出为 JSONL.gz 到私有 OSS，以便在线永久保留之外有冷备。
37. 作为运维，我想导出按年幂等且默认关闭，以便开关打开后重复执行不覆盖出问题。
38. 作为管理员，我想操作日志只读、没有编辑或删除入口，以便审计不可篡改。

### M11 系统配置

39. 作为管理员，我想查看当前 9 项有效配置，以便确认报名默认值与分组权重。
40. 作为管理员，我想缺失的配置键回退默认 30/4/2、70/30、50/25/25、3，以便空配置也能工作。
41. 作为管理员，我想只提交要改的键，其余保持原值，以便一次只调一项。
42. 作为管理员，我想非法数值返回 40001，以便不会把坏配置写进库。
43. 作为管理员，我想手动与自动权重合计必须 100，以便分组评分口径稳定。
44. 作为管理员，我想保存后只影响之后新建的比赛与后续分组，以便历史数据不被回写。
45. 作为审计员，我想每次保存只记一条 `SETTINGS_UPDATE`，含完整前后 9 键，以便变更可追溯。
46. 作为管理员，我想保存带幂等键且不带版本，以便重复提交不重复写、并发以后写生效。

### 契约与列表

47. 作为球员，我想 `AUTH-10` 的 `user.wechatBound` 告诉我是否已绑定微信，以便个人中心展示状态。
48. 作为管理员，我想 `PlayerAdmin.wechatBound` 支持 B07 的微信列与解绑入口显隐，以便账号管理。
49. 作为无账号球员，我想 `wechatBound=false`，以便字段始终是布尔值。
50. 作为安全审查者，我想任何响应都不返回 openid，以便只暴露绑定状态。
51. 作为管理员，我想 `sections` 不再包含 dashboard，以便登录后落到首个有权限分区。
52. 作为管理员，我想 INV-04 的 `type` / `status` 非法枚举返回 40001，以便筛选参数被强校验。
53. 作为管理员，我想 AUTH-13 的 `status` 非法枚举返回 40001，以便账号列表参数被强校验。
54. 作为管理员，我想 AUTH-13 支持 `keyword` 按姓名或手机号模糊匹配，以便 B08 选人。
55. 作为管理员，我想 `keyword` 去首尾空格、空串不过滤、超过 64 字符返回 40001，以便输入有明确边界。
56. 作为前端开发者，我想 shared 的 `User` / `PlayerAdmin` 类型增加布尔字段，以便页面批次零改动消费。

### 数据保留

57. 作为运维，我想单一保留任务每日 03:30 Asia/Shanghai 执行，以便所有清理有统一入口。
58. 作为运维，我想通知满 90 天物理删除，以便表体积可控。
59. 作为运维，我想失效邀请满 30 天后先删使用记录再删邀请，以便外键与审计语义正确。
60. 作为运维，我想 Outbox SENT 与 FAILED 各保留 7 天、PENDING 不清理，以便重试不被误删。
61. 作为运维，我想消费去重记录保留 7 天、幂等键按过期时间清理，以便重放窗口与存储平衡。
62. 作为运维，我想每轮最多删 1000 行并循环到不足一批，以便大表删除不长时间锁表。
63. 作为运维，我想单类清理失败不阻断其他类，成功记 INFO、失败记 ERROR，以便排障。
64. 作为运维，我想保留天数可由 `yunhefc.retention.*` 覆盖且必须为正数，以便不同环境调整。
65. 作为运维，我想邀请到期任务仍每日 03:00 只置失效，与 03:30 物理删除分工不变，以便职责清晰。

### 真实短信与微信

66. 作为注册/找回密码/绑定手机的用户，我想真实收到 6 位验证码，以便完成账号流程。
67. 作为系统，我想服务商返回成功后才写入验证码，以便失败时不落一个永远收不到的码。
68. 作为用户，我想服务商流控时得到 42901 与重试时间，以便知道稍后再试。
69. 作为运维，我想 APPCODE/签名/模板/系统错误映射为 50001 且不自动重试，以便失败可观测、不放大请求。
70. 作为运维，我想 prod 下 `SMS_MOCK_ENABLED=true` 或缺少 APPCODE/签名/模板时启动失败，以便不会假发短信。
71. 作为微信内用户，我想静默授权后完成绑定或登录，以便不出现授权确认页。
72. 作为用户，我想授权 code 失效或 state 不匹配时得到 40001 与站内提示，以便重试。
73. 作为非微信浏览器用户，我想得到 42201「请在微信内打开」，以便知道换环境。
74. 作为运维，我想 prod 下微信 real 模式缺少 AppID/Secret/回调地址时启动失败，以便授权链路不带病上线。
75. 作为安全审查者，我想日志只记 errcode、不记 code/openid/secret，以便凭证与身份不泄露。

### 跨切面

76. 作为开发者，我想 92/92 接口与 05 完全一致，以便页面批次只消费、不改后端。
77. 作为开发者，我想本批不新增接口、权限码与错误码，以便契约冻结不被打破。
78. 作为开发者，我想本批不改任何表结构、Flyway 或 Quartz DDL，以便上线迁移保持幂等。
79. 作为开发者，我想 `mvn verify` 与 ArchUnit 全绿，以便模块边界不被新代码破坏。
80. 作为开发者，我想 dev/test 全 mock、prod fail-fast，以便本地开发与生产安全两不误。
81. 作为审计员，我想审计仍经 Outbox 异步写入，以便接口响应不被审计故障拖慢。

---

## Implementation Decisions

### 1. 契约与边界（全批）

- 唯一契约来源是 05：§2.7（上传）、§3.10–3.15（NTF/AUD/SET/FIL）、§4（响应对象）、§5.1（权限码）。本批不新增接口编号、权限码或错误码；复用 `40001`、`40301`、`40401`、`41501`、`42201`、`42901`、`50001`。
- 模块边界沿用 03 的分层与 ArchUnit：controller → application → domain；OSS 网关只在 `infra.storage`，domain 不接触 SDK；跨域只经 contracts 只读端口。
- 审计与通知保持 Outbox 异步；application 层只写 Outbox，不在业务事务中同步落 `audit_log` / `notification`。
- Quartz 只用 JDBC JobStore 与 `QuartzJobBean` + Trigger，禁止 `@Scheduled`；任务委托 application 服务，便于集成测试直接调用。
- 配置新增键写 `application.yml`（`@ConfigurationProperties`），不新增 `@Value`；`application-prod.yml` 与通用生产自检归 B0，本批只在 OSS/SMS/WeChat 适配器层做 `prod` profile 的 fail-fast，避免与 B0 重复创建 profile。

### 2. M13 OSS 与头像（01 / 02）

- `OssGateway` 端口方法固定：`putObject`、`signedUrl`、`exists`、`getObject`；实现 `AliyunOssGateway`（real）与 `InMemoryOssGateway`（mock/test，审计导出测试复用）。
- 配置键 `yunhefc.oss.{endpoint,bucket,access-key-id,access-key-secret}` + `OSS_MODE=mock|real`；local/test 默认 mock，prod 必须 real。prod 下 mock 或缺任一凭证 → 启动失败。
- 对象键 `avatar/{userId}/{ulid}.{ext}`，`ext` 由服务端文件头嗅探得到（`jpg|png|webp`），不加环境前缀；ULID 使用大写 Crockford 字符集。
- FIL-01 `POST /c/files/avatar`，权限 `player:edit:own`，PENDING 可用；FIL-02 `POST /b/players/{id}/avatar`，权限 `player:edit:any`，目标球员不存在 40401。两者都是 `multipart/form-data` 字段 `file`，响应 `{objectKey,signedUrl}`，只上传不绑定。
- 校验：空/损坏/超 2,097,152 字节 → 40001 且 `data.details` 指明原因；文件头不是 JPEG/PNG/WebP 或伪装 → 41501；OSS 写入失败 → 50001。`spring.servlet.multipart.max-file-size=2MB`、`max-request-size=3MB`；后端以 2MiB 为准。
- 读语义：`player.avatar_url` 只存对象键；`Player`、`PlayerPublic`、`PlayerAdmin`、球队页与 `MeView.player` 的 `avatarUrl` 在 DTO 组装时现签 15 分钟 URL。`MeView.player` 当前类型缺 `avatarUrl`，本票补齐（属于 05 §4 已冻结语义，不是新契约）。
- 写语义：PLR-03 / PLR-06 的 `avatarUrl` 只接受 `^avatar/\d+/[0-9A-HJKMNP-TV-Z]{26}\.(jpg|png|webp)$`；空串与空白归一为 null；不做 OSS 存在性探测。
- 签名统一在读出 DTO 时进行，不在实体或 mapper 中；审计快照只存对象键。
- 已知限制：上传接口不限流；未绑定的孤儿对象不清扫。

### 3. M09 通知（03）

- 新增 `NotificationController` + `NotificationApplicationService` + 响应 DTO；`NotificationMapper` 增加列表、单条读取与批量已读所需的自定义 SQL（列表与已读必须带 `user_id` 约束）。
- NTF-01 `GET /c/notifications`：`unread` 可省略；`true` 仅未读，`false`/缺省为全部；`PageQuery` 分页；固定 `created_at DESC, id DESC`。
- NTF-02 `GET /c/notifications/unread-count`：返回 `{count}`，复用现有 `countUnread`。
- NTF-03 `POST /c/notifications/{id}/read`：只按 `id + userId` 查；非本人或不存在统一 40401；已读时返回原 `readAt`，不覆盖时间。
- NTF-04 `POST /c/notifications/read-all`：只更新本人未读；返回 `{updated}`，0 也 200。NTF-03/04 按 05 不属于幂等键接口，不引入 `Idempotency-Key`。
- `Notification` 响应对象：`id,type,typeLabel,title,body,refType?,refId?,readAt?,createdAt`；`typeLabel` 取 `NotificationType.label()`（大写枚举名）。
- PLR-08 转正：在账号生命周期编排的同一事务中补 `ROLE_CHANGED` Outbox（收件人为被转正本人，角色取转正后快照）；不去改其他流程共用的球员服务。
- 通知 90 天物理删除：自定义 `@Delete`（避免 `@TableLogic` 把删除改成软删），按 `created_at` 删除；不加索引；实际调度在 09 票接入。
- shared 包新增通知类型模块并在 `index` 导出：`Notification`、`UnreadCount`、`ReadResult`、`ReadAllResult`；本批不做 UI、不改 `MeView` 的 `unreadCount`。
- `PendingAccountFilter` 已预留 NTF 路由，不改安全白名单。

### 4. M10 审计（07 / 08）

- AUD-01 `GET /b/audit-logs`，权限 `audit:view:any`；参数全部可选、AND 组合：`actor,action,targetType,targetId,from,to,page,pageSize`。
- `actor` 纯数字按 `actor_user_id` 精确匹配，否则按 `actor_name` 模糊匹配（长度 ≤64）；`action`/`targetType` 精确匹配；`targetId ≥1`；`from`/`to` 为 ISO-8601 UTC，`from > to` → 40001；固定 `created_at DESC, id DESC`；`pageSize` 走 `PageQuery`；空结果空 Page。
- 服务层只暴露查询，不提供 update/delete；按当前球队过滤。
- `MaskUtils` 落 `common.util`，纯函数递归处理对象与数组，键名不区分大小写：`phone` 前 3 后 4（长度 <7 为 `***`）；`openid`/`passwordHash`/`tokenHash`/`token` 替换为 `***`（非字符串值同样替换）；IP 由独立 `maskIp` 处理（IPv4 掩去末段、IPv6 保留前 3 组 + `::*`）。`region`、`reason`、`avatarUrl` 不脱敏。
- AUD-01 响应对象 `AuditLog`：`id,actor:{id?,name?},action,targetType,targetId?,beforeRedacted?,afterRedacted?,requestId?,ipMasked?,createdAt`；`before/after` 是脱敏后的 JSON 对象（不是字符串）；系统操作 `actor.id=null`、`actor.name="系统"`。
- 库内 `before_json/after_json/ip` 保留原文；脱敏只在响应组装时做。
- 补点：微信已登录绑定与手机号绑定两条路径写 `WECHAT_BIND`（`targetType=USER`，快照 `openid="***"`，操作人为本人）；邀请到期置失效写 `INVITE_EXPIRE`（系统操作人，`targetType=INVITE`，`{status:0} → {status:2}`）。已有 `WECHAT_UNBIND` 保持不变。
- 既有审计动作名大小写不一致（`grouping.*` / `team_match`）按存储值原样返回，本批不规范化数据。
- 年度导出：Quartz 每年 1 月 1 日导出上一自然年（Asia/Shanghai）为 JSONL.gz（一行一条、按 `id` 升序、快照为 JSON 对象），写 `audit/export/{year}/audit-{year}.jsonl.gz`，复用 `OssGateway`；`exists` 为真则跳过（按年幂等）；开关 `yunhefc.audit.export.enabled` 默认 false；失败 ERROR 日志，不自动重试、不删除在线数据。

### 5. M11 系统配置（04）

- 新增 `SettingsController` + `SettingsApplicationService` + `Settings` DTO；SET-01 `GET /b/settings`（`settings:view:any`），SET-02 `PATCH /b/settings`（`settings:manage:any` + `Idempotency-Key`）。
- 扁平 9 键：`maxPlayers,maxGuests,maxWaitlist,groupingManualWeight,groupingAutoWeight,groupingAppearanceWeight,groupingGoalWeight,groupingAssistWeight,groupingMinAppearances`；响应 `{settings}`。
- 读取：缺失或不可解析的键逐项回退 30/4/2、70/30、50/25/25、3（与现有两个只读适配器一致）。
- 写入：部分合并，未传键保持原值；按合并后的完整 9 键校验；非法 → 40001。
- 校验与 `GroupingWeights.resolve` 同源：`maxPlayers 1–99`；`maxGuests/maxWaitlist/minAppearances 0–99`；`manualWeight/autoWeight 各 0–100 且合计=100`；三个子权重各 0–100 且合计 >0。SET-02 对非法值直接拒绝，不交给分组层静默回退。
- 落库：`system_setting.setting_value` 写 JSON 数字（不是字符串），`value_type=NUMBER`；按 `(team_id,setting_key)` upsert；不新增缓存。
- 审计：每次 PATCH 一条 `SETTINGS_UPDATE`，`targetType=SETTINGS`，`targetId=teamId`，before/after 为完整 9 键；无 PII。
- 生效边界：只影响之后的建赛默认值与分组生成；不回写已创建比赛、不回算已保存分组。
- 不带 `version`，最后写入生效。

### 6. 契约与列表收口（05）

- `UserView`（AUTH-03/04/09/10/11/15/17 共用）增加 `wechatBound: boolean`，由 `app_user.openid` 是否为空派生；始终返回，未绑定 false，不返回 openid。唯一组装点是 `UserViewAssembler.toView(AppUser)`，无需额外查询。
- `PlayerAdminView` 增加 `wechatBound`；经 `UserAccountSnapshot` 传递该布尔值（快照增加字段，不暴露 openid）；无账号球员 false。`PlayerAdminApplicationService.toView` 是唯一映射点。
- `UserSummary` 不加该字段；`MeView` 不新增顶层字段。
- `AuthService.sections` 移除 `dashboard`，其余分区与权限映射不变；前端 `ADMIN_SECTIONS`、`permissions` 测试与 admin 路由的成对移除属于 B2（`01-admin-shell-without-dashboard`），本批只改后端并在规格中标注该配对约束，不编辑 `frontend/packages/permissions`。
- INV-04（`/b/invites`）：`type∈{0,1}`、`status∈{0,1,2}`，非法 → 40001；复用 `InviteType.of` / `InviteStatus.of`；`pageSize` 已由 `PageQuery` 校验，不重复实现。
- AUTH-13（`/b/users`）：`status∈{0,1,2}`，非法 → 40001；新增可选 `keyword`：去首尾空格、空串不过滤、长度 >64 → 40001，对 `player.name` 或 `app_user.phone` 模糊匹配；分页与排序保持现状。`AppUserMapper` 现有查询不支持跨 `player.name` 搜索，需要新增分页查询（JOIN 或 `EXISTS`），响应仍由 `UserViewAssembler.toSummaries` 组装。
- 前端 `frontend/packages/shared` 的 `User` 与 `PlayerAdmin` 各加 `wechatBound: boolean`；无新请求、无 UI。

### 7. 数据保留任务（09）

- 单一 `DataRetentionJob` 每日 03:30 Asia/Shanghai 执行（Quartz Trigger 显式设置时区），编排各域 retention port；`InviteExpiryJob` 保持 03:00 只置失效，不合并。
- 保留期与删除方式：

| 数据 | 保留 | 删除条件 | 实现要点 |
|---|---|---|---|
| notification | 90 天 | `created_at < now-90d` | 自定义 `@Delete` |
| invite_link + invite_use | 失效后 30 天 | `status=2 且 expires_at < now-30d`，或 `status=1 且 revoked_at < now-30d` | 同事务先删 `invite_use` 再删 `invite_link` |
| outbox_event SENT | 7 天 | `sent_at < now-7d` | 现有方法保留 |
| outbox_event FAILED | 7 天 | `created_at < now-7d` | 删除前 WARN 数量；PENDING 不清 |
| consumed_event | 7 天 | `processed_at < now-7d` | 现有方法保留 |
| idempotency_key | 过期即清 | `expires_at <= now` | 现有方法保留 |

- 新增 `yunhefc.retention.{notification-days,invite-days,outbox-days,consumed-event-days}`，默认 90/30/7/7，`@ConfigurationProperties` 正数校验，可由环境变量覆盖，无管理界面。
- 批大小 1000 为代码常量；每类循环删除到不足一批。
- 单类失败只记 ERROR 并继续其他类；成功记 INFO（数量），不用 DEBUG 作为唯一记录。
- 物理删除必须自定义 `@Delete`（或绕过 `@TableLogic`）；不新增索引、不改 schema。
- 通知删除方法由 03 提供，本票只接入调度与配置。

### 8. 真实短信与微信（06）

- 新增 `SmsSender` 端口与 `MarketplaceSmsSender`（阿里云云市场 HTTP + APPCODE）；配置键 `SMS_APPCODE`、`SMS_SIGN_NAME`、`SMS_TEMPLATE_ID`（只从环境变量读取，不进 GitHub Secrets）。
- 一个签名 + 一个含 `${code}` 的通用模板，REGISTER/RESET/BIND 共用；用途只影响 Redis key 与业务校验。
- `SmsCodeService.send` 改为：生成 6 位数字码 → 调 `SmsSender` → 成功后才写 Redis（TTL 300 秒）；失败不落码、不自动重试。mock 开启时跳过真实发送并写 `mockCode`。
- 校验语义不变：6 位、TTL 300 秒、5 次失败作废；Redis key 与 attempts 结构保持现状，避免现有测试与调用方变化。
- 错误映射：号码非法 → 40001；服务商流控 → 42901 且带 `retryAfterSeconds`；APPCODE/签名/模板/系统错误 → 50001；不新增错误码。
- 限流保持现状（5 次/分钟/手机号，跨用途共用）。
- fail-fast：prod 下 `SMS_MOCK_ENABLED=true` 或缺 APPCODE/签名/模板 → 启动失败；dev/test/CI 显式 `SMS_MOCK_ENABLED=true`。
- 微信保持 `WechatOAuthClient` 端口与 mock/real 选择；补：`40029/40163 → 40001`，其他 provider/系统失败 → 50001；非微信 UA → 42201 文案「请在微信内打开」；日志只记 errcode，不记 code/openid/secret；不自动重试。
- 微信 prod fail-fast：`WECHAT_ENABLED=true`、`WECHAT_MODE=real` 且缺 AppID/Secret/回调地址 → 启动失败；回调地址仍为同源 www 的 `/api/v1/c/auth/wechat/callback`（浏览器授权回调另由 C 端页面承接，不在本批）。
- 短信/微信真实适配器在测试中通过假端口注入（`FakeSmsSender`、既有 `MockWechatOAuthClient`），不新增网络级集成测试。

### 9. 文档对齐（实施前）

- 05 §6 仍有一句「头像上传返回 URL，保存资料时写入 `player.avatar_url`」，与 §2.7/§3.15/§4 的 `objectKey` 契约矛盾；`docs/rules/api-rules.md` 也有「业务表只存 URL」的旧表述。实施时以 §2.7/§3.15 为准，顺手修正这些过时句子（不改契约），并在 06 的实施记录中注明。
- `V1__baseline_schema.sql` 的 `avatar_url` 注释为「头像URL」，与 04 的「私有 OSS 对象键」不一致；本批不改 Flyway（迁移不可变），以 04 与代码语义为准。

---

## Testing Decisions

### 好测试的标准

- 只断言外部行为：HTTP 状态、envelope 的 `code/message/data`、数据库副作用、`outbox_event` 中的审计/通知载荷；不断言私有方法、Mapper 调用次数或实现结构。
- 每个测试一个行为，命名描述业务规则；使用固定 Clock 与固定测试数据，不依赖真实时间或网络。
- 失败必须能定位到契约或规则，不靠日志猜测。
- 不 mock 业务域内部；跨域用真实适配器或既有 contracts 端口。外部服务只用端口假实现。

### 缝 1：HTTP/API 集成（主缝，复用）

- 复用 `IntegrationTestSupport`：`@SpringBootTest` + MockMvc + Testcontainers MySQL 8.4 / Redis 6.2.24；真实 `YHFC_C`/`YHFC_B` Cookie 与 CSRF；每测试执行 `db/test/reset.sql` + V3 种子，并清空 Redis。
- 覆盖：NTF-01~04 的权限/PENDING/分页/已读幂等/40401；AUD-01 的过滤、边界、403、空结果、响应脱敏与库内原文；SET-01/02 的默认值、部分合并、非法值、审计；FIL-01/02 的权限、PENDING、类型、大小、对象键、签名 URL；`wechatBound` 在 C/B 两侧响应中的派生（AUTH-10/15、PLR-05/06/07/08/09、无账号球员 false）；`sections` 不含 dashboard（同步修正 `AuthHardeningIT` 中断言 dashboard 存在的用例）；INV-04/AUTH-13 非法枚举与 `keyword`（含 trim、空串、>64、姓名/手机号命中）；保留任务各表物理删除、批大小与失败隔离；PLR-08 的 `ROLE_CHANGED` 通知。
- 前端 `permissions` 包的 dashboard 断言与 `ADMIN_SECTIONS` 移除由 B2 负责，B1 不修改该包；B1 只在 shared 的 `auth.ts` 加字段。
- Quartz 直接调用任务委托的应用服务（不启动调度器），并断言 `JobDetail`/`CronTrigger` 的 key、cron 与时区（沿用 `MatchAutoStatusIT` 写法）。
- 先例：`MatchEditIT`、`RegistrationRosterIT`、`IdempotencyIT`、`MatchAutoStatusIT`、`PlayerConvertIT`、`DataRetentionIT`、`WechatAuthIT`、`PageQueryTest`。

### 缝 2：纯领域单测（窄，复用）

- JUnit5 + AssertJ，无 Spring；只测组合爆炸或纯函数：
  - `MaskUtilsTest`：手机号、IPv4/IPv6、递归对象/数组、键名大小写、非字符串敏感值、null。
  - 设置校验：9 键边界、主权重合计、子权重合计、部分合并后的组合。
  - 文件头嗅探：JPEG/PNG/WebP 正例、空文件、截断、伪装的非图片。
  - 审计导出序列化：JSONL 一行一条、`id` 升序、gzip 可解、快照为对象。
- 先例：`PageQueryTest`、`EventRulesTest`、`GroupingSettingsAdapterTest`。

### 假端口（不单独成缝）

- `InMemoryOssGateway` 用于 FIL 与导出测试（真实 `AliyunOssGateway` 不做网络级测试）。
- `FakeSmsSender` 用于成功/失败/流控映射测试；mock 关闭时的真实发送路径由假端口验证。
- `MockWechatOAuthClient` 已存在，用于授权/回调流程；真实客户端的 errcode 映射以单元测试覆盖。

### 环境与数据

- dev/test 全 mock：`SMS_MOCK_ENABLED=true`、`OSS_MODE=mock`、`WECHAT_MODE=mock`；不执行生产 SQL。
- 空库 Flyway 幂等由 `FlywayMigrationIT` 覆盖；本批不新增迁移。
- 断言审计时优先查 `outbox_event`（同步可断言）或直接调用消费者处理器；既有测试已用该方式。

---

## Out of Scope

- B0 基座：`deploy/`、Dockerfile、Compose、Nginx、CI/CD、`application-prod.yml`、通用 prod 自检与日志配置。
- B2–B4 页面：B07/B08/B09/B10/B11、C01/C04–C09、C02 筛选、C06 通知区块与头像入口、C03 报名闭环、微信回调落地页。
- 前端通知轮询、角标、设置页、日志页与头像上传 UI。
- 任何 `schema.sql` / Flyway / Quartz DDL 改动；任何新表、新列、新索引。
- Kafka topic 保留期配置（部署票）、压测、备份恢复演练、依赖漏洞扫描。
- 头像孤儿对象清扫、上传限流、OSS 生命周期/版本控制。
- M14 二期能力与多队/平台超管。
- 实施票拆分本身（由后续 to-tickets 产出）。

---

## Further Notes

### 实施票序（交 to-tickets）

| 票 | 文件 | Blocked by |
|---|---|---|
| 01 | `01-m13-oss-gateway-and-upload.md` | — |
| 02 | `02-avatar-signed-read-and-key-validation.md` | 01 |
| 03 | `03-m09-notification.md` | — |
| 04 | `04-m11-settings.md` | — |
| 05 | `05-contracts-and-lists.md` | — |
| 06 | `06-real-sms-wechat.md` | — |
| 07 | `07-m10-audit-query.md` | — |
| 08 | `08-audit-annual-export.md` | 01、07 |
| 09 | `09-retention-job.md` | 03 |

建议每票单独会话、票间 `/clear`；`01`/`03`/`04`/`05`/`06`/`07` 可并行，`02` 等 `01`，`08` 等 `01` 与 `07`，`09` 等 `03`。

### 已在本规格中消解的歧义

- `avatarUrl` 的读/写语义：读响应现签 URL；写请求只接受 FIL 返回的 `objectKey`；数据库与审计只存对象键。
- FIL 只上传不绑定；绑定统一走 PLR-03/PLR-06。
- `MeView.player` 补 `avatarUrl` 是 05 §4 已有语义的落地，不是新契约。
- AUD-01 脱敏只在响应层；库内与年度冷备保留原文。
- 既有分组审计动作名大小写不一致，本批按原值返回，不做数据迁移。
- SET-02 不带 `version`，最后写入生效；只影响后续建赛与分组。
- 通知 90 天删除、邀请 30 天删除、Outbox FAILED 7 天删除均走物理删除；`@TableLogic` 必须绕开。
- 短信成功才落码；微信失败按 errcode 分类，不自动重试。
- prod fail-fast 的 profile 由 B0 提供，本批只在适配器层校验自己的配置键。

### 已知限制

- 通知 90 天删除按 `created_at`，无前导索引；单队数据量可接受。
- 通知角标由前端 60 秒轮询（B3/B4 实现），本批只提供 NTF-02。
- 设置无并发版本控制，后写生效。
- 真实 OSS/短信/微信依赖外部凭证；本批只用假端口与 dev mock 验证，真机验收在 B0/B4 与上线检查单。
- 审计年度导出默认关闭，开启后按年幂等；失败靠 ERROR 日志与 runbook 手工补导。
- 上传不限流，账号被盗可刷存储；孤儿对象不清扫。

### 交付结果（交付后回填）

| 票 | 提交 | 状态 |
|---|---|---|
| 01 | `086473a` | 已交付 |
| 02 | `ae619be` | 已交付 |
| 03 | — | — |
| 04 | — | — |
| 05 | — | — |
| 06 | — | — |
| 07 | `de1c59b` | 已交付 |
| 08 | `5cb399a` | 已交付 |

### 完成标准

- 后端门：05 的 92 个接口全部实现；`mvn verify` 全绿（含 ArchUnit）；空库 Flyway 幂等；无 schema/Flyway/Quartz DDL 改动。
- 行为门：本规格 User Stories 全部可演示；dev/test mock 可启动；prod fail-fast 生效。
- 文档：契约以 05 §2.7/§3.10–3.15/§4 为准；实施记录写 06；同步 `CLAUDE.md` 的代码状态。
