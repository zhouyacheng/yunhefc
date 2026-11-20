# 02 — 通知触发矩阵与 handler 现状

> 研究票：`.scratch/mvp-release/issues/02-research-notification-matrix.md`
> 只读盘点；事实截至当前工作区。标注约定：**[事实]** 代码/文档可直接核对；**[推断]** 由代码或文档组合得出；**[待决策]** 需 07 票定稿。

## 0. 范围与口径

- 事实源：需求 NOTIFY-01~05、DQ-12；`docs/design/04.数据库设计.md` §2.4/§5.22/§10；`docs/design/05.API接口设计.md` §3.10、§4；`docs/design/06.设计方案评审结果.md` §3.2、§32、§41；通知模块与各域发布点。
- **[事实]** 05 §3.10 只定义 NTF-01~04 四个读取/已读接口，未定义 `MATCH_CREATED` / `MATCH_SIGNUP_CLOSED` / `MATCH_LOCKED` / `MATCH_STARTED` / `MATCH_FINISHED` 的通知事件或触发契约（`docs/design/05.API接口设计.md:283-290`）。这五个类型名只出现在 04 §2.4/§5.22、06 §3.2 与 `NotificationType` 枚举中（`docs/design/04.数据库设计.md:82`、`:489`；`docs/design/06.设计方案评审结果.md:47`；`backend/src/main/java/com/yunhefc/notification/domain/NotificationType.java:10-14`）。
- 通知链路：业务事务写 Outbox → `OutboxRelay` 按事件类型选 topic → `NotificationEventConsumer` → `NotificationEventHandler` 写 `notification`。通知事件白名单 7 个（`backend/src/main/java/com/yunhefc/contracts/messaging/EventTypes.java:19-22`）；未在白名单的事件会被 relay 直接抛异常（`backend/src/main/java/com/yunhefc/infra/outbox/OutboxRelay.java:81-88`）。

## 1. 结论先行

1. **[事实]** 现有 7 类通知全部经 Outbox/Kafka 异步实现：报名成功、候补递补、比赛延期、比赛取消、账号审核通过、账号注销、角色变更（`EventTypes.java:19-22`；`NotificationEventHandler.java:104-146`）。
2. **[事实]** `NotificationType` 中 2–6（比赛创建/报名截止/锁定/开赛/结束）只有枚举与 DB 编码，没有对应 `EventTypes` 常量、没有任何发布点、handler 也不处理；消费者白名单不包含它们。
3. **[事实]** 需求文档内部对“普通比赛状态变更是否通知”存在冲突：需求 §9.2 与 MATCH 业务规则 8、02 §6.4 要求通知；NOTIFY-01、12.5 规则 26 与 06 §41 只要求/实现报名成功、递补、改期、取消。**[待决策]** 由 07 票定稿。
4. **[事实]** NTF-01~04 后端接口、C 端通知区块、未读角标、前端轮询全部未实现；仅 AUTH-10 返回 `unreadCount`（`AuthService.java:170-181`）。
5. **[事实]** NOTIFY-02 分享链接只有原型实现，正式前端比赛详情没有分享按钮或复制逻辑。
6. **[事实]** 通知 90 天保留只是文档约束，`DataRetentionService` 未清理 `notification`。
7. **[事实]** NOTIFY-05/DQ-12 的“实时逐条、不聚合”在现有 7 类事件上成立；已实现类型的文案不含手机号。

## 2. 源事件 → 通知类型 → 收件人 → 文案 → 现状

