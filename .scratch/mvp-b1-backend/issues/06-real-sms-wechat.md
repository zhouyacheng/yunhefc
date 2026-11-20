# 06 — 真实短信与微信失败口径

Type: implementation
Status: resolved

**What to build:** 生产可用的短信与微信链路：验证码经阿里云云市场短信真实下发，只有服务商成功后才写入验证码；服务商失败按类型映射为明确的业务错误；生产环境错误开启 mock 或缺少凭证时启动失败。微信真实授权补齐失败分类与文案，不再把所有失败都当作 50001。

**Blocked by:** None — can start immediately

**Status:** resolved

- [x] 新增 `SmsSender` 端口与 `MarketplaceSmsSender`（HTTP + APPCODE）；配置 `SMS_APPCODE`、`SMS_SIGN_ID`、`SMS_TEMPLATE_ID`、`SMS_ENDPOINT`，只从环境变量读取。
- [x] 发送流程：生成 6 位数字码 → 调 `SmsSender` → 成功后才写 Redis（TTL 300 秒）；失败不落码、不自动重试；mock 开启时写固定 mock 码。
- [x] 一个签名 + 一个含占位符的通用模板，供注册、重置密码、绑定手机三种用途共用；用途只影响 Redis key 与业务校验。
- [x] 校验语义不变：6 位、300 秒、5 次失败作废；Redis key 与尝试次数结构保持现状；接口限流保持 5 次/分钟/手机号。
- [x] 错误映射：号码非法 → 40001；服务商流控 → 42901 且带 `retryAfterSeconds`；APPCODE/签名/模板/系统错误 → 50001；不新增错误码。
- [x] prod 下 `SMS_MOCK_ENABLED=true` 或缺少 APPCODE/签名/模板时启动失败；dev/test/CI 显式开启 mock。
- [x] 微信：`40029/40163` 映射为 40001，其他 provider/系统失败映射为 50001；非微信 UA 返回 42201「请在微信内打开」；不自动重试；日志只记 errcode，不记 code/openid/secret。
- [x] prod 下 `WECHAT_ENABLED=true`、`WECHAT_MODE=real` 且缺 AppID/Secret/回调地址时启动失败；`WECHAT_MODE=mock` 也启动失败；回调路径保持同源 www。
- [x] 用假端口覆盖：发送成功、发送失败不落码、流控映射、mock 关闭、微信 errcode 分类、非微信 UA、prod 缺配置启动失败；`mvn verify` 全绿。

## Comments

- 2026-11-12 交付：提交 `e895752`（feat: add real marketplace SMS and wechat failure mapping）。
- 实现位置：`auth.sms.SmsSender`、`MarketplaceSmsSender`、`SmsSendException`、`SmsConfig`；`auth.application.SmsCodeService`；`auth.wechat.WechatOAuthException`、`RealWechatOAuthClient`、`WechatConfig`、`WechatProperties`；`auth.application.WechatAuthService`；`common.config.SmsProperties`；`application.yml`。
- 短信协议按参考实现 `AliyunMarketSmsCodeSender`：`POST {SMS_ENDPOINT}?mobile=...&templateId=...&smsSignId=...&param=**code**:...`，Header `Authorization: APPCODE <SMS_APPCODE>`，响应 JSON `code=0` 为成功；默认 endpoint `https://gyytz.market.alicloudapi.com/sms/smsSend`。票面 `SMS_SIGN_NAME` 以实际产品的 `SMS_SIGN_ID` 落地。
- 验证：`mvn verify` BUILD SUCCESS（178 单测 / 306 IT / 0 failures，JDK 21）；新增 `SmsCodeServiceTest`、`MarketplaceSmsSenderTest`、`SmsConfigTest`、`RealWechatOAuthClientTest`、`WechatConfigTest`、`WechatAuthServiceTest`，并补 `AuthServiceTest.sendSmsRejectsInvalidPhoneBeforeCallingSmsService`。
- 评审：Standards/Spec 两轴评审完成，已修复：空 `code` 直接 40001、`SMS_ENDPOINT` 非法在启动期失败且发送期不把含手机号/验证码的 URL 抛给全局日志、验证码格式化与流控码匹配改用 `Locale.ROOT`、HTTP 200 流控读取 `Retry-After`、README 默认启动命令补 `dev` profile。未改动接口、权限码、错误码、表结构与 Flyway。
- 文档：06 追加 §59 实施记录；`backend/README.md` 环境变量表与 `CLAUDE.md` 代码状态同步；B1 README 回填交付表。
- 待联调：真实短信与微信依赖产品凭证与线上回调域名，真机验收归 B4。
