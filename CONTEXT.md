# 术语表

## CI 基础镜像

供给 e2e job 的预装运行环境镜像：Node、pnpm、Playwright Chromium、JDK、mysql client 均已固化。
版本由不可变 tag 编码（`24.04_pw<playwright>_node<node>`），与前端 lockfile 的对应关系见
[ADR 0001](docs/adr/0001-ci-base-image.md)。

## e2e job

仅在 main 运行的真实后端冒烟链路：真实 JDK 后端 + MySQL + Redis + Playwright 浏览器用例。
与 PR 三项必需检查不同，它可以失败重跑而不阻塞合并。

## 分页 Feed module

小程序 `utils/paged-feed.ts`：统一 reset/append、key 切换、generation 丢弃与 load-more 守卫的列表 module；页面只消费 state 快照。

## guarded-load module

小程序 `utils/guarded-load.ts`：按 key 维护代次的加载 module，负责迟到响应丢弃、loading/error 复位与失败空态回退；页面只提供 fetcher、成功映射与回退字段。

## 写操作 outcome

小程序 `pages/match-detail/actions.ts`：比赛详情写操作只接收领域输入（比赛 id、版本、报名状态、事件 payload），返回 `WriteOutcome`（patch/toast/error/reload/refreshEventId）；setData、忙闲标记、确认弹窗与弹层开关由页面负责。
