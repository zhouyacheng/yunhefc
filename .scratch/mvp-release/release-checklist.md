# MVP 上线检查单

Status: active（模板；执行时逐项回填）
Version: v1.0（2026-11-12）
Owner: zyc
Decision record: `docs/design/06.设计方案评审结果.md` §53
Batch spec: [batches.md](batches.md)
Acceptance baseline: [research/05-acceptance-matrix.md](research/05-acceptance-matrix.md)

本文件是票 19 的产物：可直接执行、逐项打勾的上线检查单。**本票不执行检查**；执行发生在 B0–B4 完成后的生产 RC 验收与 cutover 阶段。

---

## §0 口径与签收规则

### 0.1 硬门槛与可延期

**硬门槛（任一未过即 no-go，不允许当天现场补救）**

- B0–B4 全部完成门通过（`batches.md` §2）。
- 112 条 MVP 验收全部通过（§3）；9 条当前无覆盖项由 B1–B4 补齐后必须有**页面级证据**。
- F0 外部证据齐全（§2），不能用 mock 替代生产。
- 安全 11 项通过（§6）。
- 空库 Flyway 幂等与发布前迁移通过（§5）。
- RC 发布与回滚演练各完成一次（B0 完成门）。
- 同源冒烟与首位管理员 runbook 登录通过（§5）。
- RDS 自动备份 / binlog / PITR 可恢复时间点在控制台可见（§5.4）。
- cutover 前 main 已冻结 ≥24 小时，最终签收在 cutover 前 24 小时内完成。

**可延期（不影响 112 条验收、安全基线与数据完整性）**

- UI 视觉细节、非阻断的易用性问题、性能优化、文档补充。
- 延期项逐条记入 §10，由签收人确认；不写入 06 作为设计变更。

### 0.2 验收环境

- **自动化证据**：PR CI（后端 `mvn verify`、前端 `pnpm typecheck/build/test`）+ main CI（Playwright E2E 使用 MySQL 8.4 + Redis 6.2.24 service 容器与真实后端；main 后端仅打包，不重复 verify）。合并用 merge commit 时以 PR head 的 verify run 为准，并在证据中记录 run URL。
- **人工演示与真实外部服务**：B4 完成后的**生产 RC**（不对外、不公告）。包括真实短信、小程序真机登录、OSS 上传与签名 URL、同源冒烟、首位管理员登录。
- 本地产物只用于开发自测，不作为签收证据。

### 0.3 时间与值守

- **代码冻结**：cutover 前 24 小时起 main 只接受发布/回滚相关改动（workflow、环境变量、runbook）；业务代码变更顺延。
- **签收**：cutover 前 24 小时完成 112 条签收与全部硬门槛核对；cutover 用的 `sha-<7>` 在签收时固定。
- **Cutover 窗口**：工作日 20:00 后。
- **观测期**：T+30 分钟即时判定；T+24 小时密切观察；T+72 小时稳定声明。
- **值守**：单人决策；发布结果通过钉钉/企微 webhook 通知；cutover 前后在队伍群公告。
- **回滚顺序**：先 `release.sh --rollback` 回代码；仅迁移不向后兼容或数据损坏才用 RDS PITR。

### 0.4 证据格式

| 类型 | 记录方式 |
|---|---|
| 自动化 | 测试类/用例名 + CI run URL + 结果 |
| 人工演示 | 截图或录屏路径 + 日期 + 操作人 + 环境 |
| 外部服务 | 控制台截图 / 命令输出 / 真机收码截图 / 小程序真机登录截图 |
| 配置检查 | 命令与原始输出（`curl -I`、`openssl`、`redis-cli` 等） |

---

## §1 Go/No-Go 硬门槛

| # | 门槛 | 证据 | 结果 | 日期 |
|---|---|---|---|---|
| 1 | B0 基座完成门 | `batches.md` §2 B0；RC 发布 + 回滚演练记录 | ☐ | |
| 2 | B1 后端与契约完成门 | `mvn verify` 全绿（PR run）；95/95 接口；空库 Flyway 幂等 | ☐ | |
| 3 | B2 B 端页面完成门 | 6 条页面 Playwright 全绿；`pnpm -r` 全绿 | ☐ | |
| 4 | B3 C-A 公开页完成门 | 7 条页面 Playwright 全绿；无 console error | ☐ | |
| 5 | B4 C-B 互动与小程序登录完成门 | C03 闭环 + 小程序真机登录验收 | ☐ 小程序登录真机已验（2026-11-20，见 `.scratch/miniprogram-login/spike.md`）；C03 闭环待 RC | |
| 6 | 112 条 MVP 验收全绿 | §3 签收表 | ☐ | |
| 7 | F0 外部证据齐全 | §2 | ☐ | |
| 8 | 安全 11 项通过 | §6 | ☐ | |
| 9 | 空库 Flyway 幂等 + 发布前迁移 | §5.1–§5.3 | ☐ | |
| 10 | 同源冒烟 + 首位管理员 runbook | §5.4–§5.5 | ☐ | |
| 11 | RDS 备份 / PITR 可见 | §5.6 | ☐ 不执行（2026-11-15 决策，§10 #18） | |
| 12 | 24 小时冻结 + 前 24 小时签收 | §0.3、§11 | ☐ | |

**结论**：☐ Go　☐ No-go（原因：____）

---

## §2 F0 外部证据

F0 与 B0/B1 同时启动，不等代码批次；每项必须有控制台/命令证据。

