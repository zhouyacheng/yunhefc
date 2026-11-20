# 03 「历史赛程」文案与语义对齐

Status: resolved
Blocked by:

## 问题

P-49 后 C01 Hero 取 startAt 最大的一场；Hero 下方的「历史赛程」实际是其后按 startAt 降序的最近 3 场，可能包含未来比赛。02 §5.1 与 UI 仍用「历史赛程」，语义易误解。

## 修复

- 确认文案：建议「最近赛程」或「更多比赛」。
- 同步 `docs/design/02.页面结构与交互设计.md` §5.1、`frontend/apps/web/src/pages/HomePage.vue`、`frontend/e2e/web-home.spec.ts`、`prototype/assets/js/public.js`。
- 纯文案，不改取数与排序行为。

## 验收

- 文档/UI/E2E/原型用词一致。
- `pnpm --filter @yunhefc/web typecheck`、web 单测与 `web-home.spec.ts` 全绿。

## Comments

- 2026-11-17 文案定为「最近赛程」，已同步 02 §5.1、05 MAT-02 说明、`HomePage.vue`、`web-home.spec.ts`、`prototype/README.md` 与 `public.js`；06 的 §64/§73 历史决策段保留原文并补 §76 修订注记。验证：`pnpm --filter @yunhefc/web typecheck`、web 单测 185、`web-home.spec.ts` 5 passed、`node --check prototype/assets/js/public.js`。
