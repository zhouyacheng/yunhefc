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

## 跨页 invalidation module

小程序 `utils/invalidation.ts`：写入方用 `invalidate('my-registrations')` 声明命名资源已失效，订阅方用 `subscribe(...).consume()` 在刷新时机消费一次即复位；资源名是接口的一部分，不再暴露隐藏布尔。

## 写操作 outcome

小程序写操作只接收领域输入，返回 `WriteOutcome`（patch/toast/error/reload/refreshEventId）；setData、忙闲标记、确认弹窗与弹层开关由页面负责。报名/取消当前仍由首页、我的、比赛详情三页各自调用；批次 2 票 04 将收拢到 `utils/registration-writes.ts` 供三页共用。签到、状态修改与事件录入仍在 `pages/match-detail/actions.ts`，复用同一类型。

## 派生状态装配

目标态（批次 2 票 05）：小程序 `utils/match-detail-state.ts` 把比赛详情、本人报名、会话与当前时间派生为页面 patch 与报名/取消决策，页面只应用结果、不重算规则。当前派生逻辑仍在 `pages/match-detail/index.ts` 的 `syncWindowState()`。