| # | 事项 | 最晚需要 | 证据形式 | 结果 |
|---|---|---|---|---|
| 1 | ECS 2C2G + 2GB swap + Docker + 系统盘 ≥40GB | B0 完成门 | `free -m`、`docker version`、`df -h` 输出 | ☐ |
| 2 | RDS MySQL 8.4（应用账号 DML / 迁移账号 DDL、私网白名单） | B0 完成门 | 控制台截图 + 两账号分别登录 | ☐ |
| 3 | www DNS（admin/api 子域已取消） | B0 完成门 | `dig` 输出 | ☐ |
| 4 | 证书只需覆盖 www（SAN 含 DNS:www 或 *.domain，剩余 >30 天） | B0 完成门 | `openssl x509 -enddate` + SSL Labs | ☐ |
| 5 | ACR 双仓库 + ECS 只读拉取凭证 | B0 完成门 | `docker manifest inspect` 双镜像 | ☐ |
| 6 | `/opt/yunhefc/.env`（600）+ 密码管理器离线副本 | B0 完成门 | `stat -c '%a %n'` + 密码管理器记录 | ☐ |
| 7 | RAM 用户 + dev/prod 独立私有 Bucket（`avatar/`、`audit/export/`） | B1 联调 | 控制台策略 JSON + Put/Get/签名 URL | ☐ |
| 8 | 短信 APPCODE / 签名 / 模板 | B4 真实验收 | 真机收到验证码截图 | ☐ |
| 9 | 小程序 AppID/Secret / request 合法域名 | B4 真实验收 | 小程序 `wx.login` + 绑定手机号成功页 | ☐ 真机已验（2026-11-20）；生产 HTTPS 域名待复验 |
| 10 | 域名 ICP 备案 | cutover | 备案号 + 公网 HTTPS 可访问 | ☐ |
| 11 | 首位管理员一次性 SQL（`bootstrap-admin.sql`） | B0 完成门 | B 端登录、28 权限、`/c/me` player 非空、已改密 | ☐ |

---

## §3 112 条 MVP 验收记录

口径：全部通过才可 cutover；页面需求必须有页面级证据（Playwright，或人工演示 + 截图）。基线见 [research/05-acceptance-matrix.md](research/05-acceptance-matrix.md)。「结果」执行时填「通过 / 不通过 / 人工通过」。

