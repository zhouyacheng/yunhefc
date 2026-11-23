# 发布脚本加固（本批 1–4）

Type: task
Status: ready-for-agent
Blocked by: —

## 问题

1. `rc-acceptance-wizard.sh:187` 标注「只读检查；不修改生产」，Stage 9 实际连发 51 次错误登录、锁定 15 分钟；`:530` 仍检查「微信内完成授权」，与 P-74 小程序登录不符；`:500` grep 的日志路径与实际 volume 不一致。
2. `rc-acceptance-wizard.sh:491` gitleaks `--source .` 不扫 git 历史，清单 §6 #10 要求含历史。
3. `rc-deploy-prereq-wizard.sh:377` 使用 `ssh-keyscan` 且 `StrictHostKeyChecking=no`，与 issue 13「不用 ssh-keyscan」冲突。
4. `release.sh` 未校验 P-35/RUNBOOK §2.1 要求的 infra 双 manifest；冒烟中 `/c/auth/wechat/authorize` 的 404/401 断言无法区分「已删除」与「需登录」，应删除（06 §107）。

## 需求

- 1：抬头改为「默认只读；唯一会写生产的是 §6.9 锁定验证，需显式确认」；Stage 10 改为小程序真机登录；日志抽查改用 `compose logs`；破坏性步骤保留 confirm。
- 2：gitleaks 加 `--log-opts=--all`。
- 3：去掉 `ssh-keyscan` 与 `StrictHostKeyChecking=no`，改用固定 known_hosts 或用户确认的指纹。
- 4：`release.sh` 增加 `yunhefc-infra-kafka:3.7.2`、`yunhefc-infra-redis:6.2.24` 双 manifest 校验；删除 wechat 端点断言，保留 `mini/login` 空 code 400。

## 延后（见 04）

- 五个向导共享库重构、domain 正则与 env schema 漂移。
- `deploy.yml` rsync `--delete` 评估。

## Comments

- 2026-11-22：1–4 已实现。`release.sh` 增加 infra 双 manifest 校验并删除 wechat 断言；`rc-acceptance-wizard.sh` 修正只读文案、Stage 10、日志来源与 gitleaks 历史；`rc-deploy-prereq-wizard.sh` 去除 `ssh-keyscan` 与 `StrictHostKeyChecking=no`，改用可信 known_hosts 或用户粘贴指纹。`bash -n` 通过；未在生产演练。
- 2026-11-22 复审补：日志抽查改用 RUNBOOK §0 的 `compose_current logs`；Stage 10 的 RDS 备份项改为风险接受口径（未开启记 SKIP，不阻断）。
