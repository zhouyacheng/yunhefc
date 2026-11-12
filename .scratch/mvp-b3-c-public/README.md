# B3 C-A 公开页 — 实施规格

Status: resolved
Type: spec

事实源：`.scratch/mvp-release/batches.md` §1 note 2、§2 B3、§5、§6；`.scratch/mvp-release/issues/11-grilling-c-end-pages.md`、`07-grilling-notification.md`、`09-grilling-list-contract.md`、`21-grilling-wechat-binding.md`；`docs/design/02.页面结构与交互设计.md` §2.1/§2.3/§2.4、§5.1–§5.9、PQ-01/PQ-05/PQ-08；`docs/design/05.API接口设计.md` §2.2–§2.8、§3.1–§3.5、§3.10、§3.14、§3.15；`docs/design/06.设计方案评审结果.md` §43、§46、§51、§52、§55；`.scratch/mvp-b1-backend/README.md` 交付表；`frontend/` 与 `scripts/sql/test-data.sql` 现状。

本规格只定义 B3 的实施边界与验收口径，不写业务代码、不改 `schema.sql` / Flyway / Quartz DDL、不改后端。

---

## Problem Statement

B1 已把后端补齐到 92/92 接口：M09 通知 NTF-01~04、M13 OSS/FIL-01、`wechatBound`、`MeView.unreadCount` 均已交付并有集成测试。但 C 端页面仍停在骨架：

- 只有 `/login`、`/matches`、`/matches/:id`、`/account` 四条路由；没有首页、球队页、个人中心、邀请落地与 403/404，未知路由静默回赛程。
- `AppShell.vue` 只有「赛程 / 我的」两个 Tab，没有首页、球队，没有未读角标。
- `AccountPage.vue` 只有身份卡与退出；资料编辑、头像、通知列表/已读、我的报名、账号安全、待审核横幅全部缺失。
- `stores/session.ts` 的 `apply()` 丢弃 `MeView.unreadCount`，登录后角标没有初始值。
- `packages/api-client/src/client.ts` 只支持 JSON：只要 `body !== undefined` 就强制 `Content-Type: application/json`，FIL-01 的 multipart 上传无法发出。
- `scripts/sql/test-data.sql` 只有 4 场比赛与 20 名 ACTIVE 球员；没有通知、没有待审核账号、没有邀请、没有可展开的球员组，通知/登录/邀请页的黄金路径无法落测。
- `docs/design/02` §5.1–§5.9 与需求 AUTH-07、NOTIFY-01/03/05、REG-03、INVITE-01~05 的页面验收没有对应实现。

结果：访客能看到的只有赛程列表，球员无法注册、维护资料、查看通知或接受邀请；M09 的后端能力没有入口，B4 的 C03 报名闭环也缺少登录、注册与邀请上下文这些前置。

## Solution

按 8 张实施票交付 C-A 公开页；`01` 先落地公共基座，`02/03/04/06` 在基座之上并行，`05/07` 收口认证与邀请，`08` 处理票 02 评审遗留。每票以页面门 + 测试门收口：

| 票 | 内容 | 前置 | 主要交付 |
|---|---|---|---|
| 01 | 四 Tab 壳层、`/profile`、403/404、角标、轮询、api-client multipart、B3 测试种子 | 无 | 路由/壳层/会话/轮询/错误页/E2E 骨架/种子 |
| 02 | C01 首页与报名 CTA | 01 | Hero、近期 3 场、火力占位、REG-01、空态 |
| 03 | C02 赛程完整筛选 | 01 | 五类筛选、搜索、重置、URL 同步、分页 |
| 04 | C04 球队页 | 01 | 位置分组、默认 10、展开、STA-01 合并 |
| 05 | C05 登录/注册/找回密码 | 01 | 登录、注册、短信码、邀请上下文、待审核提示 |
| 06 | C06 个人中心 | 01 | 身份卡、资料编辑、头像、通知、我的报名、账号安全、待审核横幅 |
| 07 | C07 邀请落地 | 01（软依赖 05） | INV-01 判定、登录/注册透传、INV-02、失效/404 |
| 08 | 评审遗留处理（C01 后续） | 02 | MatchCard 抽取、AppShell 单表化、截止刷新、刷新重试、CTA 收敛 |

