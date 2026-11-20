# 16 — 安全基线

Type: grilling
Status: resolved
Blocked by: 12

## Question

确定上线前必须满足的安全基线：

- TLS/证书、Cookie 的 Secure/Domain/SameSite、CORS 白名单。
- Nginx 后真实 IP 与 `forward-headers`；复核「`getRemoteAddr()` 取不到真实 IP + cookie-secure 默认 false + CORS」组合导致的登录死循环或限流误伤。
- Actuator / Swagger / 错误页的暴露面；生产 profile 关闭调试信息。
- 密钥与环境变量管理；仓库不得含真实凭证。
- 登录失败锁定、IP 限流与 Nginx 限流的分工。
- 日志脱敏（手机号、令牌、请求体）与依赖漏洞检查的执行方式（基本检查，不做全面安全审计）。
- 票 12 约束：C/B 同源反代 `/api`，Cookie host-only；CORS 仅作兜底；Actuator 只经 Nginx 内网，不对公网直接暴露；限流/锁定阈值在本票定。
- 票 12 遗留：Flyway 启动迁移使运行时数据库账号需要 DDL 权限；是否拆分迁移账号或改独立迁移步骤在本票定。Redis/Kafka 仅内网，是否加 Redis 密码/认证也在本票定。

产出上线前必查项与配置清单。事实指针：03 §5 安全 / §6.4、04 答案、`application.yml`、`common/security`、`common/error/GlobalExceptionHandler`、Nginx 规划。

## Answer

状态：resolved（2026-11-12）。以下为已确认决策与上线前必查清单；本票不写 Nginx/Compose/代码，配置落地归票 18 切片后的实施批次，检查单由票 19 复用。

### 范围与门禁

- 安全只做基本检查：不做渗透测试、不做压测、不做全面安全审计。
- **不进 CI**：不新增 gitleaks / dependency-check / pnpm audit job；`.github/workflows` 保持票 13 的范围。
- 上线前人工一次性执行：gitleaks（含 git 历史）、日志明文 grep、HTTPS 与安全响应头冒烟；结果写入票 19 检查单。
- **不做依赖漏洞扫描**：依赖以 Spring Boot 3.5.16 管理版本与前端 lockfile 为准，靠人工升级；残余风险见下。

### 传输与会话

- TLS 仅 1.2/1.3，禁 1.0/1.1；80 → 301 https；通配符证书宿主机只读挂载，续期手工执行（不在 CI），检查单提前 30 天核对到期时间。
- HSTS：`max-age=15552000; includeSubDomains`，**不 preload**。
- 安全响应头（Nginx 统一加）：`X-Content-Type-Options: nosniff`、`Referrer-Policy: strict-origin-when-cross-origin`、`X-Frame-Options: DENY`、最小 `Permissions-Policy`（`camera=(), microphone=(), geolocation=(), payment=()`）；`server_tokens off`；**本期不加 CSP**（Vant/Element 内联样式误伤成本高）。
- Cookie：host-only（不设 Domain）、`HttpOnly`、生产 `Secure=true`、`SameSite=Lax`、Path 保持 `/api/v1/c` / `/api/v1/b`；C 端 7 天 / B 端 2 小时滑动。C/B 仍是 `YHFC_C` / `YHFC_B` 两套。
- CORS 仅兜底：允许 `https://www.<domain>` 与 `https://admin.<domain>`，`allowCredentials=true`，方法与头沿用现有白名单（`CorsConfig`），`maxAge=3600`；dev 额外允许 `localhost:5273/5274`。禁止通配符。
- 生产自检（启动 fail-fast）：`SESSION_COOKIE_SECURE=true`、`SESSION_COOKIE_DOMAIN` 为空、CORS 白名单必须是上述两个 https 域名（拒绝 localhost / `*`）、`DB_PASSWORD` 不得为默认 `yunhefc`、`REDIS_PASSWORD` 非空；叠加票 14/15 的短信、OSS、微信 fail-fast。

### 暴露面