| # | 需求 | 验收点（简） | 验证方式 | 证据来源 | 环境 | 结果 | 日期 |
|---|---|---|---|---|---|---|---|
| 1 | AUTH-01 | 注册/登录、手机号唯一、密码规则 | JUnit | `AuthFlowIT`、`AuthServiceTest` | CI | | |
| 2 | AUTH-02 | 短信验证码与频率限制 | JUnit + 人工 | `SmsCodeServiceTest` + 真机收码 | CI / RC | | |
| 3 | AUTH-03 | 微信小程序登录、首次绑手机、mp_openid 唯一 | JUnit + 人工 | `MiniProgramAuthIT` + 真机 | CI / RC | | |
| 4 | AUTH-04 | 开放注册待审核；邀请注册免审 | JUnit | `AuthFlowIT`、`PlayerApprovalIT`、`InviteFlowIT` | CI | | |
| 5 | AUTH-05 | 待审核只读；注销不可登录 | JUnit | `AuthHardeningIT`、`PlayerDisableIT` | CI | | |
| 6 | AUTH-06 | 短信重置密码；管理员换绑留痕 | JUnit | `AuthHardeningIT`、`AuthServiceTest` | CI | | |
| 7 | AUTH-07 | 匿名可看公开内容；报名引导登录 | Playwright | `web.spec.ts` + B3 首页 CTA | CI | | |
| 8 | RBAC-01 | 28 个权限码、格式、不可新增 | Vitest | `permissions/src/index.test.ts` | CI | | |
| 9 | RBAC-02 | 多角色权限取并集 | JUnit | `RoleManagementIT`、`PlayerConvertIT` | CI | | |
| 10 | RBAC-03 | GUEST/PLAYER/ADMIN 互斥与叠加 | JUnit | `AuthFlowIT`、`RoleManagementIT` | CI | | |
| 11 | RBAC-04 | 角色 CRUD、权限勾选、用户分配、审计 | JUnit + Playwright | `RoleManagementIT` + B08 黄金路径 | CI / RC | | |
| 12 | RBAC-05 | 默认角色、有用户角色不可删 | JUnit | `RoleManagementIT` | CI | | |
| 13 | RBAC-06 | 最后一个管理员不可降权 | JUnit | `RoleManagementIT`、`M01ConcurrencyIT` | CI | | |
| 14 | RBAC-07 | 注销是状态，不删历史 | JUnit | `PlayerDisableIT` | CI | | |
| 15 | PLAYER-01 | 档案字段与球衣号唯一 | JUnit | `PlayerAdminIT`、`PlayerSelfProfileIT` | CI | | |
| 16 | PLAYER-02 | 位置枚举；1 主多副 | JUnit | `PlayerSelfProfileIT` | CI | | |
| 17 | PLAYER-03 | 外挂转正、历史保留 | JUnit | `PlayerConvertIT` | CI | | |
| 18 | PLAYER-04 | 球员注销、历史保留 | JUnit | `PlayerDisableIT` | CI | | |
| 19 | PLAYER-05 | 仅头像上传；二期不做照片视频 | Playwright + 人工 | B2/B3 上传入口 + 生产 OSS 真机 | CI / RC | | |
| 20 | PLAYER-06 | 管理员设 1–5 星；分组与展示 | JUnit + Playwright | `PlayerAdminIT` + C04 页面 | CI / RC | | |
| 21 | PLAYER-07 | 球员自助改资料白名单 | JUnit | `PlayerSelfProfileIT` | CI | | |
| 22 | TEAM-01 | 位置分组；组内出场降序、号码升序 | Playwright | B3 C04 黄金路径 | CI / RC | | |
| 23 | TEAM-02 | 每组默认前 10，可全部/收起 | Playwright | B3 C04 黄金路径 | CI / RC | | |
| 24 | TEAM-03 | 姓名/号码/位置/地区/头像/星级/赛季统计 | Playwright | B3 C04 黄金路径 | CI / RC | | |
| 25 | TEAM-04 | 匿名可见公开字段；敏感字段不可见 | Playwright | B3 C04 匿名访问 | CI / RC | | |
| 26 | TEAM-05 | 仅正式且正常球员；外挂不进入 | Playwright | B3 C04 黄金路径 | CI / RC | | |
| 27 | MATCH-01 | 创建/编辑字段；截止与锁定推导 | JUnit | `MatchAdminIT`、`MatchEditIT` | CI | | |
| 28 | MATCH-02 | 四种比赛类型 | JUnit | `MatchAdminIT` | CI | | |
| 29 | MATCH-03 | 本期仅 8 人制 | JUnit | `MatchAdminIT` | CI | | |
| 30 | MATCH-04 | 完整状态机与取消/延期分支 | JUnit | `MatchStatusMachineTest` | CI | | |
| 31 | MATCH-05 | 自动流转与管理员覆盖 | JUnit | `MatchAutoStatusIT` | CI | | |
| 32 | MATCH-06 | 关闭报名/延期/重排/取消/提前结束/重置 | JUnit + Playwright | `MatchStatusIT`、`MatchLifecycleIT` + B02 | CI / RC | | |
| 33 | MATCH-07 | 延期保留报名、重算截止、重排回报名中 | JUnit + Playwright | `MatchLifecycleIT` + B02 | CI / RC | | |
| 34 | MATCH-08 | 取消保留报名、通知、不计缺勤 | JUnit + Playwright | `MatchLifecycleIT`、`MatchNotificationIT` + B02 | CI / RC | | |
| 35 | MATCH-09 | 赛程卡片视觉：星期/比分/胜负/类型/编号位置 | 人工演示 | RC 截图 | RC | | |
| 36 | MATCH-10 | 赛事/赛季/轮次；赛季仅四位年份 | JUnit | `MatchAdminIT`、`MatchSnapshotTest` | CI | | |
| 37 | MATCH-11 | 对外主客队；队内橙/兰；胜平负 | JUnit + Playwright | `MatchPublicIT` + C02 页面 | CI / RC | | |
| 38 | MATCH-12 | 详情状态/事件/比分；有事件显示推导比分 | Playwright | `web.spec.ts`、`admin.spec.ts` + B4 C03 | CI / RC | | |
| 39 | MATCH-13 | 生成可分享链接并转发 | Playwright | B4 C03 分享黄金路径 | CI / RC | | |
| 40 | MATCH-15 | 「{年份}赛季第N场（累计M场）」自动递增 | JUnit + Playwright | `MatchAdminIT` + 赛程展示 | CI / RC | | |
| 41 | MATCH-16 | 编辑限制：不可改赛季/类型/主客；容量下界 | JUnit | `MatchEditIT` | CI | | |
| 42 | REG-01 | 仅未开始/报名中/未过截止可报名 | JUnit | `RegistrationSignupIT` | CI | | |
| 43 | REG-02 | 正式/外挂独立名额；候补仅正式 | JUnit | `RegistrationPolicyTest` | CI | | |
| 44 | REG-03 | 已报名/候补/已取消/已移除 | JUnit | `RegistrationSignupIT`、`RegistrationCancelIT` | CI | | |
| 45 | REG-04 | 未满确认；正式满进候补；候补满拒绝；外挂满拒绝 | JUnit | `RegistrationPolicyTest` | CI | | |
| 46 | REG-05 | 锁定前可取消、锁定后不可 | JUnit + Playwright | `RegistrationCancelIT` + B4 C03 | CI / RC | | |
| 47 | REG-06 | 管理员移除并触发递补 | JUnit | `RegistrationAdminRemoveIT` | CI | | |
| 48 | REG-07 | 最早候补递补并通知 | JUnit | `RegistrationCancelIT`、`RegistrationNotificationIT` | CI | | |
| 49 | REG-08 | 截止/锁定校验；当天 00:00 / 01:00 推导 | JUnit | `MatchTimeRulesTest` | CI | | |
| 50 | REG-09 | 同一比赛同一用户仅一条有效报名 | JUnit | `RegistrationSignupIT` | CI | | |
| 51 | REG-10 | 外挂需登录；邀请引导注册/报名 | JUnit | `RegistrationSignupIT`、`InviteLifecycleIT` | CI | | |
| 52 | REG-11 | 正式/管理员看名单；外挂只看自己 | JUnit | `RegistrationRosterIT`、`MyRegistrationsIT` | CI | | |
| 53 | REG-12 | 默认 30/4/2；可配置；建赛可覆盖 | JUnit + Playwright | `MatchAdminIT`、`RegistrationPolicyTest` + B09 | CI / RC | | |
| 54 | ATT-01 | 开赛前 4h 至当天 23:59 可签到 | Playwright | `web.spec.ts` + `CheckInWindowTest` | CI | | |
| 55 | ATT-02 | 仅已报名（确认）球员可自签 | JUnit | `AttendanceCheckInIT` | CI | | |
| 56 | ATT-03 | 正常/缺勤/临时到场 | JUnit | `AttendanceAdminIT` | CI | | |
| 57 | ATT-04 | 来源：自签/管理员/系统 | JUnit | `AttendanceCheckInIT`、`AttendanceAdminIT`、`AttendanceAbsenceIT` | CI | | |
| 58 | ATT-05 | 管理员可标记/修正任意用户 | JUnit | `AttendanceAdminIT` | CI | | |
| 59 | ATT-06 | 临时到场不占名额、计出场、不计缺勤 | JUnit | `AttendanceAdminIT`、`StatsAggregatorTest` | CI | | |
| 60 | ATT-07 | 结束 +6h 未签到判缺勤；可改 | JUnit | `AttendanceAbsenceIT` | CI | | |
| 61 | ATT-08 | 正式/管理员看全队；外挂只看自己 | JUnit | `AttendanceAdminIT`、`AttendanceCheckInIT` | CI | | |
| 62 | EVENT-01 | 进球/乌龙/换人/红黄牌；点球标记 | JUnit | `EventCoreIT`、`EventAdminIT`、`EventRulesTest` | CI | | |
| 63 | EVENT-02 | 进球可带助攻 | JUnit | `EventRulesTest`、`EventCoreIT` | CI | | |
| 64 | EVENT-03 | 换人记录换上/换下；仅管理员增改删 | JUnit | `EventAdminIT`、`EventCoreIT` | CI | | |
| 65 | EVENT-04 | 点球计入进球与比分 | JUnit | `EventRulesTest`、`EventScoreTest` | CI | | |
| 66 | EVENT-05 | 乌龙是我方球员，比分记给对方 | JUnit | `EventScoreTest` | CI | | |
| 67 | EVENT-06 | 球员录/改自己事件，赛后 24h | JUnit | `EventCoreIT` | CI | | |
| 68 | EVENT-07 | 管理员可改/删任意事件，不限时间 | JUnit | `EventAdminIT` | CI | | |
| 69 | EVENT-08 | 仅管理员可删除 | JUnit | `EventAdminIT` | CI | | |
| 70 | EVENT-09 | 创建/修改/删除留历史 | JUnit | `EventAdminIT`、`DataRetentionIT` | CI | | |
| 71 | EVENT-10 | 比分与事件独立；不一致提示不阻断 | Playwright | `admin.spec.ts` B04 | CI | | |
| 72 | EVENT-11 | 分钟 1–120 与半场均必填 | JUnit | `EventRulesTest`、`EventCoreIT` | CI | | |
| 73 | EVENT-12 | 主客队归属；有分组时按分组限制 | JUnit | `EventGroupingIT`、`EventRulesTest` | CI | | |
| 74 | EVENT-13 | 上半场→下半场、分钟升序、同分钟进球优先 | JUnit | `EventCoreIT` | CI | | |
| 75 | EVENT-14 | 仅换人显示换上/换下 | JUnit + Playwright | `EventCoreIT` + B4 C03 弹窗 | CI / RC | | |
| 76 | EVENT-15 | 时间轴居中；主队左、客队右 | 人工演示 | RC 截图 | RC | | |
| 77 | LINEUP-01 | 仅管理员编辑；正式只读；外挂/访客不可看 | Playwright | `admin.spec.ts`、`LineupBoardIT` | CI | | |
| 78 | LINEUP-02 | 8 套预置阵型 | JUnit + Playwright | `FormationTemplateIT`、B05 | CI | | |
| 79 | LINEUP-03 | 复制/重命名/删除；预置不可改删 | Playwright | `admin.spec.ts` B05 | CI | | |
| 80 | LINEUP-04 | 首发 8 + 替补；可加入/移出 | Playwright | `admin.spec.ts` B05 | CI | | |
| 81 | LINEUP-05 | 对外可设我方/对手方；对手仅阵型与占位 | JUnit + Playwright | `LineupBoardIT` + B2 B05 | CI / RC | | |
| 82 | LINEUP-06 | 队内/训练可排双方；有分组按橙/兰限制 | JUnit + Playwright | `LineupBoardIT` + B2 B05 | CI / RC | | |
| 83 | LINEUP-07 | 拖拽球员到位置 | 人工演示 | RC 录屏 | RC | | |
| 84 | LINEUP-08 | 版本控制；并发保存冲突提示 | Playwright | `admin.spec.ts`、`LineupBoardIT` | CI | | |
| 85 | GROUP-01 | 候选为本场报名/加入名单；外挂按开关 | JUnit | `GroupingIT`、`GroupingCandidatesTest` | CI | | |
| 86 | GROUP-02 | 手动 70% + 自动 30%；全局可配 | JUnit + Playwright | `GroupingScoreCalculatorTest` + B09 | CI / RC | | |
| 87 | GROUP-03 | 出场率/场均进球/助攻，候选池归一化 | JUnit | `GroupingScoreCalculatorTest` | CI | | |
| 88 | GROUP-04 | 缺失默认 3 星；少于最少出场优先手动分 | JUnit | `GroupingScoreCalculatorTest` | CI | | |
| 89 | GROUP-05 | 人数均分；≥2 门将每队至少 1；位置均衡；警告 | JUnit | `GroupingObjectiveTest`、`GroupingWarningsTest` | CI | | |
| 90 | GROUP-06 | 蛇形 + 局部搜索 + 随机；总分差最小 | JUnit | `GroupingAlgorithmTest` | CI | | |
| 91 | GROUP-07 | 可拖拽；实时总分差与位置分布 | Playwright + 人工 | `admin.spec.ts` B06 + RC 拖拽录屏 | CI / RC | | |
| 92 | GROUP-08 | 保存后不自动重算；名单变化提示 | JUnit | `GroupingIT` | CI | | |
| 93 | GROUP-09 | 保存分数快照，历史可解释 | JUnit | `GroupingIT` | CI | | |
| 94 | GROUP-10 | 位置不足提示，不阻断 | JUnit | `GroupingWarningsTest` | CI | | |
| 95 | GROUP-11 | 仅「锁定」状态可生成/调整/保存 | Playwright | `admin.spec.ts` B06、`GroupingIT` | CI | | |
| 96 | GROUP-12 | 展示全部确认报名；有分组分两列；可清除重生成 | Playwright | `admin.spec.ts` B06 | CI | | |
| 97 | NOTIFY-01 | 报名成功/递补/改期取消/角色变更产生站内消息 | JUnit + Playwright | 通知相关 IT + B3 C06 | CI / RC | | |
| 98 | NOTIFY-02 | 比赛/报名页可分享到微信群 | Playwright | B4 C03 分享黄金路径 | CI / RC | | |
| 99 | NOTIFY-03 | 已读/未读；可标记已读 | Playwright | B3 C06 通知黄金路径 | CI / RC | | |
| 100 | NOTIFY-05 | 实时逐条、不聚合；仅必要触发 | JUnit | 通知 IT + research/02 口径 | CI | | |
| 101 | INVITE-01 | 入队邀请注册免审并加入球队 | JUnit | `InviteFlowIT` | CI | | |
| 102 | INVITE-02 | 比赛邀请引导报名；创建权限 | JUnit + Playwright | `AuthHardeningIT`、`InviteLifecycleIT` + B3 C07 | CI / RC | | |
| 103 | INVITE-03 | 默认 7 天；默认不限次；可撤销/过期 | JUnit | `InviteLifecycleIT`、`M01ConcurrencyIT` | CI | | |
| 104 | INVITE-04 | 记录邀请人，便于追溯 | JUnit | `InviteLifecycleIT` | CI | | |
| 105 | INVITE-05 | 管理员可撤销；过期不可用 | JUnit | `InviteFlowIT`、`M01ConcurrencyIT` | CI | | |
| 106 | CONFIG-01 | 默认 30/4/2 可配置 | JUnit + Playwright | SET-01/02 + B09 | CI / RC | | |
| 107 | CONFIG-02 | 新建带入配置；可覆盖；不影响已建比赛 | JUnit + Playwright | `MatchAdminIT` + B09 保存生效边界 | CI / RC | | |
| 108 | LIST-01 | 赛程类型/地点/状态/年份/月份筛选；搜索生效 | Playwright | B3 C02 黄金路径 | CI / RC | | |
| 109 | LIST-02 | 球员姓名/地区模糊；类型/管理员/状态/位置/星级筛选 | JUnit + Playwright | `PlayerAdminIT` + B07 | CI / RC | | |
| 110 | LIST-03 | 默认号码升序；可选号码/星级/位置/地区排序 | JUnit + Playwright | `PlayerAdminIT` + B07 表头排序 | CI / RC | | |
| 111 | LIST-04 | 默认每页 10，可选 20/50/100 | JUnit + Playwright | `PageQueryTest` + 页面分页 | CI / RC | | |
| 112 | LIST-05 | 空结果提示；分页控件不报错 | Playwright | B3 C02 / B07 空态 | CI / RC | | |

