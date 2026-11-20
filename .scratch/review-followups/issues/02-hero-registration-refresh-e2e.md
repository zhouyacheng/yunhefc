# 02 Hero 报名与静默刷新失败 E2E 覆盖

Status: resolved
Blocked by: 01

## 问题

P-49 后 C01 Hero 固定为 startAt 最大的比赛（种子中为 1001，LOCKED），默认测试球员 1002 已报名；原「报名成功后静默刷新失败 → 重试刷新」E2E 被删除，02 §5.1「未满直接报名」与 §8.2 错误重试失去覆盖。

`HomePage.vue` 的 `load(true)` 在报名后静默刷新，失败时置 `refreshFailed` 并展示「重试刷新」；该路径当前没有自动化用例。

## 需要决策

两种可行 seam，二选一后再实现：

- **A. 永久 fixture**：新增一场晚于 1001、报名中且 1023 未报名的比赛（如 1006），同步调整所有 Hero 断言（web-home、schedule、admin 等）。真实度最高，改动面最大。
- **B. 网络改写**：E2E 仅拦截 `GET /api/v1/c/matches?sort=startAt:desc&pageSize=4`，把 1005 置顶为 Hero，再用 1023 走真实报名接口；静默刷新请求失败一次。改动面小，但用例对查询参数与响应形状有耦合。

## 验收

- 新用例走真实报名接口并成功。
- 静默刷新失败一次后出现「刷新失败」与「重试刷新」，点击后队列数据恢复、按钮消失。
- 无 console error。

## Comments

- 2026-11-17 选择 seam B：`web-home.spec.ts` 新增「报名后静默刷新失败可重试」。仅拦截 `GET /matches?sort=startAt:desc&pageSize=4` 并把 1005 置顶；以 1002 走真实 `POST /matches/1005/registrations`，第二次列表请求返回业务错误，断言「刷新失败」「重试刷新」、重试后队列确认数 +1 与「查看详情/已报名」。不清理报名：保留给后续 `web-match-detail` 递补用例使用，种子重放会删除 1002 在 1005 的报名。`pnpm test:e2e --project=web e2e/web-home.spec.ts` 5 passed。
