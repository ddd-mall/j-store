# Agentic CI/CD Kubernetes Level 0 需求

## 背景

Agentic CI/CD 已具备只读治理合同、workspace 协调器、Codex App Server 协议和固定运行时预检，但还没有部署到可持续运行的集成环境。内部开发集群 `jstore-dev-k8s` 已运行 j-store、PostgreSQL、Redis 和监控栈，可承载首个 Symphony 只读观察实例；实际地址不进入仓库。

## 目标

在开发 Kubernetes 集群中部署一个可停止、可恢复、可审计的 Symphony Level 0 单实例，为真实 disposable Issue 只读演练提供运行环境，同时保持现有 j-store 工作负载和数据不变。

## 范围

- 固定 Symphony、Codex、Erlang/Elixir 运行时并生成可复现容器镜像。
- 建立独立 `agentic-cicd` namespace、ServiceAccount、PVC、Deployment 和内部 Service。
- 挂载受信 `WORKFLOW.md`，使用单并发、只读 Codex sandbox 和显式 workspace/log 目录。
- 提供上下文绑定的部署、状态检查、smoke 和停止入口。
- 为后续接入 GitHub App 短期只读 token 保留 Secret 注入方式，但不把任何凭据写入仓库。

## 非目标

- 本迭代不创建或修改 GitHub Issue、分支、提交、PR、review、邮件或发布。
- 不向 Symphony 授予 Kubernetes API 权限，不挂载管理员 kubeconfig。
- 不修改 `jstore`、`postgresql`、`monitoring` 等现有 namespace 的资源或数据。
- 不执行付费模型 turn；disposable Issue 演练在运行环境通过无凭据 smoke 后单独授权。
- 不提供多副本、高可用、跨节点 PVC 恢复或生产级网络隔离。

## 验收标准

1. namespaced Kubernetes 资源只能渲染到 `agentic-cicd`；唯一允许的集群级资源是绑定该 namespace PVC 的专属 Retain Local PV，仓库中不得包含 Secret 对象或真实凭据。
2. Symphony Deployment 必须保持一个副本、使用 `Recreate`、禁用 ServiceAccount token automount，并以非 root、无额外 capability、禁止提权的身份运行。
3. workspace 和日志必须写入 PVC；配置、临时目录和 home 必须有明确挂载，不能写容器只读根文件系统。
4. 镜像必须验证 Symphony commit `8001b52e3062495a16e520e4ceaf8f9de868c4d0` 和 Codex CLI `0.146.0`，不得跟随浮动 `main` 或 `latest`。
5. 受信 WORKFLOW 必须保持 `max_concurrent_agents=1`、`read-only` sandbox、拒绝 approval，并禁用全部远端写能力。
6. 首次部署使用明确的非秘密无效哨兵 token，只允许完成版本预检、配置加载和进程级 smoke；它不得取得 Issue 或启动 Codex turn。
7. 部署命令必须显式匹配 kube context 和 namespace；失败时不得自动修改现有 j-store 或数据库资源。
8. 停止或回滚 Symphony 不得影响 GitHub Actions、现有 PR 或 j-store 运行。

## 质量目标

- **安全边界**：低敏感开发环境仍维持 namespace、身份和凭据隔离，Coding Agent 不获得 kubeconfig。
- **可靠性**：单实例、单并发；Pod 重建后 workspace 与日志仍存在。
- **可运维性**：版本、健康、日志、当前 Pod 和 PVC 状态可由单一 smoke 命令确认。
- **可回退性**：删除 Deployment/Service 即可停止，PVC 默认保留以供审计。
