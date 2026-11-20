# 01 — 交付缺口盘点：92 接口与 C/B 页面对账

Type: research
Status: resolved
Blocked by: —

## Question

按需求 112 条 MVP 与 `docs/design/05.API接口设计.md` 的 92 个接口，对账后端 controller 与前端 C/B 路由和页面，列出尚未交付的项。

重点覆盖：

- M09 通知、M10 审计、M11 配置、M12 列表筛选/排序/分页、M13 公开站点与个人中心的后端接口与页面。
- B 端 `players`/`roles`/`invites`/`audit`/`settings`/`dashboard` 占位页（`frontend/apps/admin/src/pages/SectionPage.vue`）。
- C 端首页、球队页、个人中心、通知页的缺失（当前只有 login/matches/match-detail/account）。
- 现有 Playwright/Vitest 覆盖面（只盘点，不做验收决策）。

本票只做「接口 / 页面 / 测试」层面的交付盘点，不深入通知触发矩阵、审计记录覆盖或配置语义（分别见 02 / 03 / 06）。输出按模块的「已交付 / 缺失 / 待决策」三列，带文件指针与 05/02 条目编号，并给出初始实施批次候选（不排序）。

事实指针：`docs/design/05.API接口设计.md` §3、`docs/design/02.页面结构与交互设计.md` §4–§7、`frontend/apps/{web,admin}/src/router`、`backend/src/main/java/com/yunhefc/**/controller`、`frontend/e2e`。

## Answer

- 后端接口：92 个中已交付 83 个；缺失 9 个，集中在 M09 通知（NTF-01~04）、M10 审计（AUD-01）、M11 配置（SET-01/02）、M13 文件（FIL-01/02）。
- B 端页面：12 个中真实页面 6 个（B00、B02–B06）；B01/B07/B08/B09/B10/B11 共 6 个仍为 `SectionPage` 占位，其中 B07/B08/B11 后端已就绪，B09/B10 前后端双缺。
- C 端页面：9 个中仅 C02/C03/C05 有可用页面；C01/C04/C07/C08/C09 缺失，C06 以 `/account` 部分替代设计 `/profile`；C03 无报名/取消、邀请、分享、管理员状态入口。
- M12：后端筛选/排序基本齐备；C02/B02 前端仅暴露 status，B07 无页面；AUTH-13/INV-04 未校验 pageSize 白名单；NTF/AUD 列表缺失。
- 测试：Playwright 14 条、Vitest 40 条；覆盖集中在已交付的 C02/C03 与 B02–B06，B01/B02 CRUD/B07–B11/C01/C04/C06–C09 未覆盖。
- 待决策：C06 路由命名、C03 报名 Tab 结构、AUTH-13/INV-04 分页校验范围、头像上传批次边界、B02 交付深度。

完整清单与文件指针：[research/01-delivery-gap.md](../research/01-delivery-gap.md)