**批次完成门**

- 02 §5.1–§5.9 对应页面交付；加载中/失败/空数据/无权限状态齐备。
- 每页一条 Playwright 黄金路径，全部无 console error；未登录访客不发起 `GET /c/me`，不产生 401。
- `pnpm -r build`、`pnpm -r typecheck`、`pnpm -r test` 全绿。
- 无后端、`schema.sql`、Flyway、Quartz DDL 改动；无新接口、新权限码、新错误码。
- `test-data.sql` 中 B3 区块可重复执行，通知已读、报名、邀请使用等状态可重置。

---

## User Stories

### 全局壳层与错误页

1. 作为访客，我想在首页、赛程、球队、我的四个 Tab 之间切换，以便浏览公开内容。
2. 作为已登录球员，我想在「我的」Tab 看到未读通知角标，以便知道有新消息。
3. 作为访客，我想不登录也能看首页、赛程、球队与比赛详情，以便先了解球队。
4. 作为访客，我想在需要登录的操作处被带到登录页并在登录后回到原页面，以便不丢失意图。
5. 作为任何用户，我想未知路由进入 404、无权限页面进入 403，以便看到明确说明而不是被静默重定向。
6. 作为开发者，我想访客访问公开页时不发 `/c/me`、不产生 401 与 console error，以便验收门禁可测。

### C01 首页

7. 作为访客，我想看到下一场比赛的时间、地点、主客队与报名队列，以便决定是否参与。
8. 作为访客，我想点击报名 CTA 后被引导登录，以便继续报名。
9. 作为待审核用户，我想看到「账号待审核」提示且报名入口禁用，以便知道还需管理员审核。
10. 作为正式球员，我想在名额未满时直接报名，以便不等管理员。
11. 作为正式球员，我想在正式名额满时加入候补，以便仍有机会。
12. 作为外挂球员，我想在外挂名额满时看到明确拒绝原因，以便不反复尝试。
13. 作为球员，我想在已报名时看到「查看详情」，以便确认状态。
14. 作为任何用户，我想在报名截止、锁定、进行中、已结束、已延期、已取消时看到禁用原因，以便知道为什么不能报名。
15. 作为访客，我想在没有待进行比赛时看到空态与去赛程入口，以便不面对空白页。

### C02 赛程

16. 作为访客，我想按比赛类型、地点、状态、年份、月份筛选，以便快速找到比赛。
17. 作为访客，我想点击搜索才应用筛选，点击重置清空，以便不因每次点选都请求。
18. 作为访客，我想筛选条件写入 URL，以便刷新、分享或后退后恢复。
19. 作为访客，我想每页 10/20/50/100 条并可翻页，以便浏览长列表。
20. 作为访客，我想空结果时看到重置入口，以便快速回到全部比赛。

### C04 球队

21. 作为访客，我想按前锋/中场/后卫/门将分组查看球员，以便了解阵容结构。
22. 作为访客，我想每组默认看到 10 人并可展开/收起，以便页面不被长名单撑爆。
23. 作为访客，我想看到球员头像、姓名、号码、位置、星级、地区与本赛季出场/进球/助攻，以便评估球员。
24. 作为访客，我想统计缺失时看到降级展示而不是报错，以便页面稳定。
25. 作为访客，我不想看到手机号、外挂球员或详情页入口，以便符合公开字段范围。

### C05 登录/注册

26. 作为已注册球员，我想用手机号 + 密码登录，以便进入个人中心。
27. 作为新用户，我想用手机号、验证码、密码、姓名、主位置自助注册，以便加入球队。
28. 作为通过邀请进入的新用户，我想在注册时携带邀请 token 并看到邀请上下文，以便一次完成入队。
29. 作为新用户，我想注册成功后看到「账号待审核」提示并按白名单跳转，以便知道下一步。
30. 作为忘记密码的用户，我想用短信验证码重置密码，以便恢复访问。
31. 作为被限流的用户，我想看到明确的重试提示，而不是笼统失败，以便稍后再试。

