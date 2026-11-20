# MVP 可上线 — 实施批次规格

Status: resolved（2026-11-12）

事实源：`.scratch/mvp-release/issues/01`–`17`、`20`、`21` 的 Answer；`.scratch/m05-m08-build/README.md`（粒度、门禁与交接先例）；`docs/云河FC-需求文档.md` v1.15；`docs/design/01`–`06`；`docs/rules/*`；`CLAUDE.md`。

本文件是票 18 的产物：把剩余工作切成可执行的实施批次，供后续 to-tickets 逐批出票。本文件不写实施代码。

---

## 1. 总览与顺序

```
B0 基座（可与 B1 并行，最先启动）
      │
B1 后端与契约补齐
      ├──────────────┐
B2 B 端页面        B3 C-A 公开页     （并行）
      └──────┬───────┘
           B4 C-B 互动与微信         （依赖 B3，必须相邻）
             │
           收口（票 19，非实施批）
```

| 批次 | 名称 | 前置 | 可并行 | 交付形态 |
|---|---|---|---|---|
| B0 | 基座：部署、CI/CD、prod 配置与可观测性 | 无 | 与 B1 全程并行 | `deploy/`、prod profile、`ci.yml`、`deploy.yml` |
| B1 | 后端与契约补齐：M09–M13 + wechatBound + sections | 无 | 与 B0 并行 | 92/92 接口 + 契约类型 |
| B2 | B 端页面：B02 深度 + B07–B11 + 下线 B01 | B1；B0 只影响 E2E/RC | 与 B3 并行 | 6 个可运营页面 |
| B3 | C-A 公开页：C01/C04–C07/C09 + C02 筛选 + 通知区 | B1 | 与 B2 并行 | 访客/球员可用闭环 |
| B4 | C-B 互动与微信：C03 补齐 + C08 | B3；真实微信/短信凭证 | 批内两票可并行 | 报名闭环 + 微信登录 |
| 收口 | 上线检查单与 cutover 口径 | B0–B4 | — | 票 19 |

**关键顺序约束**

1. **B1 内 M13（OSS）先于 M10 年度导出**：导出复用 `OssGateway`；M13 的头像签名读同时被 B2（B07）与 B3（C04/C06）消费，必须只做一次。
2. **B2 与 B3 可并行**：分属 `frontend/apps/admin` 与 `frontend/apps/web`。共享包按文件设 owner：`auth.ts`（`wechatBound`）与通知类型在 B1 完成，`match.ts` 归 B2，C 端列表辅助归 B3；两轨不交叉修改同一文件。`scripts/sql/test-data.sql` 按区块设单一 owner：通知/PENDING 种子归 B1 的 M09 票，球员/角色/邀请种子归 B2 的 B07 票；两轨只追加自己的区块，禁止同时重写同一段。
3. **B4 必须紧跟 B3**：C-A 已开放报名入口，取消/名单闭环在 C-B，中间不得 cutover。
4. **B0 与 B1 无互相阻塞**：B0 的 CI 镜像与部署验证可以先用当前 main 版本；B1 完成后用 RC 版本走一次完整发布。

---

## 2. 批次定义

### B0 基座

**目标**：在任何业务批次完成前，具备可部署、可发布、可回滚、可观测的生产基座。

**范围（建议票序）**

1. `01-prod-profile-and-observability.md`
   - `application-prod.yml`：RDS 私网连接与 Hikari 参数、Redis 密码、Kafka bootstrap、`server.forward-headers-strategy=framework`、Actuator 仅 `health,info` + liveness/readiness、`show-details=never`、springdoc 关闭、`server.error.include-*=never`、`flyway.enabled=false`、host-only + Secure Cookie、CORS 白名单。
   - prod 启动 fail-fast 自检：`SESSION_COOKIE_SECURE=true`、无 Cookie Domain、CORS 仅 www/admin https、`DB_PASSWORD` 非默认、`REDIS_PASSWORD` 非空；叠加短信/OSS/微信 fail-fast（实现归 B1 各适配器票，配置键在此冻结）。
   - `logback-spring.xml`：ECS JSON、requestId/userId、按天 + 100MB、14 天 / 2GB。
   - 测试：prod profile 上下文加载、fail-fast 反例单测；dev/test 行为不变。
2. `02-deploy-files.md`
   - `deploy/backend.Dockerfile`、`deploy/web.Dockerfile`（C/B dist → nginx 镜像）、`deploy/docker-compose.yml` + prod override（mem_limit / JVM 参数 / healthcheck）、Kafka topic 预创建（禁止自动建 topic）。
   - `deploy/nginx/`：www/admin/api 三子域、TLS 1.2/1.3、安全头、`X-Forwarded-For` 覆写、SPA fallback、`client_max_body_size 3m`、静态缓存与 gzip。
   - `deploy/bin/release.sh`：`sha-<7>`、`state/previous`、pull → 切 `current` → up → readiness ≤180s → 冒烟；`--rollback`。
   - `deploy/RUNBOOK.md`：RDS PITR、`.env` 恢复、证书重签、审计补导、FAILED Outbox、清理失败、首个管理员、迁移账号分离。
   - `scripts/sql/bootstrap-admin.sql`、`deploy/.env.example`。
