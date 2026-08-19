# Agentic CI/CD 运行手册

## 当前能力级别

当前仓库配置处于 **Level 0：只读观察**：

- 可以读取带 `agent:queued` 的 GitHub Issue、代码、PR 和 CI 状态；
- 可以在临时 workspace 中形成计划和风险报告；
- 不允许修改代码、提交、推送、创建/更新PR、写Issue comment/label、请求PR review、合并或发布；
- `config/agentic-cicd/state-contract.json` 中的 capability level 与 flag 是当前能力权威事实。Level 0 已明确区分 `bootstrap_local_workspace=true` 与保持关闭的 `local_workspace_write`、`freeze_local_candidate`、`run_isolated_gate`、`create_remote_branch`、push 和 PR 能力；本地可信 bootstrap 不再与远端建分支共用模糊字段。
- 每个 Issue 最多启动一个只读 observer turn；可信 complete hook 随后把内部 phase 置为 complete，后续轮询在创建 App Server 前短路，不产生重复模型调用。

能力升级必须通过受审 PR 更新合同、测试和本手册；仅扩大 GitHub token 权限不会自动扩大流程授权。

仓库已包含未来阶段路由、CandidateRevision 冻结和隔离 Gate Runner 实现，但 `local_workspace_write=false`、`freeze_local_candidate=false`、`run_isolated_gate=false`，因此 implementer workspace-write、冻结和 Gate 调度入口仍不可达。只有完成 Level 1 剩余准入、恢复和真实模型验收后才能通过单独受审变更开放；Supervisor 和 after hook 始终禁止直接执行 workspace 中的验证脚本。每个model turn的complete hook还必须回传由host phase context绑定的phase、role、head SHA和可选CandidateRevision；controller在写状态前校验该绑定并幂等消费session/thread/turn，禁止把延迟或重复Reviewer callback按回流后的implement phase重新分类。

## 当前准入状态

1. 远端 develop ruleset（`Protect develop`）已 active，六个 required contexts 与模板一致且无 bypass actor；仍需用合法和故意违规的 disposable Draft PR 验证实际 enforcement。
2. 历史 Gitleaks finding 已审计为未合并测试 fixture，并按精确 fingerprint 处置；最新 `develop` 的 Quality、Security（包含 `secret-scan`）和 Qodana 已绿色。
3. 固定 Symphony源码与不可变运行镜像、凭据轮换、无模型 exact-candidate Reviewer、四个恢复点、disposable Issue `#50`创建及GitHub-only credentialed Level 0 rollout已有证据；该Deployment随后已缩容为0。Issue仍为`agent:candidate`，当前集群没有Codex auth Secret，Codex-auth observer rollout、真实 observer/Independent Reviewer turn和端到端闭环尚未完成。在这些事项完成前保持 Level 0，不得开放分支、push 或 Draft PR。

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
| Issues | Read |
| Pull requests | Read |
| Actions / Checks | Read |
| Administration | None |
| Secrets / Environments / Deployments / Workflows | None |

迭代4的Contents/PR write与Issue label/comment write、迭代5的Draft -> Ready及可选review request均需分别批准并由独立机器能力控制。无论权限如何，自动化不得自动合并、approve或发布。

不要把 App private key 或长期 token 写入仓库、`WORKFLOW.md`、Issue、日志或 workspace。向 Symphony 注入 `JSTORE_SYMPHONY_GITHUB_TOKEN` 时，必须使用短期 installation token；运行版本必须能从 Codex 子进程中清除 GitHub token 环境变量及其别名。

## 主机准备

- 使用专用 Linux VM 或容器主机，与生产网络、生产数据库和生产凭据隔离。
- 安装 `git`、固定版本的 Symphony 运行时和稳定版`codex`；仓库不绑定单一Codex版本，启动前必须通过App Server v2兼容性smoke。
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

部署前先运行只读预检。它只检查源码提交、安全祖先、工作树、Codex稳定版输出和 Elixir 构建工具，不启动 Symphony、Codex thread 或模型 turn：

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

