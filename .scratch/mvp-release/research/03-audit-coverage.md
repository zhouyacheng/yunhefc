# 03 — 审计留痕覆盖与脱敏现状

- 状态：resolved
- 日期：2026-11-11
- 范围：只读核对需求、设计文档、`backend/` 源码；未改代码、未执行 SQL。
- 标注约定：**【事实】** 代码/文档可直接验证；**【推断】** 由事实推导但无直接实现证据；**【待决策】** 需求/设计未明确、需产品/技术确认。

## 1. 必须留痕的写操作清单

| 需求来源 | 必须留痕操作 | 判定 |
|---|---|---|
| 需求 §11.1（`docs/云河FC-需求文档.md:777`） | 角色/权限、用户审核/注销、比赛状态、管理员移除报名、管理员标记考勤、事件变更、阵容/分组保存、邀请变更 | 硬性清单 |
| 需求 §6.1 规则 10（`:243`） | 角色/权限/用户角色变更 | 硬性清单 |
| 需求 AUTH-06（`:222`） | 管理员后台换绑手机号 | 硬性清单 |
| 需求 §6.1 规则 2（`:235`） | 微信解绑/换绑“全程留痕” | 硬性清单（范围含绑定/解绑） |
| 需求 §6.3 规则 8（`:331`） | 比赛状态变更、延期、取消，且记录前后快照 | 硬性清单 + 快照 |
| 需求 §6.4 规则 6（`:378`） | 取消/移除报名“保留审计” | 表述较宽，是否含球员自助取消见 §8 |
| 需求 §8 规则 19（`:701`） | 角色/权限变更、管理员操作、事件变更、邀请变更 | “管理员操作”为开放集合 |
| 需求 §11.4（`:795`） | 审计日志在线永久保留，每年导出 OSS 冷备 | 保留/导出要求 |
| 需求 §12.6 #30（`:869`） | 操作日志可通过管理后台查询（注明“MVP 后端已具备”） | 查询要求 |
| 设计 01 §4.11（`docs/design/01.功能模块梳理.md:280-292`） | 留痕范围、操作人/动作/对象/时间/前后值、后台查询 | 设计确认 |
| 设计 03 §5.4（`docs/design/03.整体架构设计.md:335-362`） | 审计年度导出 OSS Quartz 任务 | 设计确认 |
| 设计 05 §3.11/§4（`docs/design/05.API接口设计.md:292-296,364`） | AUD-01 查询、快照脱敏、IP 脱敏 | API 契约 |
| 设计 04 §2.3/§5.23/§7（`docs/design/04.数据库设计.md:43-51,503-520,606`） | 审计不删除、永久保留、年度 OSS | DB 契约 |

## 2. 写入链路与机制事实

**【事实】** 写链路：应用服务显式调用 `AuditRecorder` → `OutboxService.append` 写 `outbox_event`（`AuditRecorder.java:42`）→ Outbox Relay → Kafka `yunhefc.audit.events`（`application.yml:69`）→ `AuditEventHandler` 落 `audit_log`（`AuditEventHandler.java:42-53`）。审计与业务同事务，异步落库。

**【事实】** `AuditRecorder` 只有 2 个入口：`recordCurrent(...)` 从 `CurrentUser` 取操作人（`AuditRecorder.java:23-27`），`record(teamId, actorUserId, actorName, ...)` 供系统任务/注册使用（`:29-43`）。快照直接 `ObjectMapper.writeValueAsString`（`:38-39,45-54`），**无任何脱敏逻辑**。

**【事实】** 生产代码 `auditRecorder.record*` 共 32 个调用点：attendance 2、registration 1、RoleService 1 个 helper、InviteService 4、AuthService 4、AccountLifecycle 3、MatchAutoStatus 1、MatchAdmin 6、Grouping 4、Lineup 1、Event 4、PlayerAdmin 1。RoleService helper（`RoleService.java:178-179`）被 5 处调用，故实际动作发射点为 **36 个**。测试代码另有 2 处 `audit_log` 断言（`backend/src/test/java/com/yunhefc/AuthFlowIT.java:140`、`MessagingReliabilityIT.java:157`），不计入生产覆盖。

