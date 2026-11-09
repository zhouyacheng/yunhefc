# 云河FC

单支业余球队的运营工具 + 对外展示窗口。

## 当前状态

- 需求文档：`docs/云河FC-需求文档.md`（v1.15，MVP 基线）
- 设计文档：`docs/design/01`–`06`（模块、页面、架构、数据库、API、评审结果）
- 静态可交互原型：`prototype/`，纯 HTML/CSS/JS，无后端、无构建、无外部依赖
- 数据库基线：`scripts/sql/schema.sql`、`quartz.sql`
- 后端、前端、部署目录尚未纳入本仓库

原型覆盖比赛创建与状态流转、延期/重新排期、报名/候补/签到、事件录入、阵容与模板、分组、RBAC 角色权限、球员管理、邀请链接、球队展示等主流程。数据存浏览器 `localStorage`，key 为 `yunhefc_prototype_v1`。

## 运行原型

```bash
python3 -m http.server 4173 --directory prototype
```

访问 `http://localhost:4173/index.html`。原型无需安装依赖。

## 目录

```
docs/       需求文档
prototype/  静态可交互原型
```

## 规划技术栈（尚未落地）

| 层 | 选型 |
|---|---|
| 后端 | Java 21 + Spring Boot + MyBatis-Plus + Flyway + MySQL |
| 缓存/锁 | Redis + Spring Session |
| 定时任务 | Quartz |
| 消息 | Kafka + Outbox |
| 前端 | Vue 3 + Vite（C 端 Vant，B 端 Element Plus） |
| 部署 | Docker Compose + Nginx |

## 文档

- 需求文档：`docs/云河FC-需求文档.md`
- 设计文档：`docs/design/01.功能模块梳理.md` 至 `06.设计方案评审结果.md`
- 规范：`docs/rules/database-rules.md`、`api-rules.md`、`kafka-rules.md`、`logging-rules.md`、`java-rules.md`
- 数据库基线：`scripts/sql/schema.sql`、`scripts/sql/quartz.sql`
- 原型说明：`prototype/README.md`

## 后续

- 原型尚未实现、设计已覆盖：微信网页授权、密码重置/换绑
- 按需求文档落地后端与前端实现