smoke 会按`config/agentic-cicd/codex-app-server.lock.json`接受当前已安装的稳定版Codex CLI，用同一二进制生成 v2 JSON schema、校验 Implementer/Reviewer 请求并完成初始化握手。它不会创建 thread 或发送模型 turn。构建入口把通过检查的实际版本写入镜像tag、label和source record，最终制品身份仍由完整镜像digest固定。

Gradle 验证必须使用 JDK 25。运行服务或 CI 时在受管环境中从实际 `java` 路径显式设置 `JAVA_HOME`，不要依赖交互 shell 的默认 JDK：

```bash
test "$(java -version 2>&1 | head -n 1)" != ""
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
export PATH="$JAVA_HOME/bin:$PATH"
./scripts/quality-gate.sh
```

## Level 2 disposable仓库本地预检

真实GitHub E2E前先运行纯配置预检。该命令只读取两个本地JSON文件，不读取token、不访问网络、不部署，也不改变权威能力合同：

```bash
python3 scripts/agentic-cicd-controller.py github-e2e-preflight \
  --repository '<owner/disposable-repository>' \
  --repository-url 'https://github.com/<owner/disposable-repository>.git' \
  --contract config/agentic-cicd/state-contract.level2-disposable.example.json
```

预检会拒绝`ddd-mall/j-store`、非规范仓库名、URL不一致、Level 0/1或不完整Level 2能力、终端能力开启以及required checks漂移。示例profile不是部署合同；默认运行镜像仍只包含权威Level 0合同。预检通过不构成GitHub App权限、token注入、模型调用、push、PR、Issue、Ready或review request授权。

取得精确disposable仓库目标后，可以从洁净、受审源码生成尚未部署的Level 2制品候选：

```bash
./scripts/agentic-cicd-controller-image-build.sh \
  --output-dir /absolute/reviewed/output \
  --symphony-source /absolute/path/to/pinned/symphony \
  --disposable-level2-repository '<owner/disposable-repository>'
```

构建入口不接受任意合同路径，只使用固定示例profile；它在Docker调用前生成并验证repository、HTTPS URL、capability level和合同摘要绑定。输出tag、labels、source record和最终digest区分Level 0与该Level 2候选。此命令只构建本地制品，不导入containerd、不写集群、不读取token、不访问GitHub。后续部署必须使用单独受审入口，把同一repository和digest写入专用overlay并再次取得外部写授权；不得直接复用Level 0部署脚本或手工覆盖Deployment环境变量。

人工核对构建输出中的source record和摘要后，可生成不写集群的部署候选。`--source-record-sha256`必须来自前一步受审证据，不得在同一命令中临时计算后即视为人工确认：

```bash
python3 scripts/agentic-cicd-level2-deployment-prepare.py \
  --source-record /absolute/reviewed/output/jstore-agentic-controller-<revision>-level2-<contract>-binding-<binding>.source.json \
  --source-record-sha256 '<reviewed-source-record-sha256>' \
  --repository '<owner/disposable-repository>' \
  --github-app-login '<app-slug>[bot]' \
  --reviewer '<reviewer-login>' \
  --output-dir /absolute/reviewed/level2-deployment-candidate
```

入口验证source record、archive SHA-256、SBOM/SLSA subject、完整镜像digest、合同/binding摘要、repository和handoff身份一致性，再生成只读`manifest.yaml`与`deployment-profile.json`。manifest固定使用`symphony-github-token`和`symphony-codex-auth`引用，不含Secret值；App bot login与reviewer作为非Secret的受审环境值，主容器和init container使用同一digest-qualified镜像。该命令只调用本地Kustomize渲染，不访问Kubernetes API，也不执行`apply`。后续镜像导入、Secret、server dry-run、Deployment rollout及GitHub写操作仍是独立授权步骤。

