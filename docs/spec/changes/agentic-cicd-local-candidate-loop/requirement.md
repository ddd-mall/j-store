# Agentic CI/CD 本地候选闭环变更需求

## 背景

`develop@ab92f349c4949755d1a9dedef168f8f388f8ed23` 已包含 Kubernetes Level 0、可信阶段桥、单 turn 路由、exact-head GateReceipt 合同和不可变交付基础。当前运行能力仍为只读观察：`local_workspace_write=false`，不会产生候选修改，也尚未用唯一 Symphony 生命周期证明“实现—隔离验证—独立评审—返工—恢复”闭环。

现有实现还缺少三个进入本地写入前不可绕过的条件：可覆盖未提交文件的稳定候选身份、与 Supervisor/Codex 凭据隔离的确定性 Gate Runner，以及锁定 Symphony 运行时的完整构建与供应链验收。缺少这些条件时直接开放 workspace-write，会让门禁或 Reviewer 检查的内容与 Implementer 实际修改不一致，或让候选代码在持有控制面权限的进程中执行。

对目标开发集群的只读盘点进一步确认：集群由 `k8s-master` 和 `k8s-worker1` 两个节点组成，使用 Flannel VXLAN；当前没有已验证的 NetworkPolicy 执行器。`agentic-cicd` 强制 Pod Security `restricted`，不能依靠带 `NET_ADMIN` 的 init container 自行封网。Symphony 的 40Gi 状态卷是绑定 master 的 Local PV，而 worker1 更适合执行资源密集的 Gradle Gate。Level 1 因此必须增加网络策略执行能力和跨节点只读候选分发，不能把现有 NetworkPolicy 对象或 Local PV 当作已满足隔离的证据。

## 目标

在开发 Kubernetes 集群中完成 **Level 1：本地候选闭环**：一个经人工准入的 disposable Issue 由唯一 Symphony Supervisor 驱动，在隔离 workspace 中产生本地候选，经无控制面凭据的 Gate Runner 验证，再由独立只读 Reviewer 对同一不可变候选给出决定；失败 finding 可返回下一轮实现，Supervisor 重启后能够从可信状态恢复。

Level 1 的终点仍是本地证据和人工确认，不创建远程分支、提交、PR，不发送邮件，不合并、发布或写生产。

## 范围

- 将“本地 workspace 分支”“workspace 内容写入”“候选冻结”和“远端分支写入”拆成独立机器能力，消除当前 `create_branch=false` 与可信 bootstrap 创建本地分支之间的语义漂移。
- 由可信控制器冻结包含 tracked、untracked、删除、文件模式和符号链接策略的不可变候选身份，不修改 workspace index，不创建远端 ref。
- 建立隔离 Gate Runner；候选命令不得在 Symphony、hook、控制器或持有 GitHub/Kubernetes 凭据的进程中直接执行。
- 在不替换现有 Flannel CNI 的前提下，为集群增加并验证标准 NetworkPolicy 执行能力；启用前审计现有策略并准备对当前 workload 的回归和回滚。
- 保留 Symphony 状态和 workspace 在 master Local PV；通过无凭据、只读 Artifact Broker 将不可变 CandidateRevision 提供给 worker1 Gate Job，不跨 namespace 挂载 Supervisor PVC。
- GateReceipt 绑定候选身份、可信命令集、runner 镜像摘要、退出码和日志摘要，只允许 exact-candidate PASS 进入 review。
- Reviewer 使用独立 Symphony session 和只读候选副本，ReviewProposal 继续由可信 TurnReceipt 绑定为 ReviewDecision。
- 完成锁定 Symphony 源码的完整编译、测试、依赖漏洞处置和不可变镜像验证。
- 在 disposable Issue 上完成成功路径、finding 返工、熔断和重启恢复演练。
- 更新总迭代计划、运行手册和最终证据摘要。

## 非目标

- 不开放远端 branch、commit push、Draft PR、Draft -> Ready 或 Issue 自动写入。
- 不授予 GitHub Administration、Actions/Workflow write、Secrets、Deployments、生产 namespace 或数据库权限。
- 不自动合并、发布、部署业务应用或执行数据库迁移。
- 不提高并发；同一仓库仍只允许一个 Supervisor 和一个活跃 Agent。
- 不在本阶段实现邮件通知、跨仓库事务或多 Supervisor 高可用。
- 不把组件测试、Pod Ready 或一次模型自述当作端到端完成证据。

## 验收标准

### AC-LC-01 能力语义收敛

机器合同必须分别表达本地 workspace bootstrap、本地内容写入、候选冻结、远端分支/push/PR 等能力。Level 1 只能开启前三项；远端写入、邮件、合并、发布和生产写入必须保持关闭。合同测试必须拒绝会隐式连带开启远端能力的配置。

### AC-LC-02 不可变候选身份

可信 Snapshotter 必须从实际 workspace 生成 `CandidateRevision`，至少绑定 base SHA、Git tree SHA、策略摘要和制品摘要。它必须覆盖 tracked、untracked、删除和可执行位，不使用工作区 index，不跟随越界符号链接，并拒绝 special file、嵌套仓库、路径穿越和运行时 metadata。相同内容产生相同身份，任一字节或模式变化都产生不同身份。

### AC-LC-03 隔离 Gate Runner

候选验证必须在独立、一次性执行环境中运行。执行环境不得持有 GitHub token、Kubernetes API token、Supervisor state、宿主机目录或生产网络访问；默认禁网、非 root、只读根文件系统、最小可写临时目录、资源/时间上限和 `automountServiceAccountToken: false`。Supervisor、hook 和可信控制器不得直接执行候选脚本。