| # | 源事件（发布点） | 通知类型 | 收件人 | 文案要点 | 现状 |
|---|---|---|---|---|---|
| 1 | `REGISTRATION_CONFIRMED`：报名落库且状态为 CONFIRMED（`backend/src/main/java/com/yunhefc/registration/application/RegistrationApplicationService.java:95-97`） | 0 `REGISTRATION_CONFIRMED` | 报名本人（payload `userId`） | 标题“报名成功”；正文“你已成功报名「{season}赛季第{seasonMatchNo}场（累计{totalMatchNo}场） {matchTitle}」，请留意赛前通知。”；`ref_type=MATCH` | **已实现**：`NotificationEventHandler.java:109-115`、`NotificationType.java:8` |
| 2 | `WAITLIST_PROMOTED`：取消/管理员移除/注销编排触发同池最早候补递补（`RegistrationCancellationService.java:101-111`；调用方 `RegistrationApplicationService.java:132`、`RegistrationAdminApplicationService.java:62`、`RegistrationLifecycleApplicationService.java:45-50`） | 1 `WAITLIST_PROMOTED` | 被递补者本人（payload `userId`） | 标题“候补递补成功”；正文“你已递补为「{season}赛季第{seasonMatchNo}场（累计{totalMatchNo}场） {matchTitle}」的正式报名…”；`ref_type=MATCH` | **已实现**：`NotificationEventHandler.java:116-122`、`NotificationType.java:9` |
| 3 | `MATCH_POSTPONED`（`rescheduled=false`）：MAT-07 延期（`MatchAdminApplicationService.java:298`、`:372`）及 MAT-12 C 端延期分支（`:206-220`） | 7 `MATCH_POSTPONED` | 该场 CONFIRMED + WAITLISTED 报名者（`NotificationEventHandler.java:68-77`；`RegistrationReadAdapter.java:113-119`） | 标题“比赛延期”；正文含比赛名与新开始时间（`M月d日 HH:mm`） | **已实现**：`NotificationEventHandler.java:92-97`、`NotificationType.java:15` |
| 4 | `MATCH_POSTPONED`（`rescheduled=true`）：MAT-11 重新排期（`MatchAdminApplicationService.java:332`、`:372`） | 7 `MATCH_POSTPONED`（复用，不产生 2 `MATCH_CREATED`） | 同上 | 标题“比赛恢复报名”；正文“已重新排期至 …” | **已实现**：`NotificationEventHandler.java:92-97` |
| 5 | `MATCH_CANCELLED`：MAT-08 取消（`MatchAdminApplicationService.java:363`）及 MAT-12 取消分支（`:233-237`） | 8 `MATCH_CANCELLED` | 该场 CONFIRMED + WAITLISTED 报名者 | 标题“比赛取消”；正文含取消原因 | **已实现**：`NotificationEventHandler.java:86-90`、`NotificationType.java:16` |
| 6 | `ACCOUNT_APPROVED`：PLR-07 审核通过 PENDING→ACTIVE（`AccountLifecycleApplicationService.java:64-77`，事件在 `:74`） | 10 `ACCOUNT_APPROVED` | 被审核用户本人 | 标题“账号审核通过”；正文“你的账号已审核通过，现在可以报名参加比赛” | **已实现**：`NotificationEventHandler.java:123-129`、`NotificationType.java:18` |
| 7 | `ACCOUNT_DISABLED`：PLR-09 注销 ACTIVE→DISABLED（`AccountLifecycleApplicationService.java:139`） | 11 `ACCOUNT_DISABLED` | 被注销用户本人 | 标题“账号已注销”；正文“历史数据将保留” | **已实现**：`NotificationEventHandler.java:130-136`；但注销后无法登录（`AuthService.java:151`），站内信不可见（见 §5 待决策） |
| 8 | `ROLE_CHANGED`（ROL-05）：角色权限保存后通知该角色当前 ACTIVE 用户（`RoleService.java:122-132`、`:181-188`；收件人 SQL `UserRoleMapper.java:82-88`） | 9 `ROLE_CHANGED` | 该角色下全部 ACTIVE 用户 | 标题“角色变更”；正文“你的角色已更新：{roles}” | **已实现**：`NotificationEventHandler.java:137-143`、`NotificationType.java:17`；`roles` 直接拼接集合 `toString()`（`:140`） |
| 9 | `ROLE_CHANGED`（ROL-07）：用户角色分配（`RoleService.java:143-172`，事件在 `:169`） | 9 `ROLE_CHANGED` | 被调整用户本人 | 同上 | **已实现**：同上；05 §3.13（`docs/design/05.API接口设计.md:317`）、06 P-25（`docs/design/06.设计方案评审结果.md:164`、`:410`） |