运行时不会把render成功当作凭据可用证明。`phase-context`只有在任务phase为`complete`且合同开启push后才读取GitHub运行输入；在任何candidate promotion、Git命令、GitHub adapter构造或Snapshot写入前，它要求token及GitHub签发expiry同时存在且剩余至少65秒，并按能力要求合法的App bot login和人工reviewer。任一输入缺失、过期、非法或身份冲突都立即失败；Git push和每个HTTP请求仍会再次检查token lease。该fail-closed行为只防止漂移配置产生部分副作用，不构成token权限或真实E2E验收。

## Level 2 disposable真实演练契约

本节固定GH-15与GH-16的执行边界，不构成外部写授权。开始前必须在仓库外形成一次性授权记录，至少写明：精确disposable `owner/name`、GitHub App bot与人工reviewer、controller和Symphony完整revision、镜像digest、Kubernetes context/namespace、允许的GitHub写操作、演练时间窗、最多Issue/branch/PR数量、token签发与到期时间，以及证据保留和远端清理决定。目标不得是`ddd-mall/j-store`或其大小写别名；任一字段变化都需要新的授权记录。

每个场景只接受同一授权记录绑定的GitHub API原始响应、TaskSnapshot、Supervisor脱敏日志和必要的Git命令输出作为证据。证据保存在仓库外的只读目录，记录采集时间、退出码、repository、Issue/PR number、base/head SHA、Pod UID和文件SHA-256；不得保存token、Authorization header、Secret内容、未脱敏远端正文或个人Codex认证。单元测试、fake transport、render结果、复选框和操作者口述不能替代以下远端事实：

| ID | 操作与故障注入 | 必须观察到的远端与持久状态 |
|---|---|---|
| `GH15-01` | 正常候选首次进入远端闭环，随后重放同一`phase-context` | 只有一个任务branch和一个以`develop`为base的Draft PR；PR number与head在Snapshot中一致；重放不增加PR、Workpad或review request |
| `GH15-02` | 当前head的一个required check失败，再由新候选修复 | 失败head始终保持Draft并形成候选失败路由；新head不复用旧CI、review或Ready回执，六个required contexts重新取证 |
| `GH15-03` | 当前head产生actionable review thread并完成返工 | thread未解决时保持Draft；返工产生新head并重新执行本地Gate、独立Reviewer、CI和review门禁；旧评论只作为audit证据 |
| `GH15-04` | `develop`在候选期间前移，分别覆盖可合并与冲突路径 | 可合并路径产生包含新base的新head且不force push；冲突路径恢复原workspace并返回实现阶段，不创建第二个branch或PR |
| `GH15-05` | 所有当前head门禁通过后转Ready，并使三个handoff信号中的至少一个暂时失败 | Ready只发生一次；至少一个有效远端回执后进入`human_review`；失败增强项独立重试，已成功信号不重复；三者全失败的对照场景保持handoff pending |
| `GH15-06` | 分别在push、Draft、Ready和handoff远端成功但本地保存前停止Supervisor，再用同一状态卷恢复 | 恢复后仍是同一branch、PR number和head；已存在远端事实以`observation`回执恢复，不重复远端副作用 |
| `GH15-07` | 用独立测试身份尝试绕过required checks和`develop`保护 | 违规push或不满足门禁的合入尝试被ruleset拒绝；controller自身没有approve、merge、release、deployment或workflow写调用 |
| `GH16-01` | 独立核对App安装范围、仓库权限和进程/文件边界 | App只安装到该disposable仓库；仅具备本闭环所需Metadata/Contents/Issues/Pull requests/Checks读取或写入，Administration、Secrets、Environments、Deployments和Workflows均无写权限；Codex环境、workspace、Snapshot和脱敏日志均搜索不到token |

