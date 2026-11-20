# 14 — 真实短信与微信接入

Type: grilling
Status: resolved
Blocked by: 04

## Question

`SmsCodeService` 在 mock 关闭时直接拒绝，生产注册/重置/绑定会断；微信网页授权也需生产配置。确定：

- 短信服务商与 API、签名与模板报备、验证码文案、有效期与重发间隔。
- 发送失败、重试、限流与用户提示；`SMS_MOCK_ENABLED` 的默认值与生产开关策略。
- 微信网页授权生产配置：AppID/Secret、回调域名、授权失败与未关注路径、解绑与重新授权。
- 真实凭证的存放位置（服务器环境变量/Secrets）与切换步骤。
- 首批上线前的验证清单（真实手机号、微信内置浏览器）。
- 票 12 约束：网页授权回调固定 `https://www.<domain>/api/v1/c/auth/wechat/callback`（同源 www），不落在 api 子域；host-only Cookie 依赖此路径。

产出接入清单与失败处理口径；不写代码、不做压测。事实指针：需求 AUTH-02/03、05 §2.4、`auth/application/SmsCodeService.java`、`auth/controller/WechatAuthController.java`、`application.yml`、04 答案。

## Answer

状态：resolved（2026-11-12）。以下为已确认决策与接入清单；本票不写代码、不改 04/05 契约，配置与适配器落地归 18 号票切片后的实施批次。

### 短信（阿里云云市场，HTTP + APPCODE）

- **渠道**：阿里云云市场短信，认证方式 **HTTP + APPCODE**。只冻结厂商无关的 `SmsSender` 端口与 `MarketplaceSmsSender`（HTTP/APPCODE）适配器契约；产品名、接口地址、模板 ID 在实施前由人工提供，联调前用 mock。
- **签名与模板**：**一个签名 + 一个通用模板**（含 `${code}`），REGISTER/RESET/BIND 共用；用途只影响 Redis key 与业务校验。
- **验证码**：6 位数字、TTL 300 秒，沿用 `yunhefc.sms.ttl-seconds`；验证 5 次失败作废不变。
- **限流**：**保持现状 5 次/分钟/手机号**（跨用途共用，现有 `AuthController` 的 `sms:<sha256(phone)>` 检查）；不新增 60 秒间隔或日配额。已知风险：单号一小时最多 300 条，成本与骚扰受服务商侧限额兜底。
- **失败处理**：**服务商返回成功后才写 Redis**；失败不落码、不自动重试。通用映射：号码非法 → `40001`；服务商流控 → `42901`（带 `retryAfterSeconds`）；APPCODE/签名/模板/系统错误 → `50001`。不新增错误码。
- **开关**：`SMS_MOCK_ENABLED` 默认 `false`，dev/test 为 `true`；**prod 下 mock=true 或 APPCODE/签名/模板缺失则启动失败（fail-fast）**。
- **凭证**：ECS `/opt/yunhefc/.env`（票 13 已定）：`SMS_APPCODE`、`SMS_SIGN_NAME`、`SMS_TEMPLATE_ID`；不进 GitHub Secrets。实际落地按云市场产品 gyytz 使用 `SMS_SIGN_ID`（见 B1 票 06/06 §59）。

### 微信网页授权

- **生产配置**：`WECHAT_ENABLED=true`、`WECHAT_MODE=real`、`WECHAT_APP_ID`、`WECHAT_APP_SECRET`、
  `WECHAT_CALLBACK_URL=https://www.<domain>/api/v1/c/auth/wechat/callback`（票 12 固定，同源 www，host-only Cookie）。公众号网页授权域名登记 `www.<domain>`，根路径提供 `MP_verify_*.txt`。
- **scope**：**`snsapi_base`**，静默获取 openid，无授权确认页，不获取也不存储昵称/头像；业务只使用 openid。
- **失败口径**：非微信 UA → `42201`「请在微信内打开」；缺少 code、state 失效 → `40001`，前端显示**站内提示页**「微信授权未完成」并提供重试/返回首页；微信 `40029/40163` → `40001`；AppID/Secret/微信系统错误 → `50001`；只记 errcode，不记 code/openid/secret；不自动重试。
- **解绑与重新授权**：**仅管理员 `AUTH-12`**，留痕（票 08 已定补微信绑定审计）。已登录账号绑定其他微信 → `42201`「账号已绑定其他微信，请联系管理员解绑」；openid 已绑其他账号 → `42201`「该微信已绑定其他账号」；管理员解绑后重新走网页授权即可绑新微信；未关注公众号不做特殊分支。
- **与票 21 边界**：本票**不决定** `AUTH-10` 是否返回 `wechatBound`，留给票 21。

### 上线前验证清单

- 短信：真机三用途收码；5 次/分钟限流；错误码 5 次锁定；过期码；服务商失败路径（不落码）；prod 关闭 mock；缺凭证启动失败。
- 微信：微信内首次授权（静默、无确认页）→ 绑手机；老用户登录；已登录绑微信；管理员解绑后重绑；缺少 code/state 失效提示页；非微信浏览器提示。
- 凭证轮换演练一次（APPCODE 与微信 Secret）。

### 影响面

- 不改 04/`schema.sql`；不改 05 契约（`AUTH-07/08` 路径与响应不变）。
- 02 需补「微信授权未完成」提示页文案，实施批次同步 02 + 06；本票只记录口径。
- 票 18 将上述配置、`SmsSender`/`MarketplaceSmsSender`、prod fail-fast、提示页纳入实施切片。