## 3. 已定义类型但无触发：2–6

| 通知类型 | 设计依据 | 事件发布点 | Handler | 现状 |
|---|---|---|---|---|
| 2 `MATCH_CREATED`（比赛创建） | 04 §2.4/§5.22（`docs/design/04.数据库设计.md:82`、`:489`）；需求 §9.2“比赛创建/恢复通知”（`docs/云河FC-需求文档.md:720`） | 创建流程 `MatchAdminApplicationService.java:64-82`、`persist()` `:569-575` 只写审计，无 Outbox | 无 | **缺事件 + 缺 handler**；重新排期“恢复”实际复用 type 7 |
| 3 `MATCH_SIGNUP_CLOSED`（报名截止） | 同上；需求 §9.2“报名截止通知”（`:721`） | 手动状态修改 `MatchAdminApplicationService.java:195-231`、自动流转 `MatchAutoStatusApplicationService.java:35-92` 均无 Outbox | 无 | **缺事件 + 缺 handler** |
| 4 `MATCH_LOCKED`（锁定） | 同上；需求 §9.2“锁定通知”（`:722`） | 同上 | 无 | **缺事件 + 缺 handler** |
| 5 `MATCH_STARTED`（开赛） | 同上；需求 §9.2“开赛提醒”（`:723`） | 同上 | 无 | **缺事件 + 缺 handler** |
| 6 `MATCH_FINISHED`（结束） | 同上；需求 §9.2“结束通知”（`:724`） | 同上 | 无 | **缺事件 + 缺 handler** |

补充事实：

- **[事实]** 即便发布上述事件，`EventTypes` 没有常量、`NOTIFICATION_EVENTS` 不含它们，`OutboxRelay.topicFor()` 会抛 `事件类型未配置 topic`（`OutboxRelay.java:81-88`）；即使直接投递到通知 topic，handler 也会在白名单判断处直接 return（`NotificationEventHandler.java:43-45`）。
- **[事实]** 需求内部存在两种口径：
  - 支持通知：需求 §9.2（`docs/云河FC-需求文档.md:718-726`）、MATCH 业务规则 8“比赛状态变更、延期、取消必须通知已报名者”（`:331`）、02 §6.4“普通状态直接重置并通知”（`docs/design/02.页面结构与交互设计.md:371`）。
  - 仅四类：NOTIFY-01“报名成功、候补递补、改期/取消、角色变更”（`docs/云河FC-需求文档.md:581`）、12.5 规则 26“报名成功、递补、改期、取消产生站内消息”（`:862`）、06 §41“其他状态流转（截止/锁定/开赛/结束）不发通知事件”（`docs/design/06.设计方案评审结果.md:499`）。
- **[待决策]** 07 票需明确：启用 2–6 并补事件/handler，还是删除类型并把 NOTIFY 口径收敛为四类；以及“普通状态重置”是否纳入。

## 4. 账号生命周期中未产生站内信的相邻发布点

| 源事件 | 当前行为 | 需求/设计依据 | 现状 |
|---|---|---|---|
| 自助注册进入 PENDING | 仅审计 `ACCOUNT_REGISTER`，无 Outbox、无通知（`AuthService.java:91-137`，审计在 `:131-133`） | 需求 §9.1 通知列“注册成功提示待审核”（`docs/云河FC-需求文档.md:712`） | **[待决策]** “提示”是注册响应/页面提示还是站内信；无对应 `NotificationType` |
| INV-02 入队邀请接受：PENDING→ACTIVE、移除 GUEST、授予 PLAYER | 仅审计 `INVITE_ACCEPT`，不发 `ACCOUNT_APPROVED` / `ROLE_CHANGED`（`InviteService.java:141-180`） | 05 INV-02（`docs/design/05.API接口设计.md:171`）；需求 12.5 规则 27“入队邀请注册免审”（`docs/云河FC-需求文档.md:863`） | **[待决策]** 免审场景是否仍需“审核通过/角色变更”站内信 |
| PLR-08 外挂转正：移除 GUEST、授予 PLAYER | 仅审计 `PLAYER_CONVERT`，无 Outbox（`AccountLifecycleApplicationService.java:84-105`） | 05 PLR-08 未写通知（`docs/design/05.API接口设计.md:186`）；02 仅画“角色调整”流程（`docs/design/02.页面结构与交互设计.md:549`） | **[事实]** 当前无通知；**[待决策]** 是否纳入“角色变更” |