**统计**：通过 __ / 112；不通过 __；人工通过 __。签收人：____　日期：____

---

## §4 Playwright 黄金路径

**门槛**：main 全绿 + RC 复跑全绿 + 无 console error；失败产物（`frontend/test-results`、`playwright-report`）保留 7 天。

**数量**：现有 14 条 + B2 6 条 + B3 7 条 + B4 2 条 = **29 条**。

**运行顺序**（同一命令 `CI=1 pnpm test:e2e`，按依赖从基础到闭环）：

| 段 | 路径 | 来源 |
|---|---|---|
| 1 | 认证与会话：C/B 登录、未登录跳登录带 redirect | 现有 `web.spec.ts` / `admin.spec.ts` |
| 2 | 公开赛程：列表、C02 筛选/URL 恢复/空态；访客首页→登录引导 | 现有 + B3 C01/C02 |
| 3 | 球队页：匿名访问、分组/前 10/展开、星级与统计、无手机号 | B3 C04 |
| 4 | 注册/邀请：注册→待审核、邀请接受、403/404 | B3 C05/C07 |
| 5 | 通知：分页、已读、角标归零 | B3 C06 |
| 6 | 报名/考勤：C03 报名→取消、签到、B03 标记考勤 | 现有 + B4 C03 |
| 7 | 事件/阵容/分组：B04 事件、B05 模板与阵容、B06 生成/调整/保存/清除 | 现有 |
| 8 | B 端页面：B02 创建→编辑→延期→重排→取消；B07 筛选+编辑+头像；B08 权限保存；B09 设置保存；B10 筛选+详情脱敏；B11 创建+复制+撤销 | B2 |
| 9 | 分享与微信：C03 分享（复制 + `navigator.share`）、状态变更；微信真实授权 | B4 |
| 10 | 权限边界：侧栏 sections、无权限直访 403、只读球员不能生成分组 | 现有 |

