# MVP 可上线 — 实施路线图

Status: resolved（2026-11-12；01–21 全部关闭，无 open/claimed 票）

## Destination

产出可执行的「MVP 可上线」实施批次与上线检查单：M09–M13、B 端占位页、deploy/CI/CD 的决策全部关闭，112 条 MVP 验收、Playwright 黄金路径、空库 Flyway 幂等与部署冒烟口径就绪。本 map 只出决策与票，不写业务代码。

## Notes

- 事实源：需求 v1.15；`docs/design/01`–`06`；`docs/rules/*`；`CLAUDE.md`；既有批次 `.scratch/m05-m08-build/` 与 `.scratch/m05-m08-roadmap/map.md`。
- 上线形态（2026-11-15 同域收敛修订，P-38）：真实生产；单 ECS + Docker Compose + Nginx；仅 `www.` 主站，C 端 `/`、B 端 `/admin/`、API `/api/v1/**`；取消 `admin.`/`api.` 子域；真实 OSS/ACR/微信网页授权/短信/ICP 备案均已就绪；微信授权与备案为首批硬前提；CI/CD 纳入范围。
- 生产拓扑（2026-11-11 票 12 确认，2026-11-15 按 P-38 修订）：ECS 2C2G；MySQL 用阿里云 RDS MySQL 8.4；Redis/Kafka 自建 Compose；C/B 均经 www 同源反代 `/api`；首位管理员一次性 SQL runbook。
- 验收硬门槛：112 条 MVP 可演示、Playwright 黄金路径、空库 Flyway 幂等、部署后冒烟；安全只做基本检查；不做压测、不做备份恢复演练。
- 已知差距（待各 research 票核实）：notification 仅 7 类事件 handler、无 NTF-01~04；audit 有 32 处记录点但快照未脱敏、无 AUD-01；settings 有读适配器与 V3 种子、无 SET-01/02；OSS 零代码；`SmsCodeService` 关闭 mock 即拒绝；CI 仅 prototype `node --check`；`deploy/` 不存在。
- 设计冻结：任何 02/04/05 契约变更先改文档并记入 06，再进实施批次。
- 跟踪约定见 `docs/agents/issue-tracker.md`；每票顶部写 `Type:`、`Status:`、`Blocked by:`。

## Decisions so far

- [交付缺口盘点：92 接口与 C/B 页面对账](issues/01-research-delivery-gap.md) — 后端 83/92，缺 NTF-01~04、AUD-01、SET-01/02、FIL-01/02；B 端 6 个占位页，C 端缺 5 页。
- [通知触发矩阵与现有 handler 差距](issues/02-research-notification-matrix.md) — 7 类事件已实现；2–6 类型无事件/无 handler；NTF-01~04、分享、角标、90 天清理缺失；通知口径存在文档冲突。
- [审计留痕覆盖与脱敏现状](issues/03-research-audit-coverage.md) — 32 调用点/36 动作覆盖 §11.1 清单；AUD-01、年度导出、统一脱敏缺失；微信绑定/配置/自助报名签到未留痕。
- [部署基线与 CI/配置面盘点](issues/04-research-deploy-baseline.md) — `deploy/` 与 CI 基本为空；无 prod profile/forward-headers/日志；SMS mock、OSS 零代码、V3 无管理员。

