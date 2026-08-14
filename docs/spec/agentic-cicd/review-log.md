# Agentic CI/CD 评审与验证记录

## 迭代 1：控制面与只读运行骨架

### 候选身份

- 分支：`codex/agentic-cicd-orchestration`
- 基线：`origin/develop@daf184ab9bb3f3bf811ae2158de704df6762b2a8`
- 范围：规格、机器合同、GitHub Issue Form、只读 `WORKFLOW.md`、运行手册、确定性校验和合同测试。
- 未执行外部写入：未创建标签、Issue、GitHub App、ruleset、commit、push 或 PR。

### TDD 证据

1. 先添加 `tests/governance/test_agentic_cicd_contract.py`。
2. 首次执行产生 1 个 FAIL 和 6 个 ERROR，均因 `state-contract.json`、`symphony.lock.json`、Issue Form、`WORKFLOW.md`、runbook 和校验器尚不存在。
3. 完成最小实现后，聚焦测试为 8/8 PASS。
4. 额外加入“Issue Form 不得自动应用调度标签”的负向合同；测试先因 `agent:queued` FAIL，随后将 intake 标签改为 `agent:candidate` 并通过。

### 执行证据

| 检查 | 结果 | 说明 |
|---|---|---|
| `python -m unittest tests/governance/test_agentic_cicd_contract.py` | PASS | 8 tests |
| `python -m unittest discover -s tests/governance -p 'test_*.py'` | PASS | 30 tests |
| `python -m unittest discover -s tests/tooling -p 'test_*.py'` | PASS | 20 tests |
| `bash scripts/check-agent-governance.sh` | PASS | 包含 Agentic CI/CD 合同校验 |
| `python scripts/check-agentic-cicd.py` | PASS | 状态、权限、checks、runtime lock 和 Issue/Workflow 合同一致 |
| Issue Form 与 `WORKFLOW.md` YAML 解析 | PASS | 使用 PyYAML 6.0.3 |
| `git diff --check` | PASS | 无 whitespace error |
| `./scripts/quality-gate.sh` | PASS | 在 WSL Ubuntu、Oracle JDK 25.0.1 下完成全部六阶段；Python/spec/governance/tooling 78 tests PASS，Spotless、Licensee、Gradle 全量测试和 `verifyLicenseArtifacts` 均完成；最后两个 Gradle daemon 状态均为 0，未发现失败测试 XML |

### 环境说明

- Windows 默认 shell 调用的 `bash` 实际进入 WSL Ubuntu；Windows JDK 不能作为 Linux `gradlew` 的 `JAVA_HOME`。
- Ubuntu 登录 zsh 的 JDK 定义来自环境同步前保留的 `~/.zshrc.before-env-sync`，其中 `JAVA_25=/usr/share/java/jdk-25.0.1`；本次最终质量门禁显式使用该路径。
- 曾尝试安装用户目录 Temurin 25.0.4 以绕过无 sudo 环境，但发现既有 JDK 25 后未用于最终证据；该文件位于用户目录，不属于仓库候选。

### 自审结果

- PASS：Issue Form 不会让公共仓库任意提交者直接触发执行。
- PASS：Level 0 同时使用机器 capability flag、只读 sandbox 和 workflow 明文禁令限制写入。
- PASS：Symphony 固定到完整、已验证签名的 commit `8001b52e3062495a16e520e4ceaf8f9de868c4d0`，该提交包含 GitHub/GitLab token alias 清除修复。
- PASS：required checks 与 `develop` ruleset 模板精确一致。
- PASS：自动 approve、merge、release 和 production write 均为 false。
- SKIPPED：尚未由独立 Reviewer 对当前候选 SHA 给出最终批准；迭代 1 是配置骨架，合并前仍需独立或人工 review。

### 未决外部事项

1. 远端 `develop` ruleset 尚未实际启用。
2. `develop` push 的历史 `secret-scan` 仍失败，需要凭据所有者/管理员审计和决定处置方式。
3. 尚未创建 GitHub App、标签或部署 Symphony；这些外部写入没有从本次实现授权中推定。

### 结论

