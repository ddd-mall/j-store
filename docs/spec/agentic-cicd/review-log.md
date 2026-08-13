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
