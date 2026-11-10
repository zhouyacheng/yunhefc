# 云河FC 项目协作规范

## 0. 当前阶段

- **设计冻结**：需求 v1.15；设计文档 01 v0.12、02 v0.13、03 v1.11、04 v0.16、05 v0.20、06 v1.35。
- **评审通过**：P-01~P-25 已确认；设计成果不再主动变更。
- **代码状态**：`backend/` 骨架、Flyway V1–V5、M01（C/B 认证、RBAC、邀请、Redis 会话、Outbox、幂等、微信 mock/real）、M02（PLR-01/03/05/06/07/08/09、STA-01）与 M03（MAT-02/03/04/05/06/07/08/09/10/11/12 及 MATCH-05 自动流转）已实现；`frontend/`、`deploy/` 尚未创建。
- **原型定位**：`prototype/` 仅演示交互与信息架构，不是生产代码；正式实现以设计文档为准。
- **下一步**：进入 M04 报名模块（REG-*）。

## 1. 唯一事实源

改动前先读对应文档；文档冲突时，以需求文档 + 06 已确认决策为准。

| 内容 | 事实源 |
|---|---|
| 需求、MVP 边界、验收 | `docs/云河FC-需求文档.md` |
| 模块职责、依赖 | `docs/design/01.功能模块梳理.md` |
| 页面、交互、状态矩阵 | `docs/design/02.页面结构与交互设计.md` |
| 架构、技术栈、分包 | `docs/design/03.整体架构设计.md` |
| 表结构、枚举编码、索引 | `docs/design/04.数据库设计.md` |
| API 契约、权限、错误码 | `docs/design/05.API接口设计.md` |
| 评审结论、P-01~P-25 | `docs/design/06.设计方案评审结果.md` |
| 数据库/API/Kafka/日志/Java 规则 | `docs/rules/*.md` |
| 业务 DDL / Quartz DDL | `scripts/sql/schema.sql`、`scripts/sql/quartz.sql` |
| 可交互原型 | `prototype/`（`prototype/README.md`） |

**同步规则**：需求/DB/API/页面任一变更，必须同步所有受影响文档与原型；DB 同步 04 + `schema.sql`，API 同步 05 + 04，页面同步 02 + `prototype/`，决策记录写入 06。

## 2. 项目硬约束

- **单队 MVP**：8 人制业余球队；不引入多队、平台超管、微服务、K8s、二期功能。
- **技术栈固定**：Java 21、Spring Boot 3.5.x、MyBatis-Plus、Flyway、MySQL 8.4、Redis 6.2.24、Kafka 3.7.2（单节点 KRaft）、Quartz JDBC、Vue 3 + Vite + TypeScript、C 端 Vant、B 端 Element Plus、Docker Compose + Nginx。
- **文件存储**：统一使用阿里云 OSS；开发、测试、生产同一 OSS，私有 Bucket + 签名 URL。
- **数据库约定**：
  - 枚举字段 `TINYINT UNSIGNED`，`COMMENT` 写数字与枚举映射；27 张业务表带表级中文 `COMMENT`，所有字段带中文 `COMMENT`。
  - 27 张业务表均有 `is_deleted`（0-未删除；1-伪删除）；查询默认过滤 `is_deleted=0`。
  - `registration.active` 为 0/1（1-有效；0-无效）；`active_unique` 承担唯一键。
  - 业务表 0 物理外键；Quartz 11 表、5 个框架外键为官方例外。
  - **永不使用 `CHECK`**：数据库永久禁用任何 `CHECK`/`chk_` 约束；枚举、范围与跨字段规则全部由应用层强校验（04 §8.1）。
  - 伪删除不释放唯一键；现有 58 条业务索引保留，不重复建可覆盖索引。
- **API 约定**：
  - 路径 `/api/v1/c/**`（C 端）、`/api/v1/b/**`（B 端）；统一 envelope，成功 `code=0`。
  - HTTP 状态码表达类别；body 返回 `code/message/data/requestId`。
  - 列表统一 `Page<T>`；小型只读集合按 05 §2.2 例外。
  - 枚举响应返回数字 `field` + `fieldLabel`；请求只接受数字 code，Label 只读。
  - 高风险写接口带 `Idempotency-Key`；阵容/状态/事件等用 `version` 乐观锁。
  - 权限码统一 `资源:动作:范围`；ADMIN 拥有全部权限；PENDING 只走状态白名单。
