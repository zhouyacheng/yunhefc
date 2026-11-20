# 01 交付缺口盘点：92 接口与 C/B 页面对账

Type: research
Status: resolved

> 范围：只做「接口 / 页面 / 测试」层面的交付盘点。通知触发矩阵、审计记录覆盖、配置语义分别由 02 / 03 / 06 号票负责，本文不展开。
>
> 标记约定：**【事实】** = 可直接由文件或文档核对；**【推断】** = 依据现状推导但未运行验证；**【待决策】** = 需要协调者/用户选择。
>
> 事实源：`docs/design/05.API接口设计.md` §3、`docs/design/02.页面结构与交互设计.md` §4–§7、`backend/src/main/java/com/yunhefc/**/controller`、`frontend/apps/{web,admin}/src/router`、`frontend/e2e`、`frontend/packages/*/src/*.test.ts`。

## 0. 总览

| 项目 | 现状 | 依据 |
|---|---|---|
| 需求 MVP 条数 | 112 | `docs/云河FC-需求文档.md:139-155`；§6.1–§6.12 逐节计数 14+12+15+12+8+15+8+12+4+5+2+5=112 |
| 05 §3 接口数 | 92 | `docs/design/05.API接口设计.md:138-333` |
| 后端已实现接口 | 83 / 92 | 见 §2 全量对账 |
| 后端缺失接口 | 9：NTF-01~04、AUD-01、SET-01/02、FIL-01/02 | 见 §2.10–§2.15 |
| C 端页面 | 设计 9 个；有路由 4 个（含 1 个部分替代），缺 5 个 | `docs/design/02.页面结构与交互设计.md:160-172`；`frontend/apps/web/src/router/index.ts:27-55` |
| B 端页面 | 设计 12 个；真实页面 6 个（B00、B02–B06），占位 6 个（B01、B07–B11） | `docs/design/02.页面结构与交互设计.md:174-189`；`frontend/apps/admin/src/router/index.ts:12-29` |
| Playwright | 14 条（C 端 5、B 端 9） | `frontend/e2e/web.spec.ts`、`frontend/e2e/admin.spec.ts` |
| Vitest | 40 条，仅 3 个共享 package 测试文件 | `frontend/packages/api-client/src/index.test.ts`、`frontend/packages/permissions/src/index.test.ts`、`frontend/packages/shared/src/index.test.ts` |

**【事实】** 后端 92 个接口中，M01–M08 与 ROL/STA 的 83 个均已有 controller 方法；缺口集中在 M09 通知、M10 审计、M11 配置、M13 文件四组。
**【事实】** B 端缺口集中在 6 个由 `SectionPage.vue` 承接的分区；其中 B07/B08/B11 的后端接口已齐，B09/B10 后端也未实现，B01 为组合读页面且依赖 AUD-01。
**【事实】** C 端缺口最大：9 个页面中仅 C02/C03/C05 有可用的页面骨架，C06 只有极简 `/account`，C01/C04/C07/C08/C09 没有路由。

## 1. 方法与边界

**【事实】** 盘点步骤：

1. 从 05 §3 逐行提取 92 个接口 ID、方法、路径。
2. 在 `backend/src/main/java/com/yunhefc/**/controller` 中按 `@RequestMapping` + 方法级 mapping 对账（见 §2）。
3. 用 02 §4 页面清单对账 `frontend/apps/web/src/router/index.ts` 与 `frontend/apps/admin/src/router/index.ts`（见 §3、§4）。
4. 从 controller 参数、application service 分页实现核对 M12（见 §5）。
5. 从 `frontend/e2e` 与 `frontend/packages/*/src/*.test.ts` 核对测试覆盖（见 §6）。
6. 只输出批次候选，不排序、不排期（见 §7）。

**【事实】** 以下内容未验证、也未纳入本票：接口运行时行为、通知触发是否正确、审计记录是否完整、配置默认值语义、部署拓扑、CI/CD。分别由 02/03/04/06/12/13 等票处理。

## 2. 05 §3 全量接口对账（92 条）

状态说明：**已交付（后端）** 表示存在 controller 方法；不代表前端页面已接入。路径省略 `/api/v1` 前缀。

### 2.1 M01 账号与权限（17/17）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| AUTH-01 | GET | `/c/auth/csrf` | `backend/src/main/java/com/yunhefc/auth/controller/AuthController.java:42` | 已交付（后端） |
| AUTH-02 | POST | `/c/auth/sms-codes` | `.../AuthController.java:47` | 已交付（后端） |
| AUTH-03 | POST | `/c/auth/register` | `.../AuthController.java:53` | 已交付（后端） |
| AUTH-04 | POST | `/c/auth/login` | `.../AuthController.java:63` | 已交付（后端） |
| AUTH-05 | POST | `/c/auth/logout` | `.../AuthController.java:73` | 已交付（后端） |
| AUTH-06 | POST | `/c/auth/password/reset` | `.../AuthController.java:79` | 已交付（后端） |
| AUTH-07 | GET | `/c/auth/wechat/authorize` | `backend/src/main/java/com/yunhefc/auth/controller/WechatAuthController.java:28` | 已交付（后端） |
| AUTH-08 | GET | `/c/auth/wechat/callback` | `.../WechatAuthController.java:36` | 已交付（后端） |
| AUTH-09 | POST | `/c/auth/wechat/bind-phone` | `.../WechatAuthController.java:45` | 已交付（后端） |
| AUTH-10 | GET | `/c/me` | `.../AuthController.java:85` | 已交付（后端） |
| AUTH-11 | PATCH | `/b/users/{id}/phone` | `backend/src/main/java/com/yunhefc/auth/controller/RoleController.java:99` | 已交付（后端） |
| AUTH-12 | DELETE | `/b/users/{id}/wechat-binding` | `.../RoleController.java:105` | 已交付（后端） |
| AUTH-13 | GET | `/b/users` | `.../RoleController.java:89` | 已交付（后端） |
| AUTH-14 | GET | `/b/auth/csrf` | `backend/src/main/java/com/yunhefc/auth/controller/BackendAuthController.java:39` | 已交付（后端） |
| AUTH-15 | POST | `/b/auth/login` | `.../BackendAuthController.java:44` | 已交付（后端） |
| AUTH-16 | POST | `/b/auth/logout` | `.../BackendAuthController.java:56` | 已交付（后端） |
| AUTH-17 | GET | `/b/me` | `.../BackendAuthController.java:62` | 已交付（后端） |

