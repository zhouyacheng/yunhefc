# 向导共享库与 rsync 清理（RC 后）

Type: task
Status: ready-for-agent
Blocked by: 02

## 问题

1. 五个向导复制同一段 169 行库（约 850 行）；domain 校验正则循环重复 4 次；env schema 三处来源已漂移（OSS endpoint hangzhou vs shanghai）。
2. `deploy.yml` 的 rsync 无 `--delete`，重跑同一 sha 会留陈旧快照文件。

## 需求

- 1 抽共享库前先确认 5 个向导的差异面；正则与 env schema 收敛为单一来源。
- 2 评估 `--delete` 是否会误删 `release.env` 与 `.deployed`；确认排除规则后再加。
