# Symphony 可信阶段桥任务

- [x] `PB-01` 记录唯一 Supervisor、单 turn redispatch 和可信回执缺口。
- [x] `PB-02` 调整 IterationPacket：首次实现允许 session 为 null，评审前强制可信 implementer session。
- [x] `PB-03` 增加无运行时身份的 ReviewProposal schema。
- [x] `PB-04` 实现 TurnReceipt 与 SymphonyPhaseBridge 的 exact-head/独立会话绑定。
- [x] `PB-05` 将阶段、finding 和最后回执纳入原子 TaskSnapshot 恢复。
- [x] `PB-06` 增加协议、状态转换、失败回流、新 head 失效和治理合同测试。
- [ ] `PB-07` 在锁定 Symphony 源码实现 turn-receipt 与受限 `submit_review_proposal` host tool；不得嵌套 App Server或让 Reviewer 写 workspace。
- [ ] `PB-08` 将可信控制器构建到镜像只读路径，修复 exact `origin/develop` bootstrap，并更新受信 WORKFLOW 为单 turn 阶段路由。
- [ ] `PB-09` 固定 Symphony/j-store 双 revision，使用不可变镜像 tag和 hashed ConfigMap，验证运行时变化必定创建新 Pod。
- [ ] `PB-10` 在指定 Linux 主机完成 patch apply/build、控制器测试、故障注入和完整质量门禁。
- [ ] `PB-11` 完成 Symphony 网络依赖安全升级与独立安全审查，并确认受影响远程凭据已轮换。
- [ ] `PB-12` 经外部写授权注入短期 GitHub App token、创建 disposable Issue，执行已授权的一次独立只读模型评审并绑定 exact head。
- [x] `PB-13` 增加 validate 阶段与 exact-head GateReceipt，Implementer after hook 只冻结 head，不执行 workspace 代码。
- [x] `PB-14` 在 Symphony patch 中按可信 phase 动态选择 workspace-write/read-only，并在 validate/complete 阶段启动 App Server 前短路。
- [ ] `PB-15` 将 WORKFLOW 收敛为 `max_turns: 1`，接入可信 complete-turn hook，并验证无第二 App Server、无候选代码 host 执行。

## 2026-08-15 实施进度

- PR #42 已将可信阶段编排候选合入 `develop@6f0cbf3c403e02db98ce59adfc126898b8fbdc0c`；这证明仓库侧合同已集成，不证明 PB-07 至 PB-12、PB-15 的运行时退出条件完成。
- 下一阶段由 `../agentic-cicd-local-candidate-loop/` 承接稳定 CandidateRevision、隔离 Gate Runner、Symphony完整供应链资格和 disposable Issue恢复演练。

- `PB-07`：已形成锁定补丁候选，暴露可信 turn receipt 环境和受限 `submit_review_proposal` host tool；已通过 patch apply、Elixir format 和独立文件编译，尚缺完整 Symphony Mix 构建/测试。
- `PB-08`：已将控制器构建入口固定到镜像 `/opt/jstore-agentic-controller`，并实现 exact `origin/develop` bootstrap、任务分支和 host-owned snapshot 初始化；动态阶段路由、`max_turns: 1` 与 implement/review sandbox 切换尚未接通。
- `PB-09`：已实现双 revision 镜像命名、补丁 SHA-256 锁、hashed WORKFLOW ConfigMap 和新 Pod UID smoke 合同；尚未获得 Kubernetes rollout 写授权，不能勾选完成。
- `PB-10`：j-store 候选已在指定 Linux 主机通过完整质量门禁；Symphony 完整构建因固定基础镜像拉取超时及临时容器 apt 超时未完成，故仍保持未完成。
- `PB-13/PB-14`：validate/GateReceipt、Level 0 observer、动态 sandbox 与无模型短路均已有协议、控制器和补丁证据；当前能力合同仍显式禁止 local workspace write。
- `PB-15`：WORKFLOW 和状态合同已收敛为单 turn，absolute-path complete hook 已接入；因尚未部署新镜像并执行真实 invocation，保持未完成。

PB-01 至 PB-06 是已完成的仓库组件切片；PB-07 至 PB-12 是 I3-08 的退出条件，不能因合同测试、Level 0 Pod Ready 或旧镜像 smoke 通过而提前勾选。
