# 05 — 112 条 MVP 验收矩阵与测试覆盖对账

> 研究票：`.scratch/mvp-release/issues/05-task-acceptance-matrix.md`
> 只读盘点；事实截至当前工作区。标注约定：**[事实]** 文档/代码可直接核对；**[推断]** 由实现组合得出；**[待定]** 需验收检查单或后续票定稿。

## 0. 口径

- 事实源：`docs/云河FC-需求文档.md` 需求清单与 §12 验收标准；`frontend/e2e`、`frontend/**/*.test.ts`、`backend/src/test`；`CLAUDE.md` 验证清单。
- **[事实]** 需求文档中带 `✅ MVP` 的需求行恰好 **112 条**，与票面一致。分模块：AUTH 7、RBAC 7、PLAYER 7、TEAM 5、MATCH 15、REG 12、ATT 8、EVENT 15、LINEUP 8、GROUP 12、NOTIFY 4、INVITE 5、CONFIG 2、LIST 5。
- 不计入矩阵：二期 `MATCH-14`、`NOTIFY-04`；§13.2 的 11 条 Q（待确认）。需求表共 125 条 ID 行 = 112 MVP + 2 二期 + 11 Q。
- 验证方式取值：
  - `Playwright`：端到端页面路径已有自动化断言。
  - `Vitest`：前端包/纯函数单测。
  - `JUnit`：后端单元测试或 Spring Boot + Testcontainers 集成测试。
  - `人工演示`：已实现但只有视觉/交互细则，无自动化断言。
  - `无覆盖`：功能未实现或页面缺失，当前无法自动验证也无法演示。
- 现状取值：`已覆盖` / `部分覆盖` / `未覆盖`。
- 判定规则：纯后端业务规则以 JUnit 为主；用户路径优先 Playwright；展示/拖拽/布局类无 E2E 断言时记人工演示；功能未实现记无覆盖。前端缺失但后端接口有测试的，验证方式记 JUnit，并在备注标出 UI 缺口。

## 1. 结论先行

1. **[事实]** 112 条中：可自动化 **100 条**（Playwright 11、Vitest 1、JUnit 88）；需人工演示 **3 条**；当前无覆盖 **9 条**。
2. **[事实]** 无覆盖集中在：球队页整页缺失（TEAM-01~05 共 5 条）、头像 OSS 上传（PLAYER-05）、比赛分享链接（MATCH-13）、通知分享（NOTIFY-02）、通知已读/未读（NOTIFY-03）。
3. **[事实]** 自动化基线：Playwright 2 个 spec、14 条测试；Vitest 3 个文件；后端 69 个 Java 测试文件（含 1 个测试基类）。
4. **[推断]** 后端业务规则覆盖较完整（认证、RBAC、比赛、报名、考勤、事件、阵容、分组、统计、邀请、幂等/并发）；最大风险在前端页面层：C 端无球队页/通知中心/分享按钮，B 端球员、角色、设置、审计、邀请仍为占位页。
5. **[事实]** 系统设置 API SET-01/02 未实现；CONFIG-01/02 只能通过建赛快照与报名策略测试间接验证。
6. **[事实]** 真实微信授权、短信、OSS 上传依赖外部环境，当前仅有 mock/字符串落库，需上线前人工验证。

## 2. 自动化盘点

### 2.1 Playwright（14 条）

| 文件 | 用例 | 覆盖需求 |
|---|---|---|
| `frontend/e2e/web.spec.ts` | 登录后看到真实赛程 | AUTH-01、MATCH-09（粗粒度） |
| 同上 | 登录后进入比赛详情并签到 | ATT-01、ATT-02、ATT-03 |
| 同上 | 比赛详情分组 Tab 只读展示 | GROUP-12、LINEUP-01（只读侧） |
| 同上 | 录入进球后时间线与推导比分更新 | EVENT-01、EVENT-06、EVENT-10、MATCH-12 |
| 同上 | 未登录访问受保护页跳登录并带 redirect | AUTH-07 |
| `frontend/e2e/admin.spec.ts` | 管理员登录后看到比赛列表 | AUTH-01、RBAC-04（入口）、MATCH-09 |
| 同上 | B03 选择比赛并标记考勤 | ATT-03、ATT-05 |
| 同上 | B06 生成分组并展示 A/B | GROUP-05、GROUP-06、GROUP-11 |
| 同上 | B06 调整 → 保存 → 清除 → 重新生成 | GROUP-07、GROUP-09、GROUP-12 |
| 同上 | B05 阵型模板复制、重命名、删除 | LINEUP-02、LINEUP-03 |
| 同上 | B05 选阵型并排满 8 人首发后保存，C03 只读查看 | LINEUP-01、LINEUP-04、LINEUP-08 |
| 同上 | 只读球员可查看分组但不能生成 | GROUP-11、RBAC-04（权限） |
| 同上 | B04 录入/编辑事件并保存比分 | EVENT-07、EVENT-10、EVENT-11、MATCH-12 |
| 同上 | 侧栏按 sections 过滤，直访无权限分区显示 403 | RBAC-04、RBAC-05（权限边界） |