场景必须按`GH15-01`至`GH15-07`顺序执行；每个故障注入都使用新的任务Issue，但总量不得超过授权记录。只有全部场景、`GH16-01`独立复核和证据SHA-256清单均通过，才能勾选GH-15/GH-16。真实验证失败时保留Draft和证据，先把Supervisor缩容为0；不得通过手工修正Snapshot、force push、关闭竞争PR、扩大App权限或跳过场景继续。删除Secret、关闭PR、删除branch、Issue/label清理和撤销App安装分别属于新的外部写操作，按授权记录中的清理决定执行。

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
3. 确认真实模型 turn 使用的模型、认证来源、审计归属和本次模型调用已经批准；smoke 成功不代表已获模型调用授权。
4. 从可信 `origin/develop` 提取 `WORKFLOW.md` 到部署配置目录，记录其 blob SHA；不得运行候选分支版本。
5. 注入短期只读 GitHub token 和明确 workspace root。
6. 启动 Symphony，并开启只允许管理员访问的本地 dashboard/API。
7. 用一个不要求代码修改的 disposable Agent Goal Issue 验证读取和计划输出。
8. 记录该次observer的误调度、重复认领、恢复、turn消耗和人工接管结果；连续两周只读观察按总任务迭代6执行。

由于当前 `WORKFLOW.md` 禁止远端写，Workpad 在 Level 0 可以只写入部署端审计日志。开放 Issue comment write 后才回写唯一 `## Codex Workpad` 评论。

## 开发 Kubernetes 集成环境

内部开发集群 `jstore-dev-k8s` 承载首个 Level 0 实例。实际地址由操作者的 SSH/kubeconfig 管理，不写入仓库；首个 Level 0 候选的历史边界和证据摘要见 `docs/spec/agentic-cicd/archive.md`，当前准入状态以总任务账本和机器合同为准。

当前 profile：

- kube context：`kubernetes-admin@kubernetes`，仅用于人工 bootstrap；不会挂入 Symphony Pod。
- namespace：`agentic-cicd`，与 `jstore`、`postgresql` 和 `monitoring` 隔离。
- 节点：`k8s-master`，单副本、单并发。
- 状态：专属 Local PV `/var/lib/jstore-agentic-cicd`，回收策略 `Retain`；不具备跨节点 HA。
- dashboard：ClusterIP `symphony:4000`，默认不创建 Ingress。
- dashboard bind：部署副本只在受信根 `WORKFLOW.md` 上增加 `server.host: 0.0.0.0`，使 Pod 探针和 ClusterIP 可访问；无 NodePort、LoadBalancer 或 Ingress。
- 首次 smoke：使用非秘密哨兵 token `level0-no-github-access`，GitHub 会拒绝请求，因此不能取得 Issue 或触发 Codex turn。
- NetworkPolicy执行器：Flannel保持不变，独立 `kube-router-firewall` DaemonSet只运行 firewall controller；固定镜像和回滚约束见 `deploy/kubernetes/agentic-cicd/network-policy-engine/`。`kube-network-policies`曾因返回流量兼容性在实机失败，不能恢复使用。
- Gate基础设施：`agentic-cicd-gates` namespace已启用 restricted Pod Security、ResourceQuota、LimitRange和 default-deny；`agentic-cicd/gate-dispatcher`只可在该 namespace管理 Job、观察 Pod和读取日志。Artifact Broker、正式 Dispatcher和离线 Gate Runner已完成双节点镜像导入、跨节点 Broker/Gate 正反例、凭据隔离和无模型 exact-candidate Reviewer 验收。机器合同仍保持 Level 0，直到剩余准入和端到端验收完成。

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

### Level 1 Gate Runner 物料与控制面

Gate Runner 只能从受审、洁净的 j-store 提交构建。构建入口运行完整质量门禁，固定 JDK 和 `kubectl` 下载摘要，并输出单平台 OCI archive、镜像 manifest digest 和 archive SHA-256：

```bash
./scripts/agentic-cicd-gate-image-build.sh \
  --output-dir /absolute/reviewed/output
```

将同一个 archive 复制到 master 和 worker1；在两个节点的交互终端分别执行同一导入命令。导入脚本先校验 archive SHA-256 及 OCI 布局，再调用 containerd；不得按节点重新构建或使用浮动 tag：