3. `03-ci-pipeline.md`
   - `ci.yml`：PR 必需 `prototype` / `backend` / `frontend`；main 追加 `e2e`（MySQL 8.4 + Redis 6.2.24 service、boot jar、`test-data.sql`、Playwright）与 `images`（双 ACR 镜像、`sha-<7>`、禁止 latest）。
   - `deploy.yml`：`workflow_dispatch` 输入 `sha`、Environment `production` 人工批准、manifest 校验、rsync `deploy/`、SSH `release.sh`、webhook 通知。
   - 分支保护与必需检查口径写入 RUNBOOK/README。

**完成门**

- 干净 ECS 上 compose up；迁移账号先 `flyway migrate`（应用账号无 DDL），应用以 `flyway.enabled=false` 启动。
- readiness ≤180s；冒烟：health + C 端首页 + 一条公开读接口。
- 首位管理员 runbook 执行成功：B 端登录、ADMIN 权限数 28、`/c/me` 返回 `player` 非空、改密。
- CI 三项 PR 检查绿；main 的 e2e/images 绿；`deploy.yml` 能完成一次 RC 发布与一次回滚演练（不对外）。
- 空库 Flyway 幂等；无 schema/Flyway 改动。

**外部任务（可立即启动）**：ECS 2C2G + swap + Docker、RDS MySQL 8.4（应用/迁移账号、私网白名单）、三子域 DNS、通配符证书、ACR 双仓库、`.env` 落盘（600）。

---

### B1 后端与契约补齐

**目标**：92/92 接口与全部已冻结契约落地；页面批次只消费、不再改后端。

**范围（建议票序）**

1. `01-m13-oss-storage.md`：`infra.storage.OssGateway`（`putObject/signedUrl/exists/getObject`）、`AliyunOssGateway` + `InMemoryOssGateway`、FIL-01/02、`avatarUrl` 对象键 + 现签 15 分钟 URL、文件头嗅探与 ≤2MiB 校验、prod fail-fast。
2. `02-m09-notification.md`：NTF-01~04、分页/未读/已读/全部已读、PLR-08 `ROLE_CHANGED` Outbox、通知 90 天物理删除（自定义 `@Delete`）；同步 `frontend/packages/shared` 的通知类型与 `unreadCount`（无 UI）。
3. `03-m10-audit.md`：AUD-01、`MaskUtils`、`WECHAT_BIND` / `INVITE_EXPIRE` 补点、年度 JSONL.gz 导出任务（依赖 01）。
4. `04-m11-settings.md`：SET-01/02 部分合并 PATCH、9 键校验（与 `GroupingWeights.resolve` 同源）、`SETTINGS_UPDATE` 审计。
5. `05-contracts-and-lists.md`：`User.wechatBound` / `PlayerAdmin.wechatBound`（含 `frontend/packages/shared/src/auth.ts` 类型）、`AuthService.sections` 移除 dashboard、INV-04 / AUTH-13 枚举校验、`AUTH-13 keyword`。
6. `06-retention-job.md`：单一 `DataRetentionJob` 03:30 编排通知/邀请/Outbox/消费去重/幂等键，`yunhefc.retention.*` 配置、每轮 ≤1000 行；与 `InviteExpiryJob` 分工不变（依赖 02）。
7. `07-real-sms-wechat.md`：`SmsSender` 端口 + `MarketplaceSmsSender`（HTTP/APPCODE）、成功才落码、错误映射、prod mock/缺配置 fail-fast；微信 real 模式缺口补齐（回调路径、失败口径）。

**完成门**

- 05 的 92 个接口全部实现；无新接口编号、无新权限码、无新错误码。
- `mvn verify` 全绿（含 ArchUnit）；空库 Flyway 幂等；无 `schema.sql`/Flyway/Quartz DDL 改动。
- 新增 IT 覆盖：通知权限与已读、AUD-01 过滤/分页/`from>to`/403/脱敏、清理物理删除、SET 校验、FIL 权限/类型/大小、头像签名 URL、wechatBound、真实适配器 fake 端口。
- dev/test 默认 mock；prod 缺凭证启动失败。

**外部任务**：RAM 用户 + 私有 Bucket（`avatar/`、`audit/export/`）、dev Bucket 联调、短信 APPCODE/签名/模板、微信 AppID/Secret 与网页授权域名（后两项只影响 B4 真实验收）。

---