### 2.2 Vitest（3 个文件）

| 文件 | 覆盖 |
|---|---|
| `frontend/packages/api-client/src/index.test.ts` | envelope、401、CSRF、403 分类、Idempotency-Key、safeRedirect |
| `frontend/packages/permissions/src/index.test.ts` | 28 个权限码、格式、并集判断、后台分区映射 |
| `frontend/packages/shared/src/index.test.ts` | Page、枚举文案、事件/签到窗口、分组队名 |

### 2.3 JUnit（69 个 Java 文件，含 1 个测试基类）

主要测试类与模块映射：

| 模块 | 测试类 |
|---|---|
| 认证/RBAC | `AuthFlowIT`、`AuthHardeningIT`、`WechatAuthIT`、`CsrfIT`、`SessionIsolationIT`、`SessionSlidingIT`、`RoleManagementIT`、`M01ConcurrencyIT`、`AuthServiceTest`、`SmsCodeServiceTest` |
| 球员 | `PlayerAdminIT`、`PlayerApprovalIT`、`PlayerConvertIT`、`PlayerDisableIT`、`PlayerSelfProfileIT` |
| 球队/统计 | `TeamPageIT`、`StatsRollupIT`、`StatsAggregatorTest` |
| 比赛 | `MatchAdminIT`、`MatchEditIT`、`MatchLifecycleIT`、`MatchStatusIT`、`MatchAutoStatusIT`、`MatchPublicIT`、`MatchNotificationIT`、`MatchStatusMachineTest`、`MatchTimeRulesTest`、`MatchSnapshotTest` |
| 报名 | `RegistrationSignupIT`、`RegistrationCancelIT`、`RegistrationAdminRemoveIT`、`RegistrationRosterIT`、`RegistrationNotificationIT`、`RegistrationRateLimitIT`、`MyRegistrationsIT`、`RegistrationPolicyTest`、`RegistrationApplicationServiceTest` |
| 考勤 | `AttendanceCheckInIT`、`AttendanceAdminIT`、`AttendanceAbsenceIT`、`CheckInWindowTest` |
| 事件 | `EventCoreIT`、`EventAdminIT`、`EventGroupingIT`、`EventRulesTest`、`EventScoreTest` |
| 阵容 | `LineupBoardIT`、`FormationTemplateIT`、`LineupRulesTest` |
| 分组 | `GroupingIT`、`GroupingAlgorithmTest`、`GroupingCandidatesTest`、`GroupingObjectiveTest`、`GroupingScoreCalculatorTest`、`GroupingWarningsTest`、`RosterFingerprintTest` |
| 邀请 | `InviteFlowIT`、`InviteLifecycleIT` |
| 基础设施/契约 | `FlywayMigrationIT`、`IdempotencyIT`、`MessagingReliabilityIT`、`OutboxRelayTest`、`DataRetentionIT`、`ApiErrorMappingIT`、`PageQueryTest`、`ArchitectureTest` |

## 3. 账号与权限（AUTH / RBAC，1–14）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 1 | AUTH-01 | 手机号+密码注册/登录；手机号唯一；密码 8–64 位含字母数字 | JUnit | 已覆盖 | `AuthFlowIT.selfRegistrationLoginAdminAndTeamInviteFlow`；`AuthServiceTest` 覆盖唯一冲突；Playwright 登录路径 |
| 2 | AUTH-02 | 短信验证码；开发固定 123456；频率限制 | JUnit | 已覆盖 | `SmsCodeServiceTest`、`AuthFlowIT.sendSms`、`CsrfIT`；真实短信需人工 |
| 3 | AUTH-03 | 微信内网页授权；首次绑手机；openid 唯一 | JUnit | 已覆盖 | `WechatAuthIT.authorizeCallbackBindPhoneAndReplayProtection`；mock 模式，真实公众号授权需人工 |
| 4 | AUTH-04 | 开放注册待审核 GUEST；入队邀请免审 PLAYER；比赛邀请只引导 | JUnit | 已覆盖 | `AuthFlowIT`、`PlayerApprovalIT`、`InviteFlowIT` |
| 5 | AUTH-05 | 待审核可浏览不可写；注销不可登录 | JUnit | 已覆盖 | `AuthHardeningIT.pendingAccountMayUseWhitelistedWritesAndIsRejectedForOthers`、`PlayerDisableIT.oldSessionIsRejectedAfterDisable` |
| 6 | AUTH-06 | 短信重置密码；管理员换绑手机号并留痕 | JUnit | 已覆盖 | `AuthHardeningIT.passwordResetAllowsLoginWithNewPassword`、`adminCanChangePhoneAndUserLogsInWithIt`、`AuthServiceTest` |
| 7 | AUTH-07 | 未登录可看公开内容；报名时引导登录/注册 | Playwright | 已覆盖 | `web.spec.ts` 未登录跳登录并带 redirect；报名按钮引导仅页面文案，未 E2E |
| 8 | RBAC-01 | 权限点 `资源:动作:范围`；预定义 28 个，管理员不能新增 | Vitest | 已覆盖 | `permissions/src/index.test.ts` 校验数量、唯一性与格式 |
| 9 | RBAC-02 | 多角色权限取并集 | JUnit | 部分覆盖 | `RoleManagementIT`、`PlayerConvertIT.convertReplacesOnlyGuestRoleKeepingCustomRoles`；无专门“并集”断言，依赖 `hasPermission` |
| 10 | RBAC-03 | GUEST/PLAYER/ADMIN；ADMIN 叠加 PLAYER；GUEST 与 PLAYER 互斥 | JUnit | 已覆盖 | `AuthFlowIT`、`PlayerConvertIT`、`RoleManagementIT` |
| 11 | RBAC-04 | 角色 CRUD、权限勾选、用户分配、变更审计 | JUnit | 已覆盖 | `RoleManagementIT.roleCrudPermissionsAndGuards`；B 端角色页占位（`SectionPage.vue`） |
| 12 | RBAC-05 | 默认角色不可删；有用户拥有时不可删 | JUnit | 已覆盖 | `RoleManagementIT.roleCrudPermissionsAndGuards` |
| 13 | RBAC-06 | 最后一个 `role:manage` 用户不可降权 | JUnit | 已覆盖 | `RoleManagementIT`、`M01ConcurrencyIT.concurrentMutualAdminDemotionKeepsAtLeastOneAdmin` |
| 14 | RBAC-07 | 注销是账号状态，不删除历史数据 | JUnit | 已覆盖 | `PlayerDisableIT` |