```bash
./scripts/agentic-cicd-gate-image-import.sh \
  --archive /absolute/reviewed/output/jstore-agentic-gate-<revision>.oci.tar \
  --sha256 '<build 输出的 archive SHA-256>' \
  --image-tag '<build 输出的 GATE_IMAGE_TAG>' \
  --image-ref '<build 输出的 GATE_IMAGE_REF>'
```

master 上的 Local PV 目录使用不同 Unix UID 和单向只读挂载隔离 request/receipt；必须由操作者以交互式 `sudo` 创建，并保留 setgid 使共享读取组稳定。不要把 sudo 密码放入命令或日志：

```bash
sudo install -d -o 10001 -g 11001 -m 2770 \
  /var/lib/jstore-agentic-candidates \
  /var/lib/jstore-agentic-gate-requests
sudo install -d -o 10002 -g 11001 -m 2770 \
  /var/lib/jstore-agentic-gate-receipts \
  /var/lib/jstore-agentic-artifact-leases
```

确认新的受审 Supervisor/controller 镜像已经导入 master 后，部署 credential-free Broker 和 Dispatcher。`--gate-image` 必须使用 build 输出的 `name:revision@sha256:digest`；该入口不会改变机器能力合同，也不会开启 GitHub 写权限：

```bash
./scripts/agentic-cicd-kubernetes-gate-deploy.sh \
  --context kubernetes-admin@kubernetes \
  --controller-image '<受审 Supervisor/controller name@sha256:digest>' \
  --gate-image '<build 输出的 GATE_IMAGE_REF>'
```

部署后仍先进行无模型 fixture smoke，核对 worker1 Pod 的 runtime image ID、单次 Broker lease、GateReceipt、无 Kubernetes token和到现有 workload 的拒绝流量。只有这些证据和独立评估通过后，才可在单独受审变更中开启 `local_workspace_write`、`freeze_local_candidate` 及 `run_isolated_gate`。

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

真实只读观察前，管理员需要为专用 GitHub App 生成短期 installation token，通过不入库的 overlay/Secret 注入 `JSTORE_SYMPHONY_GITHUB_TOKEN`，并移除哨兵值。模型认证使用单独的固定 Secret，只挂载受限 `auth.json`和经白名单裁剪的`config.toml`；不得把个人 token、管理员 kubeconfig 或宿主机 Codex 登录目录挂入 Pod。

### 短期 GitHub token 注入

仓库中的 `development-credentialed-observer` overlay把 Level 0哨兵替换为固定的 `agentic-cicd/symphony-github-token` Secret引用，并把 `agentic-cicd/symphony-codex-auth` 的`auth.json`和裁剪后`config.toml`只读挂载到专用 Pod HOME；它不生成 Secret、不包含秘密值，也不改变 capability合同。锁定的 Symphony源码必须通过 runtime preflight，证明 `JSTORE_SYMPHONY_GITHUB_TOKEN`、`GITHUB_TOKEN`、`GH_TOKEN`、`GITHUB_ENTERPRISE_TOKEN`和`GH_ENTERPRISE_TOKEN`在 Codex/App Server子进程边界全部清除。

先把专用 GitHub App生成的短期 installation token放入仓库外的 `0400`或`0600`文件。以下命令只执行 server-side dry-run，不写集群，也不打印 token：

```bash
./scripts/agentic-cicd-github-token-secret.sh \
  --context kubernetes-admin@kubernetes \
  --token-file /absolute/restricted/path/github-installation-token \
  --expires-at-epoch-seconds '<github-issued-expiration-epoch>' \
  --dry-run
```

只有凭据所有者确认受影响旧凭据已撤销/轮换，并再次取得对精确 context、namespace和 Secret的写授权后，才能执行：