**【事实】** `AUTH-10` 已返回 `unreadCount`，由 `backend/src/main/java/com/yunhefc/auth/application/AuthService.java:76` 注入 `NotificationReadPort`；但通知列表/已读接口本身缺失（见 §2.10）。

### 2.2 M01 邀请（6/6）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| INV-01 | GET | `/c/invites/{token}` | `backend/src/main/java/com/yunhefc/auth/controller/InviteController.java:36` | 已交付（后端） |
| INV-02 | POST | `/c/invites/{token}/accept` | `.../InviteController.java:41` | 已交付（后端） |
| INV-03 | POST | `/c/matches/{id}/invites` | `.../InviteController.java:51` | 已交付（后端） |
| INV-04 | GET | `/b/invites` | `.../InviteController.java:61` | 已交付（后端） |
| INV-05 | POST | `/b/invites` | `.../InviteController.java:72` | 已交付（后端） |
| INV-06 | POST | `/b/invites/{id}/revoke` | `.../InviteController.java:79` | 已交付（后端） |

**【事实】** INV-04 的前端入口 B11 为 `SectionPage` 占位（见 §4.2）。

### 2.3 M02 球员（7/7）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| PLR-01 | GET | `/c/team/players` | `backend/src/main/java/com/yunhefc/query/controller/TeamPlayersController.java:19` | 已交付（后端） |
| PLR-03 | PATCH | `/c/me/player` | `backend/src/main/java/com/yunhefc/player/controller/PlayerSelfController.java:22` | 已交付（后端） |
| PLR-05 | GET | `/b/players` | `backend/src/main/java/com/yunhefc/player/controller/PlayerAdminController.java:27` | 已交付（后端） |
| PLR-06 | PATCH | `/b/players/{id}` | `.../PlayerAdminController.java:45` | 已交付（后端） |
| PLR-07 | POST | `/b/players/{id}/approve` | `backend/src/main/java/com/yunhefc/auth/controller/PlayerAccountAdminController.java:28` | 已交付（后端） |
| PLR-08 | POST | `/b/players/{id}/convert` | `.../PlayerAccountAdminController.java:35` | 已交付（后端） |
| PLR-09 | POST | `/b/players/{id}/disable` | `.../PlayerAccountAdminController.java:43` | 已交付（后端） |

**【事实】** PLR-01 已按位置组、出场降序、号码升序实现（`backend/src/main/java/com/yunhefc/query/application/TeamPlayersApplicationService.java:31-60`），但 C04 球队页缺失（见 §3）。
**【事实】** PLR-03 仅接受 `avatarUrl`，头像上传接口 FIL-01 缺失（见 §2.15）。

### 2.4 M03 比赛（11/11）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| MAT-02 | GET | `/c/matches` | `backend/src/main/java/com/yunhefc/match/controller/MatchPublicController.java:32` | 已交付（后端） |
| MAT-03 | GET | `/c/matches/{id}` | `.../MatchPublicController.java:47` | 已交付（后端） |
| MAT-04 | POST | `/b/matches` | `backend/src/main/java/com/yunhefc/match/controller/MatchAdminController.java:33` | 已交付（后端） |
| MAT-05 | PATCH | `/b/matches/{id}` | `.../MatchAdminController.java:60` | 已交付（后端） |
| MAT-06 | POST | `/b/matches/{id}/status` | `.../MatchAdminController.java:67` | 已交付（后端） |
| MAT-07 | POST | `/b/matches/{id}/postpone` | `.../MatchAdminController.java:75` | 已交付（后端） |
| MAT-08 | POST | `/b/matches/{id}/cancel` | `.../MatchAdminController.java:91` | 已交付（后端） |
| MAT-09 | GET | `/b/matches` | `.../MatchAdminController.java:40` | 已交付（后端） |
| MAT-10 | GET | `/b/matches/{id}` | `.../MatchAdminController.java:54` | 已交付（后端） |
| MAT-11 | POST | `/b/matches/{id}/reschedule` | `.../MatchAdminController.java:83` | 已交付（后端） |
| MAT-12 | POST | `/c/matches/{id}/status` | `.../MatchPublicController.java:52` | 已交付（后端） |

**【事实】** MAT-12 仅后端存在；C03 没有管理员状态下拉入口（`frontend/apps/web/src/pages/MatchDetailPage.vue:660-693` 仅 4 个只读 Tab）。
**【事实】** B02 `MatchesPage.vue` 只调用 MAT-09 列表，没有接入 MAT-04~08/11（`frontend/apps/admin/src/pages/MatchesPage.vue:40-49`）。