- Actuator 仅 `health,info` + liveness/readiness；`management.endpoint.health.show-details=never`；只经 Nginx 内网访问，backend 不发布端口。
- `api` 子域只放行 `/api/` 与 `/actuator/health`，其余返回 404；前端仍走 www/admin 同源。
- springdoc：**dev 引入**用于联调；prod 关闭 `springdoc.api-docs.enabled` 与 `swagger-ui.enabled`，上线前验证 `/v3/api-docs`、`/swagger-ui/**` 均 404。
- 错误响应：统一 envelope（`code/message/requestId`），无堆栈、SQL、内部路径；prod `server.error.include-*=never`、关闭 whitelabel、关闭 debug/devtools。
- B 端 admin 与 api 子域**不加 IP 白名单**，只靠登录、权限码与后端校验。

### 真实 IP

- Nginx **覆写** `X-Forwarded-For $remote_addr`（不使用 `$proxy_add_x_forwarded_for`，防客户端伪造左侧值），并设 `X-Forwarded-Proto`、`X-Forwarded-Host`。
- 后端 `server.forward-headers-strategy=framework`；backend 仅 Compose 内网可达。`getRemoteAddr()`、审计 `ip`、限流 key 均取真实客户端 IP。
- 此项**取代票 12** 的 `$proxy_add_x_forwarded_for` 写法；03/06 已按本票同步。

### 账号与内网

- Flyway 使用**独立迁移账号**（DDL）；应用账号仅 DML（SELECT/INSERT/UPDATE/DELETE）。
- prod **`spring.flyway.enabled=false`**；发布 runbook 增加前置步骤：用 flyway 容器/CLI + 迁移账号先 `migrate`，成功后再切 `current` 启应用；迁移失败不发版。dev/test 仍在启动时迁移。
- Redis 增加 `requirepass`（仅 Compose 内网，不发布端口）；`spring.data.redis.password` 从 ECS `.env` 读取；prod 密码为空即启动失败。dev/test 无密码。
- Kafka 不启用 SASL/SSL，仅 Compose 内网且不发布端口；MySQL 走 VPC 私网明文，不启用 `sslMode`。

### 限流与锁定

- 全部在应用层（Redis），**不使用 Nginx `limit_req`**。
- 保持现状：登录 10 次/分（`sha256(phone)` + IP）、短信 5 次/分/号码、报名 10 次/分（用户 + IP）、验证码校验 5 次失败作废。
- **新增短信 10 次/小时/IP**（满足需求 AUTH-02「按手机号/IP 限流」）。
- 本期**不新增**全站基础限流与上传限流（票 06 §49 已知风险延续）；03 中「其余接口基础限流」改为本期不做。
- 账号连续 5 次失败锁 15 分钟不变；凭证错误统一 `40102`（不区分手机号不存在/密码错误）；超限统一 `42901` + `Retry-After`。
- Redis 故障时限流降级放行并 `warn`；会话同样依赖 Redis，登录/鉴权本身会失败，不额外做 fail-closed。

### 密钥与日志

- 运行时密钥只放 ECS `/opt/yunhefc/.env`（权限 600，不入库）；GitHub Secrets 只放 ACR 写凭证、ECS SSH、webhook；ACR 拉取用服务器侧只读凭证。`.env.example` 只放占位符。
- 新增 `com.yunhefc.common.util.MaskUtils`：手机号前 3 后 4（长度 <7 为 `***`）；`openid`、`token`、`Authorization`、`password` 整体 `***`；日志调用点显式脱敏，不依赖全局替换。
- prod 日志级别 INFO（不开 DEBUG）；补 `MaskUtils` 单测。
- Nginx access log **保留完整 `$request_uri`**（用户确认）；接受微信回调 `code/state` 一次性凭证落盘的风险。

### 上线前必查清单

