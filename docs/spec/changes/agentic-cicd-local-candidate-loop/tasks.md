# Agentic CI/CD Level 1 本地候选闭环任务

## 前置门：事实与权限

- [x] `LC-K01` 将目标集群事实固化为可执行 preflight：Kubernetes版本、两个节点、Flannel/CNI链、NetworkPolicy执行器、Pod Security、节点资源、Local PV拓扑和当前 Agentic RBAC。
  - 证据：preflight对当前“无 NetworkPolicy执行器”状态必须 FAIL；不得只检查 NetworkPolicy API存在。
- [x] `LC-K02` 为现有 Flannel设计独立的 policy-only NetworkPolicy执行器变更，优先评估固定 digest的 `kube-network-policies` addon，并与 kube-router firewall-only方案比较。
  - 证据：Kubernetes `v1.28.15`兼容性、内核/netfilter前置条件、现有 jstore策略影响、版本锁、安装/回滚步骤和独立网络评审。
  - 人工门：安装/卸载网络执行器是集群级写操作，不由本计划自动授权。
- [x] `LC-K03` 在维护窗口完成跨节点 NetworkPolicy正反例和现有 workload回归；default-deny必须真实阻断 ingress/egress，j-store、Redis、DNS、入口和监控必须保持健康。
  - 失败处理：立即回滚执行器并保持 Level 0；不得删除或放宽现有策略来伪造通过。
  - 2026-08-15证据：`kube-network-policies`候选因返回流量兼容性失败并回滚；`kube-router v2.10.0` firewall-only以固定 digest完成 2/2 rollout、master→worker1允许流量、独立 ingress/egress拒绝、业务健康、真实清理回滚和重装复验。
- [x] `LC-K04` 建立 `agentic-cicd-gates`隔离 namespace设计：restricted Pod Security、ResourceQuota、LimitRange、default-deny、Broker-only allow和 Dispatcher跨 namespace最小 RBAC。
  - 证据：Symphony SA继续无 token/无 Job权限；Dispatcher无 Secret、exec、PVC或其它 namespace权限；Gate Job不能访问 Kubernetes API和现有 workload。
  - 2026-08-16证据：实机 namespace、资源限制、default-deny、Broker-only allow和最小 RBAC全部生效；Symphony Pod无 ServiceAccount token且 `create jobs` 被拒绝；Gate的 network-admission init在候选代码启动前实际证明 API、TCP/UDP DNS和公网拒绝、Broker允许，同等受限的 worker1探针进一步证明 j-store `10.107.27.233:8080`、Redis `10.101.151.46:6379` 和 PostgreSQL `10.108.123.199:5432` 全部拒绝。
- [x] `LC-K05` 定义无 Registry时的节点镜像分发合同：同一 OCI archive按 digest导入 master/worker1 containerd，Pod使用 Never pull并核对 runtime image ID。
  - 证据：导入前来源/摘要、导入后两节点 image ID、Pod实际 image ID和回滚摘要一致；不得使用 `latest`或只存在于错误节点的本地 tag。
  - 2026-08-16证据：同一 Gate archive SHA-256 `fb21e77b...c09c1cc3` 在 master/worker1一致，两节点均将完整 manifest digest `sha256:30f48b2e...e152334` 标记为 CRI managed；network-admission、fetch和主 Gate容器的实际 image ID均与该 digest一致。上一已验证回滚目标 `sha256:e5269ad8...651487d` 以 Never-pull Job分别在 master和worker1实际启动并返回 0，两个 runtime image ID一致。

- [x] `LC-01` 更新总计划与机器能力合同，拆分本地 bootstrap、workspace write、candidate freeze、isolated gate 和远端写能力；增加非法组合负向测试。
  - 证据：合同、治理检查和 Level 0 回归测试一致；所有远端写能力仍为 false。
  - 2026-08-15证据：合同升级为 version 2 / capability level 0，`bootstrap_local_workspace=true`，其余本地写入和全部远端写入保持 false；治理测试拒绝旧 `create_branch`、Level 0本地写入和 Level 1远端写入组合。
- [ ] `LC-02` 完成此前暴露的远程环境/provider/GitHub 凭据轮换确认，并迁移到不进入交互 shell、日志或 Codex 子进程的短期注入方式。
  - 所有者：凭据所有者；仓库只记录脱敏处置结论，不记录值。
  - 阻塞：未确认前不得注入真实 token或启动模型 turn。
- [ ] `LC-03` 完成 I0-04 的 disposable Draft PR/ruleset 正反例演练计划并取得精确外部写授权。
  - 证据：合法分支方向、required checks、删除/force-push拒绝；演练对象和清理记录可审计。
  - 说明：该项是进入远程写入迭代 4 的硬门；不单独阻塞无远程写的 Snapshotter/Gate Runner 组件开发。

