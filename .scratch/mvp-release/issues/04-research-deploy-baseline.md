# 04 — 部署基线与 CI/配置面盘点

Type: research
Status: resolved
Blocked by: —

## Question

对照 `03.整体架构设计.md` §5 / §6.4 的部署约束，盘点仓库当前的部署与配置面：

- `deploy/` 缺失；`.github/workflows/ci.yml` 仅做原型 `node --check`；后端/前端构建、测试、镜像、发布均无。
- `application.yml` 的 profile、环境变量、Actuator/健康检查、Flyway/Quartz/Kafka 配置、CORS、Cookie/Secure、forward-headers。
- OSS、微信、短信的配置面；`SmsCodeService` 在 mock 关闭时的行为；`V3` 种子是否包含管理员用户。
- 三子域、Nginx、TLS、真实 IP 传递（`X-Forwarded-For` / `server.forward-headers-strategy`）的现状。
- 记录 12/13/14/15/16/17 票需要决策的配置项与风险。

输出「已定义 / 仓库已有 / 缺失 / 风险」清单，带文件指针；不写部署方案。事实指针：03 §5、§6.4、`backend/src/main/resources/application.yml`、`db/migration/V1–V5`、`.github/workflows/ci.yml`、`auth/application/SmsCodeService.java`、`infra/`。

## Answer

- `deploy/` 零实现；CI 仅 prototype 语法检查，无后端/前端/镜像/E2E/SSH 发布。
- 无 prod profile、forward-headers、logback；Cookie Secure 默认 false、CORS 默认 localhost。
- 短信关闭 mock 即拒绝；OSS 零依赖/零配置；V3 无管理员用户。
- 三子域与前端相对 `/api` 基址冲突、首位管理员引导、真实 IP、Actuator 暴露面待 12/16 票定。

详见 [research/04-deploy-baseline.md](../research/04-deploy-baseline.md)
