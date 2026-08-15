# Agentic CI/CD 运行手册

## 当前能力级别

当前仓库配置处于 **Level 0：只读观察**：

- 可以读取带 `agent:queued` 的 GitHub Issue、代码、PR 和 CI 状态；
- 可以在临时 workspace 中形成计划和风险报告；
- 不允许修改代码、提交、推送、创建/更新 PR、发送邮件、合并或发布；
- `config/agentic-cicd/state-contract.json` 中的 capability flag 是当前能力权威事实。
- 每个 Issue 最多启动一个只读 observer turn；可信 complete hook 随后把内部 phase 置为 complete，后续轮询在创建 App Server 前短路，不产生重复模型调用。

能力升级必须通过受审 PR 更新合同、测试和本手册；仅扩大 GitHub token 权限不会自动扩大流程授权。

仓库已包含未来阶段路由实现，但 `local_workspace_write=false`，因此 implementer workspace-write 分支不可达。开放该能力前还必须落地隔离 gate runner；Supervisor 和 after hook 禁止直接执行 workspace 中的验证脚本。

## 当前准入状态

1. 远端 develop ruleset（`Protect develop`）已 active，六个 required contexts 与模板一致且无 bypass actor；仍需用合法和故意违规的 disposable Draft PR 验证实际 enforcement。
2. 历史 Gitleaks finding 已审计为未合并测试 fixture，并按精确 fingerprint 处置；最新 `develop` 的 Quality、Security（包含 `secret-scan`）和 Qodana 已绿色。
3. 固定 Symphony/Codex 运行时、真实独立 Reviewer turn 和 disposable Issue 端到端演练仍缺证据。在这些事项完成前保持 Level 0，不得开放分支、push 或 Draft PR。

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
- 将 `JSTORE_SYMPHONY_SOURCE` 设置为锁定提交的 Symphony 源码绝对路径。
- 将 `JSTORE_SYMPHONY_REPOSITORY_URL` 设置为只读 clone URL。
- 使用进程监管器保证单实例；不要同时启动两个指向同一仓库的 Supervisor。

### 开发 Kubernetes 目标环境

- 专用 CI/CD 试点优先部署到用户指定的开发 Kubernetes 集群；主机地址和运维身份保留在仓库外，不得把 SSH 私钥、kubeconfig 或登录材料提交到仓库或复制进 Pod。
- 集群中已有的单节点项目 Pod 只证明基础调度环境存在，不证明 PR #40、固定 Symphony 运行时、权限边界或恢复流程已经验收。
- Level 0 使用独立 namespace、单副本 Deployment、专用 ServiceAccount 和持久化状态目录；不得挂载生产 kubeconfig、生产数据库凭据或宿主机用户主目录。
- ServiceAccount 默认不授予集群级管理、Secrets 写入、生产 namespace、应用 Deployment 修改或任意 `exec` 权限。读取 GitHub 所需的短期 token 通过受控 Secret 注入，并在进入 Codex 子进程前清除。
- 实际执行 SSH 登录、namespace/ServiceAccount/PVC 变更、镜像导入、`kubectl apply`、扩缩容或 Secret 注入前，必须再次确认精确目标和操作；本节只记录选定的目标环境，不构成部署授权。
- 部署验证按“固定镜像来源与摘要 → Level 0 预检 → 单 Pod 启动 → 只读 smoke → 重启恢复 → 缩容为 0”执行，并保留命令、退出码、Pod UID、镜像摘要和脱敏日志。

### Linux 开发主机预检与验证

需要 Linux 交付证据时，在用户指定的远端 Linux 开发主机及其原生文件系统上运行；连接信息保留在仓库外。不得把 WSL 对 Windows 工作区的 `/mnt/*` 挂载路径作为全量质量门禁执行目录，避免跨文件系统扫描和 Gradle I/O 限制进度。验证应从精确 `origin/develop` 创建临时副本，只复制候选 diff，不修改远端长期工作目录。

部署前先运行只读预检。它只检查源码提交、安全祖先、工作树、Codex 精确版本和 Elixir 构建工具，不启动 Symphony、Codex thread 或模型 turn：

```bash
export JSTORE_SYMPHONY_SOURCE=/absolute/path/to/symphony
python3 scripts/check-agentic-cicd-runtime.py
```

随后使用隔离 Python 依赖运行 App Server smoke：

```bash
UV_CACHE_DIR="${TMPDIR:-/tmp}/j-store-uv-cache" \
  uv run --with-requirements requirements-quality.txt \
  python scripts/smoke-codex-app-server.py
```

smoke 会核对 `config/agentic-cicd/codex-app-server.lock.json` 中的精确版本、用同一二进制生成 v2 JSON schema、校验 Implementer/Reviewer 请求并完成初始化握手。它不会创建 thread 或发送模型 turn。

Gradle 验证必须使用 JDK 25。运行服务或 CI 时在受管环境中从实际 `java` 路径显式设置 `JAVA_HOME`，不要依赖交互 shell 的默认 JDK：