迭代 1 的仓库内退出条件满足，可以进入不依赖远端写权限的迭代 2。Level 0 能力保持不变，不能据此开启 push、Draft PR 或邮件。

## 迭代 2：Workspace 与任务状态协调器

### TDD 证据

1. 先添加 `tests/tooling/test_agentic_cicd_coordinator.py`，首次因 `agentic_cicd` 包不存在而 ERROR。
2. 添加状态协调器和 workspace 管理器后，首批 11 tests PASS。
3. 新增篡改 workspace metadata 与大小写分支漂移负向测试，先得到 2 FAIL；加入 issue/base/branch 元数据验证并统一 `codex/gh-*` 后通过。
4. 新增受限 workspace 清理测试，先因 `remove` 不存在得到 2 ERROR；实现精确路径、可信 metadata、dirty-worktree 拒绝和保留分支后通过。
5. 新增非 `queued` 状态不可 claim 的负向测试，并将唯一 claimable state 固化到机器合同。

### 已实现能力

- `scripts/agentic_cicd/coordinator.py`
  - 合同驱动的状态转换和互斥 claim；
  - 同一根因最多两个不同 strategy fingerprint，第三个进入 `fused`；
  - 基础设施重试独立计数，达到上限进入 `blocked`；
  - turn、墙钟时间和费用预算；
  - host kill switch、原子快照和幂等键。
- `scripts/agentic_cicd/workspace.py`
  - 仅接受规范 `GH-<positive-number>`；
  - 每次新建前 fetch 并锁定真实 `refs/remotes/origin/develop`；
  - 生成符合仓库策略的 `codex/gh-<number>-<slug>`；
  - 从可信 metadata 恢复并验证 Issue、base SHA、branch 和实际 worktree branch；
  - 只允许移除精确、无用户改动的 Issue workspace，保留 local branch 供审计。

### 聚焦验证

| 检查 | 结果 |
|---|---|
| `python -m unittest tests/tooling/test_agentic_cicd_coordinator.py` | PASS，15 tests |
| 与迭代 1 合同测试组合执行 | PASS，24 tests |
| Ubuntu `python3 -m unittest discover -s tests/governance -p 'test_*.py'` | PASS，31 tests |
| Ubuntu `python3 -m unittest discover -s tests/tooling -p 'test_*.py'` | PASS，35 tests |
| Ubuntu `bash scripts/check-agent-governance.sh` | PASS |
| `git diff --check` | PASS |
| Ubuntu `./scripts/quality-gate.sh` | PASS；spec-dev 28、governance 31、tooling 35 tests，Spotless、Licensee、Gradle regression 和 53 个 JAR license 验证全部通过 |

### 边界与残余项

- Level 0 capability flag 仍禁止创建真实分支、push、PR 和邮件；协调器当前没有生产 CLI，不会被 `WORKFLOW.md` 调用。
- 本轮验证使用临时 Git 仓库，没有清理用户真实 workspace 或分支。
- 真实 GitHub/Symphony 恢复和进程故障注入尚未执行，保留在 I4/I6。
- 独立 Reviewer 最终批准仍未执行；实现者自审不能替代该批准。

## 迭代 3：Codex 协议与独立评审机制

### TDD 与实现证据

1. 先添加 App Server、IterationPacket、ReviewDecision、独立会话和 exact-head PASS 测试，首次因 `agentic_cicd.app_server` 不存在而 ERROR。
2. 实现 JSONL client 和结构化协议后首批 9 tests PASS；随后增加 scalar/array 类型负向合同，测试先 FAIL，再加入严格运行时类型检查后通过。
3. 增加 host-owned `head_sha`/session identity 校验，拒绝模型自报的其他 reviewer 身份或候选 SHA。
4. ReviewDecision 写入原子 TaskSnapshot；恢复后只对原 head SHA 保持 PASS，新 head 查询自动为 false。
5. 固定 `codex-cli 0.146.0` 后，用该二进制动态生成 v2 schema，校验 Implementer/Reviewer thread 与 review turn 参数，并完成真实 `initialize`/`initialized` 握手。

