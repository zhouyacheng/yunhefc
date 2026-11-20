# 01 — 前端工程骨架与首个纵向切片

**What to build:** C/B 两端从零可启动、可登录、各自看到一屏真实只读数据；共享 api-client、权限与类型包就位；Vitest + Playwright 测试基线可跑。本票不做任何业务页面（B03/C03 等）。

**Blocked by:** None — can start immediately.

**Status:** resolved

- [ ] pnpm workspace：`apps/web`（Vant 4）、`apps/admin`（Element Plus 2）、`packages/{api-client,permissions,shared}`；Node 24（`.nvmrc` + `engines`）；只用 pnpm，版本写入 `packageManager`；ESLint Vue flat config + Prettier。
- [ ] C 端：C05 登录（手机号 + 密码）、C 壳层（顶部导航 + 移动底栏）、C02 赛程（`MATCH-09 GET /c/matches`，真实分页）。
- [ ] B 端：B00 登录（`AUTH-15/17`）、B 壳层（侧栏按 `sections` 过滤；直访无权限分区渲染 403）、B02 比赛列表只读（`GET /b/matches`）。
- [ ] api-client：原生 fetch + `credentials: include`；C/B base 路径；统一 envelope 解析；`ApiError` 携带 `status/code/message/requestId`；CSRF token 只存内存，缺失时取一次并重试一次；401 清会话跳登录并带站内白名单 `redirect`；403 分区准入与对象级分开处理；`Page<T>` 与枚举 `field/fieldLabel` 辅助；`Idempotency-Key` 与 `40901` 归一化预留。
- [ ] permissions：手写 05 的 28 个权限常量、`ADMIN_SECTION_PERMISSIONS`、`hasPermission`；后端 `permissions/sections` 仍是唯一权威。
- [ ] shared：手写 M01–M04 DTO 类型；暂不接 `openapi-typescript`。
- [ ] 路由 `createWebHistory(import.meta.env.BASE_URL)`；admin `BASE_URL` 默认 `/admin/`；Pinia 存会话与权限；Vue Router 守卫只做导航与 redirect；Vite dev `/api` 代理到本地后端。
- [ ] Vitest：api-client（envelope、401/403、CSRF 重取、requestId）与 permissions 判断。
- [ ] Playwright 打真实本地后端（dev profile + `scripts/sql/test-data.sql`），每端一条冒烟：C 登录 → 赛程；B 登录 → 比赛列表；无 console error；不引入 MSW。
- [ ] `pnpm install`、`pnpm -r build`、`pnpm -r typecheck`、`pnpm -r test` 全绿。
- [ ] 明确不做：B03/C03 模块页、注册/微信/邀请落地、`deploy/`、前端 CI job。

## Result

- 交付：`72d7173`、`39934c7`、`8423e97`。
- 验证：批次收尾 `mvn verify` 全绿（107 单元 + 250 集成）；前端 lint/typecheck 通过；Playwright 14 条黄金路径通过（2026-11-11）。
