# Agentic CI/CD 运行手册

## 当前能力级别

当前仓库配置处于 **Level 0：只读观察**：

- 可以读取带 `agent:queued` 的 GitHub Issue、代码、PR 和 CI 状态；
- 可以在临时 workspace 中形成计划和风险报告；
- 不允许修改代码、提交、推送、创建/更新 PR、发送邮件、合并或发布；
- `config/agentic-cicd/state-contract.json` 中的 capability flag 是当前能力权威事实。

能力升级必须通过受审 PR 更新合同、测试和本手册；仅扩大 GitHub token 权限不会自动扩大流程授权。

## 上线前硬阻塞

1. 当前远端没有 active `develop ruleset`。管理员必须按 `.github/rulesets/README.md` 应用并验证模板。
2. 当前 `develop` push 的 `secret-scan` 仍因历史发现失败。必须先审计、必要时轮换，再由人批准历史处置方式。
3. 在上述两项完成前，Supervisor 只能运行只读观察，不得开放分支、push 或 Draft PR。

## Symphony 来源

使用 `config/agentic-cicd/symphony.lock.json` 固定的 OpenAI Symphony Elixir reference implementation。该实现属于预览软件，只能在可信、隔离的非生产环境评估。

部署端必须：

1. 克隆 `openai/symphony`；
2. checkout 锁文件中的完整 commit；
3. 验证该 commit 包含 `required_ancestor_commits`；
4. 从源码构建并保留来源和校验记录；
5. 禁止使用浮动 `main`、未校验下载或自动升级。

## GitHub App

推荐创建仅安装到 `ddd-mall/j-store` 的专用 GitHub App。只读观察阶段的目标权限是：

| 权限 | Level 0 |
|---|---|
| Metadata | Read |
| Contents | Read |
| Issues | Read；如需写 Workpad，单独批准 Write |
| Pull requests | Read |
| Actions / Checks | Read |
| Administration | None |
| Secrets / Environments / Deployments / Workflows | None |

后续 Contents/PR write、Issue label/comment write、Draft -> Ready 均需在对应迭代单独批准。无论权限如何，自动化不得自动合并、approve 或发布。

不要把 App private key 或长期 token 写入仓库、`WORKFLOW.md`、Issue、日志或 workspace。向 Symphony 注入 `JSTORE_SYMPHONY_GITHUB_TOKEN` 时，必须使用短期 installation token；运行版本必须能从 Codex 子进程中清除 GitHub token 环境变量及其别名。

## 主机准备

- 使用专用 Linux VM 或容器主机，与生产网络、生产数据库和生产凭据隔离。
- 安装 `git`、固定版本的 Symphony 运行时和兼容的 `codex`。
- 创建仅供该服务使用的 workspace 和 log 根目录，禁止使用仓库根或用户主目录作为递归清理目标。
- 将 `JSTORE_SYMPHONY_WORKSPACE_ROOT` 设置为显式绝对路径。
- 将 `JSTORE_SYMPHONY_REPOSITORY_URL` 设置为只读 clone URL。
- 使用进程监管器保证单实例；不要同时启动两个指向同一仓库的 Supervisor。

### Ubuntu / WSL 开发预检

本仓库当前在 WSL Ubuntu 验证。交互环境中的 Codex 安装位于 `~/.bun/bin`；服务环境不能依赖 Windows PATH 透传，应显式提供 Ubuntu 原生路径：

```bash
export PATH="$HOME/.bun/bin:$HOME/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
python3 scripts/smoke-codex-app-server.py
```

smoke 会核对 `config/agentic-cicd/codex-app-server.lock.json` 中的精确版本、用同一二进制生成 v2 JSON schema、校验 Implementer/Reviewer 请求并完成初始化握手。它不会创建 thread 或发送模型 turn。

本机 Gradle 验证使用 Ubuntu zsh 配置中记录的 JDK 25：`JAVA_25=/usr/share/java/jdk-25.0.1`。运行服务或 CI 时应在受管环境中显式设置 `JAVA_HOME`，不要依赖交互 shell 的默认 JDK：