### 当前聚焦验证

| 检查 | 结果 |
|---|---|
| `python3 -m unittest tests/tooling/test_agentic_cicd_protocol.py` | PASS，12 tests |
| coordinator、protocol、合同测试组合 | PASS，35 tests |
| `bash scripts/check-agent-governance.sh` | PASS |
| `python3 scripts/smoke-codex-app-server.py` | PASS；固定版本、动态 v2 schema 和 Linux 初始化握手 |
| Ubuntu `./scripts/quality-gate.sh` | PASS，395 秒；spec-dev 28、governance 31、tooling 47 tests，Spotless、Licensee、Gradle regression 和 53 个 JAR license 验证全部通过 |

### 安全边界和未决项

- smoke 没有调用 `thread/start`/`turn/start`，未启动模型，也未发生 GitHub 或邮件写入。
- `role-routing.json` 允许未来 Implementer 在隔离 workspace 写入，但 Level 0 capability 与当前 `WORKFLOW.md` 仍为只读；该路径尚未激活。
- I3-04 需要真实、独立 Product Steward/Evaluator 模型会话；涉及模型认证和费用，未从普通实现请求推定授权。
- I3-07 需要创建 disposable GitHub Issue，属于外部写操作，且当前 `develop` ruleset/secret-scan 硬阻塞仍未解除。

## 远端准入审计

- GitHub CLI `2.97.0` 已认证为 `pan102887`，对 `ddd-mall/j-store` 的 viewer permission 为 `ADMIN`；该个人 token 只用于初始化与验证，不作为常驻 Agent 凭据。
- 已创建并回查 `agent:candidate`、`agent:queued`、`agent:waiting-ci`、`agent:human-review`、`agent:blocked`、`agent:fused`、`agent:cancelled` 和 `risk:human-approval` 标签。
- `develop@daf184ab9bb3f3bf811ae2158de704df6762b2a8` 的 Security Gate 失败 job 已重跑，仍稳定报告 2 个 finding。
- 使用 workflow 固定且 SHA-256 校验通过的 Gitleaks `8.30.1` 在 Ubuntu 原生临时仓库复现：只扫描 develop 历史为 0；加入全部 `origin/*` refs 后复现 2 个 finding。
- 两个 finding 仅位于未合并且无关联 PR 的 `origin/codex/branch-management-governance`，对应文件 blob 与此前已审查的 SHA-256 ordering-key 测试 fixture 完全相同。保留分支中的未合并工作，不删除远端 ref；候选增加两个精确 commit/path/rule/line fingerprint，不扩大规则范围。
- 候选提交 `93fe23d0a1d5d6b695d4edd22c37b2d87087334a` 已推送并创建唯一 Draft PR `#27`，目标为 `develop`；远端 `Protect develop` ruleset `20787654` 已启用且无 bypass actor。
- PR 首轮 `secret-scan` 和 `branch-policy` PASS；`quality` 因精确 allowlist 测试未同步而 FAIL，保留严格相等断言并加入两个已审计 fingerprint 后，聚焦测试 3/3、全量 governance 31/31 PASS。
- PR 首轮 `static-analysis` 报告 `scripts/smoke-codex-app-server.py` 的显式 `Popen.encoding` 为 Python 3.6 兼容 finding；移除冗余参数而不增加 Semgrep ignore。下一候选提交会使旧 head 的检查结论失效并触发完整 PR CI。

## 迭代 3 延续：可信基线收敛与运行时预检

### 事实收敛

- 当前推进分支从 `origin/develop@2542ee92a50bf87c427637d81d6445e4b2cea1db` 创建。
- GitHub Actions 对该 SHA 的 Quality、Security 和 Qodana 三条 push workflow 均为 success。
- GitHub Rulesets API 返回 `Protect develop`（ID `20787654`）为 active，六个 required contexts 与仓库模板一致，且没有 bypass actor。
- 因此 I0-01 至 I0-03 已有完成证据；I0-04 尚缺合法与故意违规 Draft PR 的实际 enforcement 演练，Level 0 写能力继续关闭。

### TDD 与实现证据

