# 01 报名资格收拢为一个 deep module

Status: resolved
Blocked by: none

## 目标

让“能否报名/候补/取消”只有一个 module 拥有规则；首页 Hero 与比赛详情退化为 adapter，只做字段映射与渲染。

## 现状与摩擦

- `utils/registration.ts` 的 `signupState`/`cancelState` 与 `utils/home-cta.ts` 的 `homeCta` 各自维护状态、截止、名额、外挂、待审核判定，以及两套状态文案。
- `utils/home-view.ts` 再次拼装 Hero CTA；`pages/match-detail/index.ts` 的 `syncWindowState` 又独立拼 `canSignup` 与权限。
- 理解“能否报名”要在 4 个 module 之间跳转；改一条规则要同步两处实现与两组测试，测试会一起变绿，掩盖规则漂移。

## 深化方向

- 一个 registration-eligibility module：输入比赛、本人报名、角色/权限、当前时间，输出一个决策（可用动作、禁用原因、队列/递补状态、展示文案）。
- 首页与详情页只保留 adapter：把决策映射成各自的视图字段，不再判断规则。
- interface 先设计再实现；不预先把首页文案硬塞进 module。

## 删除测试

删掉任一现有实现，复杂度不会消失，只会出现在另一个调用方——当前拆分不是 module 边界。

## 收益

- leverage：一个 interface 覆盖首页与详情两个调用方。
- locality：规则与文案只改一处。
- 测试：一张角色 × 状态 × 时间窗边界表替代两份近似测试。

## 范围

- 改 `utils/registration.ts`、`utils/home-cta.ts`、`utils/home-view.ts`、`pages/match-detail/index.ts` 及其测试。
- 保持 C01/C03 现有文案与禁用原因不变。

## 不做

- 不改后端判定或错误码。
- 不改比赛状态机与报名 API。
- 不引入 `@yunhefc/*`。

## 验收

- [x] 报名/候补/取消判定只剩一份实现。
- [x] 首页与详情页不再各自判断状态、名额、截止、待审核。
- [x] 新增决策表测试覆盖匿名、待审核、正式、外挂、候补、状态 0–6、截止/锁定前后。
- [x] 原有 CTA 文案、禁用原因、按钮行为不变（两处口径统一见 Comments）。
- [x] `typecheck`、`test`、`pnpm lint` 通过。

## 风险

- 首页 CTA 与详情页的文案并不完全同构，合并时容易把页面文案带进 module；先用决策字段表达，再由 adapter 映射。

## Comments

- 2026-11-20 架构扫描开票（Strong）。
- 2026-11-20 实施完成（TDD）：
  - `utils/registration.ts` 成为唯一判定入口 `registrationDecision({ ... }) → { signup, cancel }`；报名、候补、取消共用同一套 `isActive`、比赛状态文案表与权限判定。
  - `utils/home-cta.ts` 退化为 `SignupDecision → HomeCtaResult` 映射；首页专属文案（查看详情 / 已报名 / 候补中）留在 adapter，不进入 module。
  - `utils/home-view.ts` 组装决策（`buildHero` 新增 `permissions` 入参）；`pages/match-detail/index.ts` 的 `syncWindowState` 与 `pages/profile/index.ts` 的取消入口只消费决策字段。
  - `pages/match-detail` 的 `data.canSignup` 删除，权限由 module 依据 `permissions` 判定；`hasActiveRegistration` 由 `signup.kind === 'registered'` 推导。
  - 决策表测试：`tests/registration.test.ts` 覆盖匿名、待审核、正式、外挂（含 player 缺失）、候补、状态 0–6/9、截止无效/前后、锁定前后、权限缺失、名额/候补满；`tests/home-cta.test.ts` 只保留决策→CTA 映射。
  - 用例数 198 → 199：删除的是首页与详情重复的规则用例（已由决策表覆盖），映射、权限、待审核、候补接线用例净增；覆盖行为无减少。
  - 迁移对照：用临时脚本把旧 `signupState` / `cancelState` / `homeCta` 原文与新 module + adapter 在「匿名/待审核 × 外挂/正式/无 player × 状态 0–6/9 × 截止前后 × 锁定前后 × 5 组名额 × 报名状态 0–3 × 权限有无」全组合上逐例比对，除下述两处外输出完全一致（临时文件已删除）。
  - 两处口径统一（均为旧实现的缺口）：截止时间无法解析时首页 CTA 由“继续按名额判定”改为“报名截止时间无效”；首页 CTA 纳入 `registration:create:own` 判定（ADMIN / PLAYER / GUEST 均持有，只有被改权的角色可见差异）。后者与 Web C01 现有实现不同；按本票“module 输入含角色/权限”的方向保留，Web 端不在本票范围。
  - 验证：`pnpm --filter @yunhefc/miniprogram typecheck`、`pnpm --filter @yunhefc/miniprogram test`（24 文件 / 199 用例）、`frontend` 下 `pnpm lint`、`prettier --check`（改动文件）全部通过。
- 2026-11-20 code-review（medium）复审：
  - 已修：blocked 决策内联构造改为复用 `blockedDecision()`；`tests/home-view.test.ts` 增加待审核与候补接线用例，锁定 module → adapter 字段口径。
  - 未改（不属于本票、均为迁移前既有行为）：比赛状态修改/报名冲突未标记“我的报名”失效；我的报名列表 `canCancel` 只看状态 0/1 且不看锁定时间（handler 已用决策复核）；详情页 30 秒定时器整体重刷事件行与 `now`/`derivedScore`/`canEditAny` 死状态。前两项可作为后续票。
  - 待办：页面 data 形状未变，但首页 CTA 与详情报名/取消仍需开发者工具/真机走查；按本批次 README 约定未提交 git。
