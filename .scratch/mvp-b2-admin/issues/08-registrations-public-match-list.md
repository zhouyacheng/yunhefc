# 08 — 报名/考勤页比赛选择器改用公开赛程

Type: implementation
Status: resolved
Blocked by: —（验证票 01 时发现）

**What to build:** 只有 `attendance:view:any` 的只读用户进入 B 端报名/考勤页时，比赛选择器不应请求需要 `match:view:any` 的 `GET /b/matches`；改用公开 `GET /c/matches`，与分组/阵容页保持一致，避免登录落点即产生 403 与「加载失败」。

## Scope

- `RegistrationsPage` 的比赛列表从 `api.get<Page<MatchAdmin>>('/matches')` 改为 `publicApi.get<Page<MatchSummary>>('/matches')`。
- 模板只消费 `id/title/startAt/status`，`MatchSummary` 已足够。

## Owned files

- `frontend/apps/admin/src/pages/RegistrationsPage.vue`

## Test obligations

- `pnpm exec playwright test --project=admin e2e/admin.spec.ts:276` 通过且无 console error。
- admin 全量 9/9。

## Result

- 交付：`cef7401`（比赛选择器改用 `publicApi` 公开赛程；选中后从公开比赛详情补齐 `capacity/guestCapacity/waitlistCapacity`，失败按 `—` 展示）。
- 验证：只读球员用例 `e2e/admin.spec.ts:276` 无 403 console error；重放种子后 admin 全量 9/9 通过（2026-11-12）。
- 未执行：无后端/DB/接口契约改动；事件页仍按 `event:edit:any` 使用 B 端比赛列表，未在本票调整。