## 5. NTF-01~04 与 C 端展示缺口

| 项 | 契约 | 代码/页面现状 | 差距 |
|---|---|---|---|
| NTF-01 列表 | `GET /c/notifications`，`unread,page,pageSize`，`Page<Notification>`，倒序（`docs/design/05.API接口设计.md:287`） | 无 notification controller；`NotificationMapper` 只有 `countUnread` 与 `insertBatch`（`NotificationMapper.java:14-31`） | **缺接口/查询实现** |
| NTF-02 未读角标 | `GET /c/notifications/unread-count` → `{count}`（`docs/design/05.API接口设计.md:288`） | 无该接口；AUTH-10 `GET /c/me` 已返回 `unreadCount`（`docs/design/05.API接口设计.md:157`；`AuthService.java:170-181`；`AuthDtos.java:78-84`） | **缺独立接口**；AUTH-10 字段已存在 |
| NTF-03 单条已读 | `POST /c/notifications/{id}/read` → `{readAt}`，仅本人（`docs/design/05.API接口设计.md:289`） | 无 controller，无 update 方法 | **缺接口** |
| NTF-04 全部已读 | `POST /c/notifications/read-all` → `{updated}`，仅本人未读（`docs/design/05.API接口设计.md:290`） | 无 controller，无 update 方法 | **缺接口** |
| PENDING 白名单 | NTF-01~04 对 PENDING 放行（`docs/design/05.API接口设计.md:110`） | `PendingAccountFilter` 已登记四条路由（`PendingAccountFilter.java:43-56`） | 仅白名单就绪，接口不存在 |
| C06 通知区块 | 个人中心展示未读标记、单条已读、全部已读；MVP 不新增独立通知页（`docs/design/02.页面结构与交互设计.md:314-317`、`:624`） | C 端 `/account` 只有身份卡与退出（`frontend/apps/web/src/pages/AccountPage.vue:18-30`）；路由无通知页（`frontend/apps/web/src/router/index.ts:38-49`） | **缺页面区块** |
| 未读角标/轮询 | NTF-02 用于角标；07 票待定轮询策略（`issues/07-grilling-notification.md:14`） | `MeView.unreadCount` 类型已存在（`frontend/packages/shared/src/auth.ts:44-50`），但 session `apply()` 只保存 user/player/permissions（`frontend/apps/web/src/stores/session.ts:17-22`）；AppShell 仅“赛程/我的”两个 Tab（`AppShell.vue:29-32`）；全前端无通知/轮询代码 | **缺角标与轮询；AUTH-10 字段被丢弃** |
| 通知 DTO | `id,type,typeLabel,title,body,refType?,refId?,readAt?,createdAt`（`docs/design/05.API接口设计.md:363`） | 前端无对应类型（`frontend/packages/shared/src` 仅 `MeView.unreadCount`） | **缺前端类型** |

## 6. 分享链接（NOTIFY-02 / MATCH-13）