```bash
./scripts/agentic-cicd-github-token-secret.sh \
  --context kubernetes-admin@kubernetes \
  --token-file /absolute/restricted/path/github-installation-token \
  --expires-at-epoch-seconds '<github-issued-expiration-epoch>' \
  --apply
```

Secret同时保存`token`与非秘密的`expires-at-epoch-seconds`。入口要求到期时间在执行时剩余5分钟至2小时；Deployment通过两个独立`secretKeyRef`注入，任一缺失或运行时剩余时间不足都会在Git/HTTP调用前返回`token_unavailable`。更新Secret不会更新已运行Pod的环境变量，后续Deployment rollout仍需单独授权。

### Codex API Key认证注入

当前受审入口只接受 Codex CLI API Key登录缓存和一份裁剪后的自定义provider配置：JSON对象必须且只能包含非空`OPENAI_API_KEY`；配置必须选择一个无内嵌凭据的HTTPS Responses provider，并只保留模型、推理强度、provider名称和URL。根据OpenAI官方认证说明，API Key适用于受信自动化并按Platform标准费率计费；`CODEX_API_KEY`只支持`codex exec`，不得用于Symphony的`codex app-server`。`auth.json`等同密码处理；原始`config.toml`也可能包含其他provider、MCP或策略配置，两者均不得提交、打印或通过挂载整个宿主机`.codex`目录注入。

模型调用前使用专用OpenAI Platform项目和可撤销API Key；request/token rate limit与spend controls作为外部运维防护。Supervisor记录可信turn、墙钟时间和input/output token用量，但不维护模型费率表或推算账单；`max_cost_microusd`保留为兼容的未接线字段，不得据此宣称存在实时费用硬上限。真实模型调用仍须按仓库统一治理规则取得外部付费操作授权。

先执行不写集群的server-side dry-run：

```bash
./scripts/agentic-cicd-codex-auth-secret.sh \
  --context kubernetes-admin@kubernetes \
  --auth-file /absolute/restricted/path/auth.json \
  --config-file /absolute/restricted/path/config.toml \
  --dry-run
```

只有凭据所有者对精确`kubernetes-admin@kubernetes / agentic-cicd / symphony-codex-auth`写入授权后，才能执行：

```bash
./scripts/agentic-cicd-codex-auth-secret.sh \
  --context kubernetes-admin@kubernetes \
  --auth-file /absolute/restricted/path/auth.json \
  --config-file /absolute/restricted/path/config.toml \
  --apply
```

两个Secret创建和Deployment rollout都是独立写操作。只有rollout也取得授权后，才从已提交且洁净的受审controller revision运行部署入口；`--credentialed-observer`只选择Secret引用overlay并检查`codex login status`，不启动模型、不开启Level 1或任何远程写能力：

部署入口会在任何`sudo`、镜像构建或集群写入前强制执行source-only runtime preflight，核对锁定Symphony HEAD、祖先、洁净源码和全部GitHub token清除边界；失败即停止。受审routing patch同时把模型可见的host-side `github_api`限制为GET，写方法在调用GitHub client前拒绝。

```bash
./scripts/agentic-cicd-kubernetes-deploy.sh \
  --context kubernetes-admin@kubernetes \
  --symphony-source "$HOME/source/symphony" \
  --credentialed-observer
```

GitHub token到期、模型Key撤销或演练结束后，先缩容 Supervisor为 0；删除或替换任一Secret属于单独凭据写操作，不能从停止授权中推定。不要直接 `kubectl apply -k` credentialed overlay，因为部署入口还负责绑定受审 controller镜像 digest、patch摘要、新 Pod UID和Codex登录就绪状态。

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
6. 已消费的GitHub人工交接幂等键。

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
2. Issue Workpad/状态标签与远端短分支、提交；
3. 唯一 Draft PR；
4. Draft -> Ready与可选PR review request；
5. 迭代5完成后重新切换只读profile观察两周，再按迭代6逐步开放低风险任务。

每次升级都必须先写负向权限测试和恢复测试。自动合并、自动发布、生产写入始终不在升级序列中。