## 4. 球员档案与球队页（PLAYER / TEAM，15–26）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 15 | PLAYER-01 | 档案字段：姓名、球衣号、头像、地区、主/副位置 | JUnit | 已覆盖 | `PlayerAdminIT.updateOnlyTargetColumnsAndRejectsDuplicateFormalJersey`、`PlayerSelfProfileIT` |
| 16 | PLAYER-02 | 位置枚举；1 主位置 + 多副位置 | JUnit | 已覆盖 | `PlayerSelfProfileIT.rejectsInvalidPositionAndSecondaryPositions` |
| 17 | PLAYER-03 | 外挂转正式；历史数据保留 | JUnit | 已覆盖 | `PlayerConvertIT`（含球衣冲突回滚、角色替换） |
| 18 | PLAYER-04 | 球员注销；不删历史 | JUnit | 已覆盖 | `PlayerDisableIT`（取消未开赛报名、递补、旧会话失效） |
| 19 | PLAYER-05 | 仅头像上传；比赛照片/视频二期 | 无覆盖 | 未覆盖 | 仅 `player.avatar_url` 字符串可写（`PlayerSelfProfileIT`）；无上传接口、无 OSS 代码；见 research 15 |
| 20 | PLAYER-06 | 管理员设置 1–5 星；用于分组并对外展示 | JUnit | 部分覆盖 | `PlayerAdminIT` 覆盖 skill 过滤/排序/校验；分组评分用 `GroupingScoreCalculatorTest`；球队页展示无 UI |
| 21 | PLAYER-07 | 球员可改姓名、地区、主/副位置；号码、星级、角色管理员维护 | JUnit | 已覆盖 | `PlayerSelfProfileIT`（字段白名单、部分清空、权限） |
| 22 | TEAM-01 | 按前锋→中场→后卫→门将分组；组内出场降序、号码升序 | 无覆盖 | 未覆盖 | 后端 `TeamPageIT.teamPlayersGroupsByPositionAndSortsByAppearancesThenJersey` 已覆盖接口；C 端无球队页路由，无法演示 |
| 23 | TEAM-02 | 每组默认前 10，可显示全部/收起 | 无覆盖 | 未覆盖 | `TeamPageIT.teamPlayersLimitCapsEachPositionGroupAndAllIgnoresLimit` 覆盖 limit；前端无页面 |
| 24 | TEAM-03 | 展示姓名/号码/位置/地区/头像/星级/赛季统计 | 无覆盖 | 未覆盖 | `TeamPageIT` + `StatsRollupIT` 覆盖数据；前端无球队页 |
| 25 | TEAM-04 | 匿名可见公开字段；手机号、考勤、账本、战术不公开 | 无覆盖 | 未覆盖 | 接口仅返回 `PlayerPublicSnapshot` 公开字段；无匿名球队页可演示 |
| 26 | TEAM-05 | 仅展示正式且正常球员；外挂不进入球队页 | 无覆盖 | 未覆盖 | `TeamPlayersApplicationService` 基于正式 active 名单；无页面/E2E |