### 2.5 M04 报名与候补（7/7）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| REG-01 | POST | `/c/matches/{id}/registrations` | `backend/src/main/java/com/yunhefc/registration/controller/RegistrationController.java:24` | 已交付（后端） |
| REG-02 | DELETE | `/c/matches/{id}/registrations/me` | `.../RegistrationController.java:33` | 已交付（后端） |
| REG-03 | GET | `/c/me/registrations` | `backend/src/main/java/com/yunhefc/query/controller/MyRegistrationsController.java:22` | 已交付（后端） |
| REG-04 | GET | `/c/matches/{id}/registrations/me` | `.../MyRegistrationsController.java:30` | 已交付（后端） |
| REG-05 | GET | `/c/matches/{id}/registrations` | `backend/src/main/java/com/yunhefc/query/controller/RegistrationRosterController.java:20` | 已交付（后端） |
| REG-06 | GET | `/b/matches/{id}/registrations` | `.../RegistrationRosterController.java:31` | 已交付（后端） |
| REG-07 | DELETE | `/b/registrations/{id}` | `backend/src/main/java/com/yunhefc/registration/controller/RegistrationAdminController.java:21` | 已交付（后端） |

**【事实】** C03 未调用 REG-01/REG-02：`frontend/apps/web/src/pages/MatchDetailPage.vue:293-380` 只有详情、本人报名读取与签到；没有报名/取消动作。
**【事实】** C06 未接入 REG-03；`frontend/apps/web/src/pages/AccountPage.vue:1-32` 无「我的报名」区块。

### 2.6 M05 考勤（6/6）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| ATT-01 | POST | `/c/matches/{id}/check-in` | `backend/src/main/java/com/yunhefc/attendance/controller/AttendanceController.java:26` | 已交付（后端） |
| ATT-02 | GET | `/c/matches/{id}/attendance/me` | `.../AttendanceController.java:34` | 已交付（后端） |
| ATT-03 | GET | `/c/matches/{id}/attendance` | `.../AttendanceController.java:40` | 已交付（后端） |
| ATT-04 | GET | `/b/matches/{id}/attendance` | `backend/src/main/java/com/yunhefc/attendance/controller/AttendanceAdminController.java:30` | 已交付（后端） |
| ATT-05 | PUT | `/b/matches/{id}/attendance/{playerId}` | `.../AttendanceAdminController.java:40` | 已交付（后端） |
| ATT-06 | POST | `/b/matches/{id}/attendance/temp` | `.../AttendanceAdminController.java:51` | 已交付（后端） |

**【事实】** B03 已接入 ATT-04/05/06 与 REG-07（`frontend/apps/admin/src/pages/RegistrationsPage.vue:130-168,191-285`）。
**【推断】** C03 设计要求的「名单与考勤只读展示」未实现；当前 REG-05 仅用于事件选人（`MatchDetailPage.vue:328-340`），不渲染全队名单。

### 2.7 M06 事件与比分（7/7）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| EVT-01 | GET | `/c/matches/{id}/events` | `backend/src/main/java/com/yunhefc/event/controller/EventController.java:28` | 已交付（后端） |
| EVT-02 | POST | `/c/matches/{id}/events` | `.../EventController.java:33` | 已交付（后端） |
| EVT-03 | PATCH | `/c/events/{id}` | `.../EventController.java:41` | 已交付（后端） |
| EVT-04 | DELETE | `/b/events/{id}` | `backend/src/main/java/com/yunhefc/event/controller/EventAdminController.java:47` | 已交付（后端） |
| EVT-05 | PATCH | `/b/matches/{id}/score` | `.../EventAdminController.java:55` | 已交付（后端） |
| EVT-06 | POST | `/b/matches/{id}/events` | `.../EventAdminController.java:31` | 已交付（后端） |
| EVT-07 | PATCH | `/b/events/{id}` | `.../EventAdminController.java:39` | 已交付（后端） |

**【事实】** C03 已接入 EVT-01/02/03（`MatchDetailPage.vue:314-326,486-490`）；B04 已接入 EVT-04/05/06/07（`EventsPage.vue:303-493`）。

### 2.8 M07 阵容与模板（8/8）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| LIN-01 | GET | `/c/matches/{id}/lineups/{side}` | `backend/src/main/java/com/yunhefc/lineup/controller/LineupController.java:23` | 已交付（后端） |
| LIN-02 | GET | `/b/matches/{id}/lineups/{side}` | `backend/src/main/java/com/yunhefc/lineup/controller/LineupAdminController.java:29` | 已交付（后端） |
| LIN-03 | PUT | `/b/matches/{id}/lineups/{side}` | `.../LineupAdminController.java:36` | 已交付（后端） |
| LIN-04 | POST | `/b/matches/{id}/lineups/{side}/template` | `.../LineupAdminController.java:45` | 已交付（后端） |
| LIN-05 | GET | `/b/formation-templates` | `backend/src/main/java/com/yunhefc/lineup/controller/FormationTemplateController.java:29` | 已交付（后端） |
| LIN-06 | POST | `/b/formation-templates` | `.../FormationTemplateController.java:37` | 已交付（后端） |
| LIN-07 | PATCH | `/b/formation-templates/{id}` | `.../FormationTemplateController.java:45` | 已交付（后端） |
| LIN-08 | DELETE | `/b/formation-templates/{id}` | `.../FormationTemplateController.java:54` | 已交付（后端） |

**【事实】** B05 已接入 LIN-02/03/04/05/06/07/08，并组合 REG-06 + GRP-02（`LineupPage.vue:379-444,663-834`）；C03 已接入 LIN-01（`MatchDetailPage.vue:594`）。

