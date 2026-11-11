# Issue tracker：本地 Markdown

本仓库的 issue 与 spec 以 Markdown 文件形式存放在 `.scratch/`。`.scratch/` 当前未纳入 git；除非仓库开始提交它们，否则按本地工作文档处理。

## 约定

- 每个 feature 一个目录：`.scratch/<feature-slug>/`
- feature 的 spec/索引为 `.scratch/<feature-slug>/README.md` —— 记录事实源、票序与约束
- 实施 issue 一票一文件：`.scratch/<feature-slug>/issues/<NN>-<slug>.md`，从 `01` 编号 —— 禁止使用单一合并 tickets 文件
- triage 状态记录在每个 issue 文件顶部的 `Status:` 行（角色字符串见 `triage-labels.md`）
- 依赖关系记录在顶部的 `Blocked by:` 行
- 评论与对话历史追加到文件末尾的 `## Comments` 标题下

## 当技能要求「发布到 issue tracker」

在 `.scratch/<feature-slug>/` 下新建文件（必要时创建目录）。

## 当技能要求「获取相关 ticket」

读取给定路径的文件。用户通常会直接传入路径或 issue 编号。

## Wayfinding 操作

供 `/wayfinder` 使用。**map** 是一个文件，每个 ticket 对应一个 **child** 文件。

- **Map**：`.scratch/<effort>/map.md` —— Notes / Decisions-so-far / Fog 正文。
- **Child ticket**：`.scratch/<effort>/issues/NN-<slug>.md`，从 `01` 编号，正文写问题。`Type:` 行记录 ticket 类型（`research`/`prototype`/`grilling`/`task`）；`Status:` 行记录 `claimed`/`resolved`。
- **Blocking**：顶部 `Blocked by: NN, NN` 行。只有当列出的每个文件都为 `resolved` 时，ticket 才算解除阻塞。
- **Frontier**：扫描 `.scratch/<effort>/issues/` 中处于 open、未阻塞且未认领的文件；编号最小者优先。
- **Claim**：开始任何工作前，设置 `Status: claimed` 并保存。
- **Resolve**：在 `## Answer` 标题下追加答案，设置 `Status: resolved`，然后把上下文指针（要点 + 链接）追加到 `map.md` 的 Decisions-so-far。