## 切片 A：Symphony 供应链资格

- [x] `LC-04` 审计锁定 Symphony commit及其依赖，形成单独的运行时升级/缓解候选。
  - 证据：上游 commit、漏洞与许可证清单、兼容性、回滚 commit和独立安全评审。
  - 2026-08-16证据：锁定上游 `8001b52e...4d0` 的原始依赖有27个Hex公告；审查后的39项依赖锁清除全部公告，许可证仅MIT、Apache-2.0和BSD-2-Clause，并记录兼容修正、既有部署回滚digest与独立复评。完整记录见 `evidence/2026-08-16-symphony-supply-chain-audit.md`。
- [x] `LC-05` 在指定 Linux 主机原生文件系统完成两段 patch顺序应用、`mix compile`、`mix test`、依赖审计和 Codex 精确版本 smoke。
  - 网络策略：优先既有代理；不可用时使用官方镜像/软件源；需要登录时停止并向用户申请。
  - 2026-08-16证据：提交 `89c7b462...be401` 的两阶段审计JSON绑定最新routing patch `b60be305...7535`、依赖锁、fixture和两个基础镜像；`mix compile --warnings-as-errors`、296项测试、Hex审计、escript构建和 `codex-cli 0.146.0` 精确smoke全部PASS。报告SHA-256为 `736c8a35...8954`。
- [x] `LC-06` 构建不可变 Supervisor 候选，固定 Symphony/j-store revision、patch hash、Codex 版本、基础镜像 digest和 WORKFLOW hash。
  - 证据：镜像 digest、OCI labels、SBOM/来源记录和无浮动 tag 检查。
  - 2026-08-16证据：controller `3a537df4...24f52` 构建的runtime manifest为 `sha256:305a2b8a...4d1a9`；实际labels逐项匹配，唯一SPDX与SLSA statement均绑定该digest，Docker archive和三份来源制品均记录独立SHA-256。详见 `evidence/2026-08-16-controller-image-build.md`。

## 切片 B：不可变候选身份

- [x] `LC-07` 先以测试定义 CandidateRevision，覆盖 tracked、untracked、删除、文件模式和重复冻结稳定性。
  - 证据：`tests/tooling/test_agentic_cicd_candidate.py`覆盖内容/模式变化、新增/删除、重复冻结、真实 index摘要不变，以及 Git归一化产生相同 tree时原始 worktree字节仍改变候选身份。
- [x] `LC-08` 实现使用临时 Git index 的可信 Snapshotter，生成 tree、规范化 archive和 host-owned manifest，不创建 commit/ref或修改 workspace index。
  - 证据：`CandidateSnapshotter`使用独立 `GIT_INDEX_FILE`和规范化 tar；受信控制器只在 validate阶段绑定 manifest，重复冻结幂等，新实现轮次清除旧绑定；完整仓库规模冻结 smoke通过。
- [x] `LC-09` 增加路径穿越、越界符号链接、submodule、special file、嵌套仓库、runtime metadata和 archive篡改负向测试。
  - 退出证据：Gate 与 Reviewer 只能物化同一 artifact SHA；任一候选变化使旧证据失效。
  - 证据：Snapshotter负向测试、host-owned runtime metadata校验、恶意 Git filter不执行、destination符号链接拒绝及 Reviewer恢复时逐文件/模式/符号链接 exact-archive复验均通过；GateRequest、GateReceipt、ReviewProposal和ReviewDecision绑定完整 CandidateRevision，新候选拒绝旧证据，并通过独立安全复评。

## 切片 C：隔离 Gate Runner

- [x] `LC-10` 定义 GateRequest/GateReceipt schema和原子状态，绑定 CandidateRevision、runner digest、命令策略、退出码、日志摘要、Job/Pod UID和唯一 gate ID。
  - 证据：严格 JSON schema与 Python 合同绑定完整 CandidateRevision、固定 OCI digest、可信命令 allowlist/策略摘要和运行时证据；TaskSnapshot以同目录原子替换持久化 request/receipt，拒绝非 allowlist runner/命令、旧候选、错 task、错 gate ID及重复消费。
- [x] `LC-11` 实现无模型 Gate Dispatcher及 fake Kubernetes测试，证明重复请求幂等、旧 receipt拒绝、基础设施/候选失败分流和重试预算独立。
  - 证据：`GateDispatcher`只通过 `GateJobClient`创建或恢复精确身份 Job；fake client证明重复 dispatch只创建一次，跨 task/旧 Job/UID/runtime image拒绝，非零退出产生候选 finding；基础设施失败无 candidate finding并保留 CandidateRevision，重试上限只能读取 host-owned机器合同。