1. 先添加 `tests/tooling/test_agentic_cicd_runtime.py`，首次因 `agentic_cicd.runtime` 不存在而 ERROR。
2. 实现只读 `RuntimePreflight` 和 CLI，覆盖精确 Symphony HEAD、安全祖先、tracked source 洁净度、Codex 精确版本以及 mise/Elixir 构建工具。
3. 增加治理合同，要求运行手册和治理文件清单包含预检入口；首次因 runbook 未记录命令而 FAIL，补齐文档后通过。

### 当前验证

| 检查 | 结果 |
|---|---|
| `python -m unittest tests/tooling/test_agentic_cicd_runtime.py`（通过 uv 隔离依赖） | PASS，6 tests |
| Symphony `/Users/jupeter/Sources/symphony` HEAD 与安全祖先 | PASS，精确匹配 `8001b52e3062495a16e520e4ceaf8f9de868c4d0`，tracked source 洁净 |
| Codex 固定版本 | FAIL，主机为 `0.147.0`，合同要求 `0.146.0` |
| Symphony Elixir 构建工具 | FAIL，主机尚无 `mise`，也没有原生 `elixir`/`mix` |
| Agentic CI/CD 聚焦组合 | PASS，43 tests |
| `bash scripts/check-agent-governance.sh` / `python3 scripts/check-agentic-cicd.py` / `git diff --check` | PASS |
| `./scripts/quality-gate.sh` | PASS；spec-dev 28、governance 37、tooling 69 tests，Spotless、50 模块 Licensee、189 个 Gradle 回归任务及 53 个 JAR license verification 全部完成 |

预检失败只说明当前主机不能启动受支持的试点运行时，不影响仓库内纯合同测试；不得通过放宽精确版本或跳过源码构建来伪造通过。预检不会启动 Symphony、App Server thread 或模型 turn，也不会执行 GitHub 写入。

## 2026-08-14：同步最新 develop 后重新基线

### 同步与远端证据

- 执行 `git fetch --prune origin develop` 后，本地 `develop` 与 `origin/develop` 均为 `f2c2521d7d43c657b1cf1cc9d0aa2f74a29a0f36`。
- 该 SHA 的 `quality`、`static-analysis`、`dependency-vulnerability-scan`、`dependency-license-audit`、`secret-scan`、`qodana` 和 `Qodana for JVM` 七项检查均为 success。
- PR #27 `feat(agent): establish governed agentic ci/cd orchestration` 已合并，merge commit 为 `62b31dcc3391f965c6a9bfe39d9a18eca59ca5aa`，原远端任务分支已删除。
- PR #32 `ci(agent): add pinned runtime preflight` 已合并，merge commit 为 `5e790c552206c633e443be74fc03cdfdae764e99`；I3-09 已进入可信基线。
- PR #40 `feat(agentic-cicd): add Kubernetes Level 0 runtime` 仍为 Draft。当前 head `b134a9a1a8b04b35a4f2b0ca2210144161628542` 的八项 PR 检查全绿且 GitHub 判定可合并，但状态为 `BEHIND`，必须同步最新 `develop` 并重新验证后才能作为集成证据。

### 漂移发现

1. 计划头部仍引用已被最新 `develop` 包含且无独有提交的 `codex/agentic-cicd-continuation`，不能继续作为推进基线。
2. 本地旧 `codex/agentic-cicd-orchestration@bfc6535f0fff9b49e80e7aca3c0ea377fd97b5ba` 不包含 PR #27 的最终 head，且相对最新 `develop` 为 5/37 提交分叉；整体迁移会带回陈旧进度和覆盖上游运行时预检。
3. 旧原型的 Symphony `after_run` hook 会启动 `run-agentic-cicd-task.py`，后者再创建独立 `codex app-server`。该结构形成第二套模型生命周期，与 I3-08 的唯一 Symphony Supervisor 约束冲突。
4. 一次独立只读模型评审已获授权，但授权本身不是完成证据；评审必须绑定重新实现 I3-08 后的精确 head，旧候选的评审结果不能批准新候选。

### 更新后的进度判定

