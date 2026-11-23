# ADR 0004：排行榜排名引擎反向宿主在小程序，Web 经包路径引用

状态：已接受（2026-11-23）

## 背景

排行榜排名（7 榜定义、比较器、标准竞赛排名）原在 Web `features/leaderboard/ranking.ts`
与小程序 `utils/leaderboard-view.ts` 各有一份。2026-11-23 核对：核心 144 行逻辑逐字相同，
仅导出可见性与函数命名不同；行为用例也近乎重复。P-89 将双实现记为技术债（票 10），
按 ADR 0003 的结论暂不收敛。

票 10 细化时确认了一个被忽略的约束：ADR 0003 的两个前提中，「shared 拆出无浏览器依赖的
子入口」只解决**纯度**，不解决**可达性**。小程序运行时没有 bundler，DevTools 只编译
`miniprogramRoot` 内文件，pnpm 符号链接布局又让「构建 npm」不可用。因此小程序运行时执行的
代码必须物理位于 `frontend/apps/miniprogram/miniprogram/` 内；任何包外 TS 都无法被小程序加载。

由此，真正单源只有三条路：

1. 单源放进 `miniprogramRoot`，Web 反向引用（本决策）。
2. 小程序引入 bundler，或纯包产出 JS 并解决 node_modules 解析（推翻现有构建方式）。
3. 构建时把 shared 源生成到 `miniprogramRoot`（ADR 0003 已否：新增构建链与旧产物风险）。

## 决策

对应 P-90，取代 P-89 中「排行榜排名 Web/MP 双实现维持不变」的排期结论。采用反向宿主：

1. 排名引擎单源为 `frontend/apps/miniprogram/miniprogram/utils/leaderboard-engine.ts`。
   - 零运行时导入，自带最小结构化类型 `RankableEntry` / `RankableTotals`，不依赖
     `@yunhefc/shared` 或小程序 `types/api`。
   - 导出榜单键、榜单定义、`leaderboardBoard` 与泛型 `rankEntries`，两端保留各自的具体条目类型。
2. 小程序 `leaderboard-view.ts` 消费引擎，只保留 WXML 行视图、Tab 数据与头像回落。
3. Web `features/leaderboard/ranking.ts` 为薄适配层，经 workspace 包子路径
   `@yunhefc/miniprogram/utils/leaderboard-engine` 引用；`@yunhefc/web` 增加
   `@yunhefc/miniprogram: workspace:*` 依赖，小程序包在 `exports` 中只暴露该子路径。
4. 行为用例收敛到 `apps/miniprogram/tests/leaderboard-engine.test.ts`；Web 侧保留适配层冒烟，
   行视图用例留在各自平台。

## 放弃的备选

| 备选 | 放弃原因 |
|---|---|
| 共享用例夹具，不收敛实现 | 只防用例漂移，不减少重复，票 10 仍是未偿技术债 |
| 小程序引入 bundler | 推翻现有原生编译与 `prepare:vant` 流程，影响面远大于收益 |
| `prepare:shared` 生成到 miniprogramRoot | ADR 0003 已否：新增构建链与「忘了 build」的失败模式 |
| Web 直接相对路径引用小程序目录 | 路径脆弱且散落；包路径把耦合收敛到 `package.json` 与适配层一处 |

## 后果

- **正面**：排名核心只剩一份；Web 打包只打入引擎文件，不引入小程序页面；无新增构建步骤，
  小程序侧仍不依赖 `@yunhefc/shared`。
- **负面**：`@yunhefc/web` 从此依赖 `apps/miniprogram` 目录。若未来拆分小程序仓库，需先把
  引擎迁到独立包（届时可重开 ADR 0003 选择 bundler/JS 产物路线）。
- 本决策不推翻 ADR 0003 的 shared 策略，只补充「Web 与小程序都需要复用的纯逻辑」的宿主方向：
  把单源放在受约束的一端，让有 bundler 的一端反向引用。比赛展示、球队视图、报名写操作等
  其他本地移植不在本决策范围内。
- 排名口径变更必须先改引擎，再同步两端消费与用例；`pnpm typecheck`、`pnpm test`、
  Web `build` 与 `web-leaderboard.spec.ts` 是回归门槛。
