# 05 验收与证据

Status: ready-for-human
Blocked by: 03, 04

## 目标

用可复核证据证明 C10/MP09 与 STA-03 满足需求 §6.14 与 §12.6。

## 用例

1. 未登录打开 C10/MP09；7 榜切换；默认出场榜；赛季与生涯累计。
2. 出场含临时到场、不含缺勤与取消比赛；乌龙单列且不抵进球；场均按出场数。
3. 胜率：仅队内赛；未分组/训练赛/对外比赛/比分未知不进分母；平局计分母。
4. 外挂与注销不出现；零分母不进场均榜与胜率榜。
5. 名次 1、1、3；同值按次级指标 → 号码升序。
6. 汇总后 ≤1 分钟反映；接口失败可重试且不影响他页。
7. Playwright 无 console error；小程序真机通过。

## 证据

- 后端：`mvn verify` 输出；空库 Flyway 迁移结果。
- Web：`frontend/e2e/web-leaderboard.spec.ts` 与 `web-team.spec.ts` 通过记录。
- 小程序：开发者工具截图 + 真机录屏/截图（列出机型与时间）。
- 数据核对：构造一场已分组队内赛（含乌龙与平局）与一场未分组队内赛，记录球员汇总行与 STA-03 返回。

## Comments

- 2026-11-23 自动化验收完成，详见 [acceptance-runs/2026-11-23.md](../acceptance-runs/2026-11-23.md)：
  - 后端 `mvn verify` 通过（244 单测 + 366 IT）；空库 Flyway 至 V14；`StatsRollupIT`/`LeaderboardIT`/`FlywayMigrationIT` 全绿。
  - Web E2E：`web-leaderboard.spec.ts` 6/6、`web-team.spec.ts` 4/4（后者打真实后端），无 console error。
  - 数据核对（赛季 2099）：已分组平局含乌龙 + 未分组队内赛 + 取消比赛，汇总行与 STA-03 与 RANK-02~06 一致；写入到可读 53 秒。
  - 小程序：typecheck 通过、31 文件 303 项通过；Web/小程序名次用例同构。
  - 待人工：开发者工具服务端口未开启，MP09 截图与 iOS/Android 真机主链路未执行。