### C06 个人中心

32. 作为球员，我想看到头像、姓名、角色、脱敏手机号、号码、位置与星级，以便确认身份资料。
33. 作为球员，我想编辑姓名、地区、主副位置并上传头像，以便保持资料准确。
34. 作为球员，我想分页查看通知、只看未读、单条已读与全部已读，以便处理消息。
35. 作为球员，我想点击比赛类通知进入对应比赛，用户类通知只标记已读，以便快速定位。
36. 作为球员，我想在「进行中 / 历史」之间切换我的报名，以便区分当前与过往。
37. 作为球员，我想看到微信绑定状态（已绑定/未绑定）与找回密码入口，以便管理账号安全。
38. 作为待审核用户，我想看到横幅并看不到写操作入口，以便知道账号受限。
39. 作为球员，我想退出登录，以便在共用设备上结束会话。

### C07 邀请落地

40. 作为被邀请人，我想看到邀请类型、球队/比赛、邀请人、有效期与剩余次数，以便判断是否加入。
41. 作为未登录被邀请人，我想先登录或注册并在完成后回到邀请页，以便完成接受。
42. 作为已登录且非成员的用户，我想一键接受入队邀请并立即成为正式球员，以便无需管理员审核。
43. 作为已被比赛邀请的用户，我想接受后进入比赛详情，以便自行决定是否报名。
44. 作为被邀请人，我想在 token 不存在时进入 404、过期/撤销/用尽时看到失效说明，以便知道邀请不可用。
45. 作为已是成员的用户，我想看到「无需重复加入」，以便不重复操作。

---

## Implementation Decisions

### 交付顺序与阻塞边

- 固定顺序：`01 → (02 ∥ 03 ∥ 04 ∥ 06) → (05 ∥ 07)`。
- `01` 是唯一的路由、壳层、会话、轮询、api-client 与种子 owner；未合入前其他票无法独立构建。
- `05` 与 `07` 共享 `redirect` + `inviteToken` 查询契约：`05` 负责消费并在注册后保留，`07` 负责生成入口与接受。
- 每票单独会话，票间 `/clear`。

### 路由与壳层

| 路径 | 页面 | 认证 | 说明 |
|---|---|---|---|
| `/` | C01 首页 | 公开 | Hero + 近期 3 场 |
| `/matches` | C02 赛程 | 公开 | 保留现有名称 |
| `/matches/:id` | C03 详情 | 公开 | 主要逻辑已存在 |
| `/team` | C04 球队 | 公开 | |
| `/profile` | C06 个人中心 | 需登录 | 原 `/account` 重定向到此 |
| `/invite/:token` | C07 邀请落地 | 公开 | |
| `/403` | C09 | 公开 | |
| `/404` | C09 | 公开 | 通配路由落此 |
| `/login` | C05 | 公开 | 登录/注册/找回密码三种模式 |

- 底部四 Tab：首页 `/`、赛程 `/matches`、球队 `/team`、我的 `/profile`；角标只挂在「我的」。
- 顶部品牌链接 `/`；未登录显示「登录」，已登录显示姓名并链接 `/profile`。
- 登录成功默认仍回 `/matches`（兼容现有行为）；注册成功按 `redirect` 白名单跳转，无 redirect 回 `/`。
- `/account` 使用路由 `redirect: '/profile'`，保留外部旧链接；同步更新现有 E2E 的 `redirect` 断言（原 `/account` → 新 `/profile`）。
- 只有 `/profile` 设 `requiresAuth`；公开页不因未登录跳转。

### 会话、未读角标与轮询

