# J-Store 不可变制品与多集群交付需求

## 状态

本规格取代 PVC/JAR 通用应用交付方式；原开发 bootstrap 脚本和旧清单已从权威路径移除。

## 已接受假设

- development、integration、canary 和 production 集群物理隔离，但 Kubernetes、
  registry、Secret、数据库、Redis、监控和入口能力完全同构。
- 中央 CI/CD 服务器负责构建，并通过一次只连接一个目标集群的隧道访问该集群的
  Kubernetes API 和私有 registry。
- 集群之间不互通。网络差异只通过 CI/CD 中的隧道、registry 地址和必要代理配置表达。
- 每个集群在应用部署前已经满足平台契约；应用流水线不创建数据库账号、基础设施或
  长期凭据。

## 目标

一个 Git 提交只生成一个经过验证的 OCI 应用镜像。该镜像以不可变 digest 依次晋级到
不同物理集群；环境切换不得重新构建。仓库维护一套 Kubernetes base 和受控环境策略，
CI/CD 只选择目标集群、环境和网络配置。

## 验收标准

1. CI 构建 `j-store-boot` OCI 镜像后必须记录完整 `repository@sha256:digest`，并生成
   镜像 SBOM、漏洞扫描、签名或等价 provenance 证据。
2. development、integration、canary 和 production 必须部署同一个候选 digest；任何
   环境晋级都不得重新执行镜像构建。
3. Kubernetes base 必须使用逻辑镜像名，不包含公共镜像代理、`latest`、PVC JAR 或
   环境专属数据库、Secret 和 namespace bootstrap 行为。
4. 仓库必须提供四个可确定渲染的环境 overlay。生产和灰度使用 RollingUpdate、至少
   两个副本、PDB、拓扑分散和优雅终止；开发与集成可以使用较小资源，但不得改变应用
   制品。
5. 部署入口必须拒绝任何包含 tag 的镜像引用，只接受完整
   `repository@sha256:digest`，并校验显式 Kubernetes context、目标集群 UID、环境与
   namespace，在写入前完成 server-side dry-run。
6. 部署入口不得建立隧道或持有跨集群路由。CI/CD 在调用部署入口前建立单目标隧道，
   完成后关闭；一次 Job 不得同时连接两个集群。
7. 每个目标集群必须预置 `jstore-runtime` ConfigMap 保存非敏感运行参数，并通过外部 Secret
   管理或等价平台能力预置同名 Secret。应用流水线不得生成、回读或输出业务凭据。
8. registry 地址可以因网络不同而变化，但复制后的 OCI manifest digest 必须与候选
   digest 相同，并在部署前验证签名或 provenance。
9. 回滚必须部署上一个已验证 digest，不得移动 tag、重建旧提交或恢复 PVC JAR。
10. 灰度集群只有在统一外部流量控制面能向其分配真实受控流量时才可称为 canary；否则
    只能作为 production-like 验证环境。
11. canary 和 production 的应用 rollout 必须禁用启动时 Flyway；数据库迁移由独立、显式
    审批的 CI/CD 阶段在应用部署前完成，不能把镜像回滚误当作数据库回滚。

## 质量目标

- **可复现性**：同一 digest 在任一集群解析为相同 OCI manifest。
- **隔离性**：每个环境使用独立隧道身份、registry 凭据和 namespace 级 Kubernetes RBAC。
- **可审计性**：部署记录包含 commit、digest、环境、cluster UID、配置版本和结果。
- **恢复性**：任一环境可以仅用上一 digest 回滚，不依赖源码重新构建。
- **最小权限**：应用部署身份不能创建数据库账号、读取 Secret 内容或修改其它 namespace。
- **迁移安全**：生产数据库变更与应用 rollout 分离审批、执行和记录，失败时停止晋级。

## 非目标

- 不在本规格中部署或变更隧道、registry、数据库、Redis、Ingress Controller 或监控平台。
- 不自动批准生产发布、切换真实灰度流量或执行生产数据库迁移。
- 不承诺能力不符合本规格假设的异构 Kubernetes 平台无需仓库变更即可接入。