**运行记录**

| 环境 | 命令 | 结果 | 日期 |
|---|---|---|---|
| 本地 dev（`qua` `4028ff5`，JDK 21，重建库 + `test-data.sql`） | `CI=1 pnpm test:e2e` | 80 passed（12 个 spec 全部：认证/赛程/球队/邀请/通知、C03、微信、B02–B11、权限边界） | 2026-11-14 |
| main CI | `pnpm test:e2e` | ☑ 80 passed（job e2e 4m10s，run 43466733771） | 2026-11-14 |
| 本地 dev（`qua` `e5136cb`，JDK 21，重建库 + `test-data.sql`，P-74 后） | `CI=1 pnpm test:e2e` | ☑ 94 passed（2.7m；`web-wechat.spec.ts` 已随公众号链路删除，`wechatBound` 相关用例改走小程序登录） | 2026-11-20 |
| 生产 RC | `pnpm test:e2e`（指向 RC） | ☐ | |

> 2026-11-14：main 的 e2e job 曾因 runner 默认 JDK 17 启动 Java 21 jar 失败（`UnsupportedClassVersionError`），修复在 PR #3；§4 门槛仍需 main 全量 E2E 与生产 RC 复跑全绿。

---

## §5 空库 Flyway 幂等、部署冒烟与备份

> 版本号以 `backend/src/main/resources/db/migration/` 最新文件为准（当前 V13）；新增迁移时同步本文与 `deploy/RUNBOOK.md` §1.3 的校验项。

### 5.1 空库幂等

- [ ] 全新空 MySQL 8.4 schema（无 `flyway_schema_history`）。
- [ ] 用**迁移账号**执行 `flyway migrate`，V1–V13 全部成功。
- [ ] 再次执行 `flyway info`：无 pending / failed。
- [ ] `flyway_schema_history` 记录为 V1–V13。
- [ ] 应用以 `spring.flyway.enabled=false` 启动成功。
- [ ] 用**应用账号**执行任意 DDL 必须失败（无 DDL 权限）。

> **本地预检记录（2026-11-14）**：已 `DROP/CREATE yunhefc` 空库并启动 dev profile，Spring Flyway 自动应用 V1–V5，`/actuator/health` 返回 UP，`test-data.sql` 导入成功。未验证：迁移账号/应用账号分离、`spring.flyway.enabled=false` 启动、应用账号 DDL 拒绝；5.1 仍为未通过。

### 5.2 发布前迁移