- `stores/session.ts` 增加 `unreadCount`；`apply()` 保存 `MeView.unreadCount`（缺省 0）；`clearSession()` 归零。
- 增加 `refresh()`（即使 `loaded` 也重取 `/me`，用于注册后补权限）与 `refreshUnreadCount()`（NTF-02，`redirectOnUnauthorized:false`）。
- 访客不发 `/c/me`：登录/注册成功写入本地会话提示（`localStorage` 键如 `yunhefc.c.sessionHint=1`）；退出、401、`clearSession()` 清除。`AppShell` 仅在提示存在或目标路由 `requiresAuth` 时调用 `ensureLoaded()`。已知代价：上线前已存在的旧会话在下次登录前按访客展示。
- 轮询放 `apps/web/src/composables/useNotificationPolling.ts`，由 `AppShell` 挂载；不放进 `packages/shared`（shared 无 Pinia 依赖）。
- 轮询行为：登录后立即取一次；每 60 秒一次；`visibilitychange` 变为可见时刷新；路由切换后刷新；请求中进行中不再发；退出/卸载/401 停止；访客不启动；PENDING 账号允许轮询（NTF-02 在白名单内）。
- 401 静默吞掉并清会话，不产生未处理 rejection 或 console error。
- 角标显示 `unreadCount`，上限展示 `99+`；单条已读/全部已读后更新角标，下一次轮询校正。

### C01 首页

- 列表：`MAT-02?scope=upcoming&sort=startAt:asc&pageSize=3`；Hero 取第 1 条，近期赛程取同 3 条。
- Hero 详情：额外调用 `MAT-03 GET /c/matches/{id}` 获取 `capacity/guestCapacity/waitlistCapacity/signupDeadline/myRegistration`；`MatchSummary` 不含这些字段，接口数不变，落地时在 `06` 追加实现记录。
- CTA 状态机（纯函数，输入身份、账号状态、比赛状态、时间、容量、队列与 `myRegistration`）：
  - 访客 → 「登录后报名」→ `/login?redirect=/`。
  - PENDING → 禁用，文案「账号待审核，通过后可报名」。
  - 已报名（`myRegistration.status=0/1`）→ 「查看详情」并显示状态。
  - `status=0（SIGNUP_OPEN）` 且未报名：
    - `now >= signupDeadline` → 禁用「报名已截止」；
    - 正式球员且正式满、候补未满 → 「加入候补」；
    - 正式球员且正式与候补都满 → 禁用「名额与候补已满」；
    - 外挂且外挂满 → 禁用「外挂名额已满」；
    - 其他 → 「报名」。
  - `status=1` → 禁用「报名已截止」；`status=2` → 「报名已锁定」；`status=3/4` → 「比赛已开始/已结束」；`status=5` → 「比赛已延期」；`status=6` → 「比赛已取消」。
- 报名调用 `REG-01` + `Idempotency-Key`；成功后刷新 Hero 与列表；40902/40903/40905/42901 显示真实原因。
- 无 upcoming 时显示「暂无待进行比赛」+ 去赛程；不回退历史。
- 球队火力只显示标题与「二期上线」，不请求数据。

### C02 赛程

- 请求参数：`type, venue, status, season, month(YYYY-MM), page, pageSize`；固定 `scope=all`、`sort=startAt:desc`，不暴露给用户。
- 月份与年份互斥：选了月份只传 `month`；否则传 `season`。
- 筛选状态与 URL 双向同步；只写非默认值，使用 `router.replace`；刷新、后退、分享可恢复。
- 点「搜索」应用筛选并回到第 1 页；「重置」清空并立即请求；分页 10/20/50/100。
- 空结果显示重置入口；加载/失败/重试齐备。

### C04 球队

- 初始 `PLR-01?limit=10`；某组首次展开时请求 `all=true`，缓存完整列表，之后组内展开/收起不再请求。
- `STA-01` 按 `playerId` 合并；缺失统计降级为 PLR-01 的 `appearances` 与「—」进球/助攻。
- 组顺序：前锋/中场/后卫/门将；组内沿用服务端顺序（本赛季出场降序 → 号码升序）。
- 无手机号、无外挂、无详情页；头像非 http(s) 或加载失败时显示姓名首字占位。

### C05 登录/注册

