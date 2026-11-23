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

## 排行榜

`stats` 读模型提供的 7 项球员榜单：出场、进球、助攻、场均进球、场均助攻、胜率、乌龙；只收录正式且 ACTIVE 球员，公开可见，复用 1 分钟汇总。

## 生涯累计

当前赛季 + 历史累计，即不晚于当前 Asia/Shanghai 自然年的赛季汇总；排行榜 `scope=all` 使用该窗口。各计数按赛季求和，场均 = 总进球（助攻）/总出场，胜率 = 总胜场/总队内赛出场。

## 历史累计（固定列）

排行榜单赛季窗口（`scope=season`）的固定列：只汇总早于当前 Asia/Shanghai 自然年的赛季，不含当前赛季与未来赛季；不随所选赛季或榜单切换变化；`scope=all` 时隐藏并返回 `history=null`。

## 累计固定列

排行榜单赛季窗口同时展示「历史累计（不含当前赛季）」与「生涯累计（含当前赛季）」两组固定数据；三组都只展示当前榜单主指标（如出场榜只显示出场数）；生涯累计 = 当前赛季 + 历史累计；`scope=all` 时两组均隐藏并返回 `career=null`、`history=null`。

## 队内赛胜率

只统计队内赛（`team_match.type=1`）、按已保存分组判定一方的个人胜率：分母为该球员在已分组、计入出场、已结束未取消且比分已知的场次，平局计分母不算胜；训练赛、对外比赛与未分组场次不计。

## 标准竞赛排名

同值同名次、下一名跳号的排名方式（1、1、3）；并列用原始分数判定，同值内按次级指标 → 号码升序排列。

## 派生状态装配

目标态（批次 2 票 05）：小程序 `utils/match-detail-state.ts` 把比赛详情、本人报名、会话与当前时间派生为页面 patch 与报名/取消决策，页面只应用结果、不重算规则。当前派生逻辑仍在 `pages/match-detail/index.ts` 的 `syncWindowState()`。

## 队内活动

`activity` 域组织的聚餐/年会等非比赛活动：独立实体，不复用比赛；状态为报名中 → 报名截止 → 已结束（报名中可直接结束），另有已取消；已结束与已取消均为终态、不可恢复，重新组织走「复制活动」新建（复用创建接口预填，不复制报名）；满员后拒绝报名而不设候补；家属与家属备注由报名人填写，人数上限只计报名人数、不含家属；报名人数是派生值；详情公开可读但不含报名名单。

## 最近活动

队内活动列表的 `scope=latest`：按 `start_at` 降序取第一条，不论状态，含未来与已取消，忽略分页与排序；「历史」为 `start_at` 早于当前时间，「全部」不限。

## 账本条目

`ledger` 域的一笔球队收支：金额存正数、方向表达收支；分类是独立实体（内置 7 个，管理员可自定义与停用，只停用不删除），流水方向须与分类方向一致；可挂球员与活动但不自动摊分；余额 = 收入 − 支出，不设期初余额。