### 2.9 M08 队内分组（6/6）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| GRP-01 | GET | `/c/matches/{id}/grouping` | `backend/src/main/java/com/yunhefc/grouping/controller/GroupingController.java:20` | 已交付（后端） |
| GRP-02 | GET | `/b/matches/{id}/grouping` | `backend/src/main/java/com/yunhefc/grouping/controller/GroupingAdminController.java:32` | 已交付（后端） |
| GRP-03 | POST | `/b/matches/{id}/grouping/generate` | `.../GroupingAdminController.java:38` | 已交付（后端） |
| GRP-04 | PATCH | `/b/matches/{id}/grouping` | `.../GroupingAdminController.java:49` | 已交付（后端） |
| GRP-05 | POST | `/b/matches/{id}/grouping/save` | `.../GroupingAdminController.java:58` | 已交付（后端） |
| GRP-06 | DELETE | `/b/matches/{id}/grouping` | `.../GroupingAdminController.java:66` | 已交付（后端） |

**【事实】** C03 已接入 GRP-01（`MatchDetailPage.vue:546`）；B06 已接入 GRP-02~06（`GroupingPage.vue:281-628`）。

### 2.10 M09 通知（0/4）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| NTF-01 | GET | `/c/notifications` | — | **缺失** |
| NTF-02 | GET | `/c/notifications/unread-count` | — | **缺失** |
| NTF-03 | POST | `/c/notifications/{id}/read` | — | **缺失** |
| NTF-04 | POST | `/c/notifications/read-all` | — | **缺失** |

**【事实】** `backend/src/main/java/com/yunhefc/notification/` 只有 `application/`、`domain/`、`infrastructure/`，无 controller。
**【事实】** 已存在通知写入消费链路与 `countUnread` 读取适配器（`notification/application/NotificationReadAdapter.java:10-16`、`notification/infrastructure/NotificationMapper.java:16-21`），但列表、单条已读、全部已读无实现。
**【事实】** `PendingAccountFilter.java:43-44,55-56` 已把上述 4 条路由列入待审核账号白名单，说明路由曾被规划但未落地。

### 2.11 M10 审计（0/1）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| AUD-01 | GET | `/b/audit-logs` | — | **缺失** |

**【事实】** `backend/src/main/java/com/yunhefc/audit/` 只有写入链路（`AuditRecorder`、`AuditEventConsumer`、`AuditEventHandler`、`AuditLogMapper`），无查询 controller。
**【事实】** 112 条 MVP 需求中没有独立审计编号；B10 页面依据为 02 §4.2/§6.12 与 RBAC-04 的「变更审计」表述（`docs/云河FC-需求文档.md:227`）。

### 2.12 M11 系统配置（0/2）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| SET-01 | GET | `/b/settings` | — | **缺失** |
| SET-02 | PATCH | `/b/settings` | — | **缺失** |

**【事实】** `backend/src/main/java/com/yunhefc/settings/` 只有只读适配器 `RegistrationDefaultsAdapter`、`GroupingSettingsAdapter` 与 mapper/domain，无读写 controller。
**【推断】** CONFIG-01/02 的默认值读取已能支撑建赛（报名默认值）与分组（权重），但缺少 SET-01/02 后，B09 无法展示或修改配置。

### 2.13 M01 角色权限（7/7）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| ROL-01 | GET | `/b/roles` | `backend/src/main/java/com/yunhefc/auth/controller/RoleController.java:42` | 已交付（后端） |
| ROL-02 | POST | `/b/roles` | `.../RoleController.java:48` | 已交付（后端） |
| ROL-03 | PATCH | `/b/roles/{id}` | `.../RoleController.java:54` | 已交付（后端） |
| ROL-04 | DELETE | `/b/roles/{id}` | `.../RoleController.java:60` | 已交付（后端） |
| ROL-05 | PUT | `/b/roles/{id}/permissions` | `.../RoleController.java:67` | 已交付（后端） |
| ROL-06 | GET | `/b/permissions` | `.../RoleController.java:75` | 已交付（后端） |
| ROL-07 | PUT | `/b/users/{id}/roles` | `.../RoleController.java:81` | 已交付（后端） |

**【事实】** ROL 全部接口已实现，但 B08 页面为 `SectionPage` 占位（见 §4.2）。
**【事实】** AUTH-11/12/13 与 ROL-07 共用 `RoleController`；B07/B08/B11 的账号管理能力在后端已具备。

### 2.14 M02/M06 统计（1/1）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| STA-01 | GET | `/c/team/stats` | `backend/src/main/java/com/yunhefc/stats/controller/TeamStatsController.java:19` | 已交付（后端） |

**【事实】** STA-01 与 PLR-01 均可用，但 C04 球队页缺失，公开球队数据暂无页面承载（见 §3）。

### 2.15 M13 公开站点与文件（0/2）

| ID | 方法 | 路径 | controller | 状态 |
|---|---|---|---|---|
| FIL-01 | POST | `/c/files/avatar` | — | **缺失** |
| FIL-02 | POST | `/b/players/{id}/avatar` | — | **缺失** |

**【事实】** 全仓无 `/c/files`、`/b/players/{id}/avatar` 的实现；`PendingAccountFilter.java:54` 只预留了 FIL-01 路由。
**【事实】** `backend/src/main/java/com/yunhefc/infra/package-info.java:1-3` 仅声明 OSS 适配规划，无上传/签名实现。
**【事实】** 05 §3.15 明确首页/球队页不新增聚合接口，前端应使用 MAT-02 + `/c/me` + PLR-01 + STA-01（`docs/design/05.API接口设计.md:325-327`）；因此 M13 的缺口主要是页面与文件接口，不是聚合接口。

### 2.16 接口小计