**【事实】** `audit_log` 结构见 `scripts/sql/schema.sql:407-425` 与 `04.数据库设计.md:503-520`：`team_id、actor_user_id、actor_name、action、target_type、target_id、before_json、after_json、request_id、ip、created_at、is_deleted`；索引 `(team_id,created_at)`、`(target_type,target_id)`、`(actor_user_id,created_at)`。

## 3. 调用点矩阵（动作 → 目标类型 → 调用点 → before/after 快照 → 脱敏风险）

### 3.1 账号、角色、邀请

| 动作 | 目标类型 | 调用点 | before / after 快照字段 | 脱敏风险 |
|---|---|---|---|---|
| `ROLE_CREATE` | `ROLE` | `RoleService.java:91`（helper `:178-179`） | null / `RoleView{id,code,name,isDefault,permissions}`（DTO `RoleDtos.java:14`） | 无 |
| `ROLE_RENAME` | `ROLE` | `RoleService.java:102` | `{name}` / `RoleView` | 无 |
| `ROLE_DELETE` | `ROLE` | `RoleService.java:118` | `RoleView` / null | 无 |
| `ROLE_PERMISSIONS_UPDATE` | `ROLE` | `RoleService.java:130` | `[permissionCode]` / `RoleView` | 无 |
| `USER_ROLES_UPDATE` | `USER` | `RoleService.java:168` | `[roleCode]` / `[roleCode]` | 无 |
| `ACCOUNT_REGISTER` | `USER` | `AuthService.java:135-136` | null / `{status,role}` | 无手机号/密码 |
| `PASSWORD_RESET` | `USER` | `AuthService.java:204-206` | null / null | 未含 passwordHash |
| `PHONE_CHANGE` | `USER` | `AuthService.java:243-245` | `{phone}`（已 mask）/ `{phone}`（已 mask） | 已脱敏；`UserViewAssembler.maskPhone` 规则 `:79-85` |
| `WECHAT_UNBIND` | `USER` | `AuthService.java:257-259` | `{openid:"***"}` / null | 已脱敏；openid 未明文 |
| `USER_APPROVE` | `USER` | `AccountLifecycleApplicationService.java:71-73` | `{status=PENDING}` / `{status=ACTIVE}` | 无 |
| `PLAYER_CONVERT` | `PLAYER` | `AccountLifecycleApplicationService.java:103` | `{type,jersey,roles}` / 同上（helper `:178-184`） | 无 |
| `USER_DISABLE` | `USER` | `AccountLifecycleApplicationService.java:137-139` | `{status=ACTIVE}` / `{status=DISABLED,reason}` | `reason` 自由文本，可能含个人信息【推断】 |
| `INVITE_ACCEPT`（比赛邀请） | `INVITE` | `InviteService.java:158` | null / `InviteView{id,type,typeLabel,team,match,inviter{id,name,phoneMasked,status,roles,type},expiresAt,maxUses,usedCount,remainingUses,status,statusLabel,exhausted,createdAt}`（`AuthDtos.java:129-145,91-100`） | `phoneMasked` 为脱敏手机号；无 token/明文手机号 |
| `INVITE_ACCEPT`（入队邀请） | `INVITE` | `InviteService.java:179-180` | `{status:0}` / `{status:1,playerType:1}` | 无 |
| `INVITE_CREATE` | `INVITE` | `InviteService.java:216-218` | null / `{type,expiresAt,maxUses}` | 未含邀请 token |
| `INVITE_REVOKE` | `INVITE` | `InviteService.java:264-266` | `{status}` / `{status=REVOKED}` | 无 |

### 3.2 比赛、报名、考勤

