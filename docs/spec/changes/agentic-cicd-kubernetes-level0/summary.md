# Agentic CI/CD Kubernetes Level 0 交付证据

## 交付结果

2026-08-14 已在内部开发集群 `jstore-dev-k8s` 部署 Symphony Level 0 单实例。实例使用无权限哨兵 token，只验证进程、内部 dashboard/API、固定 Codex 运行时、持久卷和恢复路径，不读取 GitHub Issue、不启动 Codex turn、不修改 j-store 或数据库。

运行态证据：

- Deployment `agentic-cicd/symphony` 为 `1/1`，镜像 `jstore-agentic-cicd:8001b52e-codex-0.146.0`，运行在 `k8s-master`。
- 镜像标签验证 Symphony revision `8001b52e3062495a16e520e4ceaf8f9de868c4d0`，容器内 `codex --version` 为 `codex-cli 0.146.0`，运行用户为 `10001:10001`。
- PVC `symphony-state` 绑定 40 GiB Retain Local PV `agentic-cicd-symphony-state`。
- Service 为 ClusterIP，无 Ingress、Secret、RoleBinding，也没有引用该 ServiceAccount 的 ClusterRoleBinding；ServiceAccount token 未自动挂载。
- `/api/v1/state` 返回 running/blocked/retrying 均为 0，Codex input/output/total tokens 均为 0。
- 现有 `jstore` Deployment 保持 `1/1`，j-store 与 Redis Pod 均为 Ready/Running。

## 可恢复性证据

1. 在 PVC 写入 `level0-pvc-persistence-ok` 后删除 Pod，Deployment 创建新 Pod，标记仍可读取。
2. 停止脚本将 Deployment 缩容到 0，PVC 保持 Bound。
3. 重新应用受审清单后实例恢复 Ready，smoke 再次通过，PVC 标记仍可读取。
4. 停止和恢复过程未访问或修改 `jstore`、`postgresql`、`monitoring` namespace。

## 验证证据

- `python3 -m unittest tests.tooling.test_agentic_cicd_kubernetes tests.governance.test_agentic_cicd_contract`：18 tests PASS。
- `python3 scripts/check-agentic-cicd.py`：PASS。
- `bash scripts/check-agent-governance.sh`：PASS。
- Kubernetes kustomize render 与 server-side dry-run：PASS。
- `scripts/agentic-cicd-kubernetes-smoke.sh`：`AGENTIC_CICD_LEVEL0_READY`。
- `scripts/quality-gate.sh`：PASS（治理、合同、格式、许可证、189 个 Gradle task 回归测试及发布物许可证验证）。

## 实施中发现并收敛的偏差

- Docker 客户端残留失效代理会注入构建容器；部署脚本现在仅对本次 build 清空代理参数，官方基础镜像源与 Debian/npm/Hex 源构建通过。
- 目标主机 SSH 免密但 sudo 需要人工认证；部署脚本允许交互式 sudo，仅用于固定 Local PV 目录和 containerd 镜像导入，不接收或记录密码。
- 容器内从 GitHub clone Symphony 出现 HTTP/2 中断；构建改为校验主机同步 checkout 的固定提交与洁净工作树，再通过 BuildKit named context 输入源码。
- Symphony 默认 dashboard 只绑定 loopback；Kubernetes 配置只在受信根 `WORKFLOW.md` 上增加 `server.host: 0.0.0.0`，使 Pod 探针和 ClusterIP 可用。无外部 Service 类型或 Ingress，合同测试保证其余 workflow 内容逐字一致。
- 首版 smoke 预期了不存在的 `agents` 字段；已按实际稳定 API 字段 `running`、`counts`、`codex_totals` 修正并增加回归合同。

## 残余风险与下一迭代准入

- Symphony 固定提交的 Hex 锁定依赖在构建时报告多项 2026 年安全公告，其中包括 Bandit、Mint、Phoenix、Plug、Req 等网络组件的高危拒绝服务问题。当前以 ClusterIP、无 Ingress、无真实 token、零 agent turn 和资源限制隔离，仅适用于此 Level 0 smoke。
- 在注入真实 GitHub token、开放 dashboard 或执行 disposable Issue 前，必须由人工明确发起 Symphony 提交/依赖升级，记录公告到修复版本映射，重新构建扫描并通过独立安全审查。
- Local PV 固定单节点，不具备跨节点恢复或 HA；建立内部 registry 和动态存储后再迁移到 worker 节点。
- 当前没有真实 GitHub App installation token，因此未验证 Issue 拉取、tracker 速率限制、Codex 模型 turn、独立 Reviewer 或费用上限。这些均属于后续迭代，不得从本次 smoke 推断为已完成。
