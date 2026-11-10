# M04 报名与候补 — 实施票

事实源：`docs/云河FC-需求文档.md`（REG、NOTIFY）、`docs/design/01` §4.5/§4.10、`02` §5.3/§5.6/§6.5/§7.2、`03` §4.2/§4.3/§5.2/§5.3、`04` `registration`/`notification`、`05` §3.5/§4。

按阻塞边顺序执行，每票单独会话，票间 `/clear`：

1. `issues/01-signup-waitlist.md` — REG-01/09
2. `issues/02-cancel-promotion.md` — REG-02 + 需求 REG-05/07（依赖 01）
3. `issues/03-admin-remove.md` — REG-07 + 需求 REG-06/07（依赖 02）
4. `issues/04-my-registrations.md` — REG-03/04（依赖 01）
5. `issues/05-registration-roster.md` — REG-05/06（依赖 01）
6. `issues/06-registration-notifications.md` — NOTIFY-01 报名/递补（依赖 01/02/03）
7. `issues/07-match-reschedule-cancel-notifications.md` — NOTIFY-01 改期/取消，M03 遗留（依赖 06）
8. `issues/08-registration-rate-limit.md` — 报名接口限流（依赖 01，可与 02–07 并行）

约束：单队 MVP；跨域只经 contracts 端口；跨域只读组合放 `query`，不被业务域反向依赖；名额以数据库行锁/条件更新为正确性保证，Redis 锁不是正确性保证；事务例外仅报名生命周期与 P-05 账号生命周期编排；通知只经 Outbox → Kafka，禁止同步写通知表；不改 `schema.sql`，不执行 SQL，不改 05 接口契约。