| 模块 | 接口数 | 已交付 | 缺失 |
|---|---:|---:|---:|
| M01 账号与权限 | 17 | 17 | 0 |
| M01 邀请 | 6 | 6 | 0 |
| M02 球员 | 7 | 7 | 0 |
| M03 比赛 | 11 | 11 | 0 |
| M04 报名与候补 | 7 | 7 | 0 |
| M05 考勤 | 6 | 6 | 0 |
| M06 事件与比分 | 7 | 7 | 0 |
| M07 阵容与模板 | 8 | 8 | 0 |
| M08 队内分组 | 6 | 6 | 0 |
| M09 通知 | 4 | 0 | 4 |
| M10 审计 | 1 | 0 | 1 |
| M11 系统配置 | 2 | 0 | 2 |
| M01 角色权限 | 7 | 7 | 0 |
| M02/M06 统计 | 1 | 1 | 0 |
| M13 文件 | 2 | 0 | 2 |
| **合计** | **92** | **83** | **9** |

## 3. 按模块的「已交付 / 缺失 / 待决策」

需求条数按 `docs/云河FC-需求文档.md` §6.1–§6.12 的 ✅ MVP 行统计；一个需求可能由多个 05 接口或页面共同承载。

| 模块 | 需求（条数） | 已交付 | 缺失 | 待决策 |
|---|---|---|---|---|
| M01 账号与权限 | §6.1，14 条（AUTH-01~07、RBAC-01~07） | 17/17 后端接口；C05 登录页；B00 登录；B 端分区守卫 | C05 注册/找回密码/微信入口；C07 邀请落地；C08 微信回调；B08 角色权限页；B11 邀请管理页；B07 内的换绑/解绑入口 | 无接口契约缺口 |
| M02 球员档案 | §6.2，12 条（PLAYER-01~07、TEAM-01~05） | 7/7 后端接口；PLR-01 排序/分组；STA-01 | C04 球队页；B07 球员页；FIL-01/02 头像上传 | 【待决策】头像上传的 OSS 实现批次归 15 号票（本票只记缺口） |
| M03 比赛管理 | §6.3，15 条（MATCH-01~13、15、16） | 11/11 后端接口；C02 列表；C03 详情；B02 列表 | C01 首页；B02 创建/编辑/状态/延期/重排/取消 UI；C03 管理员状态下拉、分享、邀请队友 | 无 |
| M04 报名与候补 | §6.4，12 条（REG-01~12） | 7/7 后端接口；B03；C03 签到 | C03 报名/取消/名单展示；C06 我的报名；C03 独立「报名」Tab | 【待决策】C03 是恢复 02 §5.3 的独立「报名」Tab，还是调整设计把报名并入「概览」（当前实现为后者） |
| M05 考勤 | §6.5，8 条（ATT-01~08） | 6/6 后端接口；C03 自助签到；B03 标记/补录/临时到场 | C03 全队名单与考勤只读展示 | 无 |
| M06 事件与比分 | §6.6，15 条（EVENT-01~15） | 7/7 后端接口；C03 事件录入/编辑/时间线/推导比分；B04 事件 CRUD/比分 | 无接口缺口 | 无 |
| M07 阵容与战术 | §6.7，8 条（LINEUP-01~08） | 8/8 后端接口；C03 只读阵容；B05 排阵/模板/版本冲突 | 无接口缺口 | 无 |
| M08 队内赛分组 | §6.8，12 条（GROUP-01~12） | 6/6 后端接口；C03 分组只读；B06 生成/调整/保存/清除 | 无接口缺口 | 无 |
| M09 通知 | §6.9，4 条（NOTIFY-01/02/03/05） | 通知写入消费者、未读数适配器；AUTH-10 返回 unreadCount | NTF-01~04；C06 通知区块（未读标记/单条已读/全部已读） | 【事实】02 §5.6 已决定 MVP 不新增独立通知页，通知内嵌 C06；无需再决策 |
| M10 审计 | 无独立需求编号；依据 B10/§6.12 | 审计写入链路（Outbox + consumer + mapper） | AUD-01；B10 操作日志页 | 无（覆盖范围由 03 号票负责） |
| M11 系统配置 | §6.11，2 条（CONFIG-01/02） | 报名默认值/分组权重只读适配器；建赛与分组已消费 | SET-01/02；B09 系统设置页 | 无（配置语义由 06 号票负责） |
| M12 列表检索分页 | §6.12，5 条（LIST-01~05） | 后端 MAT-02/09、PLR-05、REG-03/05/06、ATT-03/04、INV-04 分页与筛选；前端 C02/B02/B03 分页 | C02 完整筛选+搜索/重置；B07 球员筛选/排序 UI；B10 日志列表；NTF-01/AUD-01 列表接口；AUTH-13/INV-04 的 pageSize 白名单校验 | 【待决策】是否在本期补齐 AUTH-13/INV-04 的 pageSize 校验（05 §2.2 要求，但 LIST-04 未点名这两个接口） |
| M13 公开站点 | 依赖 §6.2 TEAM-01~05 与 NOTIFY-02 | PLR-01、STA-01、MAT-02 | C01 首页；C04 球队页；C06 个人中心；C07 邀请落地；C08 微信回调；C09 错误页；FIL-01/02 | 【待决策】C06 路由命名：设计为 `/profile`，当前实现为 `/account`（`frontend/apps/web/src/router/index.ts:46-51`）；是改路由还是改设计 |

## 4. 02 §4 页面全量对账

### 4.1 C 端（9 个页面，路由 4/9）

