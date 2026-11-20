# 04 部署基线与 CI/配置面盘点

Type: research answer
Status: resolved
日期：2026-11-11
范围：只读盘点仓库；不写部署方案、不改代码/配置/设计文档。
标注：【事实】可直接由文件或文档核对；【推断】由现状推导；【待决策】留给 12–17 票定稿。

> 说明：`docs/design/03.整体架构设计.md` §5 是登录/报名/Outbox/Quartz 等运行流程，部署约束实际集中在 §6.4 与 §7；本票按 §5.1/§5.4、§6.4、§7 一并核对。
>
> 后续：部署形态已由票 12 定稿（ECS 2C2G + RDS MySQL 8.4；Redis/Kafka 自建 Compose；www/admin 同源反代 `/api`），本文件保留为 2026-11-11 的基线快照，冲突处以 `issues/12-grilling-deploy-topology.md` 与 06 §47 为准。

## 0. 结论摘要

1. 【事实】设计层已定义单 ECS + Docker Compose + Nginx、`www`/`admin`/`api` 三子域、通配符 TLS、固定镜像、ACR + GitHub Actions + SSH、私有 OSS、Flyway 启动迁移、Actuator + 结构化日志（`03:24,52-56,103-105,455-467,485-522,570-573,622-628`）。
2. 【事实】仓库层 `deploy/` 完全不存在；无 Dockerfile、Compose、Nginx、`.env.example`、备份脚本（`03:455-467` 仅为规划；`README.md` 明确「`deploy/` 尚未创建」）。
3. 【事实】CI 只有 prototype JS 语法检查，后端/前端构建、测试、ArchUnit、镜像、E2E、部署均未接入（`.github/workflows/ci.yml:1-19`）。
4. 【事实】配置面只有 `application.yml` + `application-dev.yml`，无 prod profile；无 `server.forward-headers-strategy`；Cookie `Secure` 默认 `false`、Domain 默认空；CORS 默认 localhost（`application.yml:63-67`；仅见 `application-dev.yml`）。
5. 【事实】短信关闭 mock 即拒绝（`SmsCodeService.java:68-73`）；OSS 零依赖/零配置/零实现（`infra/package-info.java:2`，`pom.xml` 无 OSS SDK）；V3 无管理员用户（`V3__seed_team_roles_permissions_settings.sql:1-101`）。
6. 【待决策】三子域与前端相对 `/api/v1` 基址的组合（`03:105` vs `frontend/apps/web/src/api/client.ts:7`）需要 12/16 票确定由 Nginx 代理还是改前端基址；首位管理员空库引导、真实 IP、Actuator 暴露面、备份任务归属同样未定。

## 1. 已定义（设计文档与已确认决策）

