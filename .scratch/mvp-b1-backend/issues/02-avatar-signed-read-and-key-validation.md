# 02 — 头像签名读与对象键写校验

Type: implementation
Status: resolved

**What to build:** 头像的读/写语义收口：所有面向客户端的球员读模型把数据库里的对象键换成 15 分钟签名 URL；球员自助资料与管理员编辑只接受上传接口返回的合法对象键，不再接受任意 URL。数据库与审计快照始终只存对象键。

**Blocked by:** 01 — M13 OSS 网关与头像上传

**Status:** resolved

- [x] `Player`、`PlayerPublic`、`PlayerAdmin`、球队页与 `MeView.player` 的 `avatarUrl` 在响应组装时现签 15 分钟 URL；无头像返回 null；签名不发生在实体或 mapper。
- [x] `MeView.player` 增加 `avatarUrl` 字段（05 §4 已冻结的读语义）。
- [x] PLR-03 / PLR-06 的 `avatarUrl` 只接受 `^avatar/\d+/[0-9A-HJKMNP-TV-Z]{26}\.(jpg|png|webp)$`；空串与空白归一为 null；其他值返回 40001。
- [x] 数据库 `player.avatar_url` 与审计快照只保存对象键，不含签名参数。
- [x] 更新现有把 `https://...` 当作头像写入并原样读出的测试，改为对象键输入与签名 URL 输出；新增非法键、空串与无头像用例。
- [x] 集成测试覆盖 C 端自助、B 端列表/编辑与球队页的签名读，以及管理员代传对象键的绑定闭环。
- [x] `mvn verify` 全绿，无 schema/Flyway/Quartz DDL 改动。

## Comments

- 2026-11-12 交付：提交 `ae619be`（feat: sign avatar URLs and validate object keys）。
- 验证：`mvn verify` BUILD SUCCESS（125 单测 / 262 IT / 0 failures，JDK 21）；新增 `AvatarObjectKeyTest` 4 项、`AvatarUploadIT` 两条 FIL→PLR 闭环；`PlayerSelfProfileIT`/`PlayerAdminIT`/`TeamPageIT` 改为对象键输入与 `mock://oss/<key>` 输出；无 schema/Flyway/Quartz DDL 改动。
- 实现位置：`infra.storage.AvatarUrlSigner`、`player.domain.AvatarObjectKey`；`PlayerAdminApplicationService.toView`、`TeamPlayersApplicationService.toView`、`AuthService`/`WechatAuthService.playerView` 现签；`PlayerSnapshot` 与 `AuthDtos.PlayerView` 增加 `avatarUrl`。
- 评审：两轴评审（Standards / Spec）已完成，按发现收紧了对象键校验（首尾空白不再 trim，严格匹配正则）并补 `/c/me` 无头像断言；无遗留阻塞项。
- 文档：05 §6、`docs/rules/api-rules.md` §3.8/§10.1 旧句修正，06 追加 §54 实施记录；05/06 文件本身已有本批未提交的文档改动，故文档改动留在工作区，未并入本次代码提交。