## 5. 比赛管理（MATCH，27–41）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 27 | MATCH-01 | 创建/编辑比赛字段；截止/锁定由开始时间推导 | JUnit | 已覆盖 | `MatchAdminIT.createAppliesOptionalDefaultsAndDerivesTimes`、`MatchEditIT` |
| 28 | MATCH-02 | 比赛类型：正式/队内/训练/友谊 | JUnit | 已覆盖 | `MatchAdminIT.createValidatesTimeScoreSideAndFields`（type=9 拒绝、默认队内） |
| 29 | MATCH-03 | 本期仅 8 人制；9/11 人制预留 | JUnit | 已覆盖 | `MatchAdminIT` 默认 `8人制`；数据模型 `format` 字段 |
| 30 | MATCH-04 | 报名中→报名截止→锁定→进行中→已结束；分支取消/延期 | JUnit | 已覆盖 | `MatchStatusMachineTest` 全状态迁移 |
| 31 | MATCH-05 | 按截止/开赛/结束自动流转；管理员可覆盖 | JUnit | 已覆盖 | `MatchAutoStatusIT`（边界、追赶、手动/取消/延期跳过、幂等） |
| 32 | MATCH-06 | 关闭报名、延期、重排、取消、提前结束；任意状态重置 | JUnit | 已覆盖 | `MatchStatusIT.manualResetSupportsForwardBackwardAndTerminalRestore`、`MatchLifecycleIT` |
| 33 | MATCH-07 | 延期保留报名；重算截止/锁定；重排后回报名中 | JUnit | 已覆盖 | `MatchLifecycleIT.postponeFromEveryAllowedStatus...`、`rescheduleOnlyFromPostponed...` |
| 34 | MATCH-08 | 取消保留报名但作废；通知；不计缺勤 | JUnit | 已覆盖 | `MatchLifecycleIT.cancelKeepsRegistrations...`、`MatchNotificationIT.cancelNotifiesRegistrants...` |
| 35 | MATCH-09 | 赛程展示主客队/时间/星期/轮次/比分/类型/胜负平/场次编号与视觉位置 | 人工演示 | 部分覆盖 | `SchedulePage.vue` 已渲染卡片；Playwright 仅断言数量和“已锁定”，未断言星期、胜负、红色背景、类型/编号位置 |
| 36 | MATCH-10 | 赛事名称/赛季/轮次轻量字段；赛季仅四位年份 | JUnit | 已覆盖 | `MatchAdminIT.createValidates...`（season `"26"` 拒绝）、`MatchSnapshotTest` |
| 37 | MATCH-11 | 对外主/客队；队内橙/兰队；展示胜平负 | JUnit | 已覆盖 | `MatchPublicIT.summaryDerivesResultTextAndQueueCounts`；队名映射见 `shared/src/index.test.ts` |
| 38 | MATCH-12 | 详情状态/事件/比分；有事件时展示推导比分；详情可改状态 | Playwright | 已覆盖 | `web.spec.ts` 录入进球后比分变 1:0；`admin.spec.ts` B04 保存/编辑事件；状态入口由 `MatchStatusIT` 覆盖后端 |
| 39 | MATCH-13 | 生成可分享链接，管理员转发微信群 | 无覆盖 | 未覆盖 | 前后端均无分享链接实现；仅原型 |
| 40 | MATCH-15 | “{年份}赛季第N场（累计M场）”；自动递增 | JUnit | 已覆盖 | `MatchAdminIT.allocatesSequentialMatchNumbersConcurrently`；页面展示未 E2E |
| 41 | MATCH-16 | 编辑时间/场地/名额；不可改赛季/类型/我方主客；名额不得低于已确认人数 | JUnit | 已覆盖 | `MatchEditIT`（不可变字段、容量下界、版本冲突、终态拒绝） |