| 项 | 已定义内容 | 事实指针 |
|---|---|---|
| 部署形态 | 单 ECS + Docker Compose；单 Spring Boot 部署单元；固定镜像版本 | `03:24,52-56,570-573` |
| Compose 服务 | MySQL 8.4、Redis 6.2.24、Kafka 3.7.2、Nginx 1.27-alpine、后端 | `03:88,465` |
| 部署目录 | `docker-compose.yml`、`nginx/`、`env/.env.example`、`scripts/` | `03:455-467` |
| 域名 | `www`（C 端）、`admin`（B 端）、`api`（后端），同一主域；B 端 `/admin/*` 等价根路径 | `03:103,573,622` |
| TLS | 通配符证书覆盖三个子域 | `03:628`（AQ-09） |
| API 入口 | C/B 请求统一发往 `api` 子域；CORS 仅允许 `www`/`admin`，允许凭证 | `03:105`；`05:104-108` |
| 会话 | 两套 Cookie `YHFC_C`/`YHFC_B` 与 Redis 命名空间；父域、HttpOnly、SameSite=Lax、生产 Secure；C 7 天、B 2 小时 | `03:286-290,504`；`05:106-108`；`06 §11.1 AQ-18/AQ-39` |
| CSRF | token 由响应体返回、前端内存保存、`X-XSRF-TOKEN` 提交；不使用 CSRF Cookie | `03:288,505`；`05:107` |
| 真实 IP | 经 Nginx 时只信任其写入的 `X-Forwarded-For` | `03:506` |
| 可观测性 | Actuator 健康检查、结构化日志、慢查询/错误日志、关键指标；MVP 不引入 Prometheus/Grafana | `03:143,509` |
| 迁移 | Flyway 应用启动执行；小步、向后兼容；启动健康检查覆盖迁移结果 | `03:518,590`；`06 §11.1 AQ-23` |
| CI/CD | GitHub Actions 构建后端镜像推 ACR；SSH 到 ECS 执行 compose 更新；前端产物随发布包 | `03:466,571-573`；`06 §11.1 AQ-01/AQ-11` |
| 密钥 | 真实密钥只放服务器环境变量或密钥管理；CI 用 GitHub Secrets；不入库 | `03:467`；`06 §11.1 AQ-10/AQ-13` |
| OSS | 私有 Bucket + 后端签发短期 URL；开发/测试/生产同一 OSS，独立 Bucket 或前缀；头像 2MB | `03:54,495`；`06 §11.1 AQ-04` |
| 短信 | 阿里云云市场；仅显式 `SMS_MOCK_ENABLED=true` 用固定码；生产默认关闭 mock | `03:517`；`06 §11.1 AQ-03` |
| 备份/保留 | MySQL 每日全量 + binlog、保留 7 天；Redis AOF everysec + RDB；日志 14 天；通知 90 天/Outbox 7 天/邀请 30 天 | `03:508-521`；`06 §11.1 AQ-21/AQ-24/AQ-25/AQ-26` |
| Quartz | JDBC JobStore、单节点；禁止 `@Scheduled` | `03:337-364` |
| Kafka | 单节点 KRaft；Outbox 与业务同事务；通知/审计独立消费组 | `03:24,316-335,490-491` |
| 测试门禁 | 后端 JUnit 5 + Testcontainers、前端 Vitest + Playwright、ArchUnit；CI 需要 Docker | `03:510`；`06 §11.1 AQ-30/AQ-32` |
| 环境 | local + prod 两套，profile + 环境变量区分 | `03:517`；`06 §11.1 AQ-22` |

## 2. 仓库已有（已落地或已配置）

| 项 | 现状 | 事实指针 |
|---|---|---|
| 数据源 | `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` 可注入；默认 `useSSL=false`、`serverTimezone=UTC` | `application.yml:5-7` |
| Flyway | 启用、`classpath:db/migration`、`baseline-on-migrate: false` | `application.yml:8-11` |
| 迁移脚本 | V1 27 张业务表；V2 11 张 Quartz 表；V3 球队/3 角色/28 权限/9 设置；V4 8 套阵型；V5 表注释 | `V1`（`grep -c '^CREATE TABLE'`=27）；`V2`（=11）；`V3:4-101`；`V4:1-15`；`V5:1-19` |
| Quartz | JDBC JobStore、`initialize-schema: never`、`isClustered: false`、`overwrite-existing-jobs: true` | `application.yml:12-21` |
| Quartz 任务 | 邀请过期 cron 可配；Outbox 中继间隔可配；比赛状态每分钟、缺勤 10 分钟、统计 10 分钟、清理 03:30 硬编码 | `application.yml:58,77`；`InviteExpiryQuartzConfig.java:29`；`OutboxRelayQuartzConfig.java`；`MatchAutoStatusQuartzConfig.java:31`；`AttendanceAbsenceQuartzConfig.java:31`；`StatsRollupQuartzConfig.java:31`；`DataRetentionQuartzConfig.java:27` |
| Kafka | bootstrap/topic/重试/监听开关可注入；幂等生产者、`acks=all`、lz4、手动提交；DLT | `application.yml:22-39,68-74`；`KafkaConfig.java:16-19` |
| Redis/Session | Redis host/port、Session namespace 可注入；Session 存 Redis；`save-mode: always` | `application.yml:40-49` |
| 会话 Cookie | `YHFC_C`/`YHFC_B`、路径 `/api/v1/c`/`/api/v1/b`、HttpOnly、可配 Secure/Domain/SameSite | `PathSessionIdResolver.java:13-16,52-80`；`application.yml:60-65` |
| CORS | `/api/**`、显式 Origin 列表、`allowCredentials(true)`、允许 CSRF/Idempotency/Request-Id 头 | `CorsConfig.java:15-25`；`application.yml:66-67` |
| Actuator | 依赖存在；暴露 `health,info`；两者 permitAll；CSRF 忽略 `/actuator/**` | `pom.xml:39-41`；`application.yml:91-95`；`SecurityConfig.java:48,53` |
| SMS | mock 开关/固定码/TTL 可注入；实现仅 mock | `application.yml:53-56`；`SmsCodeService.java:28-47` |
| 微信 | enabled/mode/appId/secret/callback/redirect 前缀/UA 校验/state TTL 可注入；`mode=real` 选真实客户端，否则 mock | `application.yml:78-86`；`WechatConfig.java:17-22`；`RealWechatOAuthClient.java:26-47` |
| dev profile | mock 短信/微信，关闭 Kafka 监听与 Outbox | `application-dev.yml:1-12` |
| CI | 仅 push/PR 下对 `prototype/assets/js/*.js` 做 `node --check` | `.github/workflows/ci.yml:1-19` |
| 测试基础设施 | Testcontainers MySQL 8.4 + Redis 6.2.24；无 Kafka 容器；测试重置库并加载 V3 种子 | `IntegrationTestSupport.java:37-44,45-54` |
| 构建入口 | Maven Wrapper、Spring Boot/Failsafe、ArchUnit；前端 pnpm lock、Vitest/Playwright 脚本 | `backend/mvnw`、`backend/pom.xml:120-143`、`frontend/package.json:16-27`、`frontend/pnpm-lock.yaml` |
| 前端 API | dev 走 Vite `/api` 代理；生产基址仍为相对 `/api/v1/{c|b}` | `frontend/apps/web/vite.config.ts:13-25`；`frontend/apps/web/src/api/client.ts:7`；`frontend/apps/admin/src/api/client.ts:7,15` |