| 动作 | 目标类型 | 调用点 | before / after 快照字段 | 脱敏风险 |
|---|---|---|---|---|
| `MATCH_CREATE` | `MATCH` | `MatchAdminApplicationService.java:575` | null / `MatchAuditSnapshot` | 无手机号/证件；含 `venue` 场地 |
| `MATCH_UPDATE` | `MATCH` | `MatchAdminApplicationService.java:186` | 快照 / 快照 | 同上 |
| `MATCH_STATUS_CHANGE` | `MATCH` | `MatchAdminApplicationService.java:229` | 快照 / 快照 | 同上 |
| `MATCH_POSTPONE` | `MATCH` | `MatchAdminApplicationService.java:297` | 快照 / 快照 | 同上 |
| `MATCH_RESCHEDULE` | `MATCH` | `MatchAdminApplicationService.java:331` | 快照 / 快照 | 同上 |
| `MATCH_CANCEL` | `MATCH` | `MatchAdminApplicationService.java:362` | 快照 / 快照 + `{reason}` | `reason` 自由文本【推断】 |
| `MATCH_AUTO_STATUS_CHANGE` | `MATCH` | `MatchAutoStatusApplicationService.java:83-84` | 快照 / 快照；`actor_user_id=null`、`actor_name=系统` | 无 |
| `REGISTRATION_REMOVE` | `REGISTRATION` | `RegistrationAdminApplicationService.java:63-65` | `{playerId,type,status,registeredAt}` / 同左 + `{status=REMOVED,cancelledAt,cancelledBy,promotedPlayerId,promoted}`（helper `:82-100`） | 无 |
| `ATTENDANCE_MARK` | `ATTENDANCE` | `AttendanceApplicationService.java:168-169` | `{matchId,playerId,status,source,recordedBy,recordedAt}` / 同左（helper `:314-323`） | 无 |
| `ATTENDANCE_TEMP` | `ATTENDANCE` | `AttendanceApplicationService.java:209-210` | 同左 / 同左 | 无 |

`MatchAuditSnapshot` 固定 26 字段（`MatchAuditSnapshot.java:16-44`）：`title,type,format,startAt,endAt,signupDeadline,cancelLock,venue,competition,season,round,homeTeamName,awayTeamName,ourSide,status,manualStatus,capacity,guestCapacity,waitlistCapacity,includeGuests,scoreHome,scoreAway,seasonMatchNo,totalMatchNo,statusUpdatedAt,statusUpdatedBy`。

### 3.3 事件、比分、阵容、分组、球员

| 动作 | 目标类型 | 调用点 | before / after 快照字段 | 脱敏风险 |
|---|---|---|---|---|
| `EVENT_CREATE` | `EVENT` | `EventApplicationService.java:153` | null / `{matchId,team,type,playerId,assistPlayerId,inPlayerId,outPlayerId,minute,half,penalty,version}`（helper `:493-507`） | 无 |
| `EVENT_UPDATE` | `EVENT` | `EventApplicationService.java:212` | 同左 / 同左 | 无 |
| `EVENT_DELETE` | `EVENT` | `EventApplicationService.java:245` | 同左 / `{deletedAt}` | 无 |
| `SCORE_UPDATE` | `MATCH` | `EventApplicationService.java:261-262` | `{scoreHome,scoreAway}` / `{scoreHome,scoreAway,derivedScoreHome,derivedScoreAway}`（`:509-518`） | 无 |
| `LINEUP_SAVE` | `LINEUP` | `LineupApplicationService.java:178` | `{matchId,side,formationTemplateId,version,starters{slotKey:playerId},bench[playerId]}` / 同左（`:474-509`） | 无 |
| `grouping.generate` | `team_match` | `GroupingApplicationService.java:154` | 已有可见分组时 `{matchId,manualWeight,autoWeight,rosterFingerprint,generatedAt,savedAt,players[{playerId,team,manualScore,autoScore,finalScore}]}`，否则 null / 同结构（`:558-577`） | 无 |
| `grouping.adjust` | `team_match` | `GroupingApplicationService.java:222` | 同结构 / 同结构 | 无 |
| `grouping.save` | `team_match` | `GroupingApplicationService.java:263` | 同结构 / 同结构 | 无 |
| `grouping.clear` | `team_match` | `GroupingApplicationService.java:286` | 同结构 / null | 无 |
| `PLAYER_UPDATE` | `PLAYER` | `PlayerAdminApplicationService.java:150` | `{name,jersey,type,position,secondaryPositions,region,avatarUrl,skill}` / 同左（`:199-210`） | `region` 为个人地区；`avatarUrl` 可能为 OSS 签名 URL【推断】 |

**【事实】** 自助修改球员（`PlayerSelfApplicationService.java:46-58`）委托 `PlayerAdminApplicationService.update`，因此也写 `PLAYER_UPDATE`，操作人为本人。

## 4. 未覆盖的写点