### B2 B 端页面

**目标**：B 端 6 个可运营页面，按 02 全量交付，替代 `SectionPage` 占位。

**范围（建议票序）**

1. `01-admin-shell-without-dashboard.md`：`ADMIN_SECTIONS` 移除 dashboard、`/admin` 与登录后落首个有权限分区、路由/侧栏清理。
2. `02-b02-match-management.md`：列表 + 创建/编辑/状态/延期/重排/取消（票 20），抽 `frontend/packages/shared/src/match.ts` 纯函数 + Vitest。
3. `03-b07-players.md`：PLR-05/06/07/08/09、AUTH-11/12、头像列与 FIL-02 上传入口、`wechatBound` 列与解绑、表头排序。
4. `04-b08-roles.md`：ROL-01~07、AUTH-13 选人与 `keyword`。
5. `05-b09-settings.md`：单表单 9 键保存、前端同源校验。
6. `06-b10-audit.md`：筛选/时间倒序/快照详情、脱敏展示、无导出入口。
7. `07-b11-invites.md`：INV-04/05/06、创建成功弹窗复制、行内撤销。

**完成门（页面门）**

- 02 §6.4/§6.9–§6.13 全部交付；加载/失败/空态/无权限齐备。
- 每页一条 Playwright 黄金路径：B02 创建→编辑→延期→重排→取消；B07 筛选 + 编辑 + 头像；B08 权限保存；B09 设置保存；B10 筛选 + 详情脱敏；B11 创建 + 复制 + 撤销。
- `pnpm -r build/typecheck/test` 绿；无 console error。
- 列表遵守 M12 契约：pageSize 10/20/50/100、筛选回到第 1 页、B07 表头排序、B10 搜索生效、B11 仅分页。

---

### B3 C-A 公开页

**目标**：访客可浏览、球员可注册/登录/维护资料、通知与邀请可用。

**范围（建议票序）**

1. `01-web-shell-tabs-errors.md`：底部四 Tab（首页/赛程/球队/我的）、`/profile` 路由与 `/account` 重定向、`/403`、`/404` 与通配路由、访客不启动通知轮询。
2. `02-c01-home.md`：`MAT-02?scope=upcoming&sort=startAt:asc&pageSize=3` + `GET /c/me`（仅登录态）、报名 CTA 与登录引导。
3. `03-c02-schedule-filters.md`：类型/地点/状态/年份/月份筛选、搜索生效、URL 同步、分页与空态（M12）。
4. `04-c04-team.md`：PLR-01（`all=true`）+ STA-01 合并、分组展示、头像。
5. `05-c05-auth-pages.md`：注册（邀请上下文）、找回密码、待审核提示、限流文案。
6. `06-c06-profile.md`：身份卡、资料编辑、FIL-01 头像上传、通知分页/未读/已读/角标、我的报名、账号安全、待审核横幅。
7. `07-c07-invite-landing.md`：INV-01 判定、登录/注册带 `inviteToken`、INV-02 接受。

**完成门（页面门）**

- 02 §5.1–§5.9 对应页面交付；加载/失败/空态/无权限齐备。
- 每页一条 Playwright：访客首页→登录引导、球队分组/展开、注册→待审核、邀请接受、通知已读→角标归零、403/404、C02 筛选/URL 恢复/空态。
- `pnpm -r build/typecheck/test` 绿；无 console error；未登录不触发 `GET /c/me` 401。

---

### B4 C-B 互动与微信

**目标**：完成报名闭环与微信登录；达到可 cutover 的功能面。

**范围（建议票序）**

1. `01-c03-completion.md`：C03 概览内报名/取消、全队名单与考勤、邀请队友、分享（稳定 URL + `navigator.share`/复制）、MAT-12 管理员状态下拉、404 分支。
2. `02-wechat-auth-and-binding.md`：C08 微信回调落地页、C05 微信入口、C06「微信：已绑定/未绑定」、失败提示页；依赖真实 MP 配置验收。

**完成门（页面门 + 验收门）**

- C03 报名→取消闭环、分享、状态变更 Playwright；分享复制与微信内提示可用。
- 微信内真实授权：静默 `snsapi_base`、绑定手机、老用户登录、管理员解绑后重绑、缺少 code/state 提示页（14 号票清单）。
- 112 条 MVP 验收矩阵全绿；Playwright 黄金路径全集无 console error。
- 真实短信/OSS/微信在 prod 下 fail-fast 生效且实际连通。

---

### 收口

票 19 基于本规格产出最终上线检查单：112 条验收记录方式、Playwright 清单、空库 Flyway 幂等、部署冒烟、安全基本检查、cutover 顺序/观测窗口/回滚触发。本规格只决定批次边界与门禁，不重复票 19 内容。

---

## 3. 上线方式

