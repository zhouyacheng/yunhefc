# 06 — C06 个人中心与通知

Type: implementation
Status: resolved
Blocked by: 01 — 四 Tab 壳层、错误页与公共基座

**What to build:** 球员在 `/profile` 看到身份卡、编辑资料与头像、分页通知与已读操作、我的报名（进行中/历史）、账号安全与待审核横幅；通知未读角标由票 01 的轮询驱动。

## Scope

- 身份卡：头像、姓名、角色、脱敏手机号、号码、位置、星级。
- 资料编辑弹窗：姓名、地区、主副位置、头像（校验 → 压缩 → FIL-01 → 预览 → PLR-03 保存）。
- 通知区块：NTF-01 分页「加载更多」、未读筛选、NTF-03 单条已读、NTF-04 全部已读、按 `refType/refId` 跳转。
- 我的报名：REG-03 `active|history` 两组，默认进行中，分页 10/20/50/100。
- 账号与安全：找回密码入口、微信绑定状态只读、退出登录。
- 待审核横幅；PENDING 隐藏写操作入口且不调用 REG-03。
- 各区块独立加载/失败/空态。

## Owned files

- `frontend/apps/web/src/pages/ProfilePage.vue`
- `frontend/apps/web/src/components/ProfileEditPopup.vue`
- `frontend/apps/web/src/components/NotificationList.vue`
- `frontend/apps/web/src/components/MyRegistrations.vue`
- `frontend/apps/web/src/features/profile/avatar.ts`
- `frontend/apps/web/src/features/profile/avatar.test.ts`
- `frontend/packages/shared/src/notification.ts`（仅追加纯函数）
- `frontend/packages/shared/src/index.test.ts`（追加对应测试）
- `frontend/e2e/web-profile.spec.ts`

## API dependencies

- `AUTH-10 GET /c/me`：身份、`player`、`unreadCount`、`user.wechatBound`。
- `PLR-01 GET /c/team/players?all=true`：进入编辑前补齐本人 `region/secondaryPositions`（`MeView.player` 缺这两个字段）。
- `PLR-03 PATCH /c/me/player`：保存资料与头像对象键。
- `FIL-01 POST /c/files/avatar`：multipart `file`，返回 `{objectKey, signedUrl}`；只上传不绑定。
- `NTF-01 GET /c/notifications?unread&page&pageSize`。
- `NTF-03 POST /c/notifications/{id}/read`；`NTF-04 POST /c/notifications/read-all`。
- `NTF-02` 由票 01 的轮询负责，本页只读 `session.unreadCount`。
- `REG-03 GET /c/me/registrations?scope=active|history&page&pageSize`。
- `AUTH-06` 找回密码（跳转 `/login?mode=reset`）；`AUTH-05` 退出。

## UI states

- 身份卡：正常；头像空或非 http(s) → 姓名首字占位。
- 编辑弹窗：字段校验、图片校验/上传中/预览、保存中、失败重试。
- 通知：加载中、失败重试、空（全部/未读）、加载更多、未读标记、全部已读成功（`updated=0` 也成功）。
- 我的报名：两组独立分页；空态区分「暂无进行中报名 / 暂无历史报名」；比赛已取消显示「已作废」。
- PENDING：横幅「账号待审核」；隐藏编辑/头像；我的报名显示审核后可用提示。
- 账号安全：微信已绑定/未绑定；退出确认。

## Implementation notes

