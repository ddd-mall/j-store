# Agentic CI/CD 已完成阶段归档

本文件只保存已经完成或被后继规格吸收的阶段性决策与证据摘要，不描述当前能力，也不作为后续任务状态来源。

当前产品意图以 [requirement.md](requirement.md) 为准，当前技术设计以 [design.md](design.md) 为准，当前进度以 [tasks.md](tasks.md) 为准。仍在推进的 Level 1 验收与证据位于 [本地候选闭环变更](../changes/agentic-cicd-local-candidate-loop/)。运行命令和实时权限边界只维护在 [Agentic CI/CD 运行手册](../../operations/agentic-cicd-runbook.md)。

## 2026-08-14 Kubernetes Level 0

### 目标与边界

首个阶段在开发 Kubernetes 集群部署可停止、可恢复、可审计的 Symphony 单实例，只验证进程、内部 dashboard/API、运行时身份、持久卷和恢复路径。该阶段使用无权限哨兵 token，不读取 GitHub Issue、不启动模型 turn，也不修改 j-store、数据库或其它 namespace。

### 已交付设计

- 独立 `agentic-cicd` namespace、单副本 `Recreate` Deployment、专用 ServiceAccount、ClusterIP Service 和 Retain Local PV。
- Pod 以非 root、只读根文件系统、无额外 capability、禁用 ServiceAccount token automount 的身份运行。
- Symphony 从锁定且洁净的源码 commit 构建；基础镜像和最终运行制品使用完整 digest 标识。
- Codex CLI 的实际版本记录在镜像 tag、label 和来源记录中；历史候选使用 `codex-cli 0.146.0`，该值只标识当时制品，不构成当前版本要求。
- 部署、smoke、停止和恢复入口绑定精确 kube context；停止保留 PVC 与日志。

### 验收结论

- 2026-08-14 的部署为 `1/1`，容器内 Symphony revision、Codex CLI、运行用户和镜像身份均通过 smoke。
- PVC 删除 Pod 后保持数据，Deployment 可恢复；缩容为 0 不删除状态。
- 部署和恢复未访问或修改 `jstore`、`postgresql`、`monitoring` namespace。
- 合同测试、Kubernetes render/server-side dry-run、治理检查、完整质量门禁和发布制品许可证验证均通过。

### 后继处置

当时发现的 Symphony 网络依赖风险、真实 GitHub 凭据、候选身份、隔离 Gate Runner 和独立 Reviewer 均已转入 Level 1 本地候选闭环。原始阶段文档已合并到本归档；精确运行版本和旧风险结论只适用于 2026-08-14 候选，不能作为当前集群状态。

## 2026-08-15 Symphony 可信阶段桥

### 目标与边界

该阶段建立 host-side 阶段桥，使 implement、validate、review、complete 能跨 Symphony invocation 恢复，同时确保模型不能自报 session、thread、turn、gate 或 reviewer 身份。阶段桥本身不授权 workspace 写入、GitHub 写入、Kubernetes rollout 或模型调用。

### 已吸收的设计

- 每次 Symphony invocation 最多一个 model turn；validate/complete 在创建 App Server 前短路。
- `ReviewProposal` 不含运行时身份；可信 TurnReceipt 与 host-owned snapshot 绑定后才生成 `ReviewDecision`。
- Implementer 完成后进入 validate；只有 exact-candidate `GateReceipt PASS` 才进入独立只读 review。
- 实现与 Reviewer 使用不同可信 session；候选变化使旧 GateReceipt 和 ReviewDecision 失效。
- hook 只执行镜像内只读控制器，不执行 workspace 中的候选脚本，也不启动第二个 App Server。
- 新 workspace 从本次 fetch 的精确 `origin/develop` SHA 创建；运行镜像绑定 Symphony/j-store 双 revision、补丁摘要与 WORKFLOW hash。

这些长期有效的结论已并入 [design.md](design.md) 和 Level 1 规格，不再在独立 requirement/design/tasks 中重复维护。

### 验收与承接

| 原任务范围 | 当前归属 |
|---|---|
| PB-01 至 PB-06：协议、阶段、回执、快照与合同测试 | 已完成，并入总设计与机器合同 |
| PB-07 至 PB-11、PB-13 至 PB-14：Symphony 适配、只读控制器、不可变镜像、供应链和阶段短路 | 已由 Level 1 的 LC-02、LC-04 至 LC-18、LC-22 提供实现和无模型/集群证据 |
| PB-12：短期凭据、disposable Issue 和真实独立模型评审 | 凭据与Issue准备已有局部证据，真实评审由 LC-20、LC-21继续承接 |
| PB-15：单 turn、可信 complete hook、无第二 App Server | 仓库合同和无模型证据已具备，真实 invocation 仍由 LC-16、LC-20、LC-21承接 |

精确的逐轮评审记录保存在 [review-log.md](review-log.md) 和 [Level 1 review log](../changes/agentic-cicd-local-candidate-loop/review-log.md)，可重放制品继续保存在 Level 1 `evidence/` 目录。
