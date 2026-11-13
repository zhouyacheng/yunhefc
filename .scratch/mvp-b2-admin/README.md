# B2 B 端页面 — 实施规格

Status: resolved

事实源：`.scratch/mvp-release/batches.md` §B2；`.scratch/mvp-release/issues/10-grilling-b-end-placeholders.md`；`docs/design/02.页面结构与交互设计.md` §5.1/§359；`docs/design/05.API接口设计.md` AUTH-11/13。

B1 已完成后端 `AuthService.sections` 移除 dashboard；本批已出票如下。

## 票序

| 票 | 内容 | 状态 | 依赖 |
|---|---|---|---|
| 01 | B 端壳层下线 B01 工作台 | resolved | — |
| 02 | B02 比赛管理 | resolved | 01 |
| 03 | B07 球员 | resolved | 01 |
| 04 | B08 角色与权限（角色 CRUD + 权限勾选） | resolved | — |
| 05 | B08 用户角色分配 | resolved | 04 |
| 06 | B09 系统设置 | resolved | — |
| 07 | B10 操作日志 | resolved | — |
| 08 | 报名页比赛选择器改用公开赛程 | resolved | —（验证票 01 时发现） |
| 09 | B11 邀请管理 | resolved | `mvp-b1-backend/issues/10` |
| 10 | B11 评审后续项 | resolved | 09 |

与 `mvp-release/batches.md` §B2「7 张票」的偏差：B08 按实施粒度拆为 04/05 两张；08 已被已 resolved 的修正票占用，B11 顺延为 09；新增 B1 前置票 10（邀请链接路径修复），B11 依赖它；10 为 B11 的 `/code-review` 后续项。