- **资料回填**：`MeView.player` 只有 `id/name/jersey/type/position/positionLabel/skill/avatarUrl`；打开编辑弹窗时先 `PLR-01?all=true`，按 `session.player.id` 找到本人，回填 `region` 与 `secondaryPositions`；找不到则留空并要求重填。保存后用 `PLR-03` 响应更新 `session.player`。
- **头像校验**：仅 `image/jpeg|png|webp`，≤2MiB；超限文案「图片不能超过 2MB，请重新选择」，格式错误「仅支持 JPG/PNG/WebP」。
- **压缩**：用 Canvas/`createImageBitmap` 缩到长边 1080px，输出 JPEG 质量 0.85；Canvas 重绘天然去除 EXIF；保留原图预览失败时的错误提示。
- **上传**：`FIL-01` 用 FormData（票 01 提供 `postForm`），字段名 `file`；拿到 `{objectKey,signedUrl}` 后显示预览；保存时 `PLR-03` 的 `avatarUrl` 只传 `objectKey`，不传签名参数。
- **通知**：列表按 `createdAt DESC`；默认每页 10，`loadMore` 直到 `page >= totalPages`；未读筛选切换时回到第 1 页并保留独立列表状态。点击通知：
  - `refType === 'MATCH'` 且 `refId` 存在 → `router.push('/matches/{refId}')`；
  - `refType === 'USER'` 或无/未知 ref → 只标记已读。
  - 标记已读失败 40401 时刷新列表；成功后本地更新 `readAt` 并调用 `session.refreshUnreadCount()`（或递减后由轮询校正）。
- **通知映射**放在 `packages/shared/src/notification.ts` 的纯函数（如 `notificationTarget(notification)`），返回 `{kind:'match', id}` / `{kind:'none'}`；本票独占该文件，避免 B2 冲突。
- **我的报名**：`REG-03` 返回 `Page<MyRegistration>`；使用现有 `@yunhefc/shared` 类型；状态文案与「已作废」按比赛状态派生；点击进入 `/matches/{matchId}`。
- **PENDING**：`user.status === 0` 时显示横幅；隐藏编辑/头像；不请求 REG-03（后端白名单不含该接口），显示「审核通过后可查看我的报名」；通知与已读仍可用。
- **微信状态**：只读展示 `session.user.wechatBound`；解绑不在本批。
- 退出：确认后 `session.logout()` → `/login`。
- 各区块错误互不影响；401 交由全局客户端处理。

## Test obligations

- Vitest `avatar.test.ts`：类型、大小、压缩尺寸/质量参数、错误文案；`packages/shared` 新增 `notificationTarget` 的 MATCH/USER/缺失 ref 分支。
- Playwright `web-profile.spec.ts`：
  - 1002 登录后进入 `/profile`，编辑姓名/地区/位置并保存成功（使用与种子相同值或专门回滚，避免污染后续用例）；
  - 通知列表显示种子未读；标记一条已读后未读样式消失；点击「全部已读」后角标归零；
  - 通知点击 MATCH 类型进入 `/matches/{refId}`；
  - 我的报名默认进行中，切换历史；
  - 1021 登录后 `/profile` 显示待审核横幅且无编辑按钮；
  - 无 console error。
- 头像上传在有 mock OSS 的环境执行；若无真实文件输入，至少覆盖校验失败的文案路径。

## Cross-ticket prerequisites

- 票 01：会话 `unreadCount`/轮询、`postForm`、`/profile` 路由、通知与 1021 种子。
- 票 05：1021 可登录；`/login?mode=reset` 入口存在。
- B1 已交付 NTF-01~04 与 FIL-01，无跨批阻塞。

## Result

- 交付：`de7eaf4`（身份卡、资料编辑弹窗、通知、我的报名、账号安全、PENDING 横幅；`notificationTarget`；Vitest/Playwright）。
- 验证：`pnpm -r test`、`pnpm -r typecheck`、`pnpm -r build` 通过；改动文件 ESLint/Prettier 通过；复位 `test-data.sql` 后 Playwright `--project=web` 33 条全绿（2026-11-12），`web-profile.spec.ts` 2 条覆盖资料保存、头像格式校验、单条/全部已读与角标、MATCH 通知跳转、我的报名两组与 PENDING 不请求 REG-03，无 console error。
- 说明：通知「全部已读」用例拦截 NTF-04，避免清空种子未读影响后续角标用例；单条已读走真实 NTF-03。通知全部/未读各自维护列表与分页状态；后端 `statusLabel` 为英文枚举，报名状态优先展示中文映射。因新增「我的报名」二级标题，同步将 `web-shell.spec.ts`、`web.spec.ts` 的「我的」标题断言改为 `exact`。
- 未执行：后端/schema/Flyway/Quartz 改动；真实 OSS 上传（仅覆盖头像格式校验失败路径）；未新增接口、权限码、错误码。
