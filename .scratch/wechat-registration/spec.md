# 微信注册 Spec

> 2026-11-20 已移除：微信注册功能已随 `3e7f508` 回滚（V12 恢复手机号非空）。本文档仅作历史记录。

- 日期：2026-11-19
- 状态：wontfix（2026-11-20 移除）
- 事实源：需求 v1.27、04 v0.29、05 v0.56、06 v1.95（P-74~P-77）
- 前置：`www.yunhefc.com` 已完成 ICP 备案；仍需公众号后台配置网页授权域名并做真机验收。

## Problem Statement

球队目前只支持「手机号 + 密码」注册，新成员必须准备手机号、接收短信验证码并设置密码才能进入系统。对只在微信里打开链接的球员，这条路径摩擦高、容易流失；同时微信授权虽已存在，却仍被强制要求先绑手机号，等于没有降低注册门槛。

## Solution

在微信内置浏览器中提供「微信注册」：微信网页授权后，只需补全姓名与主位置即可创建账号；手机号不是必需，后续可在个人中心补绑。微信注册默认进入待审核 + 外挂（PENDING + GUEST），带入队邀请时免审为正式球员（ACTIVE + PLAYER）。老用户仍可用手机号登录后在个人中心绑定微信。

## User Stories

1. As a 新球员, I want to 在微信里一键授权并只填姓名和位置就完成注册, so that 我不需要准备手机号和密码。
2. As a 新球员, I want to 注册后立即看到「账号待审核」提示, so that 我知道接下来会发生什么。
3. As a 新球员, I want to 在个人中心补绑手机号, so that 我之后可以用手机号登录和找回密码。
4. As a 新球员, I want to 通过微信内打开的入队邀请注册后直接成为正式球员, so that 我不需要再等管理员审核。
5. As a 老球员, I want to 在注册页选择「绑定已有手机号账号」, so that 我不会因为微信注册产生重复账号。
6. As a 老球员, I want to 用手机号登录后在个人中心绑定微信, so that 下次可以直接微信登录。
7. As a 管理员, I want to 在球员列表看到未绑定手机号的账号并禁止解绑其微信, so that 不会产生无法登录的死账号。
8. As a 管理员, I want to 继续通过审核/转正/注销管理微信注册账号, so that 原有账号生命周期不变。
9. As a 非微信浏览器用户, I want to 看到「微信注册请在微信内打开」并继续用手机号注册, so that 我不会卡在不可用入口。
10. As a 安全负责人, I want to 对微信注册建档限流并审计, so that 不能批量建号且操作可追溯。
11. As a 已注销用户, I want to 看到明确的解绑指引, so that 我知道联系管理员后才能重新注册。
12. As a 前端开发者, I want to `phoneMasked` 为 null 时展示「未绑定手机号」, so that 页面不会出现 `null`。
13. As a 后端开发者, I want to 手机号为 null 时登录/限流路径不抛 NPE, so that 微信登录稳定。
14. As a 运维, I want to 空库迁移到 V11 且旧镜像兼容, so that 发布可回滚。
15. As a 测试, I want to 在 mock 模式下走通完整微信注册链路, so that 不依赖真实公众号即可回归。

## Implementation Decisions

- 身份模型：openid 为微信账号的唯一身份；`app_user.phone` 改为可空；手机号非空时仍由 `uk_user_phone` 保证唯一。
- 不引入 `unionid`、不引入小程序、不新增数据库表、不新增业务索引。
- AUTH-08 形状不变：未绑定 openid 返回 `needBindPhone=true`（语义为需补全注册资料），不直接建档。
- AUTH-09 路径保持 `/c/auth/wechat/bind-phone`；请求 `{name, position, phone?, code?, inviteToken?}`：
  - `phone` 缺省：创建 phone 为空、password 为空的账号；默认 PENDING+GUEST；可用 TEAM 邀请则 ACTIVE+PLAYER 并消费邀请。
  - `phone` 存在：校验 BIND 短信；手机号已存在则把 openid 绑定到已有账号（带可用邀请时复用 INV-02 消费邀请并升级）；否则创建带手机号账号。
- 新增 AUTH-20 `POST /c/me/phone`：仅 `phone IS NULL` 的已登录账号可补绑；BIND 短信；冲突 40907；换绑仍仅管理员 AUTH-11。
- PENDING 白名单加入 AUTH-09 与 AUTH-20。
- 审计：新建账号记 `WECHAT_REGISTER`，openid 绑定记 `WECHAT_BIND`，补绑手机号记 `PHONE_BIND`；快照不落 openid 明文。
- 限流：AUTH-09 无手机号建档按 `sha256(openid)` 限流；沿用 `RedisRateLimiter`，超限 42901 + `Retry-After`。
- 无手机号且非注销的账号禁止解绑微信（避免既无 phone 又无 openid）。
- 前端：C05 注册模式增加微信注册入口（仅微信内）；邀请不可用时不带 `inviteToken`，降级为自助注册；C08 `bind=1` 页改为姓名 + 主位置，并可切换「绑定已有手机号账号」；C06 手机号为空时展示「未绑定手机号」与补绑入口。
- 接口计数 96→97；权限码仍 28；业务表仍 27；需求 ID 仍 114/112。

## Testing Decisions

- 只测外部行为：HTTP 契约、账号状态/角色、数据库可观察结果、页面可见文案；不测私有方法。
- 后端：`WechatAuthIT`、`AuthFlowIT`、`FlywayMigrationIT` 扩展；新增微信注册无手机号、带邀请、绑定已有账号、AUTH-20、DISABLED、唯一键并发、PENDING 白名单、限流、会话轮换用例。
- 前端：Vitest 覆盖 `session` 的微信注册/补绑、`validation` 条件校验、`wechat` UA 判定；Playwright 覆盖 C05→C08→C06 主链路与降级。
- 夹具：`scripts/sql/test-data.sql` 新增 1060–1063（PENDING/ACTIVE/DISABLED openid-only、老手机号账号）。
- 迁移：空库 V1→V11 11 条，`phone` 可空，`uk_user_phone` 仍存在，27 表/0 CHECK/0 业务外键。

## Out of Scope

- 小程序与 `getPhoneNumber` 自动取号。
- `unionid` 与跨应用身份打通。
- 自动账号合并。
- 自主换绑已绑定的手机号（仍仅管理员）。
- 微信注册的朋友圈/分享等增长功能。
- DISABLED 账号 openid 释放（后续单独评估）。

## Further Notes

- 微信网页授权不返回手机号，这是 A 路线的根本原因；不要在前端承诺「自动获取手机号」。
- 老手机号账号未登录时微信注册仍可能产生重复账号；靠注册页提示与管理员人工处理，不做自动合并。
- 生产 `WECHAT_MODE=real` 前必须完成公众号网页授权域名配置与真机验收。
