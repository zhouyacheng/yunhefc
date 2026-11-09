# 云河FC 后端

Spring Boot 3.5.x / Java 21 单部署单元。当前仅包含骨架与 Flyway 基线，尚未实现业务接口。

## 本地运行

```bash
./mvnw test          # Testcontainers 空库迁移 + 健康检查
./mvnw spring-boot:run
```

默认连接本机 MySQL/Redis/Kafka；可通过环境变量覆盖：

| 变量 | 默认值 |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/yunhefc?...` |
| `DB_USERNAME` / `DB_PASSWORD` | `yunhefc` / `yunhefc` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `SERVER_PORT` | `8080` |

## Flyway

`src/main/resources/db/migration/`：

- `V1__baseline_schema.sql`：27 张业务表
- `V2__quartz_schema.sql`：11 张 Quartz 表
- `V3__seed_team_roles_permissions_settings.sql`：球队、3 个默认角色、28 个权限、9 项配置
- `V4__seed_formation_templates.sql`：8 套预置阵型、64 个槽位
- `V5__add_table_comments.sql`：27 张业务表的表级中文注释

迁移在应用启动时执行；数据库不建任何 CHECK，业务规则由应用层强校验（见 `docs/design/04.数据库设计.md` §8.1）。