| 写操作 | 代码位置 | 现状 | 判定 |
|---|---|---|---|
| 微信绑定手机号 / 绑定 openid | `WechatAuthService.java:109,124-142,188` | 无 `AuditRecorder` 调用；仅解绑有 `WECHAT_UNBIND` | 与需求 §6.1 规则 2“全程留痕”存在差距【事实】 |
| 系统配置修改（SET-02） | `backend/src/main/java/com/yunhefc/settings/` 仅有 adapter/domain/mapper，无管理 controller/service | 05 `:303` 明确“写审计”，当前整体未实现 | 与 05 契约存在差距【事实】 |
| 球员自助报名 / 取消报名 | `RegistrationController.java:24,33` | 仅管理员移除写审计；自助报名/取消无审计 | 需求 §6.4 规则 6“取消/移除…保留审计”是否覆盖自助取消【待决策】 |
| 自助签到 | `AttendanceController.java:26` | 无审计；考勤行仅记录 `source/recorded_by` | §11.1 只要求“管理员标记考勤”【事实】；是否需留痕【待决策】 |
| 邀请自动过期 | `InviteExpiryJob`（`backend/src/main/java/com/yunhefc/auth/infrastructure/`） | 无审计 | “邀请变更”是否含系统过期【待决策】 |
| 阵型模板创建/改名/删除、另存模板 | `FormationTemplateApplicationService`、`LineupApplicationService.saveAsTemplate`（`:188`） | 无审计 | 不在 §11.1 明确清单【事实】；是否属“阵容保存”【待决策】 |
| 登录/登出/短信发送/通知已读 | 各 controller | 无审计 | 不在 §11.1 清单【事实】 |

补充【事实】：角色/权限/用户角色变更、审核/注销、比赛状态（含自动流转）、管理员移除报名、管理员考勤标记、事件增改删、阵容保存、分组生成/调整/保存/清除、邀请创建/接受/撤销、管理员换手机号、微信解绑均已覆盖。

## 5. 敏感字段与脱敏风险

| 项 | 证据 | 结论 |
|---|---|---|
| 快照序列化无统一脱敏 | `AuditRecorder.java:38-39,45-54` | 是否脱敏完全取决于调用点手工处理【事实】 |
| 落库无二次脱敏 | `AuditEventHandler.java:49-52` | `before_json/after_json/ip` 原样写入【事实】 |
| 手机号 | `AuthService.java:243-245` 已 mask；`InviteService.java:158` 的 `InviteView.inviter.phoneMasked` | 当前两处均为脱敏值【事实】 |
| openid | `AuthService.java:257-259` 替换为 `***` | 未落明文【事实】 |
| passwordHash/token | 所有快照均未包含；注册/重置密码快照不含 hash；邀请创建不含 token | 未发现明文【事实】 |
| 地区/地址 | `PLAYER_UPDATE` 快照含 `region`（`PlayerAdminApplicationService.java:206`） | 个人信息类字段进审计【事实】 |
| 头像 URL | `PLAYER_UPDATE` 快照含 `avatarUrl`（`:207`） | 若为 OSS 签名 URL，签名参数随快照长期保留【推断】 |
| 自由文本 | `USER_DISABLE.reason`、`MATCH_CANCEL.reason` | 可能夹带个人信息，无格式约束【推断】 |
| IP | `audit_log.ip`（`schema.sql:419`）、`AuditRecorder.java:41` | 原样明文存储；05 §4 要求响应 `ipMasked`，当前无 mask 实现【事实】 |
| 第二份明文副本 | `AuditRecorder.java:42` 将同一快照放入 Outbox `payload_json`，再经 Kafka 传输 | Outbox 已发送保留 7 天（`DataRetentionService.java:19,35`）；Kafka topic 保留期未见配置【事实/待决策】 |
| 统一脱敏工具 | `docs/rules/logging-rules.md:119` 要求 `com.yunhefc.common.util.MaskUtils` | 代码中不存在该类；现有 mask 为 `UserViewAssembler.maskPhone` 等局部实现【事实】 |

## 6. AUD-01 查询/脱敏/分页缺口

**接口契约【事实】**：`GET /b/audit-logs`，权限 `audit:view:any`，过滤 `actor,action,targetType,targetId,from,to,page,pageSize`，响应 `Page<AuditLog>`，快照脱敏后返回（`05.API接口设计.md:296`）；响应对象定义为 `id,actor?,action,targetType,targetId?,beforeRedacted?,afterRedacted?,requestId?,ipMasked?,createdAt`，其中 `phone/openid/passwordHash/tokenHash` 替换为 `***`（`:364`）。