- 保持完成：I0-01 至 I0-03、I1、I2、I3-01 至 I3-03、I3-05、I3-06 和 I3-09。
- 保持未完成：I0-04、I3-04、I3-07、I3-08，以及 I4 至 I6 的所有退出条件。
- Kubernetes Level 0 仅为 PR #40 候选能力，不在当前 `develop` 上提前标记完成。
- 旧原型中的 exact-head 校验、凭据隔离、禁止 force push、幂等键和熔断测试可以作为后续实现参考；不得直接迁移嵌套 App Server 入口或旧状态文档。

### 下一验收切片

1. 先让 PR #40 同步最新 `develop`，重新运行全部 required checks、独立审查和 Symphony 依赖风险复核。
2. 从届时最新 `origin/develop` 创建新的 I3-08 实施分支，以 Symphony 为唯一 Supervisor 接入 workspace metadata、IterationPacket、确定性门禁和独立 Reviewer 决定。
3. 对该候选执行已经授权的一次独立只读模型评审；只有 exact-head PASS 才能进入 disposable Issue 演练。
4. disposable Issue 属于 GitHub 外部写操作，执行 I3-07 前单独确认目标、标签和清理方式；在 I0-04、I3-04、I3-07、I3-08 全部形成证据前不开放 I4 的自动 push/PR 能力。

### 专用 CI/CD 部署目标补充

- 用户指定：如需把 Symphony 集成到专用 CI/CD 主机，使用指定的开发 Kubernetes 集群；连接信息保留在仓库外。该环境已有单节点项目 Pod，可作为 Level 0 部署与恢复演练的目标。
- 该决定关闭“部署平台选择”问题，但不自动关闭 PR #40 基线落后、固定依赖风险、I3-08 单一 Supervisor 接线或真实 E2E 证据缺口。
- 当前只记录目标环境。SSH 登录、Kubernetes 写入、ServiceAccount/RBAC、镜像导入、Secret 注入和模型 turn 仍分别受最小权限与精确授权约束。
- 下一次部署候选应保持单副本、独立 namespace、非生产凭据和可缩容为 0 的恢复边界；现有项目 Pod 不复用为具备额外权限的通用 Agent Pod。

### 远端 Linux 全量验证证据

- 按用户指定的验证边界，停止在 WSL `/mnt/c` 上运行且使用错误 JDK 26 的慢速门禁；该次运行没有完成，不计入通过证据。
- 在指定 Linux 开发主机的原生 `/tmp` 文件系统创建临时 detached 副本，基线精确为 `origin/develop@f2c2521d7d43c657b1cf1cc9d0aa2f74a29a0f36`，只复制本次四个文档变更；远端长期仓库保持干净，未操作 Kubernetes 资源。
- 显式使用 JDK `25.0.3` 执行 `./scripts/quality-gate.sh`，最终退出码为 0。
- 结果：spec-dev 28、governance 39、tooling 69 tests 全部 PASS；source ownership/Spotless、54 个 runtime classpath 依赖解析、54 个模块 Licensee、Gradle 全量回归、57 个 JAR license artifact 验证全部 PASS。
- 验证日志仅保存在远端临时副本，不包含 GitHub、SSH、Kubernetes 或模型凭据；临时副本不作为部署状态或长期审计存储。

## 2026-08-14：I3-08A Symphony 可信阶段桥

### 设计结论

- 锁定 Symphony 的 `AgentRunner` 在正常 invocation 结束、Issue 仍 active/routable 时由同一 Orchestrator 安排 continuation；将 `agent.max_turns` 收敛为 1 后，后续 invocation 会创建新的 App Server session，不需要第二个 Supervisor。
- 当前固定源码不会把完成 turn 的 `session_id/thread_id/turn_id` 传给 workspace hook。旧原型从 `after_run` 再启动 App Server 会破坏唯一生命周期，因此下一接线点确定为“最小可信 turn-receipt 适配”，而不是迁移旧入口。
- 模型输出改为不含运行时身份的 `ReviewProposal`；host-side `SymphonyPhaseBridge` 使用可信 `TurnReceipt`、已保存 implementer session 和 exact head 生成 `ReviewDecision`。