## 3. 缺失（零实现 / 零配置）

1. 【事实】`deploy/` 目录不存在：无 `docker-compose.yml`、Dockerfile、Nginx 站点配置、`.env.example`、备份/恢复脚本（`03:455-467` 规划；仓库根目录无 `deploy/`；`README.md`「`deploy/` 尚未创建」）。
2. 【事实】CI 无 Java 21/Maven、无 `mvn test`/Failsafe/ArchUnit、无镜像构建与 ACR 推送、无 pnpm install/build/Vitest、无 Playwright、无 SSH 部署（`.github/workflows/ci.yml:1-19`）。
3. 【事实】无 `application-prod.yml`，`application.yml` 未设置 `spring.profiles.active`；只有 dev profile（`backend/src/main/resources/application-dev.yml:1-12`）。与 `03:517`「local + prod 两套」不一致。
4. 【事实】无 `server.forward-headers-strategy`，仓库无 Nginx 配置；代码以 `getRemoteAddr()` 作为客户端 IP（`RequestIdFilter.java:27`、`AuthController.java:67`、`BackendAuthController.java:48`）。
5. 【事实】无 OSS SDK 依赖、无 `yunhefc.oss.*` 配置键、无 `OssGateway` 实现；`infra/package-info.java:2` 仅声明「Kafka、Redis、OSS、Quartz 网关」。FIL-01/02 未实现。
6. 【事实】短信无真实服务商客户端；仅 `SmsCodeService` 的 mock 分支（`SmsCodeService.java:35-66`）。mock 关闭时 `requireMockEnabled()` 抛 `INTERNAL_ERROR`（`:68-73`）。
7. 【事实】微信生产参数为空：`enabled=false`、`mode=mock`、AppID/Secret/callback 默认空、redirect 前缀默认 `/`（`application.yml:78-86`）。
8. 【事实】无 `logback-spring.xml`（`docs/rules/logging-rules.md:12,124` 要求由它统一格式/滚动/输出与脱敏）；无结构化日志、日志轮转、14 天保留配置。
9. 【事实】Actuator 只有 `health,info`；无 readiness/liveness、无 `show-details`、无 metrics、无独立管理端口；代码中无健康指标实现，无「迁移结果」专用健康检查（`application.yml:91-95`；`grep HealthIndicator` 无结果）。
10. 【事实】无 graceful shutdown、Hikari 连接池、Redis 密码、Kafka SASL/SSL、数据库 TLS 配置（`application.yml` 无对应项；`DB_URL` 默认 `useSSL=false`，`:5`）。
11. 【事实】V1–V5 无管理员用户：V3 只插入 team/role/permission/role_permission/system_setting，没有 `INSERT INTO app_user`（`V3:4-101`）。管理员只在 `scripts/sql/test-data.sql:30-33,81-88`，注释说明供 dev/Playwright。
12. 【事实】无自动备份、OSS 审计导出、通知 90 天/邀请 30 天清理脚本或任务；`DataRetentionService.java:16-24` 只清理过期幂等键、消费去重、已发送 Outbox（7 天）。
13. 【事实】无 Compose 资源上限、Kafka 磁盘保留、容器 healthcheck、日志驱动/轮转配置（`deploy/` 缺失）。
14. 【事实】Playwright 不启动后端，注释要求先启动 dev 后端并执行 `test-data.sql`（`frontend/playwright.config.ts:3-6,35-49`）；CI 未编排该前置条件。