- 单一路由 `/login`，用 `mode=login|register|reset` 区分；默认登录，保持现有 E2E 选择器。
- 登录沿用现有 AUTH-04；注册 AUTH-02 取码 + AUTH-03 提交；找回密码 AUTH-06。
- 注册字段：手机号、验证码、密码（8–64 位且含字母和数字）、姓名、主位置；`inviteToken` 从 query 透传。
- 注册成功后会话已建立但响应不含 `permissions`：调用 `session.refresh()` 补权限与未读数；显示待审核提示；按 `redirect` 白名单跳转，无 redirect 回 `/`。
- 邀请上下文：进入注册模式时用 `INV-01` 读取并展示类型/球队/比赛/邀请人/有效期；无效邀请提示后仍可按普通自助注册处理。
- 错误文案：40001 字段错误、40907 手机号已存在、40910 邀请不可用、42901 限流重试、40102 登录失败；登录失败不区分「手机号不存在/密码错误」。
- 微信授权入口不在本批（C08/B4）。

### C06 个人中心

- 身份卡：头像、姓名、角色、脱敏手机号、号码、位置、星级；头像为空显示姓名首字。
- 资料编辑：弹窗编辑姓名、地区、主副位置、头像；`PLR-03` 保存。
- `MeView.player` 只有 9 个字段，缺 `region/secondaryPositions`：进入编辑前用 `PLR-01?all=true` 找本人补齐；保存后用 `PLR-03` 响应更新。
- 头像流程：前端校验 JPG/PNG/WebP 且 ≤2MiB；长边压到 1080px、JPEG 质量 0.85、去除 EXIF；`FIL-01` multipart 上传拿到 `{objectKey,signedUrl}` 预览；保存时 `PLR-03` 提交 `avatarUrl=objectKey`。文案按 02 §5.6：「图片不能超过 2MB，请重新选择」「仅支持 JPG/PNG/WebP」「上传失败，请稍后重试」。签名 URL 过期显示占位，不自动刷新。
- 通知：NTF-01 分页「加载更多」，支持未读筛选；NTF-03 单条已读、NTF-04 全部已读；`updated=0` 仍按成功处理。
- 通知跳转按 `refType/refId`：`MATCH` → `/matches/{refId}`；`USER` 或未知/缺 refId 只标记已读；点击后同步角标。
- 我的报名：REG-03 `scope=active|history`，默认进行中；分页 10/20/50/100；状态含已报名/候补/已作废/已取消；进入比赛详情。
- 账号与安全：找回密码入口（AUTH-06）、微信绑定状态只读（`user.wechatBound`）、退出登录；解绑仅管理员，不在本批。
- PENDING：显示横幅；隐藏资料编辑与头像上传；通知可读可标记；不调用 REG-03（不在 PENDING 白名单），「我的报名」显示审核后可用提示，不把 40303 当会话失效。
- 各区块独立加载/失败/空态，单块失败不拖垮整页。

### C07 邀请落地

- 状态判断顺序：token 不存在 → `/404`；过期/撤销/次数用尽 → 失效说明；有效 → 展示内容。
- 入队邀请：球队名称、邀请人、有效期、剩余次数、加入按钮；未登录先登录/注册（`redirect=/invite/{token}`、注册带 `inviteToken`），返回后不自动接受，展示接受按钮；已是成员提示无需重复加入。
- 比赛邀请：比赛摘要（时间、地点、名额）、邀请人；未登录先登录/注册；接受 INV-02 后返回 `matchId` 并跳 `/matches/{matchId}`，不自动报名；显示候补/截止提示。
- 接受入队邀请后调用 `session.refresh()`，页面状态更新为「已是成员」。
- `shared/auth.ts` 的 `Invite.inviter` 与后端 `UserSummary` 不一致、`match` 为 `unknown`：本批在 `apps/web/src/features/invite/types.ts` 定义本地 `InviteLandingView`，不改共享类型。

### C09 错误页