1. **TLS/证书**：证书覆盖 www/admin/api 且剩余有效期 >30 天；`openssl` 或 SSL Labs 确认仅 1.2/1.3；http 跳转 301。
2. **响应头**：`curl -I` 核对 HSTS、nosniff、Referrer-Policy、X-Frame-Options、Permissions-Policy；无 CSP。
3. **Cookie**：HTTPS 下 C/B 登录后 DevTools 确认 Secure、HttpOnly、SameSite=Lax、无 Domain、Path 正确；未登录访问写接口返回 401。
4. **CORS**：www/admin Origin 预检通过；其他 Origin 不返回 `Access-Control-Allow-Origin`；prod 启动自检拒绝 localhost/通配符。
5. **真实 IP**：伪造 `X-Forwarded-For` 请求后，日志/审计 IP 仍为客户端真实 IP；backend 端口从公网不可达。
6. **暴露面**：`/actuator/env`、`/actuator/beans`、`/v3/api-docs`、`/swagger-ui/**` 在生产均 404；`/actuator/health` 仅经 Nginx 返回 UP；错误响应无堆栈/SQL。
7. **迁移与账号**：发布前 `flyway migrate` 成功、`flyway_schema_history` 为最新；应用账号执行 DDL 必须失败；应用以 `flyway.enabled=false` 正常启动。
8. **Redis/Kafka**：未认证 `redis-cli ping` 失败、应用带密码连接正常；Kafka/Redis 端口未对公网发布。
9. **限流与锁定**：登录 11 次/分触发 429、短信 6 次/分触发 429、短信 IP 维度生效；连续 5 次错误密码后第 6 次返回 40103；错误提示不泄露账号是否存在。
10. **日志与密钥**：`gitleaks` 无命中；日志 grep 无明文 phone/token/password/openid/Authorization/请求体；`.env` 权限 600；生产未执行 `test-data.sql`，首个管理员登录后已改密。
11. **端到端**：HTTPS 下 C/B 登录无重定向循环；微信内网页授权回调可完成登录；admin 无权限账号被拒。

### 与票 12 / 票 13 的偏差

- 票 12：`X-Forwarded-For` 由 append 改为**覆写** `$remote_addr`；Cookie 属性补充生产 `Secure` 与启动自检；HSTS/安全头由本票补齐。
- 票 13/12：Flyway 由「应用启动时执行」改为 **prod 发布前人工 `migrate` + 应用 `flyway.enabled=false`**；发布 runbook 增加迁移前置步骤，readiness/冒烟时序不变。
- 票 13：「不在 CI 做漏洞扫描」维持；本票确认依赖漏洞扫描也不在上线前执行。

### 残余风险

- 无依赖漏洞扫描、无 SBOM，传递依赖高危需靠人工发现。
- 无全站基础限流、上传接口不限流；账号被盗可刷存储/接口。
- MySQL 私网明文、Kafka 无 SASL；依赖 VPC/Compose 网络隔离。
- Nginx access log 保留完整 URI，微信 `code/state` 与邀请 token 会落盘（一次性/短期）。
- 无 CSP；证书手工续期，过期将导致全站不可用。
- Redis 故障时限流失效（降级放行）。

### 同步与验证

- 已同步：03 v1.17（§5.1/§7/§8/§9）、06 v1.47（§50）；本票关闭，map 追加 Decisions-so-far。
- 未改动：需求、01/02/04/05、`schema.sql`、Flyway、`deploy/`（尚不存在）。
- 未执行 SQL、未写业务代码、未实现 API、未运行后端/前端测试；配置与实现归票 18，上线检查执行归票 19。

## Comments

- 票 17 修订（2026-11-12）：运行时密钥仍只从 ECS `/opt/yunhefc/.env`（600）读取；同一份 `.env` 在密码管理器离线保管、仅用于灾难恢复。不改变「不入库、GitHub Secrets 只放部署凭证」的基线；TLS 证书丢失后重新签发，不做 ECS 磁盘快照。
- 票 19 修订（2026-11-12）：依赖检查细化为上线前一次性人工轻检（`mvn dependency:tree` + `pnpm audit --prod`），结果记录在检查单、非阻断；不做自动扫描、不进 CI 的口径不变。