## 6. 报名与候补（REG，42–53）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 42 | REG-01 | 未开始、报名中、未过截止才可报名 | JUnit | 已覆盖 | `RegistrationSignupIT.signupRequiresOpenMatchDeadlineAndPlayerProfile` |
| 43 | REG-02 | 正式/外挂独立名额；候补仅正式、全场合计 | JUnit | 已覆盖 | `RegistrationPolicyTest`、`RegistrationSignupIT.formal/guest...` |
| 44 | REG-03 | 已报名/候补/已取消/已移除 | JUnit | 已覆盖 | `RegistrationSignupIT`、`RegistrationCancelIT`、`RegistrationAdminRemoveIT` |
| 45 | REG-04 | 未满确认；正式满进候补；候补满拒绝；外挂满直接拒绝 | JUnit | 已覆盖 | `RegistrationPolicyTest` 六个分支 |
| 46 | REG-05 | 锁定前可自行取消；锁定后不可 | JUnit | 已覆盖 | `RegistrationCancelIT.cancelIsRejectedAfterLockOrWhenMatchIsNotActive` |
| 47 | REG-06 | 管理员移除并触发递补 | JUnit | 已覆盖 | `RegistrationAdminRemoveIT`（含并发移除） |
| 48 | REG-07 | 同池最早候补递补；通知本人 | JUnit | 已覆盖 | `RegistrationCancelIT.confirmedCancelPromotesEarliestWaitlisted`、`RegistrationNotificationIT` |
| 49 | REG-08 | 截止/锁定分别校验；当天 00:00 / 01:00 推导 | JUnit | 已覆盖 | `MatchTimeRulesTest.derivesDeadlineAndLockFromMatchDay`；报名侧由 `RegistrationSignupIT`/`CancelIT` 验证 |
| 50 | REG-09 | 同一比赛同一用户仅一条有效报名 | JUnit | 已覆盖 | `RegistrationSignupIT.duplicateActiveSignupIsRejectedButCancelledHistoryAllowsReapply` |
| 51 | REG-10 | 外挂需登录；比赛邀请引导注册/报名 | JUnit | 已覆盖 | `RegistrationSignupIT.guestSignupConfirmsOrIsRejectedWithoutWaitlist`、`InviteLifecycleIT.matchInviteIsConsumedOnce...` |
| 52 | REG-11 | 正式/管理员看名单；外挂只看自己 | JUnit | 已覆盖 | `RegistrationRosterIT.guestCannotReadRoster`、`MyRegistrationsIT` |
| 53 | REG-12 | 默认正式 30/外挂 4/候补 2；可配置；建赛可覆盖 | JUnit | 部分覆盖 | `MatchAdminIT.creationSnapshotsRegistrationSettings`、`RegistrationPolicyTest`；SET-01/02 未实现，系统设置改默认值无法验证 |

## 7. 考勤（ATT，54–61）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 54 | ATT-01 | 开赛前 4 小时至比赛当天 23:59 可签到 | Playwright | 已覆盖 | `web.spec.ts` 详情签到；边界由 `CheckInWindowTest`、`AttendanceCheckInIT` 覆盖 |
| 55 | ATT-02 | 仅已报名（确认）球员可自签 | JUnit | 已覆盖 | `AttendanceCheckInIT.rejectsCheckInOutsideWindowAndWithoutConfirmedRegistration` |
| 56 | ATT-03 | 正常/缺勤/临时到场 | JUnit | 已覆盖 | `AttendanceAdminIT.adminMarkCreatesAndOverwritesAttendanceWithAudit` |
| 57 | ATT-04 | 来源：自签/管理员/系统 | JUnit | 已覆盖 | `AttendanceCheckInIT`（SELF）、`AttendanceAdminIT`（ADMIN）、`AttendanceAbsenceIT`（SYSTEM） |
| 58 | ATT-05 | 管理员可标记/修正任意用户 | JUnit | 已覆盖 | `AttendanceAdminIT` |
| 59 | ATT-06 | 临时到场不占名额、计入出场、不计缺勤 | JUnit | 已覆盖 | `AttendanceAdminIT.tempPresentRequiresEligibleActiveTeamPlayer...`、`StatsAggregatorTest` |
| 60 | ATT-07 | 结束 +6h 未签到判缺勤；管理员可改 | JUnit | 已覆盖 | `AttendanceAbsenceIT`（状态时间回退、跳过取消/延期、幂等） |
| 61 | ATT-08 | 正式/管理员看全队；外挂只看自己 | JUnit | 已覆盖 | `AttendanceAdminIT.formalPlayerAndAdminSeeWholeTeam...`、`AttendanceCheckInIT.guestSeesOnlyOwn...` |

