# 01 — M05–M08 模块完成标准与交接方式

Type: grilling
Status: resolved

## Question

每个模块「做完」的验收口径是什么？需要明确：

- 后端完成标准：API 契约一致、`mvn verify` 通过、Flyway 幂等、ArchUnit 通过是否足够。
- B 端页面完成标准：是否必须随模块交付，如何验收（手动冒烟 / Playwright / 仅接口）。
- 文档同步范围：实现后哪些内容要回写 02/04/05/06 与 `CLAUDE.md`。
- 交接点如何触发：四个决策票关闭后，用什么粒度调用 to-tickets 拆实施票；前端与后端是否分开排期。

事实指针：`docs/design/03.整体架构设计.md` 测试与交付章节、`CLAUDE.md` 验证清单、`.scratch/m04-registration/README.md`。

## Answer

**模块完成 = 后端门 + 页面门，两门都过。**

- 后端门：05 中该模块接口全部实现；`mvn verify` 全绿；空库 Flyway 迁移幂等；ArchUnit 通过。
- 页面门：按 02 的模块页面映射交付——M05 为 B03 报名/考勤 + C03 签到，M06 为 B04 事件/比分 + C03 事件 Tab，M07 为 B05 阵容，M08 为 B06 分组。后端先行，页面随模块。
- 前端自动化：Vitest 组件/工具测试 + 每模块一条 Playwright 黄金路径（签到、录事件、生成分组等）。
- 交接：四张决策票全部关闭后，先做一次 to-spec 收敛整条路线，再 to-tickets 按模块出实施票（M05、M06、stats、M08、M07），用 `Blocked by` 表达依赖；不要从 map 直接进 implement。
- stats：独立切片，位于 M05+M06 之后、M08 之前；M08 显式依赖 stats。考勤与事件的汇总任务、缓存与读接口都在该切片内完成，不拆进 M05/M06，也不并入 M08。
- 文档：仅在契约变更时改 02/04/05；实现记录写 06；同步更新 `CLAUDE.md` 的代码状态。契约不变时不做文档回写。
- CI：扩展 GitHub Actions 覆盖后端 `mvn verify` 与前端 lint/test/build，作为 to-tickets 后的基础设施任务；不作为任何模块完成的前置阻塞。