### 本轮实现

- 新增 `ReviewProposal` schema 与 `TurnReceipt`；首次实现 IterationPacket 允许 `implementer_session_id=null`，评审入口则强制已有可信 implementer session。
- TaskSnapshot 新增 `iteration_phase`、`implementer_session_id`、`pending_review_findings` 和 `last_turn_receipt`，继续使用同目录原子替换恢复。
- 阶段桥覆盖 implement → review → complete、gate 失败留在实现、FAIL finding 回流、新 head 失效、错误 role/head 和同 session 拒绝。
- 新增变更规格 `docs/spec/changes/agentic-cicd-symphony-phase-bridge/`，将剩余 I3-08 拆为固定源码适配 `I3-08B` 与真实演练 `I3-08C`；I3-04、I3-07、I3-08 保持未完成。

### 验证证据

| 环境与命令 | 结果 |
|---|---|
| Windows 聚焦：合同检查及 protocol/phase bridge/coordinator/governance | PASS，47 tests |
| Windows tooling discovery | PASS，79 tests |
| Windows governance discovery | 39 PASS、1 FAIL；失败由忽略目录 `build/w/opf` 中另一个完整 worktree 被递归扫描引起，候选未修改 Gradle 文件；不作为 Linux 交付门禁结论 |
| 远端 Linux 聚焦：`git diff --check`、合同检查及四组相关测试 | PASS，48 tests |
| 远端 Linux JDK 25：`./scripts/quality-gate.sh` | PASS，spec-dev 28、governance 40、tooling 79 tests；Spotless、54 个 runtime classpaths、54 个模块 Licensee、Gradle regression 和 57 个 JAR license verification 全部通过 |

远端候选副本基于 `origin/develop@f2c2521d7d43c657b1cf1cc9d0aa2f74a29a0f36`，位于原生 Linux 临时文件系统；未执行 Kubernetes、GitHub 或模型写操作。

### 安全事件与处置要求

一次用于后台启动远程门禁的 PowerShell/SSH 引号错误把远程登录进程环境输出到本任务工具日志，其中包含敏感凭据。仓库文件和上述验证候选未写入这些值，本文也不记录任何值；但日志暴露已经发生，不能仅靠隐藏后续输出消除风险。

在继续部署、创建 disposable Issue 或执行模型 turn 前，必须由凭据所有者撤销/轮换该主机环境中的受影响 provider 与 GitHub 凭据，并从交互 shell 初始化文件迁移到受控的短期 Secret 注入。该处置属于外部密钥操作，当前未擅自执行。

## 2026-08-14：I3-08B 第一轮修复

### 已实现

- 新 workspace 不再依赖 GitHub 默认分支：可信控制器执行 no-checkout clone、强制 fetch `origin/develop`、解析完整 SHA，并从该 SHA 创建 `codex/gh-<number>-task`。
- bootstrap 同时在 PVC 的 host-owned state root 原子初始化 TaskSnapshot；workspace metadata 被写入 `.git/info/exclude`，不会污染候选提交。
- 新增受限 ReviewProposal 接收器：只在 review 阶段接受符合 schema 且匹配当前 head 的 payload，拒绝模型伪造 session/thread/turn 身份。
- 固定 Symphony 补丁把本次 App Server 的 session/thread/turn ID 交给 after hook，并为 GitHub adapter 增加 `submit_review_proposal` host tool；补丁不启动 `codex` 或第二个 App Server。
- 镜像构建同时锁定 Symphony commit、j-store controller commit 和补丁 SHA-256；WORKFLOW ConfigMap 恢复内容 hash，部署脚本要求不可变镜像名并检查新 Pod UID。
- Docker build context 使用默认拒绝 allowlist，只包含受信控制器包、控制器入口和锁定补丁，不携带 `.git`、本地缓存或被忽略文件。

### 验证证据