## 8. 事件与比分（EVENT，62–76）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 62 | EVENT-01 | 进球/乌龙/换人/红牌/黄牌；点球是进球标记 | JUnit | 已覆盖 | `EventCoreIT`、`EventAdminIT`、`EventRulesTest` |
| 63 | EVENT-02 | 进球可带助攻者 | JUnit | 已覆盖 | `EventRulesTest.assistAndPenaltyOnlyAllowedOnGoal`、`EventCoreIT` |
| 64 | EVENT-03 | 换人记录换上/换下；仅换人可填；仅管理员可增改删 | JUnit | 已覆盖 | `EventAdminIT.adminCreatesAnyEventTypeAndSubstitutionWithoutOwnershipLimit`、`EventCoreIT.fieldMatrix...` |
| 65 | EVENT-04 | 点球标记；计入进球与比分 | JUnit | 已覆盖 | `EventRulesTest`、`EventScoreTest` |
| 66 | EVENT-05 | 乌龙是我方球员，比分记给对方 | JUnit | 已覆盖 | `EventScoreTest.goalCountsForOwnTeamAndOwnGoalCountsForOpponent` |
| 67 | EVENT-06 | 球员可录/改自己事件，赛后 24h；队内/训练可任意球员；换人仅管理员 | JUnit | 已覆盖 | `EventCoreIT.createRespectsWindowTeamLockAndSubstitutionPermission`、`updateChecksOwnershipWindowVersionAndMatchState` |
| 68 | EVENT-07 | 管理员可改/删任意事件，不限时间 | JUnit | 已覆盖 | `EventAdminIT.adminUpdatesAnyEventWithoutOwnershipOrTimeWindow` |
| 69 | EVENT-08 | 仅管理员可删除 | JUnit | 已覆盖 | `EventAdminIT.playerCannotUseAdminEventEndpoints`、`deleteSoftDeletes...` |
| 70 | EVENT-09 | 创建/修改/删除留历史 | JUnit | 已覆盖 | `EventAdminIT.deleteSoftDeletesRemovesDerivedScoreAndKeepsHistory`、`DataRetentionIT` |
| 71 | EVENT-10 | 比分与事件独立；不一致提示不阻断 | Playwright | 已覆盖 | `admin.spec.ts` B04 保存不同比分后断言“不一致”提示；`EventAdminIT.scoreIsStoredIndependentlyAndReturnsDerivedScore` |
| 72 | EVENT-11 | 时间 1–120 分钟与半场均必填；默认上半场 | JUnit | 已覆盖 | `EventRulesTest.minuteMustBeOneToHundredTwenty`、`EventCoreIT.fieldMatrixRejects...` |
| 73 | EVENT-12 | 主/客队归属；队内橙/兰；有分组时按分组限制球员 | JUnit | 已覆盖 | `EventGroupingIT.savedGroupingRejectsEachWrongTeamPlayer`、`EventRulesTest.savedGroupingChecks...` |
| 74 | EVENT-13 | 上半场优先下半场；同半场分钟升序；同分钟进球优先 | JUnit | 已覆盖 | `EventCoreIT.timelineOrdersByHalfMinuteGoalPriorityThenIdAndHidesDeleted` |
| 75 | EVENT-14 | 仅换人显示/允许换上换下；其他类型隐藏并禁用 | JUnit | 部分覆盖 | `EventCoreIT.fieldMatrixRejectsStructuralAndBusinessViolations` 覆盖后端；C 端弹窗隐藏换人无 E2E |
| 76 | EVENT-15 | 时间轴居中；主队左、客队右 | 人工演示 | 部分覆盖 | `MatchDetailPage.vue` 已实现布局；无 E2E 断言左右/居中 |

## 9. 阵容与战术（LINEUP，77–84）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 77 | LINEUP-01 | 仅管理员编辑；正式球员只读；外挂/访客不可看 | Playwright | 已覆盖 | `admin.spec.ts` C 端只读查看；`LineupBoardIT.backendPermissionsSeparateViewAndEdit` |
| 78 | LINEUP-02 | 8 套预置阵型 | JUnit | 已覆盖 | `FormationTemplateIT.listsEightPresetsWithSlotsAndFiltersCustomTemplates`；E2E 断言 8 张预置卡 |
| 79 | LINEUP-03 | 复制预置并命名；自定义可重命名/删除；预置不可改删 | Playwright | 已覆盖 | `admin.spec.ts` B05 复制/重命名/删除；`FormationTemplateIT` 后端校验 |
| 80 | LINEUP-04 | 首发 8 人 + 替补列表；管理员可加入/移出 | Playwright | 已覆盖 | `admin.spec.ts` 排满 8 人保存；`LineupBoardIT.savesStartersAndBenchWithVersionAndWritesAudit` |
| 81 | LINEUP-05 | 对外比赛可设我方/对手方阵容；对手方仅阵型与占位槽 | JUnit | 部分覆盖 | `LineupBoardIT.opponentSideRecordsOnlyFormationAndEmptySlots`；前端 `LineupPage.vue` 未见对手方切换入口 |
| 82 | LINEUP-06 | 队内/训练可排双方；有分组时按橙/兰分组成员限制 | JUnit | 部分覆盖 | `LineupBoardIT.savedGroupingRestrictsEachSideToItsGroup`、`draftGroupingAndNoGroupingFallBack...`；E2E 未覆盖双列操作 |
| 83 | LINEUP-07 | 拖拽球员到位置 | 人工演示 | 部分覆盖 | E2E 用“点选球员→点槽位”而非拖拽；拖拽交互无自动化 |
| 84 | LINEUP-08 | 版本控制；并发保存冲突提示 | Playwright | 已覆盖 | `admin.spec.ts` 断言保存后版本号；`LineupBoardIT` 版本冲突、`LineupRulesTest` 规则 |