```bash
test "$(java -version 2>&1 | head -n 1)" != ""
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
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
2. 设置 `JSTORE_SYMPHONY_SOURCE`，确认 `python scripts/check-agentic-cicd-runtime.py` 和 App Server smoke 通过。
3. 确认真实模型 turn 的费用上限、模型、认证来源和审计归属已经批准；smoke 成功不代表已获模型调用授权。
4. 从可信 `origin/develop` 提取 `WORKFLOW.md` 到部署配置目录，记录其 blob SHA；不得运行候选分支版本。
5. 注入短期只读 GitHub token 和明确 workspace root。
6. 启动 Symphony，并开启只允许管理员访问的本地 dashboard/API。
7. 用一个不要求代码修改的 disposable Agent Goal Issue 验证读取和计划输出。
8. 连续观察两周，记录误调度、重复认领、恢复、turn 消耗和人工接管原因。

由于当前 `WORKFLOW.md` 禁止远端写，Workpad 在 Level 0 可以只写入部署端审计日志。开放 Issue comment write 后才回写唯一 `## Codex Workpad` 评论。

## 开发 Kubernetes 集成环境

内部开发集群 `jstore-dev-k8s` 承载首个 Level 0 实例。实际地址由操作者的 SSH/kubeconfig 管理，不写入仓库；部署边界和可复现证据见 `docs/spec/changes/agentic-cicd-kubernetes-level0/`。

当前 profile：

- kube context：`kubernetes-admin@kubernetes`，仅用于人工 bootstrap；不会挂入 Symphony Pod。
- namespace：`agentic-cicd`，与 `jstore`、`postgresql` 和 `monitoring` 隔离。
- 节点：`k8s-master`，单副本、单并发。
- 状态：专属 Local PV `/var/lib/jstore-agentic-cicd`，回收策略 `Retain`；不具备跨节点 HA。
- dashboard：ClusterIP `symphony:4000`，默认不创建 Ingress。
- dashboard bind：部署副本只在受信根 `WORKFLOW.md` 上增加 `server.host: 0.0.0.0`，使 Pod 探针和 ClusterIP 可访问；无 NodePort、LoadBalancer 或 Ingress。
- 首次 smoke：使用非秘密哨兵 token `level0-no-github-access`，GitHub 会拒绝请求，因此不能取得 Issue 或触发 Codex turn。
- NetworkPolicy执行器：Flannel保持不变，独立 `kube-router-firewall` DaemonSet只运行 firewall controller；固定镜像和回滚约束见 `deploy/kubernetes/agentic-cicd/network-policy-engine/`。`kube-network-policies`曾因返回流量兼容性在实机失败，不能恢复使用。
- Gate基础设施：`agentic-cicd-gates` namespace已启用 restricted Pod Security、ResourceQuota、LimitRange和 default-deny；`agentic-cicd/gate-dispatcher`只可在该 namespace管理 Job、观察 Pod和读取日志。Artifact Broker和正式 Dispatcher Deployment尚未部署，当前能力仍为 Level 0。

NetworkPolicy执行器使用独立入口部署；它会运行 preflight、server dry-run、两节点 rollout、跨节点 ingress/egress正反例以及现有业务回归，失败自动回滚：

```bash
./scripts/agentic-cicd-network-policy-deploy.sh \
  --context kubernetes-admin@kubernetes
```

人工 kill switch保留 Gate namespace和状态，只停止执行器并清理两个节点的 kube-router规则。脚本只接受当前 kube-proxy iptables且不存在其它 kube-router的拓扑，清理后验证 kube-proxy和 j-store健康：

```bash
./scripts/agentic-cicd-network-policy-rollback.sh \
  --context kubernetes-admin@kubernetes
```

从同步到该主机的受审候选执行：

```bash
./scripts/agentic-cicd-kubernetes-deploy.sh \
  --context kubernetes-admin@kubernetes \
  --symphony-source "$HOME/source/symphony"
```

脚本在创建固定 Local PV 目录和导入 containerd 镜像时调用 `sudo`；目标主机未配置免密 sudo，操作者需要在交互终端完成认证。不要把 sudo 密码写入命令、环境变量、仓库或日志。

部署脚本先确认 Symphony checkout 位于固定提交且工作树洁净，校验锁定的 phase-bridge patch SHA-256，并要求 j-store 控制器来源为完整且洁净的提交；随后以两个 revision 生成不可变镜像名。构建会清空 Docker 客户端中可能遗留的代理参数并使用官方软件源，但不修改主机全局代理。脚本导入本机 containerd、只创建或更新 `agentic-cicd` 资源和专属 Local PV、执行 server-side dry-run、等待新 Pod UID 并运行带运行时 revision 的 smoke。它不会读取 Secret、访问 `jstore`/`postgresql` namespace 或修改数据库。

复查状态：

```bash
./scripts/agentic-cicd-kubernetes-smoke.sh \
  --context kubernetes-admin@kubernetes \
  --image '<deploy 输出的不可变镜像名>' \
  --symphony-revision 8001b52e3062495a16e520e4ceaf8f9de868c4d0 \
  --controller-revision '<受审 j-store 完整提交 SHA>'

kubectl --context kubernetes-admin@kubernetes \
  -n agentic-cicd port-forward service/symphony 4000:4000 \
  --address 127.0.0.1
```

停止实例并保留 workspace/log：

```bash
./scripts/agentic-cicd-kubernetes-stop.sh \
  --context kubernetes-admin@kubernetes
```

重新运行 deploy 脚本会恢复单副本。删除 PVC、PV、namespace 或宿主机目录属于物料清理，不是普通停止动作，必须在审计日志后人工执行。

真实只读观察前，管理员需要为专用 GitHub App 生成短期 installation token，通过不入库的 overlay/Secret 注入 `JSTORE_SYMPHONY_GITHUB_TOKEN`，并移除哨兵值。不得把个人 token、管理员 kubeconfig 或宿主机 Codex 登录目录挂入 Pod。

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
