# 领域文档

工程技能在探索代码库时，应如何读取本仓库的领域文档。

## 探索前先读

- 仓库根目录的 **`CONTEXT.md`**，或
- 如果存在仓库根目录的 **`CONTEXT-MAP.md`** —— 它指向每个 context 的 `CONTEXT.md`。读取与本主题相关的每一个。
- **`docs/adr/`** —— 阅读与即将改动的领域相关的 ADR。多 context 仓库还要检查 `src/<context>/docs/adr/` 中的 context 级决策。

如果这些文件不存在，**静默继续**。不要指出缺失，也不要主动建议创建。`/domain-modeling` 技能（通过 `/grill-with-docs` 与 `/improve-codebase-architecture` 触达）会在术语或决策真正确定时惰性创建它们。

## 文件结构

单 context 仓库（大多数仓库）：

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

多 context 仓库（根目录存在 `CONTEXT-MAP.md`）：

```
/
├── CONTEXT-MAP.md
├── docs/adr/                          ← 系统级决策
└── src/
    ├── ordering/
    │   ├── CONTEXT.md
    │   └── docs/adr/                  ← context 专属决策
    └── billing/
        ├── CONTEXT.md
        └── docs/adr/
```

## 使用术语表中的词汇

当输出中要命名领域概念（issue 标题、重构提案、假设、测试名）时，使用 `CONTEXT.md` 中定义的术语。不要漂移到术语表明确规避的同义词。

如果需要用到的概念还不在术语表中，这是一个信号 —— 要么你在发明项目不使用的语言（请重新考虑），要么存在真实空白（记录下来交给 `/domain-modeling`）。

## 标记 ADR 冲突

如果输出与现有 ADR 矛盾，明确暴露出来，而不是静默覆盖：

> _与 ADR-0007（event-sourced orders）矛盾——但值得重新讨论，因为……_