```bash
export JAVA_HOME=/usr/share/java/jdk-25.0.1
export PATH="$JAVA_HOME/bin:$PATH"
./scripts/quality-gate.sh
```

## 标签初始化

创建以下标签前先确认不存在同名但含义不同的标签：

- `agent:candidate`（Issue Form 自动添加，只表示待人工分诊，不会触发执行）
- `agent:queued`
- `agent:waiting-ci`
- `agent:human-review`
- `agent:blocked`
- `agent:fused`
- `agent:cancelled`
- `risk:human-approval`

创建标签属于 GitHub 外部写操作，需管理员明确批准。本仓库不会通过 CI 自动创建或修改标签。

Agent Goal Issue Form 只能自动添加 `agent:candidate`。仓库所有者完成身份、范围和风险分诊后，才可人工替换为 `agent:queued`；公共仓库提交者不能仅通过创建 Issue 触发执行。

## 启动只读观察

1. 确认 `python scripts/check-agentic-cicd.py` 通过。
2. 确认 runtime commit 与 `symphony.lock.json` 一致，并运行 App Server smoke。
3. 确认真实模型 turn 的费用上限、模型、认证来源和审计归属已经批准；smoke 成功不代表已获模型调用授权。
4. 从可信 `origin/develop` 提取 `WORKFLOW.md` 到部署配置目录，记录其 blob SHA；不得运行候选分支版本。
5. 注入短期只读 GitHub token 和明确 workspace root。
6. 启动 Symphony，并开启只允许管理员访问的本地 dashboard/API。
7. 用一个不要求代码修改的 disposable Agent Goal Issue 验证读取和计划输出。
8. 连续观察两周，记录误调度、重复认领、恢复、turn 消耗和人工接管原因。

由于当前 `WORKFLOW.md` 禁止远端写，Workpad 在 Level 0 可以只写入部署端审计日志。开放 Issue comment write 后才回写唯一 `## Codex Workpad` 评论。

## 停止与 kill switch

- 首选从 Issue 移除 `agent:queued` 或关闭 Issue，使任务失去调度资格。
- 全局 kill switch 由部署端停止 Supervisor 并禁用自动重启；不得靠删除 workspace 表示停止。
- 停止后保留 Issue、branch、PR、日志和当前 workspace，先审计再决定清理。
- 发现越权、凭据进入子进程、路径异常或意外远端写入时立即停止，轮换受影响凭据并创建安全事件记录。

## 恢复

重启后按以下优先级重建事实：

1. Issue 是否打开且仍带调度标签；
2. 唯一 Workpad 或部署审计记录；
3. GitHub 上是否存在对应开放 PR 和 head branch；
4. 本地 workspace 的 remote、branch、base/head SHA；
5. 最新 head SHA 对应的 checks 和 review；
6. 已消费的通知幂等键。

任何来源冲突都标记 `agent:blocked` 并转人工；不得通过创建第二个分支或 PR 绕过冲突。

## 审计与日常检查

- 每日确认没有双活 Supervisor、未知 workspace、重复 PR 或 capability 漂移。
- 每周检查 runtime lock 与上游安全公告；升级仍需单独 PR 和验证。
- 每季度以及 required check 更名时核对远端 ruleset。
- 记录每个任务的 base/head SHA、角色、命令退出码、finding 根因、语义修复次数、基础设施重试和终止原因。
- 日志不得保存 token、Issue 中疑似秘密或未脱敏生产信号。

## 升级到写入阶段

只有 `docs/spec/agentic-cicd/tasks.md` 对应迭代的退出条件满足后，才能按顺序开放：

1. 本地 workspace 写入；
2. 远端短分支和提交；
3. 唯一 Draft PR；
4. Draft -> Ready；
5. 白名单邮件通知。

每次升级都必须先写负向权限测试和恢复测试。自动合并、自动发布、生产写入始终不在升级序列中。
