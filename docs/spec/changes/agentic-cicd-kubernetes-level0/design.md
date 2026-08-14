# Agentic CI/CD Kubernetes Level 0 设计

## 部署拓扑

```text
GitHub Issues / PR / Actions (read only)
                 |
                 v
    agentic-cicd/Symphony Deployment (replicas=1)
                 |
                 +-- trusted WORKFLOW ConfigMap
                 +-- optional GitHub token Secret reference
                 +-- workspace/log PVC
                 +-- internal dashboard Service
                 |
                 v
          Codex App Server (read-only turn)
```

Symphony 与 j-store 运行在同一开发集群，但不共享 ServiceAccount、Secret、PVC 或 namespace。Level 0 不调用 Kubernetes API，也不访问 j-store 数据库。

## 镜像与供应链

镜像在受控 Ubuntu 主机上构建，再以显式标签导入目标节点 containerd；本迭代不依赖外部私有 registry。

- Symphony 使用主机上已同步的源码 checkout；部署脚本在构建前校验完整 commit 与洁净工作树，并通过 BuildKit named context 只读送入镜像构建，避免构建过程依赖 GitHub clone。
- 基础镜像使用官方仓库的固定 digest，npm、Hex 与 Debian 软件包使用官方源；部署脚本对本次 Docker build 清空客户端遗留代理参数，不修改主机或 Docker daemon 的全局代理配置。
- Codex 从固定 npm 版本提取 Linux 原生二进制，镜像构建时执行 `codex --version`。
- Erlang 28 与 Elixir 1.19.5 按 Symphony `mise.toml` 对齐。
- 镜像标签包含 Symphony 短 SHA 和 Codex 版本，不使用 `latest`。
- Docker build、containerd import、manifest apply 和 rollout 是独立阶段，便于区分网络、构建、节点和 Kubernetes 故障。

开发 overlay 首次固定到 `k8s-master`，因为镜像在该节点本地导入且无需配置集群 registry。集群默认 local-path helper 无 control-plane toleration，因此使用专属静态 Local PV，避免修改全局 provisioner；通过节点亲和与 control-plane toleration 显式表达这一开发期选择。后续建立内部 registry 后再迁移到 worker1 和动态存储。

## Kubernetes 资源

- Namespace：Pod Security `restricted`。
- ServiceAccount：无 RoleBinding，`automountServiceAccountToken: false`。
- PV/PVC：使用固定到 `k8s-master` 的专属 Local PV，宿主机路径 `/var/lib/jstore-agentic-cicd`，保存 workspace 与日志；回收策略 `Retain`，单节点 RWO，不宣称 HA。
- ConfigMap：从仓库受信 `WORKFLOW.md` 生成，只增加 Kubernetes 运行所需的 `server.host: 0.0.0.0` transport binding；合同测试保证移除该单一部署差异后与根合同逐字一致。访问仍由无 Ingress 的 ClusterIP 边界限制。
- Deployment：`replicas: 1`、`Recreate`、只读 root filesystem、非 root、seccomp RuntimeDefault、drop ALL。
- Service：ClusterIP，仅暴露只读 dashboard/API；默认不创建 Ingress。

## 身份与凭据

仓库不生成 Secret。未来管理员在集群外创建 `agentic-cicd-github` Secret，部署时通过单独 overlay 将其键 `token` 注入 Pod。凭据以 Symphony 环境变量提供，且现有 Symphony token alias scrubbing 必须阻止其进入 Codex 子进程。

Symphony 本身不支持无 tracker token 的 suspend 模式。首次部署设置非秘密哨兵值 `level0-no-github-access`：它满足配置形状校验，但 GitHub 会拒绝请求，所以无法取得 Issue 或启动 Codex turn；该模式只验证进程、dashboard、持久卷和恢复。真实 Level 0 观察需要后续为专用 GitHub App 注入短期只读 installation token。

## 部署与回滚

部署脚本必须要求调用者提供精确 kube context，并固定 namespace。执行顺序：

1. 构建镜像并验证版本。
2. 导入当前调度节点 containerd。
3. `kubectl kustomize` 和 server-side dry-run。
4. apply namespace 与工作负载。
5. 等待 rollout，执行容器版本、PVC 和 HTTP smoke。

回滚分两级：

- 停止：将 Deployment 缩容为 0 或删除 Deployment/Service，保留 PVC 和日志。
- 完全清理：必须另行人工确认后才能删除 PVC/namespace；默认脚本不执行。

## 后续扩展边界

完成 Level 0 后依次推进：真实只读 disposable Issue、独立 Reviewer turn、唯一 Symphony 路径、Draft PR 写入、最后才是受控开发部署。Routine j-store 部署应由独立 Deploy Executor 或 GitHub Actions 执行，不把当前数据库 bootstrap 权限交给 Coding Agent。

构建期间 Hex 报告锁定依赖存在已披露安全公告，涉及 Bandit、Mint、Phoenix、Plug、Req 等网络边界组件。Level 0 依靠无 Ingress、ClusterIP、无真实 tracker token 和单实例资源上限降低暴露面；在注入真实 GitHub token 或开放 dashboard 前，必须升级 Symphony 固定提交/依赖并重新执行供应链审查，不能把当前隔离措施视为长期豁免。