| 编号 | 设计路由 | 实现路由 / 组件 | 状态 | 缺口与依据 |
|---|---|---|---|---|
| C01 首页 | `/` | `/` → 重定向 `/matches`（`frontend/apps/web/src/router/index.ts:39`） | **缺失** | 无首页组件；无下一场比赛 Hero、近期 3 场、报名 CTA、球队火力占位（02 §5.1） |
| C02 赛程 | `/matches` | `/matches` → `SchedulePage.vue`（`router/index.ts:40`） | **部分** | 仅状态筛选（`SchedulePage.vue:99-101`）；缺类型/地点/年份/月份、搜索/重置（02 §5.2、LIST-01）；分页与空态已有（`:110,84-87`） |
| C03 比赛详情 | `/matches/:id` | `/matches/:id` → `MatchDetailPage.vue`（`router/index.ts:41-45`） | **部分** | 只有 4 个 Tab（概览/事件/阵容/分组，`MatchDetailPage.vue:660-693`）；无报名/取消、全队名单、邀请队友、分享、管理员状态下拉（02 §5.3、REG-01/02/05、INV-03、MAT-12、MATCH-13） |
| C04 球队 | `/team` | — | **缺失** | 无路由/页面；后端 PLR-01+STA-01 已就绪（02 §5.4） |
| C05 登录/注册 | `/login` | `/login` → `LoginPage.vue`（`router/index.ts:31-33`） | **部分** | 仅手机号+密码登录（`LoginPage.vue:65-89`）；缺注册 Tab、找回密码、微信入口、邀请上下文（02 §5.5、AUTH-02/03/06/07） |
| C06 个人中心 | `/profile` | `/account` → `AccountPage.vue`（`router/index.ts:46-51`） | **部分/路由不一致** | 仅姓名/脱敏手机/角色/退出（`AccountPage.vue:16-31`）；缺资料编辑、通知、我的报名、改密、微信绑定状态（02 §5.6） |
| C07 邀请落地 | `/invite/:token` | — | **缺失** | 无路由/页面；后端 INV-01/02 已就绪（02 §5.7） |
| C08 微信授权回调 | `/auth/wechat/callback` | — | **缺失** | 无路由/页面；后端 AUTH-07/08/09 已就绪（02 §5.8） |
| C09 错误页 | `/403`、`/404` | catch-all → `/matches`（`router/index.ts:54`） | **缺失** | 无 403/404 页面；未匹配路由与无权限都未展示说明（02 §5.9） |

### 4.2 B 端（12 个页面，真实 6/12，占位 6/12）

路由由 `ADMIN_SECTIONS` 动态生成（`frontend/apps/admin/src/router/index.ts:61-66`；分区清单 `frontend/packages/permissions/src/index.ts:80-92`），浏览器路径带 `/admin/` 前缀（`frontend/apps/admin/vite.config.ts:8`）。

| 编号 | 设计路由 | 实现路由 / 组件 | 状态 | 缺口与依据 |
|---|---|---|---|---|
| B00 后台登录 | `/admin/login` | `/admin/login` → `LoginPage.vue`（`router/index.ts:51-55`） | 已交付 | 无后台权限提示已实现 |
| B01 工作台 | `/admin` | `/admin/dashboard` → `SectionPage.vue`（`router/index.ts:60,28`） | **占位** | 无指标卡、下一场、最近操作；依赖 MAT-09 + PLR-05 + AUD-01（02 §6.3） |
| B02 比赛管理 | `/admin/matches` | `MatchesPage.vue`（`router/index.ts:13-14`） | **部分** | 仅列表（status 筛选+分页，`MatchesPage.vue:40-49,97-113`）；缺创建/编辑/状态/延期/重排/取消（02 §6.4、MAT-04~08/11） |
| B03 报名/考勤 | `/admin/registrations` | `RegistrationsPage.vue`（`router/index.ts:16-17`） | 已交付 | 比赛选择、名单分栏、正常/缺勤、临时到场、移除与递补提示均已接入（`RegistrationsPage.vue:130-285`） |
| B04 事件/比分 | `/admin/events` | `EventsPage.vue`（`router/index.ts:19-20`） | 已交付 | 事件录入/编辑/删除、比分保存、推导比分提示（`EventsPage.vue:303-493`） |
| B05 阵容 | `/admin/lineup` | `LineupPage.vue`（`router/index.ts:22-23`） | 已交付 | 阵型、首发/替补、保存、模板 CRUD（`LineupPage.vue:379-834`） |
| B06 队内分组 | `/admin/grouping` | `GroupingPage.vue`（`router/index.ts:25-26`） | 已交付 | 生成、调整、保存、清除（`GroupingPage.vue:281-628`） |
| B07 球员 | `/admin/players` | `SectionPage.vue`（`router/index.ts:28`） | **占位** | 后端 PLR-05/06/07/08/09、AUTH-11/12、ROL-07 已齐；缺筛选/排序/分页 UI、编辑/审核/转正/注销/换绑/解绑（02 §6.9） |
| B08 角色权限 | `/admin/roles` | `SectionPage.vue`（`router/index.ts:28`） | **占位** | 后端 ROL-01~07 已齐；缺角色 CRUD、权限勾选、用户角色分配（02 §6.10） |
| B09 系统设置 | `/admin/settings` | `SectionPage.vue`（`router/index.ts:28`） | **占位（前后端双缺）** | SET-01/02 缺失，页面也无法接入（02 §6.11） |
| B10 操作日志 | `/admin/audit` | `SectionPage.vue`（`router/index.ts:28`） | **占位（前后端双缺）** | AUD-01 缺失（02 §6.12） |
| B11 邀请管理 | `/admin/invites` | `SectionPage.vue`（`router/index.ts:28`） | **占位** | 后端 INV-04/05/06 已齐；缺列表/创建/撤销/复制链接（02 §6.13） |

**【事实】** `SectionPage.vue:14-17` 的文案为「页面将在后续模块交付」，即 6 个占位分区的统一实现。
**【事实】** 占位分区仍受 `ADMIN_SECTION_PERMISSIONS` 守卫（`frontend/packages/permissions/src/index.ts:101-113`）；无权限访问会进 B 端 403 页（`router/index.ts:67-71`）。