## 4. 风险

| 风险 | 类型 | 依据 | 说明 |
|---|---|---|---|
| Nginx 后真实 IP 丢失 | 事实 + 推断 | `03:506`；`application.yml` 无 forward-headers；`AuthController.java:67` | 未启用 forward headers 时 `getRemoteAddr()` 为 Nginx/容器地址，登录限流按同一 IP 计数，可能误伤全部用户 |
| Cookie Secure/Domain 未显式设置 | 事实 | `application.yml:63-64`；`PathSessionIdResolver.java:57-65` | 生产若不注入 `SESSION_COOKIE_SECURE=true`/父域，HTTPS 安全基线与跨子域会话不符合 `03:287,504` |
| CORS 默认 localhost | 事实 | `application.yml:67`；`CorsConfig.java:15-25` | 生产未覆盖 `CORS_ALLOWED_ORIGINS` 时 www/admin 跨域请求被拒；dev 代理移除 Origin 会掩盖问题（`web/vite.config.ts:20-23`） |
| 三子域与前端相对 API 基址冲突 | 待决策 | `03:105`；`frontend/apps/web/src/api/client.ts:7`；`admin/src/api/client.ts:7,15` | 设计说请求发往 api 子域，前端却请求同源 `/api/v1`；需 12/16 票定 Nginx 代理或改基址 |
| 空库无管理员 | 事实 + 待决策 | `V3:1-101`；`test-data.sql:30-33`；票 12 | 生产 Flyway 只建角色/权限/设置；无首位管理员创建路径；Flyway 失败即启动失败（`03:518`） |
| 短信生产不可用 | 事实 | `SmsCodeService.java:68-73`；`application.yml:54` | `SMS_MOCK_ENABLED` 默认 false，注册/重置/绑定直接 500；票 14 必须先接真实短信 |
| OSS 零实现 | 事实 | `pom.xml` 无 OSS SDK；`infra/package-info.java:2`；`03:495` | 头像上传/签名 URL/FIL-01/02 均未实现；票 15 需定 Bucket/前缀/TTL/校验 |
| 日志与可观测性不足 | 事实 + 推断 | `application.yml:91-95`；无 `logback-spring.xml`；`03:509,519` | 无结构化日志、轮转、14 天保留；Actuator 无 readiness，排障与发布判定能力不足 |
| Kafka/Redis/DB 无传输安全 | 事实 | `application.yml:5,22-49` | 未配置 Redis 密码、Kafka SASL/SSL、DB TLS；生产安全基线待 16 票定 |
| 备份/保留任务不完整 | 事实 + 待决策 | `03:508,520-521`；`DataRetentionService.java:23-24` | 仅清理部分运行期数据；无 mysqldump/binlog 上传 OSS、无通知/邀请清理、无年度审计导出；票 17 定范围 |
| Testcontainers 与设计不一致 | 事实 | `03:510`；`IntegrationTestSupport.java:37-44` | 设计写 MySQL/Redis/Kafka，实际无 Kafka 容器；CI 若要覆盖消息链路需补 |
| 前端构建/部署产物无定义 | 事实 | `frontend/package.json:20-27`；无 deploy | 有 build 脚本但无产物路径、静态托管与缓存头配置；票 12/13 定 |

## 5. 12–17 票需要决策、当前未定义的配置项