- **模块边界**：controller → application → domain → infrastructure；跨模块通过应用服务、read port 或领域事件；事务例外仅报名生命周期、注册建档、账号生命周期编排三处。

## 3. 工作流

1. **读事实源**：按改动类型读取对应文档与 `docs/rules/*`。
2. **列影响面**：需求？01/02/03/04/05/06？`schema.sql`？`prototype/`？
3. **先确认再改**：遇到歧义、关键业务规则、DB/API 契约变更，一次只问一个关键问题；给出候选、建议与影响，等回答后继续。
4. **文档优先**：先改设计文档，再同步 SQL/API/原型；未经明确要求不写业务代码、不执行 SQL。
5. **验证后汇报**：跑下方验证清单，汇报改动文件、验证结果、待确认项。

**完成标准**：所有受影响文档已同步；验证清单全部通过；汇报中明确未执行项。

## 4. 目录与代码位置

```
docs/design/       01–06 设计文档
docs/rules/        database/api/kafka/logging/java 规则
scripts/sql/       schema.sql、quartz.sql、test-data.sql
prototype/         静态可交互原型
backend/           (planned) Spring Boot 单部署单元
frontend/          (planned) pnpm workspace
frontend/apps/web/     C 端 Vue 3 + Vant
frontend/apps/admin/   B 端 Vue 3 + Element Plus
frontend/packages/     共享 API、权限与类型
deploy/            (planned) Docker Compose、Nginx
```

- 后端分包按 03 §4：`common`、`contracts`、`infra`、`query`、`stats`、`auth`、`player`、`match`、`registration`、`attendance`、`event`、`lineup`、`grouping`、`notification`、`audit`、`settings`。
- 修改 Java/DB/API/Kafka/日志前，必须分别阅读 `docs/rules/java-rules.md`、`database-rules.md`、`api-rules.md`、`kafka-rules.md`、`logging-rules.md`。

## 5. 编码规范（精简）

### Java

- 命名：类 `UpperCamelCase`；方法/变量 `lowerCamelCase`；常量 `UPPER_SNAKE_CASE`。
- 参数做格式、范围、业务校验；业务异常带明确错误码。
- MyBatis-Plus 优先；复杂查询写 XML/自定义 SQL；`#{}` 绑定，动态排序走白名单。
- 避免 N+1；批量查询/写入；统一异常处理；不向前端泄露 SQL、堆栈、密钥。
- 领域规则放 domain/application，不放 Controller 或 SQL。

### Vue

- Composition API + `<script setup>`；C 端 Vant，B 端 Element Plus。
- 页面结构、交互、状态以 02 与原型为准；API 统一封装，不在页面重复写请求。
- 处理加载中、失败、空数据；前端路由守卫只做导航，权限由后端校验。
- 不在前端代码暴露密钥、服务端凭证。

### 通用

- 注释只写业务意图、复杂逻辑、关键设计原因；保持简洁。
- 未经明确要求不提交 git；提交前先确认分支与变更范围。

## 6. 验证清单

- `grep -c '^CREATE TABLE' scripts/sql/schema.sql` = 27；业务外键 = 0。
- `quartz.sql` = 11 表、5 框架外键。
- 05 = 92 接口、28 权限码；需求 = 114 ID / 112 MVP。
- Markdown 表格列数检查通过；8 个原型 JS `node --check` 通过；Playwright 冒烟无 console error。
- DB 改动：04 与 `schema.sql` 逐表核对字段/类型/默认/约束/表注释/字段注释。
- 后端建立后：`mvn test` 通过；空库 Flyway 迁移幂等；接口契约与 05 一致。
- 未执行 SQL、未实现 API、未写业务代码时，在汇报中明确说明。

## 7. 先确认的红线

以下操作先向用户确认，即使处于自动接受模式：

- 删除文件、目录或 git 历史。
- 修改 `.env`、密钥、token、证书、CI/CD 配置。
- `git push`、`git rebase`、`git reset --hard`、强制推送。
- 生产部署、公开发布、执行 SQL 脚本。
- 修改已确认的需求、技术栈或 DB/API 契约。
- 新增需求文档未定义的功能或模块。