## 5. M12 列表筛选 / 排序 / 分页现状

### 5.1 统一分页规则

**【事实】** 05 §2.2 规定：`page` 默认 1、≥1；`pageSize` 默认 10、可选 20/50/100；MAT-02/09 额外允许 1–100（`docs/design/05.API接口设计.md:51-56`）。
**【事实】** 公共 `PageQuery` 只允许 10/20/50/100（`backend/src/main/java/com/yunhefc/common/api/PageQuery.java:9-19`）。

### 5.2 逐列表对账

| 接口 | 后端筛选 | 后端排序 | pageSize 校验 | 前端现状 |
|---|---|---|---|---|
| MAT-02 `/c/matches` | type、venue、status、season、month、scope | `startAt:asc/desc` | 自定义 1–100（`MatchPublicApplicationService.java:55`），符合 §2.2 例外 | C02 仅 status 筛选（`SchedulePage.vue:99-101`）；分页 10/20/50/100（`:84-87`） |
| MAT-09 `/b/matches` | status、type、season、scope | `startAt:asc/desc` | 自定义 1–100（`MatchAdminApplicationService.java:86`），符合例外 | B02 仅 status 筛选（`MatchesPage.vue:97-113`）；分页完整 |
| PLR-05 `/b/players` | name、region、type、admin、status、position、skill | 号码/星级/位置/地区升降序白名单（`PlayerAdminApplicationService.java:236-250`） | `PageQuery` 白名单（`:47`） | B07 占位，无 UI |
| REG-03 `/c/me/registrations` | scope=active/history | 固定 | `PageQuery` 白名单（`MyRegistrationsApplicationService.java:47`） | C06 未接入 |
| REG-05/06 报名名单 | type、status | 固定 | `PageQuery` 白名单（`RegistrationRosterApplicationService.java:39`） | B03/B04/B05/B06 以 `pageSize=100` 拉取（如 `RegistrationsPage.vue:162-167`），无分页控件 |
| ATT-03/04 考勤 | status | `recordedAt` 升序 | `PageQuery` 白名单（`AttendanceApplicationService.java:129`） | B03 以 `pageSize=100` 拉取（`RegistrationsPage.vue:165-167`） |
| INV-04 `/b/invites` | type、status | id 倒序 | **未校验**（`InviteService.java:230-242`） | B11 占位 |
| AUTH-13 `/b/users` | status | id 升序 | **未校验**（`AuthService.java:209-218`） | B08/B11 占位 |
| NTF-01 `/c/notifications` | unread、page、pageSize | 时间倒序（设计） | — | **接口缺失**，前端也无通知区块 |
| AUD-01 `/b/audit-logs` | actor、action、targetType、targetId、from、to | 时间倒序（设计） | — | **接口缺失**，B10 占位 |
| ROL-01/06 | 无分页例外 | — | — | B08 占位 |

**【事实】** LIST-04 只点名赛程、比赛管理、球员查询、操作日志四类列表（`docs/云河FC-需求文档.md:629`）；其中赛程/比赛管理/球员的后端分页已实现，操作日志因 AUD-01 缺失未实现。
**【事实】** LIST-01 的 5 项筛选后端已全部支持，但 C02 只暴露 status（`SchedulePage.vue:99-101`），且没有「搜索/重置」交互。
**【事实】** LIST-02/03 的球员筛选与排序后端已实现（PLR-05），但 B07 页面占位，用户不可达（`router/index.ts:28`）。
**【推断】** LIST-05（空结果提示、分页不报错）在已有页面 C02/B02 基本满足（`SchedulePage.vue:110`、`MatchesPage.vue:115`），但 B07/B10 无法验收。

## 6. 现有 E2E / Vitest 覆盖

### 6.1 Playwright（14 条）

| 端 | 用例 | 覆盖点 | 依据 |
|---|---|---|---|
| C | 登录后看到真实赛程 | C05 登录、C02 列表、状态 Tag | `frontend/e2e/web.spec.ts:5-18` |
| C | 进入比赛详情并签到 | C03、ATT-01、REG-04 | `web.spec.ts:20-42` |
| C | 分组 Tab 只读展示 | C03、GRP-01 | `web.spec.ts:44-63` |
| C | 录入进球后时间线与推导比分更新 | C03、EVT-01/02 | `web.spec.ts:65-90` |
| C | 未登录访问受保护页跳登录并带 redirect | 路由守卫、`/account` | `web.spec.ts:92-100` |
| B | 管理员登录后看到比赛列表 | B00、B02 列表 | `frontend/e2e/admin.spec.ts:25-38` |
| B | B03 选择比赛并标记考勤 | B03、ATT-05 | `admin.spec.ts:40-68` |
| B | B06 生成分组并展示 A/B | B06、GRP-03 | `admin.spec.ts:70-105` |
| B | B06 调整→保存→清除→重新生成 | B06、GRP-04/05/06 | `admin.spec.ts:107-155` |
| B | B05 阵型模板复制、重命名、删除 | B05、LIN-05/06/07/08 | `admin.spec.ts:157-210` |
| B | B05 排满 8 人首发后保存，C03 只读查看 | B05、LIN-02/03、C03/LIN-01 | `admin.spec.ts:212-274` |
| B | 只读球员可查看分组但不能生成 | B06 权限禁用态 | `admin.spec.ts:276-298` |
| B | B04 录入/编辑事件并保存比分 | B04、EVT-04/05/06/07 | `admin.spec.ts:300-360` |
| B | 侧栏按 sections 过滤，直访无权限分区显示 403 | 分区守卫、B 端 403 | `admin.spec.ts:362-380` |

