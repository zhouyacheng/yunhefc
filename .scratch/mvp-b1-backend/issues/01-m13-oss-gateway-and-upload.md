# 01 — M13 OSS 网关与头像上传

Type: implementation
Status: resolved

**What to build:** 一条可独立演示的头像上传链路：后端接入私有 OSS 网关，球员（含待审核）上传自己的头像、管理员为指定球员代传，服务端校验文件后写入私有 Bucket，返回对象键与 15 分钟签名 URL。上传只写 OSS、不修改球员档案。dev/test 可用内存实现，prod 必须真实 OSS 且缺配置时启动失败。

**Blocked by:** None — can start immediately

**Status:** resolved

- [x] `OssGateway` 端口提供 `putObject`、`signedUrl`、`exists`、`getObject`；按 `OSS_MODE=mock|real` 选择内存实现或阿里云实现，domain 与 controller 不直接接触 OSS SDK。
- [x] 配置键 `yunhefc.oss.{endpoint,bucket,access-key-id,access-key-secret}`，可由环境变量覆盖；prod 下 `mock` 或缺少任一凭证时启动失败。
- [x] FIL-01 使用 `player:edit:own`，PENDING 账号可上传；FIL-02 使用 `player:edit:any`，目标球员不存在返回 40401。
- [x] 两个接口接受 `multipart/form-data` 字段 `file`，响应 `{objectKey,signedUrl}`；对象键为 `avatar/{userId}/{26 位 ULID}.{jpg|png|webp}`，扩展名由服务端文件头嗅探决定。
- [x] 空文件、损坏文件、超过 2,097,152 字节返回 40001 且 `data.details` 指明原因；非 JPEG/PNG/WebP 或伪装文件返回 41501；OSS 写入失败返回 50001。
- [x] 上传不写 `player.avatar_url`；multipart 上限为单文件 2MB、单请求 3MB，后端以 2MiB 为准。
- [x] 集成测试覆盖：球员上传、PENDING 上传、管理员代传、无权限、目标不存在、类型伪装、超限、空文件、对象键格式与签名 URL 存在；测试用内存网关。
- [x] `mvn verify` 全绿，ArchUnit 通过，无 schema/Flyway/Quartz DDL 改动。


## Comments

- 2026-11-12 交付：提交 `086473a`（feat: add OSS gateway and avatar upload）。
- 验证：`mvn verify` 259 tests / 0 failures / BUILD SUCCESS（JDK 21，隔离运行）；新增 `AvatarUploadIT` 9 项、`AvatarImageTest` 6 项、`OssConfigTest` 5 项、`UlidTest` 2 项、`GlobalExceptionHandlerTest` 1 项；ArchUnit 通过；无 schema/Flyway/Quartz DDL 改动。
- 实现位置：`infra.storage`（`OssGateway`/`AliyunOssGateway`/`InMemoryOssGateway`/`OssConfig`/`OssProperties`）、`player.application.AvatarUploadApplicationService`、`player.controller.AvatarController`、`player.domain.AvatarImage`、`common.util.Ulid`。
- 说明：签名 URL TTL 固定 15 分钟，不新增配置键；非 prod 下 real 模式缺凭证也 fail-fast；`exists`/`getObject` 按规格保留，供票 08 复用。
- 评审：两轴评审（Standards / Spec）已完成，修正了日志级别（ERROR→WARN）、票外 TTL 配置键、短小非图片错误码；两轴无遗留阻塞项。