| 票 | 待决策配置项 | 当前状态 |
|---|---|---|
| 12 部署拓扑/发布回滚 | Compose 服务清单与 JVM/MySQL/Redis/Kafka 资源上限、Kafka 磁盘保留；三子域 Nginx 路由/TLS/SPA 缓存；不可变 tag 与回滚；首位管理员与空库初始化顺序；Actuator/日志/告警；真实 IP | 全未定义；只有 `03:455-467,570-573,622-628` 的原则 |
| 13 CI/CD | 后端 `mvn test`/ArchUnit/镜像推 ACR；前端 pnpm/Vitest；Playwright E2E；SSH 部署；触发策略；Secrets 清单；失败/通知/回滚 | 仅 prototype `node --check`（`.github/workflows/ci.yml:1-19`） |
| 14 短信/微信 | 短信服务商、签名、模板、文案、有效期/重发、失败重试限流；`SMS_MOCK_ENABLED` 生产策略；微信 AppID/Secret/回调域名/redirect 白名单/未关注路径；凭证位置与切换 | 仅有 mock 实现与空配置面（`SmsCodeService.java:68-73`；`application.yml:53-56,78-86`） |
| 15 OSS/FIL | Bucket 与前缀隔离、私有策略；上传链路与 2MB/类型校验；签名 URL TTL/刷新；B 端代传权限与审计；错误提示 | OSS 零代码/零配置（`infra/package-info.java:2`；`pom.xml` 无 SDK） |
| 16 安全基线 | TLS/证书、Cookie Secure/Domain/SameSite、CORS 白名单；forward-headers 与真实 IP；Actuator/Swagger/错误页暴露面；密钥管理；登录锁定/IP 限流/Nginx 限流分工；日志脱敏/依赖检查 | 仅部分配置有开关；forward-headers、prod 暴露面、限流分工未定义（`application.yml:63-67,91-95`） |
| 17 备份/保留 | mysqldump + binlog + OSS、本地副本与 7 天保留；Redis AOF/RDB；通知 90/Outbox 7/邀请 30 天清理归属（Quartz vs 脚本）；年度审计导出；备份产物验证；不做恢复演练 | 仅 `03:508,520-521` 原则；`DataRetentionService.java:23-24` 只覆盖部分；无脚本 |

## 6. 专项核对结论

### 6.1 V3 是否含管理员用户

【事实】不含。V3 只插入 `team`、`app_role`（GUEST/PLAYER/ADMIN）、28 个权限码、角色权限关系、9 项 `system_setting`（`V3:4-101`）。V1 创建 `app_user` 表（`V1:30`），但 V1–V5 均无用户种子。管理员账号仅存在于非迁移脚本 `scripts/sql/test-data.sql`（`:30-33,81-88`），明确用于 dev profile + Playwright。票 12 已把「首位管理员与空库引导」列为决策项；票 06 也把首位管理员排除在设置/种子票外（`.scratch/mvp-release/issues/06-grilling-settings-and-seed.md:15`）。

### 6.2 SmsCodeService 在 mock 关闭时的行为

【事实】`send()` 与 `verify()` 入口都调用 `requireMockEnabled()`（`SmsCodeService.java:39,47`）；`mockEnabled=false` 时记录 error 并抛 `BusinessException(INTERNAL_ERROR, "短信服务暂不可用")`（`:68-73`）。不存在真实短信客户端分支，因此生产默认 `SMS_MOCK_ENABLED=false`（`application.yml:54`）会直接阻断注册、重置密码、绑定手机。票 14 必须补真实服务商与失败/限流策略。

### 6.3 OSS 配置面

【事实】`application.yml` 无任何 OSS 配置键；`backend/pom.xml` 无 OSS SDK；`com.yunhefc.infra` 只有 `package-info.java`，声明 OSS 网关但无实现（`infra/package-info.java:2`）。测试中的 `https://oss.example/...` 只是假 URL（如 `TeamPageIT.java:231`），不代表实现。票 15 需要从 0 定义配置键、Bucket/前缀、签名 URL 与上传链路。

### 6.4 微信配置面

【事实】`application.yml:78-86` 提供 `WECHAT_ENABLED`、`WECHAT_MODE`、`WECHAT_APP_ID`、`WECHAT_APP_SECRET`、`WECHAT_CALLBACK_URL`、`WECHAT_ALLOWED_REDIRECT_PREFIXES`、`WECHAT_REQUIRE_UA`、`WECHAT_STATE_TTL_SECONDS`。默认 `enabled=false`、`mode=mock`、`AppID/Secret/callback` 为空；`WechatConfig.java:17-22` 只在 `mode=real` 时使用真实客户端。即配置面已存在，生产值、回调域名与授权失败路径未定。