- [x] 发布前用迁移账号对生产库执行 `flyway migrate`，成功后才切 `current`。— 2026-11-15 重建库执行 V1–V5；后续增量至 V8，2026-11-20 补至 V13
- [ ] 迁移失败则不发版，保持上一版本在线；失败处置见 `deploy/RUNBOOK.md` §1.3。
- [ ] 目标 sha 检出中复制 `backend/src/main/resources/db/migration/*.sql` 到 `/opt/yunhefc/migrations`（含 V13）。
- [ ] `flyway migrate`：已有库补到目标版本，`flyway_schema_history` 最新为 V13 且 success=1。
- [ ] `flyway info`：无 pending / failed。
- [ ] **切 `current` 前的版本门禁**：`deploy.yml` 与 `release.sh` 都不执行迁移，必须人工确认 `flyway info` 与目标版本一致；库落后于代码时依赖新列/新表的接口会报错（本次为登录 500，见 2026-11-20 记录）。
- [ ] V8 校验：`audit_log.operated_at` 存在（`datetime(3) NULL`）、`idx_audit_team_operated` 存在、历史行 `operated_at` 已回填。
- [ ] V9 校验：`player.skill` 为 `decimal(3,2) NOT NULL DEFAULT 3.00`、`match_grouping_player.manual_score` 为 `decimal(3,2) NULL`，且两列无 1–5 范围外数据。
- [ ] V10–V12 校验：`match_grouping.manual_weight/auto_weight` 为 `decimal(5,2) NOT NULL`；`grouping.*` 系统配置为 0 行；`app_user.phone` 为 `varchar(20) NOT NULL` 且 `phone IS NULL` 计数为 0（V12 会删除无手机号账号及其关联数据，执行前先备份）。
- [ ] V13 校验：`app_user.mp_openid` 为 `varchar(64) ascii_bin NULL` 且位于 `openid` 之后，唯一索引 `uk_user_mp_openid` 存在。
- [ ] 迁移成功后才触发 `deploy` workflow（`sha=<sha7>`）；迁移失败不发版。

> **2026-11-15 实测**：初次为手工执行 `schema.sql` + `quartz.sql` 初始化（无 `flyway_schema_history`）；当日已按 `scripts/rc-db-reset.sh` 删除重建并跑通 V1–V5（39 张表含 Quartz 与 `flyway_schema_history`）。后续迁移从此可正常增量执行。

> **2026-11-20 事故记录**：生产库停在 V8、镜像已含 V13 代码，`app_user` 查询报 `Unknown column 'mp_openid'`，手机号登录返回 500。同日小程序登录返回 `42201` 是另一个原因——`WECHAT_MINI_ENABLED` 未配置（默认 `false`），与缺列无关。补跑 V9–V13 并写入 `WECHAT_MINI_*` 后恢复。V13 首次执行时 DDL 已生效但 Flyway 历史记为 failed；按 `deploy/RUNBOOK.md` §1.3 的失败处置口径修正历史，未回退重做。

### 5.3 部署冒烟

- [ ] readiness ≤180 秒；`/actuator/health` 返回 UP。— 旧三子域实测 `https://api.yunhefc.com/actuator/health` 200；同域切换后需重测 `https://www.yunhefc.com/actuator/health`
- [x] C 端首页 200；一条公开读接口 200。— `https://www.yunhefc.com/` 200；`/api/v1/c/matches?pageSize=1` 200
- [ ] C 端 `/` 与 B 端 `/admin/` 登录成功，无重定向循环。— 旧三子域 B 端登录已验证（上传头像，`userId=1`）；同域切换后需重测 C/B
- [ ] www 敏感端点 `/actuator/env`、`/actuator/beans`、`/v3/api-docs`、`/swagger-ui/**` 均 404（api 子域已取消）。— 待同域切换后重测
- [x] 旧 api 子域敏感端点四项实测 404（历史证据）。— 同域切换后由上一项 www 口径覆盖
- [ ] 小程序登录端点 `POST https://www.<domain>/api/v1/c/auth/mini/login` 对空 `code` 返回 400（业务码 40001），且 `/c/auth/wechat/**` 已不存在。未认证请求先被安全链拦为 **401**（`anyRequest().authenticated()` 先于路由匹配）；已登录请求才应得到 404。

### 5.4 首位管理员 runbook

- [x] `bootstrap-admin.sql` 执行成功（`app_user.status=1` + ADMIN 角色 + `player`）。— 2026-11-15：id=1、手机号 `17600192707`、status=1、ADMIN 角色、player 行存在、BCrypt 哈希为脚本生成
- [x] B 端登录成功；ADMIN 权限数 = 28。— 2026-11-15：B 端登录后成功上传头像（`userId=1`，`req-f08682e9…`）；权限数 SQL 校验为 28
- [ ] `/c/me` 返回 `player` 非空。
- [x] 登录后已改密，运维记录中的口令已清除。— 管理员密码由 `rc-db-reset.sh` 在创建时当场设置，无默认口令落盘或记录

### 5.5 回滚演练（B0 完成门）

- [x] `deploy.yml` 完成一次 RC 发布。— 2026-11-15 run `43699704838`（main，`workflow_dispatch`，44s，success）；`current → releases/ea92ad4`、`IMAGE_TAG=sha-ea92ad4`
- [x] `release.sh --rollback` 完成一次回滚，readiness + 冒烟通过。— 2026-11-15：先发 `5a2e4b2`（run `43700388257`，`previous → releases/ea92ad4`），再执行 `release.sh --rollback` → `current → releases/ea92ad4`、readiness `{"status":"UP"}`、冒烟通过

> **2026-11-15 实测**：`deploy.yml` 前 4 次全部失败（`rsync` 缺失、`/opt/yunhefc` 权限、`qrtz_locks` 缺失、库被删后冒烟 500）。重建数据库并修正 OSS 配置后，`43699163191`、`43698937614`、`43699704838` 连续成功；随后补发 `5a2e4b2`（`43700388257`）并完成一次回滚演练，`current` 已回到 `ea92ad4`。

### 5.6 RDS 备份 / PITR

- [ ] 控制台确认自动备份开启、保留 7 天。— **按决策人 2026-11-15 要求不执行**（风险接受，见 §10 #18）
- [ ] binlog 开启。— 同上
- [ ] 最近备份成功，PITR 可恢复时间点覆盖回滚窗口。— 同上
- [ ] `.env` 已在密码管理器离线保管；证书重签步骤可用。

---

## §6 安全检查

执行方式：上线前人工一次性执行，结果记入本表。**不新增 CI job**。