- [112 条 MVP 验收矩阵与测试覆盖对账](issues/05-task-acceptance-matrix.md) — 112 条：自动化 100（Playwright 11 / Vitest 1 / JUnit 88）、人工 3、无覆盖 9；缺口集中在球队页、头像 OSS、分享与通知已读。
- [M11 配置项、生产种子与设置页](issues/06-grilling-settings-and-seed.md) — 9 项全可配、扁平 Settings 部分合并、只影响新建比赛/后续分组、单条 SETTINGS_UPDATE 审计、Flyway V3 唯一种子。
- [通知矩阵定稿](issues/07-grilling-notification.md) — 仅 7 类触发、2–6 预留；NTF-02 60s 轮询、分享复制+navigator.share、90 天物理删除、PLR-08 复用 ROLE_CHANGED。
- [审计覆盖、脱敏与日志页边界](issues/08-grilling-audit.md) — 只补微信绑定与邀请过期；原文落库、AUD-01 读时脱敏；AUD-01 + 完整 B10；年度 JSONL.gz 导出 OSS。
- [M12 列表契约与 B 端列表复用](issues/09-grilling-list-contract.md) — 冻结现有分页/筛选/排序契约；非法参数 40001；C02 搜索生效且分页进 URL；B 端不抽共享组件，B07 表头排序、B10 搜索生效、B11 仅分页。
- [B 端占位页最小交付范围](issues/10-grilling-b-end-placeholders.md) — B07/B08/B11 全进首批且按 02 全量；B01 工作台下线、`/admin` 落首个有权限分区；头像上传延后到 OSS 批次；AUTH-13 补 `keyword`；B11 复制仅在创建成功弹窗。
- [C 端公开页面与访客体验](issues/11-grilling-c-end-pages.md) — 首批 C01/C04/C05 注册找回/C06 `/profile`/C07/C09；C03 报名并入概览，分享、管理员状态、C08 微信与头像后置；访客公开读、写操作登录引导。
- [部署拓扑、发布与回滚](issues/12-grilling-deploy-topology.md) — ECS 2C2G + RDS MySQL 8.4；Redis/Kafka 自建 Compose 并严格限额；www/admin 同源反代 `/api`（P-38 后改为仅 www，B 端挂 `/admin/`）；sha7 不可变 tag、手动批准发布、保留 3 版；首位管理员一次性 SQL runbook；Actuator readiness + ECS JSON 日志 + 云监控告警。
- [CI/CD 流水线与 Secrets](issues/13-grilling-ci-cd.md) — PR 快门禁 + main 全量；E2E 用 service 容器；前后端双镜像 `sha-<7>`（前端进 nginx 镜像）；手动发布与手动回滚；GH 只存部署凭证；Deploy 推 webhook；一次性全量。
- [真实短信与微信接入](issues/14-grilling-sms-wechat.md) — 云市场 HTTP+APPCODE、单模板 5 次/分、成功才落码 + prod fail-fast；微信 snsapi_base 静默授权、失败站内提示、仅管理员解绑。
- [OSS 文件存储与 FIL-01/02](issues/15-grilling-oss-files.md) — dev/prod 独立私有 Bucket；键 `avatar/{userId}/{ulid}.{ext}`；库存对象键、读接口现签 15 分钟 URL；FIL 只上传、绑定走 PLR-03/06；JPEG/PNG/WebP ≤2MiB。
- [安全基线与上线前检查](issues/16-grilling-security-baseline.md) — 人工安全门禁、TLS/HSTS 基线、应用层限流、手工迁移账号、prod fail-fast 与残余风险。
- [备份与数据保留](issues/17-grilling-backup-retention.md) — 仅 RDS 7 天 PITR、无额外副本/快照；Redis 不独立备份；OSS 无生命周期；通知 90/邀请 30/Outbox SENT+FAILED 7/消费去重 7 天物理删除；单一 DataRetentionJob 03:30，保留期走 `yunhefc.retention.*`；备份新鲜度仅发布前人工检查。
- [实施批次切分与顺序](issues/18-grilling-batch-slicing.md) — 5 个实施批 + 收口：B0 基座 → B1 后端与契约 → (B2 B 端 ∥ B3 C-A) → B4 C-B + 微信；单次正式上线，B1 起可 RC 部署；外部任务并行、代码侧 mock + prod fail-fast。规格 [batches.md](batches.md)。
- [B02 比赛管理交付深度](issues/20-grilling-b02-depth.md) — 首批全量 MAT-04/05/06/07/08/11；B02 独占状态入口、MAT-12 留 C-B；校验抽 shared 纯函数；验收一条 Playwright 黄金路径。
- [C06 微信绑定状态与 AUTH-10 契约](issues/21-grilling-wechat-binding.md) — User/PlayerAdmin 加 wechatBound（openid 派生）；C06 状态随 C-B、B07 列随首批；无新接口。
- [验收与上线检查单](issues/19-grilling-release-acceptance.md) — 112 条全绿 + 页面级证据；生产 RC 验收；工作日 20:00 cutover + 30m/24h/72h；24h 冻结与前 24h 签收；先回代码后 PITR；检查单独立文件 `release-checklist.md`。

## Not yet specified

<!-- 暂无；新问题先建票，无法成票的再记入本节 -->

## Out of scope

- M14 二期能力：排行榜/身价、AI 天气、账本、动态、相册视频、投票、公众号模板消息（NOTIFY-04）、资产、荣誉、主页统计、数据提醒、Excel 导入、9/11 人制。
- 多队/平台超管/跨队隔离、微服务、K8s、staging 环境。
- 压测与备份恢复演练（用户明确排除，不作为上线门槛）。
- 邮件通知（需求无此渠道）。
- 原型与视觉改版、设计文档重构。
- 业务代码实施（本 map 只出决策与票；关闭后另起批次）。
- B01 工作台：需求 MVP 与 112 条验收均无此项，且依赖 AUD-01；本期下线，02 §6.3 标为二期（见 [B 端占位页最小交付范围](issues/10-grilling-b-end-placeholders.md)）。
