# 01 MP03/MP04 对齐 C02/C03

Status: ready-for-human
Blocked by: none

## 目标

小程序比赛列表与比赛详情向 C 端 C02/C03 对齐：列表具备完整筛选与富卡片，详情具备 C03 头部与事件时间线；仍不提供任何报名类写操作。

## 验收

- [x] MP03 首次加载为 `scope=all&sort=startAt:desc`，不带 month；默认全部比赛。
- [x] 类型/地点/状态/月份/自定义时间筛选可用；300ms 合并生效，地点回车立即生效；无搜索按钮；重置恢复默认。
- [x] 月份预填当前上海月但首屏不参与；任一项变更后参与；月份与自定义范围互斥；清空月份=不限时间；清空范围不恢复月份。
- [x] 筛选使用底部抽屉；变更与重置保持打开，点遮罩关闭；月份与范围选择层位于抽屉之上。
- [x] 卡片与 C02 一致：日期、结果标签、场次、主客队/比分/胜方、类型/状态 pill、三栏报名人数。
- [x] 下拉刷新、上拉加载、总数、加载/错误重试/空态重置/已显示全部；进入详情返回不丢分页与滚动位置。
- [x] MP04 头部与 C03 一致：类型/状态 pill、结果标签、主客队/事件推导比分优先/胜方、取消与延期横幅、六项元信息。
- [x] 事件时间线为单列，中文类型/半场、球员/助攻/点球、换人上下场、球队名；空态「还没有事件记录」。
- [x] 非法 id / 404 显示「比赛不存在」并可返回；其他错误可重试；下拉刷新可用。
- [x] 文档 02/06/需求/CLAUDE.md/README 同步 P-77。

## 验证记录

- `pnpm --filter @yunhefc/miniprogram typecheck` 通过。
- `pnpm --filter @yunhefc/miniprogram test` 通过（12 文件 111 项）。
- `pnpm lint` 通过。
- `pnpm -r typecheck` / `pnpm -r test` 通过。
- 微信开发者工具与 iOS/Android 真机人工验收通过（2026-11-20）：筛选交互、抽屉/日期选择层、返回保页与详情事件展示。

## Comments

- 2026-11-20 实施 P-77：MP03 改为 C02 筛选与富卡片，MP06 共用 `templates/match-card.wxml` 与全局卡片样式；MP04 对齐 C03 头部与事件时间线，修正 `MatchEvent` 字段与中文标签。
- 2026-11-20 自定义时间性能修复：`van-calendar` 会一次性渲染 2000–2100 共 1212 个月导致打开卡顿，改为抽屉之上的轻量浮层 + 两个原生日期选择器（开始/结束）确认后生效；移除日历辅助函数与依赖。
- 2026-11-20 两轴审查整改：还原测试文件的无关 Prettier 重排；详情头部复用 `coreOf`/`MatchCoreView` 并改名 `buildMatchCard`；移除列表页平行的类型/状态标签状态；新增 `isCancelledStatus`/`isPostponedStatus`/`isSubstitutionType` 谓词；裁剪未使用的接口类型字段；02 登录入口文案同步；报名锁定缺失时显示 `—`；空态重置与页脚重试分别修正为按空列表重载、按最近失败动作重试。
- 2026-11-20 复审整改：还原 `presentation.ts` 无关格式改动；`MatchCoreView`/`buildMatchCore`/`buildMatchCard` 移到 `utils/match-view.ts` 并改名；事件文案表收回模块内；`.state`/`.page-head` 抽到 `styles/layout.wxss`；`patchForm` 使用类型化 extras；`onUnload` 抑制卸载中的 `setData`；月份清空后提供「本月」恢复入口；移除地点输入 `adjust-position=false`；README 补 `match-view` 与 `layout`；06 §101 注明 MP04 只读不渲染 C03 统计。
- 2026-11-20 人工验收通过：比赛筛选/富卡片/返回保页、详情头部/事件时间线、404。
