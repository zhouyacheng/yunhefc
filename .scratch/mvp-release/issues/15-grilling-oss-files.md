# 15 — OSS 文件存储与 FIL-01/02

Type: grilling
Status: resolved
Blocked by: 04

## Question

当前 OSS 零代码，FIL-01/02 未实现。确定：

- Bucket 与前缀：开发/测试/生产同 Bucket 的隔离方式（前缀或独立 Bucket），私有 Bucket 策略。
- 头像上传链路：前端 → 后端 → OSS；类型与 2MB 大小校验；玩家头像 URL 的存储字段。
- 签名 URL 的 TTL、刷新方式与前端缓存；B 端代传（FIL-02）的权限与审计。
- 上传失败、超限、格式错误的提示与错误码。
- 是否为首批上线的最小实现预留后续图片扩展点（不实现二期）。

产出 FIL-01/02 契约确认与实现范围；不写代码。事实指针：需求 §6.9、PLAYER-07、03 §5 文件存储、05 §3.15、04 `player` 表、`docs/rules/*`。

## Answer

状态：resolved（2026-11-12）。以下为已确认决策与 FIL-01/02 契约；本票不写代码，实现归票 18 切片后的批次。

### Bucket、对象键与配置

- 开发/生产各一个**独立私有 Bucket**（同一阿里云 OSS 账号），Bucket 无公共读；dev/prod 各用独立 RAM 用户，仅授权本 Bucket `avatar/` 前缀（PutObject/GetObject/ListBucket）。
- 对象键 `avatar/{userId}/{ulid}.{ext}`；`ext` 由服务端文件头嗅探决定（`jpg|png|webp`），不加环境前缀。
- 配置键 `yunhefc.oss.{endpoint,bucket,access-key-id,access-key-secret}`；环境变量 `OSS_ENDPOINT/OSS_BUCKET/OSS_ACCESS_KEY_ID/OSS_ACCESS_KEY_SECRET`。
- `OSS_MODE=mock|real`：local/test 默认 `mock`（无凭证可启动，联调时显式 real + dev Bucket）；prod 必须 real，缺配置或 mock 时启动失败。凭证只进本地 `.env` 与 ECS `/opt/yunhefc/.env`，不进 GitHub Secrets。
- 签名 URL TTL **15 分钟**，按响应现签；不做 Redis 缓存，不加批量签名接口。

### FIL-01 / FIL-02

- FIL-01 `POST /c/files/avatar`，`player:edit:own`，PENDING 可用；FIL-02 `POST /b/players/{id}/avatar`，`player:edit:any`，目标球员不存在返回 40401。
- `multipart/form-data`，文件字段 `file`；**只上传到 OSS，不写 `player.avatar_url`**。绑定统一走 PLR-03/PLR-06 的 `avatarUrl`，值为返回的 `objectKey`。
- 响应由 `{url,signedUrl}` 改为 **`{objectKey,signedUrl}`**。
- 校验：非空、≤2MiB、文件头仅 JPEG/PNG/WebP；空/损坏/超限 `40001`（`data.details` 指明原因），类型不符/伪装 `41501`，OSS 失败 `50001`；不新增错误码。
- 上传接口不限流（已知风险：账号被盗可刷存储）；FIL 本身不写审计，绑定时 `PLAYER_UPDATE` 快照覆盖对象键。
- 上传未保存产生的孤儿对象不清扫；是否纳入保留策略由票 17 判断。
- 入口：C06 资料编辑弹窗（FIL-01 → PLR-03）、B07 编辑弹窗（FIL-02 → PLR-06）；不做表格行独立「换头像」。

### 读语义

- `player.avatar_url` 只存对象键；`Player/PlayerPublic/PlayerAdmin`、球队页与 `MeView.player` 的 `avatarUrl` 在 DTO 组装时现签 15 分钟 URL。
- 写接口只接受对象键，格式 `^avatar/\d+/[0-9A-HJKMNP-TV-Z]{26}\.(jpg|png|webp)$`；不做 OSS 存在性探测。
- 前端直接使用 `avatarUrl` 作为 `<img src>`；过期或加载失败显示姓名首字母占位，不主动刷新；审计快照只存键。

### 前端与部署

- 选图后前端预检类型与 2MiB，Canvas 压缩至长边 1080px、JPEG 0.85（去除 EXIF）；HEIC/不可解码提示改用 JPG/PNG/WebP。
- 文案：超限「图片不能超过 2MB，请重新选择」；格式「仅支持 JPG/PNG/WebP」；失败「上传失败，请稍后重试」。
- Nginx `client_max_body_size 3m`；Spring `max-file-size=2MB`、`max-request-size=3MB`；后端以 2MiB 为准。

### 抽象与验收

- `com.yunhefc.infra.storage.OssGateway`：`putObject/signedUrl/exists/getObject`；`AliyunOssGateway`（real）+ `InMemoryOssGateway`（mock/test）；票 17 审计导出复用。
- JUnit/IT（fake gateway）覆盖校验、权限、PENDING、错误码；Vitest 覆盖压缩；Playwright 覆盖 C06 上传与 B07 展示/代传；E2E 走 mock；dev 真实 Bucket 人工验一次上传、签名与过期。

### 影响面

- 已同步：05 v0.28、04 v0.20、03 v1.16、02 v0.20、06 §49（v1.46）；`schema.sql` 仅改 `avatar_url` 注释；原型文案改 JPG/PNG/WebP。
- 未执行 SQL、未改 Flyway、未写业务代码、未运行后端测试。实施切片归票 18；Bucket/RAM 创建与真机验证归票 19。

## Comments

- 票 17 定稿（2026-11-12）：审计年度导出复用同一私有 Bucket 的 `audit/export/{year}/audit-{year}.jsonl.gz`；RAM 用户策略需同时授权 `avatar/` 与 `audit/export/` 前缀。OSS 不做版本控制、跨区域复制或生命周期规则；头像孤儿对象接受不清扫；审计导出对象永久保留。票 19 的 Bucket/RAM 检查按双前缀执行。
