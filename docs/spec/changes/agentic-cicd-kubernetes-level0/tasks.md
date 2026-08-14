# Agentic CI/CD Kubernetes Level 0 任务

- [x] K0-01 审计开发宿主机、Kubernetes、存储、j-store 和监控现状。
- [x] K0-02 明确 Level 0 范围、非目标、部署拓扑、凭据边界和回滚策略。
- [x] K0-03 以失败合同测试固定 namespace、单实例、Pod 安全、PVC、只读 WORKFLOW 和版本约束。
- [x] K0-04 实现固定运行时镜像、Kustomize base/development overlay 和上下文绑定脚本。
- [x] K0-05 运行 manifest、治理、格式和仓库质量门禁。
- [x] K0-06 在 `jstore-dev-k8s` 构建并导入镜像，部署无权限哨兵 token 模式。
- [x] K0-07 验证版本、Pod 重启、PVC 保留、内部 dashboard 和停止/恢复。
- [x] K0-08 回填实际集群证据、残余风险和下一迭代准入条件。

后续任务（不属于本变更完成条件）：

- 在注入真实 GitHub token 或开放 dashboard 前，升级 Symphony 固定提交/依赖并清除构建报告的高危网络组件公告。
- 经授权创建专用 GitHub App 短期只读 token并执行 disposable Issue。
- 将 Coordinator、IterationPacket 和独立 Reviewer 接入唯一 Symphony 路径。
- 拆分 j-store 数据库 bootstrap 与 routine deploy，并设计开发集群最小部署 RBAC。