在目标集群中，默认禁网必须由已安装且通过正反例验证的 NetworkPolicy 执行器证明，不能仅以存在 `NetworkPolicy` 对象宣称完成。策略只允许可信 fetch init访问 Artifact Broker；候选主容器不得取得下载凭据，且除已过期的只读 Broker端点外不得访问 Kubernetes API、DNS、其它 namespace、节点或公网。

### AC-LC-04 可信 GateReceipt

GateReceipt 必须由可信 dispatcher 产生并原子持久化，绑定 Issue、CandidateRevision、命令策略摘要、runner 镜像 digest、开始/结束时间、退出码、日志摘要和唯一 gate ID。旧候选、重复 gate ID、缺字段、非 allowlist runner 或模型生成的 receipt 必须被拒绝。

### AC-LC-05 单一 Symphony 生命周期

每次 invocation 仍最多一个 turn；implement 和 review 由同一 Symphony Orchestrator 分阶段重新调度，validate/complete 在创建 App Server 前短路。任何 hook、Gate Runner 或 Reviewer 工具都不得启动第二个 `codex`/App Server。

### AC-LC-06 独立 exact-candidate 评审

Reviewer 必须使用不同于 Implementer 的可信 session，以只读方式检查 Gate PASS 对应的同一 CandidateRevision。候选变化立即使 GateReceipt 和 ReviewDecision 失效。ReviewProposal 不得携带或伪造 session、thread、turn 或 gate 身份。

### AC-LC-07 返工、预算与熔断

Gate FAIL 或 Review FAIL 必须形成稳定根因 finding 并回到 implement。相同根因只允许两次有实质差异的本地修复，第三次进入 `agent:fused`；基础设施重试独立计数，不消耗语义修复预算。

### AC-LC-08 可恢复性

必须在 implement 完成后、等待 gate、gate PASS 后和等待 review 四个点执行停止/重启演练。恢复后不得重复模型 turn、Gate Job 或 Reviewer 决定；现有 CandidateRevision、turn receipt、gate ID 和预算计数保持一致。

### AC-LC-09 Symphony 供应链门禁

部署候选必须从审查过的 Symphony 完整 commit 和洁净 j-store controller commit 构建，完整 `mix compile/test` 通过。已知网络依赖漏洞必须升级、缓解或由人工明确拒绝上线；不得仅因私有开发网络降低风险评级。镜像必须固定两个源码 revision、补丁摘要、Codex 版本和基础镜像 digest。

### AC-LC-10 不可变部署与回退

部署必须以完整镜像 digest 和受信 WORKFLOW hash 产生新 Pod UID；旧 Pod 不得继续提供新版本证据。smoke 必须验证 phase routing、单 turn、revision、capability 和无凭据 Gate Job。

Supervisor 继续固定到 master；Gate Job固定到 worker1并设置不高于 4 CPU/8Gi 内存的首轮上限、active deadline和并发 1。Candidate archive经内容摘要和一次性下载能力传递，不复制 GitHub token、Supervisor state或 master Local PV。

在 Registry 尚未可用时，Supervisor、Dispatcher/Broker、Gate Runner和NetworkPolicy执行器镜像必须构建/获取一次并以 OCI archive按完整 digest分别导入目标节点 containerd；Pod使用 `imagePullPolicy: Never`和可验证的本地 digest引用。master到worker1的SSH导入属于受控部署动作，执行前仍需精确授权。回退通过缩容 Supervisor/Dispatcher为 0、停用 Gate调度或恢复上一已验证 digest，保留 PVC和审计状态。

### AC-LC-11 Disposable Issue 端到端证据

经精确外部写授权创建的低风险 disposable Issue 必须完成：人工准入、可信 develop 基线、本地实现、候选冻结、Gate PASS、独立 Reviewer exact-candidate PASS、Supervisor 重启恢复和人工终止。GitHub 上不得出现自动创建的远程分支、提交或 PR。

### AC-LC-12 退出审计

最终摘要必须记录 base/candidate/runtime identity、turn/gate/review ID、命令及退出码、重试与预算、Pod/Job UID、脱敏日志摘要、恢复结果和残余风险。删除 Issue workspace、PVC、namespace、镜像或远端对象仍需单独授权。

## 质量目标

- **安全性**：不可信候选执行环境与 GitHub/Kubernetes/Supervisor 凭据完全隔离；模型不能伪造阶段、候选、gate 或评审身份。
- **可靠性**：同一 CandidateRevision 的 gate 和 review 副作用幂等，重复调度不会重复执行。
- **可恢复性**：单实例 Supervisor 在五分钟内恢复到停止前阶段，且不丢失预算和 finding。
- **可审计性**：每次模型、gate、review 和状态转换都有稳定身份与内容摘要。
- **性能与成本**：并发保持 1；单任务沿用现有 turn、时间和费用上限；Gate Job 必须有 active deadline 和资源上限。
- **可运维性**：kill switch、缩容为 0 和上一 digest 回退均可在不删除状态的情况下执行。

## 人工审批点

以下动作不由本规格自动授权：

- 升级或替换固定 Symphony commit、基础镜像或外部依赖；
- 轮换/注入 GitHub App、provider 或集群凭据；
- 创建/标记 disposable Issue；
- 在开发集群创建 Gate Runner RBAC、Deployment/Job、Secret 或执行 rollout；
- 启动真实模型 turn及其费用；
- 推送分支、创建 PR、转 Ready、合并、发布或清理持久状态。