- `/403`：说明所需权限与当前身份，提供返回首页与切换账号（退出后去登录）。
- `/404`：资源不存在，提供返回上一页与首页。
- 通配路由改为 `/404`；`MatchDetailPage` 捕获 40401 后 `router.replace('/404')`。
- 对象级 403 仍在页面内联处理，不跳 `/403`。

### 共享包与文件归属

- B3 **不改** `packages/shared/src/match.ts`、`packages/shared/src/auth.ts`、`packages/permissions/*`（B2/B1 owner）。
- 只允许票 06 在 `packages/shared/src/notification.ts` 追加纯函数（如 `notificationTarget`、类型文案映射）与测试；B2 不碰该文件。
- `packages/api-client/src/client.ts` 与测试由票 01 独占，用于 FormData/multipart；B2 只消费。
- C 端专用逻辑（CTA 状态机、URL 查询、统计合并、头像校验、邀请状态）放 `apps/web/src/features/<feature>/`，与页面同票。
- `scripts/sql/test-data.sql` 的 B3 区块由票 01 独占；其他票只读，不改种子。

### 契约与数据不变式

- 接口路径、方法、DTO、错误码以 05 为准；枚举响应为数字 + Label，请求只接受数字 code。
- 列表统一 `Page<T>`；`pageSize` 白名单 10/20/50/100（MAT-02 允许 1–100）；筛选/搜索回到第 1 页；空结果为 200 + 空 Page。
- 写接口沿用既有幂等键与 CSRF；FIL-01 为 multipart POST，不能手工设置 `Content-Type`。
- 不改 `schema.sql`、Flyway、Quartz DDL；不新增接口、权限码、错误码。
- 通知代码 2–6 为预留，前端只需按 `typeLabel` 展示，不驱动未定义行为。

---

## Testing Decisions

### 好测试的标准

- 只断言外部行为：URL、DOM 可见文本/状态、请求参数、响应结果；不断言组件内部实现。
- 纯逻辑用 Vitest，页面用 Playwright；不引入 MSW，黄金路径打真实后端。
- 每条黄金路径收集 console error 并断言为空。
- 时间相关逻辑（CTA、签到窗口）在纯函数中注入 `now`，不依赖真实时钟。

### 缝 1：Vitest 纯逻辑

- `packages/api-client/src/index.test.ts`：FormData 透传、不设置 JSON `Content-Type`、CSRF 头存在、403 重取后重试、envelope 错误透出。
- `packages/shared/src/index.test.ts`：`notificationTarget` 的 MATCH/USER/缺失 ref 分支。
- `apps/web/src/features/home/cta.test.ts`：访客/PENDING/已报名/未满/候补/满额/外挂满/截止/锁定/进行中/结束/延期/取消全矩阵。
- `apps/web/src/features/schedule/query.test.ts`：URL 与查询参数往返、默认值省略、月份与年份互斥、筛选回第 1 页。
- `apps/web/src/features/team/merge-stats.test.ts`：按 `playerId` 合并、组顺序、缺失统计降级。
- `apps/web/src/features/auth/validation.test.ts`：手机号、验证码、密码强度、邀请 token 透传。
- `apps/web/src/features/profile/avatar.test.ts`：类型、大小、压缩参数与错误文案。
- `apps/web/src/features/invite/invite-state.test.ts`：404/失效/有效入队/有效比赛/已是成员。

### 缝 2：Playwright 黄金路径

按页面拆文件，避免同一文件并行冲突；`playwright.config.ts` 的 `testMatch` 从 `web\.spec\.ts` 调整为 `web.*\.spec\.ts`：