- **单次正式上线**：B0–B4 是内部里程碑，不对外分批公告。
- **RC 部署**：B1 起，每次 main 合并都可由 `deploy.yml` 手动批准部署到生产 ECS 做真实环境验证（无用户/不公告）；B2/B3/B4 完成后各做一次 RC 冒烟。
- **cutover 前置**：B4 完成；真实短信/微信/OSS 验收通过；112 条验收矩阵无阻断项；票 19 检查单全绿。
- **回滚**：`release.sh --rollback` 切回上一版本；数据库只做 expand/contract，不逆向迁移。

---

## 4. 外部任务与阻塞处理

外部任务整体视为 **F0（Day 0，非代码）**：与 B0/B1 同时启动，不等任何代码批次。每项在收口前必须有控制台/命令证据（证书有效期、Put/Get、真机收码、微信授权回调、RDS PITR 可见）；缺一项则票 19 的 go/no-go 不通过，不能用 mock 替代生产。

| 外部事项 | 最晚需要 | 阻塞对象 | 延期处理 |
|---|---|---|---|
| ECS / RDS / DNS / 证书 / ACR / `.env` | B0 完成门 | B0 部署验证、RC | 延期则 B0 代码完成但门禁不通过，RC 顺延 |
| RAM 用户 + dev/prod Bucket | B1 M13 真机联调 | 真实上传验证 | dev 用 mock 开发；真实联调与 cutover 前补 |
| 短信 APPCODE / 签名 / 模板 | B4 真实验收 | cutover | dev/test 用 mock；不阻塞 B1–B3 |
| 微信 AppID/Secret / 网页授权域名 / `MP_verify_*.txt` | B4 真实验收 | cutover | C08 用 mock 开发；真实验收失败不得 cutover |
| 域名备案 / 微信授权域名登记 | cutover | 外网访问与微信登录 | 与 B0 并行启动，属硬前提 |

统一口径：代码侧 dev/test 全 mock；prod 缺凭证或 mock 开启即启动失败（fail-fast），不允许静默降级。

---

## 5. 交接格式与批内票序

- 每批一个 `.scratch/<batch-slug>/README.md`：事实源、Problem/Solution、按页/模块的 User Stories、Implementation Decisions、Testing Decisions、Out of Scope、Further Notes（含交付结果表）。
- 一票一文件：`.scratch/<batch-slug>/issues/NN-<slug>.md`，顶部 `Type:`、`Status:`、`Blocked by:`；禁止合并 tickets 文件。
- 每票单独会话，票间 `/clear`；页面票依赖本批 shell 票与本模块后端票。
- 建议 slug：`mvp-b0-foundation`、`mvp-b1-backend`、`mvp-b2-admin-pages`、`mvp-b3-c-public`、`mvp-b4-c-interaction`。

**批内阻塞边**

- B0：01 → 02 → 03（03 依赖 Dockerfile 与 release.sh）。
- B1：01（M13）→ 03（M10 导出）；02 → 06（清理任务）；04、05、07 可与 02/03 并行。
- B2：01 → 02–07（页面票可并行）。
- B3：01 → 02–07（页面票可并行）；06 依赖 M09/M13（B1）。
- B4：01 → 02；02 的真实验收依赖外部配置。

---

## 6. 最容易出错的依赖点

1. **M13 头像签名读跨两端**：`Player/PlayerPublic/PlayerAdmin/MeView` 的 `avatarUrl` 改为现签 URL 会同时影响 B07、C04、C06；只在 B1 做一次，页面批不再改 DTO。
2. **dashboard 双端移除**：后端 `sections`（B1）与前端 `ADMIN_SECTIONS`/路由（B2）必须成对；只改一侧会导致登录后落 403。
3. **报名闭环跨 B3/B4**：C-A 开放报名、C-B 才有取消；单次 cutover 是安全边界，RC 阶段不得对外公告。
4. **prod 迁移账号分离**：`flyway.enabled=false` + 迁移账号 DDL / 应用账号 DML；漏配会让应用启动时无表。
5. **`SMS_MOCK_ENABLED` 默认值**：prod 默认 `false`，dev/test/CI 必须显式 `true`，否则 E2E 注册/登录链路全断。
6. **共享种子与 shared 包双轨冲突**：B2/B3 并行时若同时改 `frontend/packages/shared` 的同一文件或 `test-data.sql` 的同一区块，会出现「编译通过但契约不一致」；shared 按文件 owner、种子按区块 owner 拆分（见 §1）。

---

## 7. Out of Scope

- M14 二期能力、多队/平台超管、微服务/K8s/staging。
- 压测、备份恢复演练、邮件通知、依赖漏洞扫描（见票 16）。
- B01 工作台（本期下线）。
- 设计文档重构与视觉改版。
- 实施代码本身（本文件只定批次；实施在 map 关闭后另起批次）。