| 检查 | 结果 |
|---|---|
| Windows 相关协议、phase bridge、coordinator、runtime controller、Kubernetes、governance | PASS，64 tests |
| 远端锁定 Symphony patch apply 与 Elixir format | PASS |
| 远端无依赖 `Code.compile_file` 检查 | PASS；仅报告未加载依赖模块警告 |
| 指定 Linux 主机原生临时 worktree `./scripts/quality-gate.sh` | PASS；spec-dev 28、governance 40、tooling 95 tests，Spotless、54 个 runtime classpath、54 个 Licensee 模块、Gradle regression 和 57 个 JAR license verification 全部通过 |

### 残余门禁

- 固定 Elixir builder 镜像从 Docker Hub 拉取超时；使用旧 runtime 镜像补装 build-essential 时 apt 也超时，因此完整 Symphony `mix compile/test` 尚无通过证据。
- 依赖解析再次报告 Bandit、Mint、Phoenix、Plug、Req 等多项高危公告；PB-11 和真实 token 门禁继续阻塞。
- 当前 WORKFLOW 仍是 Level 0 `max_turns: 12` + read-only。只有动态 implement/review sandbox、阶段输入/完成 hook 和确定性 gate 接通后，才能改为 `max_turns: 1`；提前修改会造成无阶段终止条件的付费重复调度。
- 未获得本轮 Kubernetes rollout、Secret、GitHub Issue 或模型 turn 的精确写授权，因此没有改动现有 Pod，也未执行真实评审。

## 2026-08-14：I3-08B 第二轮动态阶段修复

### 设计修正

- 发现 Implementer 后由 Supervisor 直接运行 workspace `quality-gate.sh` 会执行候选可修改代码并继承 host 环境，形成凭据和权限边界风险。
- 状态机增加 `validate`：Implementer turn 只绑定可信 receipt 并冻结当前 head；独立隔离 runner 产生 exact-head GateReceipt 后才进入 review。
- GateReceipt FAIL 携带确定性 finding 回到 implement；PASS 才开放独立 reviewer。重复 gate ID 和旧 head 均被拒绝。

### 本轮实现

- 新增 phase-context、complete-turn 和 record-gate 控制器入口；所有 hook 固定使用 `/usr/bin/python3` 和镜像内只读控制器。
- 固定 Symphony routing patch 在启动 App Server 前读取 host-owned phase context，动态选择 observer/read-only、implementer/workspace-write 或 reviewer/read-only；validate/complete 直接短路，不创建模型 session。
- WORKFLOW 与状态合同收敛为 `max_turns: 1`。Level 0 observer 完成后进入内部 complete，避免 active Issue 重复产生付费 turn。
- `local_workspace_write=false` 被写入能力合同并随控制器构建进镜像；未来实现路径存在但当前不可达。
- phase bridge 与 routing 两个补丁分别使用 SHA-256 锁定，Docker 和部署脚本按顺序检查并应用。

### 验证

| 检查 | 结果 |
|---|---|
| 本地 protocol/phase bridge/coordinator/runtime controller/Kubernetes/governance | PASS，70 tests |
| 锁定 Symphony 两段 patch 顺序 apply | PASS |
| patched Elixir format | PASS |
| patched Elixir 独立模块编译 | PASS；仅有未加载项目依赖的预期 warning |
| 指定 Linux 主机原生临时 worktree `./scripts/quality-gate.sh` | PASS；spec-dev 28、governance 40、tooling 101 tests，Spotless、54 个 runtime classpath、54 个 Licensee 模块、Gradle regression 和 57 个 JAR license verification 全部通过 |

### 剩余阻塞

- 隔离 gate runner 尚未实现；因此不能把 `local_workspace_write` 改为 true，也不能执行真实 Implementer 修改流程。
- 固定 Symphony 依赖仍含多项高危公告，完整 `mix compile/test` 和供应链升级尚未完成。
- routing patch 尚未通过新镜像在 Kubernetes 上进行真实 observer/validate/reviewer invocation；部署、Secret 和模型调用仍需精确授权。
- pre-commit 候选如何获得稳定、可复核的 Git tree identity 仍需下一 delta；当前 `head_sha` 只适用于未修改的 Level 0 或已形成 Git commit 的候选。