- **[事实]** 设计明确不新增接口：前端基于稳定 URL `/matches/{id}` 复制/微信分享（`docs/design/05.API接口设计.md:15`、`:211`；`docs/design/06.设计方案评审结果.md:39`；需求 MATCH-13 `docs/云河FC-需求文档.md:317`）。
- **[事实]** C03 头部设计要求“分享按钮”（`docs/design/02.页面结构与交互设计.md:246`）；原型已实现复制当前地址并 toast（`prototype/assets/js/public.js:290`、`:585-593`）。
- **[事实]** 正式前端 `MatchDetailPage.vue` 头部只有类型/状态/场次/比分/时间/场地（`frontend/apps/web/src/pages/MatchDetailPage.vue:640-654`），无分享按钮；`frontend/apps/web`、`frontend/packages` 无 `navigator.share` / `clipboard` 调用。
- **[待决策]** 微信内是否只做复制链接，还是按 02 §4.1 的“分享能力后续接 JS-SDK”（`docs/design/02.页面结构与交互设计.md:147`）纳入 MVP。

## 7. 保留、频次与敏感信息

| 约束 | 依据 | 现状 |
|---|---|---|
| 通知保留 90 天 | 需求 `docs/云河FC-需求文档.md:795`；04 §10（`docs/design/04.数据库设计.md:627`）；03 §（`docs/design/03.整体架构设计.md:508`） | **缺清理**：`DataRetentionService.cleanup()` 只清幂等键、消费去重、已发送 Outbox（`DataRetentionService.java:23-24`、`:31-37`）；既有 Quartz 任务可直接承载（`DataRetentionQuartzConfig.java:21-28`） |
| 实时逐条、不聚合/免打扰（NOTIFY-05、DQ-12） | 需求 `docs/云河FC-需求文档.md:585`、`:589-592`；01 `docs/design/01.功能模块梳理.md:510` | **已实现**：每个事件/收件人写一行，延期/取消批量插同一事件的通知（`NotificationEventHandler.java:56`、`:68-76`），无聚合逻辑 |
| 消费幂等 | 06 §41（`docs/design/06.设计方案评审结果.md:501`） | **已实现**：消费组 + `consumed_event` 去重，去重与写通知同事务（`NotificationEventHandler.java:39-49`） |
| 文案不含手机号等敏感信息 | 需求 6.9 规则 4（`docs/云河FC-需求文档.md:592`） | **已实现类型满足**：handler 文案只用比赛标题、时间、原因、角色；报名/考勤/比赛事件的 payload 未携带手机号 |
| 注销用户可达性 | 需求 §9.1 注销通知（`docs/云河FC-需求文档.md:714`） | **[事实]** 通知行会写入，但 DISABLED 无法登录（`AuthService.java:151`），C 端无查看途径；**[待决策]** 是否属于可接受的“通知已产生但不可读” |

## 8. 其他业务域发布点核对

- **[事实]** 考勤（签到 ATT-01、管理员标记 ATT-05/06、赛后 +6h 自动缺勤）只写审计，无 `Outbox`/`EventTypes` 通知事件（`AttendanceApplicationService.java:168`、`:209`；`AttendanceAbsenceApplicationService.java:30-62`）。需求未定义考勤通知，不构成 NOTIFY-01~05 的缺口。
- **[事实]** 比赛事件、阵容、分组模块无 `Outbox` / `EventTypes` 引用；需求未定义这些领域的通知触发。
- **[事实]** 管理员移除确认报名只通知被递补者，不通知被移除者（`RegistrationCancellationService.java:53-56`、`:101-111`）；需求 REG-06 只要求“移除并触发递补”，未要求通知被移除者。

## 9. 待决策清单（供 07 票）

1. **[待决策]** 2–6 类型启用还是删除；“普通状态重置通知”是否纳入，如何与 NOTIFY-01/12.5 对齐。
2. **[待决策]** 自助注册“提示待审核”的载体（响应/页面提示 vs 站内信）。
3. **[待决策]** INV-02 入队邀请、PLR-08 转正是否补发账号/角色通知。
4. **[待决策]** 注销通知在账号不可登录下的可达性定义。
5. **[待决策]** 未读角标轮询策略与 NTF-02 是否复用 AUTH-10 `unreadCount`。
6. **[待决策]** 分享链接 MVP 范围（复制链接 vs 微信 JS-SDK）。
7. **[待决策]** 90 天通知清理归属（扩展现有 `DataRetentionService` 或独立任务）。
