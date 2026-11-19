# ADR 0001：CI e2e job 使用自建公开基础镜像

状态：已接受（2026-11-19）

## 背景

`e2e` job 每次 main 运行 9–11 分钟。实测 2026-11-18 的三次运行（如 run 44305213693）：

| 阶段 | 耗时 |
|---|---|
| `apt-get` 安装 ~90 个 Playwright 系统依赖 | 233s |
| 下载 Chromium（126MB） | 6s |

瓶颈是系统依赖的 apt 安装，不是浏览器下载。这个结论决定了后面的所有取舍。

## 决策

1. 维护一个预装运行环境的镜像，发布到 **Docker Hub 公开仓库** `danche1013/ubuntu`。
2. tag 采用**不可变 + 自描述**格式：`24.04_pw<playwright 版本>_node<node 版本>`。
3. 镜像内固化：Node 24、pnpm、Playwright 1.65.0 的 Chromium 1254 与系统依赖、**JDK 21**、
   **mysql client**。
4. `e2e` job 使用 `container:` 运行；services 不再映射端口，按服务名 `mysql` / `redis` 直连；
   seed 改用镜像内 `mysql` 客户端（容器 job 没有 docker socket）。

## 放弃的备选

| 备选 | 放弃原因 |
|---|---|
| `actions/cache` 缓存 `ms-playwright` | 实测浏览器下载只占 6s，缓存最多省 6s |
| 私有 ACR 镜像（`registry.cn-shanghai.aliyuncs.com`） | GitHub runner 跨洋拉取速度不可控，且需私有仓库凭证 |
| 保留 `setup-java`、运行期 apt 安装 mysql client | 每次多 40–80s，与"省时间"目标冲突 |
| 可变 tag（始终 `24.04_v1`） | 同日两次运行可能用到不同镜像，出问题无法回溯 |
| CI 自动构建镜像（tag 用 commit sha） | 增加 CI 复杂度与 Docker Hub 凭证管理，收益不抵成本 |

## 后果

- **正面（已实测）**：2026-11-19 手动触发 run 44485023408，e2e job 总时长 **237s**，
  基线为 511–668s，节省约 4.5–7 分钟；其中 `Initialize containers`（镜像拉取 + service 启动）41s。
  改动前后 `Run Playwright E2E` 均为 172s，说明节省全部来自被移除的 apt 安装阶段。
- **负面**：镜像需人工维护。`frontend/pnpm-lock.yaml` 升级 Playwright 或 Node 后，必须重建镜像、
  换新 tag 并同步 `.github/workflows/ci.yml`。
- **漂移的表现是"变慢"而不是"失败"**：镜像版本与 lockfile 不一致时，Playwright 会重新下载
  对应 revision，E2E 仍能通过。tag 的自描述命名是唯一提示。
- **公开镜像**：镜像只含公开工具链与浏览器，不含业务代码或凭证。
- **Docker Hub 匿名拉取有限流**：GitHub 托管 runner 共享出口 IP，若出现 429，需要改为用
  token 登录后拉取。