**实现现状【事实】**：
1. 无 AUD-01 controller/service/DTO；`audit` 包只有 `AuditEventConsumer`、`AuditEventHandler`、`AuditRecorder`、`AuditLog`、`AuditLogMapper`。
2. 无查询语句/XML：`AuditLogMapper` 仅继承 `BaseMapper`（`AuditLogMapper.java:8`），没有 actor/action/targetType/targetId/from/to 条件查询。
3. 无分页实现；05 §2.2 要求 `page` 默认 1、`pageSize` 默认 10（可选 20/50/100，`05...md:51-66`），`PageResponse` 未接入审计。
4. 无响应脱敏层：领域对象直接暴露 `beforeJson/afterJson/ip`（`AuditLog.java:25-28`），没有 `beforeRedacted/afterRedacted/ipMasked` 映射。
5. 权限码已存在：种子 `audit:view:any`（`backend/src/main/resources/db/migration/V3__seed_team_roles_permissions_settings.sql:22`），侧边栏也登记（`AuthService.java:298`），但无接口消费该权限。
6. 不可变约束无代码兜底：`AuditLog` 标注 `@TableLogic is_deleted`（`AuditLog.java:30-31`）且 `BaseMapper` 暴露 update/delete；设计要求“不可编辑/删除”（`05...md:296`、`04...md:47`），当前无服务层限制。查询侧默认会过滤 `is_deleted=0`，与“永久保留”不冲突。
7. 索引可支撑 `team_id+created_at`（时间范围/团队）、`target_type+target_id`、`actor_user_id+created_at`（`schema.sql:422-424`）；`action` 无独立索引。

## 7. 永久保留与年度导出边界

**【事实】** 文档要求：审计日志在线永久保留、每年导出 OSS 冷备（需求 §11.4 `:795`；`04...md:47,606`；`03...md:362,508`；01 §4.11 “业务上全部保留，不设条数上限”）。

**【事实】** 在线保留：`DataRetentionService.cleanup()` 只删除过期幂等键、消费去重记录、已发送 Outbox（`DataRetentionService.java:23-38`），**不触碰 `audit_log`**，与永久保留一致。未发现任何审计删除路径。

**【事实】** 年度导出：`03 §5.4` 列有 Quartz 任务 `T7 审计年度导出 OSS`（`03...md:335-362`），06 A-06 亦确认“Quartz 任务补齐审计年度导出”（`06...md:63`）。`backend/src/main/java` 中 grep `export` 无结果，`quartz` 任务只有比赛流转、缺勤、邀请过期、Outbox、统计、清理，**没有 `AuditExportJob`/Quartz 配置**。

**【待决策】** 导出边界（导出格式、OSS 路径/生命周期、是否包含 `before_json/after_json` 明文、导出失败的补偿与校验、是否与在线数据同脱敏）文档未定义。

**【待决策】** Outbox（已发送保留 7 天，`DataRetentionService.java:19`）与 Kafka `yunhefc.audit.events` 中的审计载荷属于“第二份留存”；其保留期、脱敏与 OSS 冷备的关系未在代码/文档中明确。

**【推断】** 由于导出与 AUD-01 均未实现，目前审计数据只能通过数据库直接访问；需求 §12.6 #30“MVP 后端已具备”与代码现状不符。

## 8. 待决策清单（不做取舍）

1. 需求 §6.4 规则 6 的“保留审计”是否要求球员自助报名/取消也写 `audit_log`。
2. 自助签到是否纳入审计，还是仅依赖 `attendance.source/recorded_by`。
3. 微信“换绑全程留痕”是否必须覆盖用户重新授权绑定（当前仅解绑留痕）。
4. 邀请自动过期是否算“邀请变更”并留痕。
5. 阵型模板 CRUD/另存模板是否纳入“阵容保存”。
6. §8 规则 19“管理员操作”的开放边界：是否要求所有 B 端写接口都留痕。
7. AUD-01 的脱敏规则细节：`region`、`avatarUrl`、自由文本 `reason` 是否需脱敏/截断；IP 掩码格式。
8. 年度 OSS 导出的格式、脱敏、生命周期、失败补偿。
9. Outbox/Kafka 中审计载荷的保留期与脱敏要求。