**【事实】** Playwright 依赖真实后端与 `scripts/sql/test-data.sql`，配置注释见 `frontend/playwright.config.ts:1-10`。
**【事实】** 未覆盖页面/流程：C01、C04、C06、C07、C08、C09；C05 注册/重置/微信；C03 报名/取消/名单/邀请/管理员状态；B01；B02 创建/编辑/状态/延期/重排/取消；B07–B11；M12 的 C02 全筛选与 B07 筛选排序。

### 6.2 Vitest（40 条，仅共享 package）

| 文件 | 条数 | 覆盖点 |
|---|---:|---|
| `frontend/packages/api-client/src/index.test.ts` | 18 | envelope、查询序列化、401/403、CSRF、幂等键 |
| `frontend/packages/permissions/src/index.test.ts` | 10 | 28 个权限码、权限判断、B 端分区映射 |
| `frontend/packages/shared/src/index.test.ts` | 12 | 分页结构、枚举文案、事件窗口、签到窗口 |

**【事实】** `frontend/apps/web` 与 `frontend/apps/admin` 下没有 Vitest 测试文件；页面/组件/store 均未被单测覆盖。
**【推断】** 现有 Vitest 只验证共享契约与工具函数，无法覆盖页面交付缺口；页面级验证依赖 Playwright 或后续补充组件测试。

## 7. 初始实施批次候选（不排序）

以下是候选批次，不代表优先级或排期；标注了主要依赖，供协调者切片。

| 批次 | 内容 | 主要依赖 / 备注 |
|---|---|---|
| A | M09 通知读接口：NTF-01~04 + C06 通知区块 | 后端已有读取适配器与表；C06 需先补齐 |
| B | M10 审计查询：AUD-01 + B10 页面 | 后端写入链路已存在；覆盖范围由 03 号票确认 |
| C | M11 配置读写：SET-01/02 + B09 页面 | 只读适配器已存在；配置语义由 06 号票确认 |
| D | B07 球员页 + B08 角色权限页 + B11 邀请管理页 | 三个页面后端均已就绪，可独立于 A/B/C |
| E | B02 比赛 CRUD/状态 UI + B01 工作台 | B01 的「今日操作数」依赖 AUD-01；B02 后端已就绪 |
| F | C 端公开页：C01 首页、C04 球队、C06 个人中心、C07 邀请落地、C08 微信回调、C09 错误页 | 依赖 PLR-01/STA-01/INV/AUTH 现有接口 |
| G | C03 补齐：报名/取消、全队名单、邀请队友、分享、管理员状态下拉、独立「报名」Tab | 依赖 REG-01/02/05、INV-03、MAT-12；Tab 结构需先决策 |
| H | FIL-01/02 头像上传 + B07/C06 头像入口 | 依赖 OSS 配置与 15 号票 |
| I | M12 收口：C02 完整筛选/搜索/重置、B07 筛选排序 UI、AUTH-13/INV-04 的 pageSize 校验、NTF/AUD 列表 | NTF/AUD 列表依赖 A/B；其余可独立 |

## 8. 事实、推断与待决策清单

### 8.1 事实

1. 后端 83/92 接口已实现；缺失 9 个集中在 M09/M10/M11/M13（§2.10–§2.15）。
2. B 端 6 个分区仍为 `SectionPage` 占位：dashboard、players、roles、settings、audit、invites（§4.2）。
3. C 端缺 5 个页面路由：C01、C04、C07、C08、C09；C06 路由与设计不一致（§4.1）。
4. C03 当前只有 4 个 Tab，且无报名/取消、邀请、分享、管理员状态入口（`MatchDetailPage.vue:293-380,660-693`）。
5. M12 后端筛选/排序基本齐备；前端 C02/B02 只暴露 status，B07 无页面；AUTH-13/INV-04 未做 pageSize 白名单校验（§5）。
6. Playwright 14 条、Vitest 40 条；页面级覆盖集中在已交付的 C02/C03 和 B02–B06（§6）。

### 8.2 推断

1. B01 工作台的「今日操作数」在 AUD-01 落地前无法完整实现（依据 02 §6.3 的组合读取要求，`docs/design/02.页面结构与交互设计.md:361-363`）。
2. FIL-01/02 不落地时，C06 资料编辑与 B07 头像字段只能接受已有 URL，无法完成上传闭环（依据 PLR-03 请求体与头像字段，`docs/design/05.API接口设计.md:182-184`）。
3. C03 的「概览」合并报名后，02 §5.3 的「报名」Tab 规格无法逐条验收；是否算设计偏差需协调者确认（§8.3 第 2 项）。

### 8.3 待决策

1. **C06 路由命名**：设计为 `/profile`（02 §4.1），实现为 `/account`（`frontend/apps/web/src/router/index.ts:46-51`）。保留 `/account` 并同步文档，还是改为 `/profile` 并保留旧路径重定向。
2. **C03 报名 Tab**：按 02 §5.3 增加独立「报名」Tab，还是确认当前「概览内含报名」为最终形态。
3. **AUTH-13/INV-04 分页校验**：05 §2.2 要求 10/20/50/100，但 LIST-04 未点名这两个接口；是否在本期一并补齐。
4. **头像上传批次**：FIL-01/02 与 OSS 签名流程是否并入本期（与 15 号票边界）。
5. **B02 交付深度**：本期只做列表（现状），还是补齐创建/编辑/状态/延期/重排/取消全部 MAT-04~08/11 UI。

---

**核对结论**：以 05 §3 的 92 个接口计，后端缺口为 9 个；以 02 §4 的 21 个页面计，C 端缺 5 个、部分 4 个，B 端占位 6 个、部分 1 个（B02）。其余缺口主要是页面接入、M12 前端筛选与测试覆盖，不是接口契约缺失。
