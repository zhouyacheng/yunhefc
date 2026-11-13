# 云河FC 前端

pnpm workspace：C 端 `apps/web`（Vue 3 + Vant 4）、B 端 `apps/admin`（Vue 3 + Element Plus 2）、共享包 `packages/{api-client,permissions,shared}`。

## 环境

- Node 24（`frontend/.nvmrc`），pnpm 12（`packageManager` 已固定）。
- 只用 pnpm；安装：`pnpm install`。

## 本地开发

```bash
cd frontend
pnpm dev:web      # http://localhost:5273
pnpm dev:admin    # http://localhost:5274/admin/
```

Vite dev 把 `/api` 代理到 `http://localhost:8080`，并去掉转发请求的 `Origin`，因此不需要额外 CORS 配置。admin 的 `BASE_URL` 默认 `/admin/`，部署在 admin 子域根路径时用 `ADMIN_BASE_URL=/` 构建；B 端登录页的“前往 C 端”链接用 `VITE_WEB_URL` 配置，dev 默认 `http://localhost:5273`。

## 后端 dev profile 与测试数据

后端需先启动 MySQL/Redis。dev profile 打开短信/微信 mock、关闭 Kafka 消费与 Outbox：

```bash
cd backend
DB_URL='jdbc:mysql://localhost:3306/yunhefc?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8' \
REDIS_PORT=6379 \
AUTH_LOGIN_RATE_LIMIT_MAX_ATTEMPTS=1000 \
SPRING_PROFILES_ACTIVE=dev \
./mvnw spring-boot:run
```

`AUTH_LOGIN_RATE_LIMIT_MAX_ATTEMPTS=1000` 是跑 E2E 所需的：套件按同一账号多次登录，默认 10 次/分钟会触发 429。

测试数据（含两场比赛，账号 1001/1002 的密码为 `Passw0rd!`）。在仓库根目录执行：

```bash
mysql -uyunhefc -pyunhefc yunhefc < scripts/sql/test-data.sql
# 或容器内：docker exec -i <mysql容器> mysql -uyunhefc -pyunhefc yunhefc < scripts/sql/test-data.sql
```

若本地容器映射到非默认端口（如 MySQL 3307、Redis 6380），上面的 `DB_URL`/`REDIS_PORT` 按实际端口替换。

> `test-data.sql` 仅用于测试库；重跑会覆盖 1001/1002 的密码哈希。

## 验证

```bash
pnpm install
pnpm -r build
pnpm -r typecheck
pnpm -r test
pnpm lint
pnpm format:check
```

Playwright 冒烟打真实本地后端（后端 + 种子数据需已就绪）：

```bash
pnpm exec playwright install chromium
pnpm test:e2e
```

覆盖：C 登录 → 赛程真实分页；未登录访问受保护页跳登录并带 `redirect`；B 管理员登录 → 比赛列表；无权限分区侧栏过滤与直访 403。两条登录冒烟额外断言无 console error；redirect/403 用例只断言页面结果（会话探测会产生预期的 401 网络日志）。

## 约定

- 会话与权限由后端 `GET /c/me`、`GET /b/me` 返回，前端只用于导航与渲染；权限码见 `packages/permissions`（05 §5.1）。
- `api-client` 统一处理 envelope、CSRF（内存 token，缺失时取一次并重试一次）、401 跳登录、403 分区/对象级区分、`Page<T>`、`readLabel`、`Idempotency-Key` 与 40901 归一化。
- 路由使用 `createWebHistory(import.meta.env.BASE_URL)`；Pinia 存会话；守卫只做导航与 `redirect`。