| 文件 | 路径 |
|---|---|
| `e2e/web.spec.ts` | 保留现有 5 条；更新比赛卡数量与 `/account` 断言 |
| `e2e/web-shell.spec.ts` | 访客不发 `/c/me`、`/404`、`/403`、`/account` 重定向、角标 |
| `e2e/web-home.spec.ts` | 访客 CTA → 登录；1002 在 1005 报名 → 查看详情 |
| `e2e/web-schedule.spec.ts` | 筛选 → 搜索 → URL；刷新/后退恢复；重置；空结果 |
| `e2e/web-team.spec.ts` | 分组标题、展开露出种子球员、无手机号/外挂 |
| `e2e/web-auth.spec.ts` | mock 码 `123456` 注册 → 待审核；1021 登录 → 横幅；找回密码表单 |
| `e2e/web-profile.spec.ts` | 编辑保存；单条已读 + 全部已读 → 角标归零；我的报名两组；待审核横幅 |
| `e2e/web-invite.spec.ts` | 入队邀请接受 → PLAYER；比赛邀请 → 比赛详情；不存在 token → 404 |

`e2e/support.ts` 提供 `loginAs(page, phone, password)` 与 console error 收集，由票 01 建立。

### 测试数据与环境

B3 种子由票 01 在 `scripts/sql/test-data.sql` 的 `COMMIT;` 前追加单一 `-- BEGIN B3 C-A fixtures` / `-- END B3 C-A fixtures` 区块：

1. **比赛 1005**：队内赛、`status=0`、开赛 +1h、报名截止 +30min、容量 30/4/2；先清理 1002 的报名与考勤再重置。成为首页 Hero；现有 `web.spec.ts` 比赛卡数量 4 → 5。
2. **待审核账号 1021**：`app_user.status=0`、`player.type=0`、GUEST 角色、密码 `Passw0rd!`；重放时重置状态/角色并清理 `invite_use`。
3. **球员 1030–1035**：补 6 名中场，使单组超过 10；`INSERT IGNORE`。
4. **通知 4001–4012（user 1002）**：类型 0/1/7/8/9/10 混合，读写混合，`ref_type=MATCH/USER`，`created_at` 错开；`ON DUPLICATE KEY UPDATE` 重置 `read_at/is_deleted`。
5. **邀请 5001–5005**：有效入队 `B3-INVITE-TEAM`、有效比赛 `B3-INVITE-MATCH`、过期、已撤销、已用尽；`token_hash=SHA2(token,256)`（Java `Hashing.sha256` 为小写 hex）；重放清理 `invite_use` 并重置计数/状态/过期时间。

环境：后端 dev profile（8080）+ MySQL/Redis；`SMS_MOCK_ENABLED=true`、`OSS_MODE=mock`、`WECHAT_MODE=mock`；mock 短信码 `123456`；Playwright `workers: 1`、`fullyParallel: false`；每次运行前重放 `test-data.sql`。

### 验证命令

```bash
cd frontend
pnpm --filter @yunhefc/api-client test
pnpm --filter @yunhefc/shared test
pnpm -r typecheck
pnpm -r test
pnpm -r build
pnpm lint
# 重置种子后
pnpm test:e2e --project=web
```

B3 不触碰后端，`mvn verify` 不是本批门禁；若票 02 需要补 `06` 实现记录，只改文档。

---

## Out of Scope

- C03 补齐：REG-02 取消、C03 报名/名单/考勤/签到入口、分享、邀请队友、MAT-12 管理员状态下拉、C03 404 分支的深度完善（归 B4）。
- C08 微信授权回调、C05 微信入口、C06 微信解绑（归 B4）。
- B2 B 端页面、B0 `deploy/` 与 CI/CD、收口检查单。
- 后端、`schema.sql`、Flyway、Quartz DDL、接口/权限码/错误码的任何改动。
- 真实 OSS/短信/微信生产验收；本批只用 dev mock。
- 独立通知中心页（PQ-05）、WebSocket/JS-SDK、通知聚合或免打扰。
- M14 二期能力、多队/平台超管、微服务/K8s/staging。
- B01 工作台与视觉改版。

---

## Further Notes

### 实施票

已发布到 `issues/`，按阻塞边执行，每票单独会话、票间 `/clear`：

