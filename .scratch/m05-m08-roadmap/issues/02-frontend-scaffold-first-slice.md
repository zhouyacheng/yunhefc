# 02 — 前端工程脚手架与首个纵向切片

Type: grilling
Status: resolved

## Question

`frontend/` 何时创建、如何落地？需要明确：

- pnpm workspace 结构：`apps/web`（Vant）、`apps/admin`（Element Plus）、`packages/` 共享 API/权限/类型如何划分。
- 共享 API 客户端与鉴权：请求封装、envelope 解析、CSRF、C/B 会话 cookie、权限码类型从哪里生成。
- 首个纵向切片：先做登录壳层，还是直接做 M05 B03 报名/考勤页；切片验收到哪一步（可登录、可读一屏真实数据、可跑 Playwright 冒烟）。
- 前端测试基线：Vitest + Playwright 的最小配置何时就位。

事实指针：`docs/design/03.整体架构设计.md` 前端分包与测试约定、`docs/design/02.页面结构与交互设计.md` §6.5、`frontend/`（尚未创建）。

## Answer

**首个切片走通骨架，不先做业务页面。** `frontend/` 第一次落地要证明的是鉴权、权限、路由、请求封装四条链路；B03 等模块页在此之上接入。

### 1. 工作区与工程基线

- `frontend/` 为 pnpm workspace：根 `package.json` + `pnpm-workspace.yaml`；`apps/web`（Vant 4）、`apps/admin`（Element Plus 2）、`packages/{api-client,permissions,shared}`，按 03 §6.3，不新增包。
- Node 钉 **24**（`.nvmrc` + `engines`），与 CI 和 03 一致；本机 22.23.4 需先 `nvm install 24`。包管理器只用 pnpm，版本写入 `packageManager`。
- 路由用 `createWebHistory(import.meta.env.BASE_URL)`；admin 的 `BASE_URL` 默认 `/admin/`，同一份代码在 `admin` 子域根路径或主域 `/admin/` 下都能跑，不改 02/03 的部署描述。
- Pinia 存会话与权限；Vue Router 守卫只做导航与 `redirect`。ESLint（Vue 官方 flat config）+ Prettier。Vite dev 用 `/api` 代理到本地后端，避免 CORS 分叉。
- 样式以 `prototype/assets/css/style.css` 为视觉源，映射到 Vant / Element Plus 主题变量，不另起视觉语言。

### 2. api-client 与鉴权

- 原生 `fetch` 封装，`createClient({ namespace: 'c' | 'b' })`；`credentials: 'include'`；C/B 各自 base 路径 `/api/v1/c`、`/api/v1/b`。不引 axios。
- 统一解析 envelope `{code, message, data, requestId}`；`code != 0` 抛 `ApiError`，携带 `status / code / message / requestId`。
- CSRF：token 只存内存。写请求缺 token 时先 `GET /{ns}/auth/csrf` 取一次并重试一次；登录/注册/微信回调后用响应体里的 `csrfToken` 替换；登出与 401 后清空。
- 401：清会话，跳对应登录页并带 `redirect`（站内白名单）；403：分区准入失败由路由守卫渲染 403，对象级 403 交页面行内处理；两者都不静默吞。
- 分页 `Page<T>`（`items/total/page/pageSize/totalPages`）与枚举 `field` + `fieldLabel` 的读取辅助放 `shared`/`api-client`。
- `Idempotency-Key` 生成器与 `version` 冲突（40901）归一化放进 `api-client`，首个切片只预留、不实现业务写。

### 3. 权限与类型来源

- `packages/permissions` 手写 05 §权限码的 28 个常量、`ADMIN_SECTION_PERMISSIONS` 映射与 `hasPermission()`；后端 `AUTH-10` / `AUTH-17` 返回的 `permissions`、`sections` 仍是唯一权威。
- M01–M04 的 DTO 类型手写进 `packages/shared`；`openapi-typescript` 等 springdoc 产物稳定后再接，不列入首个切片（当前 `pom.xml` 尚无 springdoc）。
- 前端守卫只决定导航与渲染；权限不足的数据访问由后端 403 兜底。

### 4. 首个切片范围与验收

- C 端：C05 登录（手机号 + 密码）、C 端壳层（顶部导航 + 移动底栏）、C02 赛程（`MATCH-09 GET /c/matches`，真实分页）。
- B 端：B00 登录（`AUTH-15/17`）、后台壳（侧栏按 `sections` 过滤，直访无权限渲染 403）、B02 比赛列表只读（`GET /b/matches`）。
- 明确不做：B03 / C03 模块页、注册 / 微信 / 邀请落地、`deploy/`、前端 CI job。
- 验收：`pnpm install`、`pnpm -r build`、`pnpm -r typecheck`、`pnpm -r test` 全绿；C/B 登录后首屏显示真实数据；未登录访问受保护路由跳登录并带 `redirect`；无权限分区不渲染、直访 403；每端一条 Playwright 冒烟无 console error。

### 5. 测试基线

- Vitest：`api-client`（envelope、401/403、CSRF 重取、`requestId`）与 `permissions` 判断；组件测试随页面增量补齐。
- Playwright：打**真实本地后端**（dev profile + `scripts/sql/test-data.sql`），每端一条黄金路径——C 登录 → 赛程；B 登录 → 比赛列表。不引入 MSW。本地需先起 MySQL/Redis/Kafka；CI 前端 job 单列任务，不是本票或任一模块完成的前置。

### 6. 交接

- 本票与 03/04 全部关闭后，按票 01：一次 to-spec 收敛，再按模块出实施票（M05、M06、stats、M08、M07）。
- 首个切片作为独立的实施票（frontend-bootstrap，第 0 号）先行，阻塞所有模块页面票；后端模块票可与它并行。
- B03 / C03 页面在 `REG-06`（M04 余票 05）落地后接入。