## 10. 队内赛分组（GROUP，85–96）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 85 | GROUP-01 | 输入为本场报名/加入名单；外挂按开关参与 | JUnit | 已覆盖 | `GroupingIT.filtersGuestsAndInactiveAccountsAccordingToTheMatchSwitch`、`GroupingCandidatesTest` |
| 86 | GROUP-02 | 手动星级 70% + 自动分 30%；全局可配 | JUnit | 已覆盖 | `GroupingScoreCalculatorTest`（权重、取整、非法权重回退） |
| 87 | GROUP-03 | 出场率 + 场均进球 + 场均助攻，候选池内归一化 | JUnit | 已覆盖 | `GroupingScoreCalculatorTest.normalizesGoalsAgainstTheCandidatePoolMaximum` 等 |
| 88 | GROUP-04 | 两者缺失默认 3 星；少于最少出场优先手动分 | JUnit | 已覆盖 | `GroupingScoreCalculatorTest.zeroAppearancesForcesManualScore`、`belowMinimumAppearances...` |
| 89 | GROUP-05 | 人数均分；≥2 门将每队至少 1；位置均衡；不满足警告 | JUnit | 已覆盖 | `GroupingObjectiveTest`、`GroupingWarningsTest` |
| 90 | GROUP-06 | 蛇形 + 局部搜索 + 随机因子/重启；总分差最小；不总是同一结果 | JUnit | 已覆盖 | `GroupingAlgorithmTest`（同种子稳定、不同种子变化、门将修复、局部搜索） |
| 91 | GROUP-07 | 可拖拽；实时显示总分差与位置分布 | Playwright | 部分覆盖 | `admin.spec.ts` 调整并断言跨队移动/总分差；真实拖拽交互仍属人工 |
| 92 | GROUP-08 | 保存后不随报名自动重算；名单变化提示重新生成 | JUnit | 已覆盖 | `GroupingIT.readReportsRosterDiffWithoutChangingTheSavedSnapshot`、`saveRequiresConfirmationForWarningsAndRosterChange` |
| 93 | GROUP-09 | 保存分数快照，历史可解释 | JUnit | 已覆盖 | `GroupingIT.generatesDraftPersistsScoresAndRequiresConfirmationToOverwrite` |
| 94 | GROUP-10 | 位置不足提示管理员，不阻断 | JUnit | 已覆盖 | `GroupingWarningsTest.reportsPositionCountDifference` |
| 95 | GROUP-11 | 仅“锁定”状态可生成/调整/保存 | Playwright | 已覆盖 | `admin.spec.ts` 生成分组；`GroupingIT.readAndGenerateApplyTypeStatusAndCandidateGates` |
| 96 | GROUP-12 | 展示全部确认报名；有分组分两列；未分组单列；可清除后重生成 | Playwright | 已覆盖 | `admin.spec.ts` 清除→生成；`GroupingIT.readReportsUngroupedAndRosterDiff...` |

## 11. 通知（NOTIFY，97–100）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 97 | NOTIFY-01 | 报名成功、候补递补、改期/取消、角色变更产生站内消息 | JUnit | 部分覆盖 | `RegistrationNotificationIT`、`MatchNotificationIT`、`RoleManagementIT`、`PlayerApprovalIT`、`PlayerDisableIT` 覆盖 Outbox→consumer→notification；C 端无通知列表 UI，无法端到端演示 |
| 98 | NOTIFY-02 | 比赛/报名页可分享到微信群 | 无覆盖 | 未覆盖 | 正式前端无分享按钮/复制逻辑；仅原型 |
| 99 | NOTIFY-03 | 已读/未读；支持标记已读 | 无覆盖 | 未覆盖 | 05 定义 NTF-01~04，但无 NotificationController；C 端无通知中心 |
| 100 | NOTIFY-05 | 实时逐条、不聚合；仅覆盖需求列出的必要触发 | JUnit | 部分覆盖 | 已实现 7 类事件逐条消费；普通状态类 2–6 未实现，口径见 research 02 与 07 票 |

## 12. 邀请链接（INVITE，101–105）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 101 | INVITE-01 | 入队邀请注册免审；加入球队 | JUnit | 已覆盖 | `InviteFlowIT.teamInviteActivatesPendingUserAndIsIdempotent` |
| 102 | INVITE-02 | 比赛邀请引导报名；管理员与正式球员可创建；仍需登录/状态校验 | JUnit | 已覆盖 | `AuthHardeningIT.matchInviteRequiresConfirmedMatchPlayerOrAdmin`、`InviteLifecycleIT.matchInviteIsConsumedOnceAcrossRegisterAndAccept` |
| 103 | INVITE-03 | 默认 7 天；默认不限次数，可设上限；可撤销/过期 | JUnit | 已覆盖 | `InviteLifecycleIT`（7 天、maxUses、过期扫描）、`M01ConcurrencyIT`（撤销/过期竞争） |
| 104 | INVITE-04 | 记录邀请人，便于追溯 | JUnit | 已覆盖 | `InviteLifecycleIT.inviteViewExposesMatchIdAndInviterSummaryWithoutInviteUrl` |
| 105 | INVITE-05 | 管理员可撤销；过期不可用 | JUnit | 已覆盖 | `InviteFlowIT`（撤销）、`M01ConcurrencyIT.revoked/expiredInviteCannotBeConsumed...` |