1. `issues/01-web-shell-tabs-errors.md` — 四 Tab 壳层、错误页与公共基座（无阻塞）
2. `issues/02-c01-home.md` — C01 首页与报名 CTA（依赖 01）
3. `issues/03-c02-schedule-filters.md` — C02 赛程完整筛选（依赖 01）
4. `issues/04-c04-team.md` — C04 球队页（依赖 01）
5. `issues/05-c05-auth-pages.md` — C05 登录/注册/找回密码（依赖 01）
6. `issues/06-c06-profile.md` — C06 个人中心与通知（依赖 01）
7. `issues/07-c07-invite-landing.md` — C07 邀请落地（依赖 01，软依赖 05）
8. `issues/08-review-leftovers.md` — 评审遗留处理（依赖 02）

`02/03/04/06` 可并行；`05` 与 `07` 在基座完成后收口；`08` 在 02 后、03 前落地。

### 已在本规格中消解的歧义

- **C02 归属**：issue 11 说完整筛选归 M12 批次，`batches.md` 把 `03-c02-schedule-filters` 放进 B3；以 `batches.md` 为准，M12 只提供契约，不另开实施批。
- **REG-01 归属**：issue 11 明确首页 CTA 包含直接报名；`batches.md` 说 C-A 开放报名入口。REG-01 归 B3 票 02；REG-02 取消与 C03 报名 UI 归 B4，两批相邻、期间不 cutover。
- **头像上传**：issue 11 的「头像只读」早于 B1 的 FIL-01 交付；按 02 §5.6 与 `batches.md` 票 06，B3 实现上传 UI。
- **访客 `/c/me`**：issue 11 允许静默 401，`batches.md` 门禁要求未登录不触发 `/c/me` 401；采用本地会话提示，二者同时满足。
- **C01 数据源**：02 §5.1 写单次 MAT-02，但 `MatchSummary` 无容量/截止/本人报名；B3 用 MAT-02 列表 + MAT-03 Hero，接口数不变，落地时在 `06` 追加实现记录。
- **种子归属**：`batches.md` 原把通知/PENDING 种子分给 B1 M09，但未交付；改由 B3 票 01 独占。
- **C06 微信状态**：issue 11 说随 C-B；B1 已交付 `user.wechatBound`，B3 只读展示，不解绑。
- **共享邀请类型**：`Invite.inviter: User` 与后端 `UserSummary` 不符；本批本地定义类型，不改共享文件，记为后续修正项。

### 已知限制

- 会话提示是客户端标记，不是服务端会话状态；清理浏览器存储后，已登录用户首次访问公开页会按访客展示，直到进入需登录页。
- 头像签名 URL 15 分钟有效，不自动刷新；过期显示占位。
- C04 首次展开需要额外 `all=true` 请求；服务端无按组计数接口。
- 通知轮询 60 秒，未读角标最多延迟一个周期；单条/全部已读后主动刷新校正。
- C02 的 `upcoming` 语义包含进行中与已延期，首页按设计取第一条，不回退历史。

### 完成标准

- 8 票全部 resolved；每票在 `## Result` 记录提交与验证。
- 02 §5.1–§5.9 页面交付；加载/失败/空态/无权限齐备。
- web 项目 Playwright 全绿且无 console error；访客不产生 `/c/me` 请求或 401。
- `pnpm -r build/typecheck/test` 全绿；无后端/schema/Flyway/DDL 改动。
- B3 种子重复执行后，通知已读、报名、邀请使用状态可复位。

### 交付结果

已完成（2026-11-12）；8 票全部 resolved：

| 票 | 文件 | 提交 | 状态 |
|---|---|---|---|
| 01 | 01-web-shell-tabs-errors.md | `9929a23` | resolved |
| 02 | 02-c01-home.md | `0c3969b` | resolved |
| 03 | 03-c02-schedule-filters.md | `03c770e` | resolved |
| 04 | 04-c04-team.md | `a0caa14` | resolved |
| 05 | 05-c05-auth-pages.md | `afaa22c` | resolved |
| 06 | 06-c06-profile.md | `de7eaf4` | resolved |
| 07 | 07-c07-invite-landing.md | `fc8b38f` | resolved |
| 08 | 08-review-leftovers.md | `050898d` | resolved |