| # | 检查项 | 命令/方法 | 结果 |
|---|---|---|---|
| 1 | TLS/证书：只需覆盖 www（SAN 含 DNS:www 或 *.domain），剩余 >30 天，仅 1.2/1.3，80→301 | `openssl x509 -enddate`、SSL Labs、`curl -I http://...` | ☐ |
| 2 | 响应头：HSTS（不 preload）、nosniff、Referrer-Policy、X-Frame-Options、Permissions-Policy；无 CSP | `curl -I` | ☐ |
| 3 | Cookie：Secure、HttpOnly、SameSite=Lax、无 Domain、Path 正确；未登录写接口 401 | DevTools + `curl` | ☐ |
| 4 | CORS：仅 www 预检通过；admin/api 与其他 Origin 无 ACAO；prod 自检拒绝 localhost/通配符/多源 | `curl -X OPTIONS` + 启动日志 | ☐ |
| 5 | 真实 IP：伪造 `X-Forwarded-For` 后日志/审计仍为真实 IP；backend 公网不可达 | 构造请求 + 查日志 + `nc` | ☐ |
| 6 | 暴露面：actuator 敏感端点、api-docs、swagger 均 404；错误响应无堆栈/SQL | `curl` | ☐ |
| 7 | 迁移与账号：`flyway migrate` 成功（V1–V13），`flyway info` 无 Pending/Failed；应用账号 DDL 失败；`flyway.enabled=false` 启动 | `flyway info` + `mysql` DDL | ☐ |
| 8 | Redis/Kafka：未认证 `redis-cli ping` 失败、应用带密码正常；端口未对公网发布 | `redis-cli` + `ss -lntp` | ☐ |
| 9 | 限流与锁定：登录 51 次/分 429、短信 6 次/分 429、短信 IP 维度生效；5 次错误后 40103；提示不泄露账号存在 | 脚本压请求 | ☐ |
| 10 | 日志与密钥：`gitleaks`（含 git 历史）无命中；日志无明文 phone/token/password/openid/Authorization/请求体；`.env` 600；生产未执行 `test-data.sql`；管理员已改密 | `gitleaks detect`、`grep`、`stat` | ☐ |
| 11 | 端到端：HTTPS 下 C/B 无重定向循环；小程序真机 `wx.login` 完成登录；无权限管理员被拒 | 真机 + 浏览器 | ☐ |

> `scripts/rc-acceptance-wizard.sh` 自动覆盖第 9 行登录 429/40103 与第 10 行 gitleaks/`.env` 权限；短信 429、IP 维度、第 1/6 行 TLS 版本与错误响应、§5.3 readiness 观测按命令列人工执行（readiness ≤180s 由 `release.sh` 强制）。

### 6.1 依赖轻检（非阻断）

- [x] `mvn dependency:tree` 输出已记录（重点看 Spring Boot 管理版本外的直接依赖）。2026-11-14：117 条，父版本 Spring Boot 3.5.16。
- [ ] `pnpm audit --prod` 输出已记录（含 high/critical 计数）。2026-11-14 未能执行：`registry.npmmirror.com` 不提供 audit endpoint（`ERR_PNPM_AUDIT_ENDPOINT_NOT_EXISTS`），与 §10 第 1 条一致。
- [x] 发现项记入 §10；**不阻断发布**，不新增 CI 扫描。本次无新增发现。

---

## §7 Cutover 时间线

| 时点 | 动作 | 结果 |
|---|---|---|
| T-24h | 冻结 main；完成 §1–§6 签收；固定 `sha-<7>`；确认 RDS 备份/PITR；确认回滚演练证据 | ☐ |
| T-0 | 迁移账号 `flyway migrate` → `flyway info` 无 Pending/Failed 且与目标版本一致 → `release.sh --sha <sha7>` → readiness ≤180s → §5.3 冒烟 → 队伍群公告放行 | ☐ |
| T+30m | 即时观测：错误率、登录、报名、通知、容器健康；判定保持或回滚 | ☐ |
| T+24h | 密切观察复盘：备份、日志、OOM、Kafka lag、Outbox；记录问题 | ☐ |
| T+72h | 稳定声明；解除冻结；未关闭项转入后续批次 | ☐ |

---

## §8 观测指标与告警阈值

| 指标 | 阈值 | 来源 | 结果 |
|---|---|---|---|
| 磁盘 | >75% | 票 12 | ☐ |
| 内存 | >90% 持续 5 分钟 | 票 12 | ☐ |
| 容器 | unhealthy | 票 12 | ☐ |
| Kafka 消费组 lag | >1000 持续 10 分钟 | 票 12 | ☐ |
| Outbox 积压 | >500 持续 15 分钟 | 票 12 | ☐ |
| OOM / swap | `dmesg` 有 OOM 或 swap 持续使用 | cutover 追加 | ☐ |
| HTTP 5xx | 发布后 30 分钟内异常升高 | 票 12 回滚触发 | ☐ |
| 登录成功率 | 明显下降或连续 401/429 异常 | cutover 追加 | ☐ |
| 报名失败 | 非业务预期错误集中出现 | cutover 追加 | ☐ |
| 通知消费 | 无新通知或积压持续增长 | cutover 追加 | ☐ |

---

## §9 回滚与 PITR

**触发条件（票 12/13）**：readiness 超时、Flyway 失败、冒烟失败、发布后 30 分钟内 5xx 或关键路径异常。

**处置顺序**

1. 命中触发条件后 **15 分钟内**执行 `release.sh --rollback` 切回上一版本。
2. 保留失败版本镜像、日志与现场，不立即删除 release 目录。
3. 重跑 readiness + 冒烟；确认恢复后再分析根因。
4. **仅当**迁移不向后兼容或出现数据损坏时使用 RDS PITR：
   - 记录故障时间点与目标恢复时间点；
   - 明确数据回退范围（RPO）并告知队伍；
   - 不做逆向迁移、不手工改表。
5. 回滚后 24 小时内补记事故记录与后续修复批次。

| 项目 | 结果 |
|---|---|
| 回滚演练证据（B0） | ☐ |
| 回滚命令与权限已确认 | ☐ |
| PITR 目标时间点可查 | ☐ |

---

## §10 已知问题与残余风险

