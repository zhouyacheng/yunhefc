# ADR 0003：小程序不引共享包，本地移植靠同构测试锁定口径

状态：已接受（2026-11-23）

`frontend/apps/miniprogram` 是微信原生编译，没有 bundler；微信开发者工具的「构建 npm」不认 pnpm 的符号链接布局（见 `frontend/apps/miniprogram/README.md`），因此依赖统一用 `prepare:vant` 物理平铺。`@yunhefc/shared` 是源码直出（`exports` 指向 `src/index.ts`），无构建产物，barrel 还包含使用 `window`/`document` 的模块。决定：小程序不引入 `@yunhefc/shared`，需要复用的纯逻辑在 `miniprogram/utils/` 本地移植，并用与 Web 同构的 Vitest 用例锁定口径；文件头注明来源。

## 放弃的备选

| 备选 | 放弃原因 |
|---|---|
| 直接 `import '@yunhefc/shared'` | pnpm 严格隔离下不可解析；DevTools 只编译 `miniprogramRoot` 内文件，也不会编译包外 TS；barrel 的浏览器 API 无 tree-shaking |
| 生成式平铺（新增 `prepare-shared` + CI 漂移检查） | 可行，但新增一条构建链与失败模式（开发者工具跑之前忘了 build，验的是旧生成物）；收益只消除重复，不改变运行时 |
| 给小程序引入 bundler | 推翻现有构建方式，影响面远大于收益 |

## 后果

- 排行榜排名（Web `features/leaderboard/ranking.ts` 与小程序 `utils/leaderboard-view.ts`）、比赛展示、球队视图、报名写操作等存在双份实现；一致性由同构测试与文件头注释约束，不靠编译期保证。
- 迁移共享逻辑前先判断是否含浏览器 API；含则不能进小程序候选。
- 若未来小程序引入 bundler 或共享包拆出无浏览器依赖的子入口，需重新评估本决策。