### 6.5 三子域、Nginx、TLS、真实 IP

- 【事实】设计：`www`/`admin`/`api` 三子域、通配符 TLS、Nginx 托管 SPA 并反代 API（`03:103-105,113-115,130,622-628`）。
- 【事实】仓库无 `deploy/nginx/`、无证书配置、无 `server.forward-headers-strategy`。
- 【事实】取客户端 IP 使用 `getRemoteAddr()`：`RequestIdFilter.java:27`、`AuthController.java:67`、`BackendAuthController.java:48`。
- 【推断】若不启用 forward headers，Nginx 反代后所有请求的 `remoteAddr` 都是代理地址；登录失败锁定/限流会按代理 IP 聚合，存在误锁与绕过风险。需在票 12/16 定 `server.forward-headers-strategy=framework`（或等价）与 Nginx 只信任自身写入的 `X-Forwarded-For`。

### 6.6 Actuator / 健康检查

【事实】依赖存在（`pom.xml:39-41`），暴露 `health,info`（`application.yml:91-95`），`/actuator/health`、`/actuator/info` 放行（`SecurityConfig.java:53`），CSRF 忽略 `/actuator/**`（`:48`）。无 `probes.enabled`、无 `show-details`、无自定义 `HealthIndicator`。`03:509,518` 要求健康检查与「迁移结果」覆盖，当前只有默认健康项；【推断】Flyway 迁移失败会阻止应用启动（Spring Boot 启动顺序），但没有可区分的 readiness 检查供发布脚本判定。

### 6.7 Flyway / Quartz / Kafka

- 【事实】Flyway：启用、classpath、`baseline-on-migrate=false`；V1–V5 已在本地 Docker MySQL 验证（`application.yml:8-11`；`06:391`）。
- 【事实】Quartz：JDBC JobStore、`initialize-schema=never`、单节点 `isClustered=false`（`application.yml:12-21`）；V2 建 11 张 QRTZ 表。任务 cron：邀请过期可配（`application.yml:58`），其余多数硬编码（见 §2）。票 17 若把保留清理归 Quartz，需要新增/改造任务配置。
- 【事实】Kafka：bootstrap/topic/重试可注入；生产者幂等、`acks=all`、lz4；消费者手动提交、CooperativeSticky；DLT（`application.yml:22-39,68-74`；`KafkaConfig.java:16-19`）。无 SASL/SSL、无 consumer group 配置项（groupId 在监听器常量中）。`03:24,490-491` 规定单节点 KRaft、按域拆 topic；仓库无 topic 创建/分区/保留期脚本。

### 6.8 CI 与测试前置

【事实】CI 仅 prototype 语法检查（`.github/workflows/ci.yml:1-19`）；无 Docker 能力声明。后端测试用 Testcontainers MySQL/Redis（`IntegrationTestSupport.java:37-44`），无 Kafka 容器，与 `03:510` 不一致。Playwright 依赖外部启动的后端与 `test-data.sql`（`frontend/playwright.config.ts:3-6`），CI 未提供编排。票 13 需决定阶段顺序、Docker 服务与 Secrets。

### 6.9 前端生产基址与静态托管

【事实】C/B 端 `baseUrl` 均为相对 `/api/v1/{c|b}`（`frontend/apps/web/src/api/client.ts:7`；`frontend/apps/admin/src/api/client.ts:7,15`），`api-client` 直接拼接（`packages/api-client/src/client.ts:64,143`）；dev 由 Vite 代理到 `localhost:8080` 并移除 Origin（`web/vite.config.ts:13-25`）。设计说请求发往 `api` 子域（`03:105`）。【待决策】生产要么在 www/admin 的 Nginx 上代理 `/api`（与「统一发往 api 子域」表述不一致），要么给前端注入 `https://api.<domain>/api/v1` 基址；两种方案的 CORS/Cookie/CSRF 行为不同，需 12/16 票定稿。

## 7. 未执行与边界

- 只读盘点；未执行 SQL、未启动服务、未修改代码/配置/设计文档、未更新 `map.md`。
- 未验证生产域名、证书、ACR、OSS、微信、短信的真实可用性；这些属于 12–17 票的决策与上线检查。
- 本文件不提出部署方案，只记录现状、缺口与待决策项。