| # | 项 | 来源 | 处置 |
|---|---|---|---|
| 1 | 无依赖漏洞自动扫描 / 无 SBOM；传递依赖高危靠人工发现 | 票 16 §残余风险 | 接受；§6.1 人工轻检记录 |
| 2 | 无全站基础限流、上传接口不限流 | 票 16 | 接受；账号被盗可刷存储/接口 |
| 3 | MySQL 私网明文、Kafka 无 SASL | 票 16 | 接受；依赖 VPC/Compose 隔离 |
| 4 | Nginx access log 保留完整 URI，微信 `code/state` 与邀请 token 落盘 | 票 16 | 接受；一次性/短期凭证 |
| 5 | 无 CSP；证书手工续期，过期将导致全站不可用 | 票 16 | 接受；提前 30 天核对证书 |
| 6 | Redis 故障时限流降级放行 | 票 16 | 接受；会话同时不可用 |
| 7 | 仅 RDS 7 天 PITR；无 OSS/本地副本、无 ECS 快照；不做恢复演练 | 票 17 | 接受；发布前人工检查 |
| 8 | 通知 90 天 / 邀请 30 天 / Outbox 7 天 / 消费去重 7 天物理删除 | 票 17 | 接受；按配置执行 |
| 9 | main 的 `e2e` job 使用 runner 默认 JDK 17，启动 Java 21 boot jar 报 `UnsupportedClassVersionError`，健康检查超时 | 2026-11-14 复核 | 已修复：PR #3 合并（`ea92ad4`），main e2e 通过（run 43466733771） |
| 10 | 生产库执行了 `test-data.sql`（2026-11-15 08:49 插入 id 1001–1023 测试账号与球员），违反 06 AQ-44 与 §5.4 | 2026-11-15 实测 | 已修复：重建库，测试数据已清除 |
| 11 | 生产库无 `flyway_schema_history`：手工执行 `schema.sql` + `quartz.sql` 初始化（27 业务表 + 11 Quartz 表），§5.1/§5.2 不成立 | 2026-11-15 实测 | 已修复：重建后 V1–V5 全部 success=1，39 张表 |
| 12 | `bootstrap-admin.sql` 以字面量 `<REPLACE_WITH_BCRYPT_HASH>` 执行，id=1 管理员无法登录 | 2026-11-15 实测 | 已修复：`rc-db-reset.sh` 生成真实 BCrypt 哈希，管理员 id=1 / `17600192707` |
| 13 | nginx `proxy_pass http://backend:8080` 仅在启动时解析；backend 重建后 502，需手工 `nginx -s reload` | 2026-11-15 实测 | 已修复（P-34）：`upstream backend_upstream { resolve }` + `resolver 127.0.0.11 valid=10s`，镜像钉 `1.27.5-alpine`；发布 `56b4b9f` 后强制 backend IP `172.18.0.4 → .6`，**未 reload**，8 秒内接口恢复 200 |
| 14 | compose 发布时从 Docker Hub 拉 `apache/kafka:3.7.2`、`redis:6.2.24`；mirror 回退直连导致一次发布失败 | 2026-11-15 实测 | 已修复（P-35）：CI `mirror-infra` 同步到 ACR（`yunhefc-infra-kafka:3.7.2`、`yunhefc-infra-redis:6.2.24`，四个 manifest 均已确认），compose 只用 `${IMAGE_REGISTRY}` |
| 15 | 验收环境为自签名证书（CN=demo1.com，链长 1，SAN 覆盖四域名） | 2026-11-15 实测 | **未处置**：配置已收敛为只需 www；仍需换受信 www 证书（Let's Encrypt DNS-01 或阿里云 CAS）；§2 #4 与微信真机授权阻塞 |
| 16 | `deploy.yml` 在 main 前 4 次触发全部失败（rsync 缺失、目录权限、readiness 超时、冒烟 500） | 2026-11-15 实测 | 已修复：run `43699704838` 成功，`current → releases/ea92ad4` |
| 17 | readiness 通过但 schema 不存在：`db` 健康指示器只执行 `SELECT 1`，库被删除后仍报 `UP`，直到业务查询才暴露 `Unknown database` | 2026-11-15 实测 | 已修复（P-36）：新增 `SchemaHealthIndicator`（`flyway_schema_history` + `team`），加入 readiness；单测 3 项通过，线上 readiness `{"status":"UP"}` |
| 18 | RDS 自动备份 / binlog / PITR 未在控制台确认（§0.1 硬门槛「RDS 备份 / PITR 可见」） | 决策人 2026-11-15 | **风险接受**：不执行；数据损坏时无可验证的恢复时间点，只能依赖 RDS 默认策略 |
| 19 | 微信真实授权未验收（§2 #9、B4 完成门），`WECHAT_MODE` 为真实模式但无成功链路证据 | 决策人 2026-11-15 | **风险接受**：不执行；C 端微信登录为未验证状态，上线后首次使用才暴露问题 |
| 20 | `images` job 两次在 20 分钟上限被取消，`5a2e4b2` 镜像需重跑才产出 | 2026-11-15 实测 | 已修复（P-37）：超时 20→40；`web.Dockerfile` 先拷 workspace 清单再 install；run `43702699777` 的 `images` 1m45s 完成 |
| 21 | 生产自 2026-11-18 起已以 +08 墙钟写入（首个部署 `c5fcb4a`，2026-11-21 为 `0fe8195`）；11-18 之前若存在未平移行，其时间早 8 小时 | 决策人 2026-11-22 | **风险接受**：本次不执行整表平移、不做定向修复；旧数据保留 8h 偏差 |

**延期项**（仅限非阻断 UI/性能/文档；逐条记录）：

| # | 项 | 影响 | 计划批次 |
|---|---|---|---|
| | | | |

---

## §11 签收

| 项目 | 内容 |
|---|---|
| cutover 版本 `sha-<7>` | |
| 签收日期 | |
| 硬门槛结果 | ☐ 全部通过　☐ 存在未通过项（原因：____） |
| Go / No-go | ☐ Go　☐ No-go |
| 决策人 | |
| 观测期结论（T+72h） | ☐ 稳定　☐ 需回滚/修复（记录：____） |
| 备注 | |