- [x] `LC-12` 增加专用 Gate Job清单与 NetworkPolicy：无 ServiceAccount token、无 Secret/hostPath/socket、非 root、只读 rootfs、禁网、资源/时间/日志上限。
  - 2026-08-16证据：正式 Job固定到 worker1，两个 init均退出 0，主 Gate在无 token、默认禁网和只读受信入口下完成全量质量门禁；持久 receipt为 `PASS` / exit 0 / findings空，并记录 Job/Pod UID、日志摘要和精确 runner digest。
- [x] `LC-12A` 增加 master只读 Artifact Broker和短时一次性 fetch合同；Gate Job在 worker1校验 CandidateRevision archive SHA后离线执行，不挂载 Supervisor PVC。
  - 2026-08-16证据：worker1 fetch init通过 master Broker一次性 lease取得 artifact `0e257854...6d4edac`，校验并物化 CandidateRevision `ec915c1c...0c4358`；主容器只使用 emptyDir副本，未挂载 Supervisor state/PVC。
- [x] `LC-13` 将 validate 阶段改为只消费可信 GateReceipt；Supervisor、after hook和 controller进程不得执行 candidate命令。
  - 退出证据：恶意候选 fixture 无法读取控制面凭据、host state或联系集群/API，且失败只污染一次性 Job。
  - 2026-08-16证据：ValidatePhaseDriver、mailbox和GateReceipt消费接线已实现，可信进程无candidate subprocess入口；实机 Gate运行中替换 Dispatcher后复用原 Job/Pod UID、不重复调度，并生成 exact-candidate PASS receipt和 cleanup marker。恶意 CandidateRevision `ee392b53...acff17` 在主 Gate容器中主动验证 GitHub/Artifact/Kubernetes凭据、Symphony/gate/candidate host state均不可见，API、j-store、Redis、PostgreSQL均不可达；其非零退出只产生绑定新revision的单一 FAIL receipt，一次性 Job随后清理。

## 切片 D：Level 1 阶段接线

- [x] `LC-14` Reviewer从 CandidateRevision只读制品启动，ReviewProposal/TurnReceipt/ReviewDecision绑定同一 revision和独立 session。
  - 2026-08-16证据：独立复评发现并修复跨Issue receipt与同UID先chmod后篡改两个绕过路径；不可变controller digest `sha256:305a2b8a...84d1a9`部署后，从镜像内`/opt/jstore-agentic-controller`重跑同一CandidateRevision正反例。2,553个条目全部只读，直接写入和chmod后篡改均被拒绝，implementer session不能充当Reviewer，独立session生成绑定同一revision的PASS decision。聚焦55项测试、治理检查和镜像内复验均PASS；未启动模型或写正式Supervisor状态。
- [ ] `LC-15` 在所有前置门通过后，将候选部署合同的 `local_workspace_write`、`freeze_local_candidate` 和 `run_isolated_gate` 置为 true；远端写、邮件、合并、发布和生产写保持 false。
- [ ] `LC-16` 验证 implement/validate/review/complete 的单 turn路由、无第二 App Server、new-candidate失效、finding回流、两次修复和第三次熔断。
  - 当前进度：本地无模型102项组合合同测试已覆盖implement完成、validate重入单次调度、Gate PASS→review、Review FAIL回流、重复/延迟callback拒绝、历史ReviewDecision保留但exact-candidate失效、两次不同修复和第三次熔断，以及validate/complete不启动模型。两段patch在锁定Symphony源码上顺序apply后，使用单次构建且身份可审计的工具链镜像完成`mix compile --warnings-as-errors`、296项测试、Hex审计和escript；报告SHA-256为`87db57f4...a053`。最新镜像`sha256:7edcb88b...66bf6`已部署并完成Level 0、exact-candidate Reviewer及AC-LC-08四个恢复点复验；真实单turn/无第二App Server仍需LC-20/LC-21的凭据与模型授权，因此本项保持未关闭。
- [x] `LC-17` 运行聚焦测试、治理检查、镜像安全检查和 `./scripts/quality-gate.sh`，再进行独立 Product Steward/Security review。
  - 2026-08-16证据：最终revision上的16项candidate、23项runtime、21项Kubernetes/构建合同测试、102项无模型组合测试、治理检查和完整六阶段`./scripts/quality-gate.sh`均PASS；新routing patch的Symphony compile/test已PASS。独立评审阻塞中间候选后，controller `86480b1f...c2c4`统一隔离bootstrap与CandidateSnapshotter全部Git子进程环境，并构建不可变候选`sha256:7edcb88b...66bf6`；SBOM/provenance唯一subject均绑定该digest，镜像内真实freeze wrapper probe证明ambient凭据和Git状态不传播。最终扫描仍为178组/12组非`unimportant` critical；独立安全复评确认环境泄漏已关闭且12项必要触发条件不可达，身份链和AC-LC-09安全资格PASS。中间`cc26...`候选已作废；实际部署仍为`BLOCKED_BY_AUTHORITY`，不得据此提前关闭LC-16/LC-22。详见`evidence/2026-08-16-lc17-controller-image-security.md`。