## 13. 系统配置（CONFIG，106–107）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 106 | CONFIG-01 | 默认正式 30/外挂 4/候补 2；可配置 | JUnit | 部分覆盖 | `MatchAdminIT.creationSnapshotsRegistrationSettings` 验证默认值与快照；SET-01/02 未实现，修改系统默认值无法验证 |
| 107 | CONFIG-02 | 新建比赛带入配置；创建时可覆盖；改配置不影响已建比赛 | JUnit | 部分覆盖 | 同上；建赛覆盖由 `MatchAdminIT` 覆盖，“改配置不影响已建比赛”因无 SET API 无法端到端验证 |

## 14. 列表、检索与分页（LIST，108–112）

| # | 需求 ID | 验收点 | 验证方式 | 现状 | 备注 |
|---|---|---|---|---|---|
| 108 | LIST-01 | 赛程按类型/地点/状态/年份/月份筛选；搜索生效；重置清空；默认时间降序 | JUnit | 部分覆盖 | `MatchPublicIT.publicListFiltersScopesSortsAndPaginates` 覆盖后端；前端 `SchedulePage.vue` 仅有状态下拉，无类型/地点/年份/月份与搜索/重置 |
| 109 | LIST-02 | 球员按姓名/地区模糊，类型/管理员/状态/位置/星级筛选；搜索后生效 | JUnit | 部分覆盖 | `PlayerAdminIT.listFiltersSortsAndValidatesPaging` 覆盖后端；B 端球员页为 `SectionPage.vue` 占位 |
| 110 | LIST-03 | 默认号码升序；可选号码/星级/位置/地区升降序 | JUnit | 部分覆盖 | `PlayerAdminIT` 覆盖 jersey/skill 排序；B 端球员页占位 |
| 111 | LIST-04 | 默认每页 10，可选 20/50/100；适用于赛程、比赛管理、球员、操作日志 | JUnit | 已覆盖 | `PageQueryTest`、`MatchPublicIT`、`PlayerAdminIT`、`MyRegistrationsIT`；前端 `SchedulePage` 有分页 |
| 112 | LIST-05 | 空结果提示，分页控件不报错 | JUnit | 部分覆盖 | `RegistrationRosterIT.emptyRosterAndCrossTeamAndUnknownMatch` 覆盖后端空结果；前端使用 Vant `Empty`，无 E2E |

## 15. 缺口汇总（只列事实与建议，不决定上线范围）

| 优先级 | 缺口 | 影响需求 | 建议验证/补齐 |
|---|---|---|---|
| P0 | C 端球队页完全缺失 | TEAM-01~05 | 上线前实现 `/team` 页面；补 Playwright：匿名访问、分组排序、前 10/展开、星级与统计、无手机号 |
| P0 | 分享链接前后端均无实现 | MATCH-13、NOTIFY-02 | 实现分享入口与复制/二维码；补 E2E |
| P0 | 通知中心与已读接口缺失 | NOTIFY-03（NOTIFY-01 端到端） | 实现 NTF-01~04 与 C 端通知列表/未读角标；补 E2E |
| P0 | 头像上传/OSS 无代码 | PLAYER-05 | 接入 OSS 私有 Bucket + 签名 URL；人工 + 接口测试 |
| P1 | B 端球员/角色/设置/审计/邀请为占位页 | RBAC-04、LIST-02/03、CONFIG-01/02 | 至少提供只读列表与设置编辑；补 Playwright 或明确人工步骤 |
| P1 | 系统设置 API SET-01/02 未实现 | CONFIG-01/02、REG-12、GROUP-02 | 实现设置读写与审计，补 JUnit |
| P1 | 赛程/事件时间线/阵容拖拽仅人工 | MATCH-09、EVENT-15、LINEUP-07 | 视觉验收清单 + 截图；拖拽补 Playwright `dragTo` |
| P2 | 真实微信/短信依赖 mock | AUTH-02/03 | 上线前真实环境人工冒烟 |
| P2 | 事件/阵容/分组部分 UI 交互未 E2E | EVENT-14/15、LINEUP-05/06、GROUP-07 | 按黄金路径补 Playwright |

## 16. 待定项

1. **[待定]** NOTIFY-01 与普通状态通知（`MATCH_CREATED`/`SIGNUP_CLOSED`/`LOCKED`/`STARTED`/`FINISHED`）的口径冲突：需求 §9.2 与 §12.5 不一致，见 research 02；由 07 票收敛后再定验收方式。
2. **[待定]** 后端 JUnit 通过是否可作为 UI 类验收的替代证据：本矩阵按“页面需求需页面验证”从严处理，球队页等记为无覆盖；验收检查单可决定是否接受接口级证据。
3. **[待定]** 真实微信网页授权、短信、OSS 的验收环境与账号归属（依赖 14、15 票）。
4. **[待定]** 分享链接的产品形态（复制 URL / 二维码 / 微信 JS-SDK）未在需求明确，影响 MATCH-13/NOTIFY-02 的验收步骤。
5. **[待定]** 「B 端占位页」是否属于 MVP 上线范围：若只要求后端能力，RBAC-04、LIST-02/03、CONFIG-01/02 可从部分覆盖提升为后端已覆盖；若要求可演示，则需补页面。