## 切片 E：开发集群验收

- [x] `LC-18` 经精确授权部署新 digest；验证新 Pod UID、image ID、双 revision、WORKFLOW hash、capability和 Supervisor无 Kubernetes token。
  - 2026-08-16证据：Symphony、Broker和 Dispatcher已升级为 controller digest `sha256:305a2b8a...84d1a9`，运行时精确绑定 Symphony revision `8001b52e...4d0`、controller revision `3a537df4...24f52` 和 WORKFLOW SHA-256 `a8c18b98...fa5f`；当前Symphony Pod UID为 `4a8cdf05-2ba0-4715-ab63-bf399d0a126f`，runtime image ID与完整digest一致且零重启。Symphony Pod无 API token且不能创建 Gate Job，三个本地写能力和全部远程写仍为 false。
  - 2026-08-16/17加固复验：经精确授权导入并部署controller `86480b1f...c2c4` / digest `sha256:7edcb88b...66bf6`。Symphony、Broker和Dispatcher均产生新Pod UID且runtime image ID一致；双revision、routing patch `00af6b18...fbe2`、WORKFLOW `ca821efe...344d`、Level 0合同、无Secret及最小RBAC全部PASS。Symphony/Broker无Kubernetes token；Dispatcher只显式挂载3600秒、固定API audience的projected token。四点恢复演练后的当前Symphony Pod UID为`d1438178-c386-4109-a322-54569f8f809e`。详见`evidence/2026-08-16-lc18-lc22-hardened-runtime.md`。
- [x] `LC-19` 运行无模型跨节点 Gate Job smoke，验证 master Broker → worker1 fetch、NetworkPolicy真实隔离、receipt、超时/失败分类和不访问现有 `jstore`/数据库 workload。
  - 2026-08-16证据：同一 CandidateRevision的成功 Job产生 exact-identity PASS receipt并在前台删除后写 cleanup marker；早期创建/fetch故障被分类为基础设施失败，旧 runner的候选命令失败产生 FAIL finding；临时 60 秒策略真实触发 `DeadlineExceeded`，产生无 candidate finding的 `INFRASTRUCTURE_FAILURE` 并清理 Job，随后恢复 900 秒策略和新 Dispatcher UID；worker1同等受限探针确认 Broker可达时 j-store、Redis和 PostgreSQL仍不可达。
- [ ] `LC-20` 经精确授权注入短期只读 GitHub App token并创建/标记 disposable Issue；完成只读 observer 单 turn。
- [ ] `LC-21` 经模型费用授权完成本地候选成功路径：Implementer → CandidateRevision → Gate PASS → 独立 Reviewer exact-candidate PASS；GitHub 无远程候选分支或 PR。
- [x] `LC-22` 使用可信 fixture完成 Gate FAIL、Review FAIL、new revision、同根因熔断以及 implement完成后、等待Gate、Gate PASS后、等待review四个恢复点演练。
  - 2026-08-16/17证据：两个恶意new revision分别得到自己的FAIL receipt，本地正式合同测试覆盖Review FAIL、旧callback、历史decision和第三次熔断。新digest部署后，可信PVC fixture分别证明implement完成后恢复为validate/no-model、等待Gate时复用同一Job/Pod、PASS receipt持久化后且消费前重启仍保持snapshot/receipt/request/预算、等待review时复用唯一只读workspace和Reviewer decision。第四点使用Job UID `ee8f220b...717151` / Pod UID `c0be4e7d...b9846`，重启后才由正式ValidatePhaseDriver消费原receipt并进入review。当前fixture SHA-256为`bbf4aadf...a4865`，详见`evidence/2026-08-16-lc18-lc22-hardened-runtime.md`。
- [ ] `LC-23` 移除调度资格并缩容为 0，保留 PVC/日志；生成 `summary.md` 映射 AC-LC-01 至 AC-LC-12。

## Level 1 退出条件

只有 LC-K01 至 LC-K05、LC-01 至 LC-23及 LC-12A全部具有可复现证据，I3-04、I3-07、I3-08B和 I3-08C才能关闭。任一 NetworkPolicy未实际执行、现有 workload网络回归失败、供应链高风险、候选身份漂移、凭据隔离失败、第二 App Server或恢复重复副作用都阻止 Level 1完成。

Level 1 完成后仍不自动进入迭代 4；远端 branch/push/PR写入必须基于 I0-04证据、专用 GitHub App最小权限和新的人工批准单独规划。
