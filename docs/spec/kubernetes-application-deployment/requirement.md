# J-Store Kubernetes 开发部署需求

## 状态

开发集群部署与指标接入已完成；生产门禁仍受 NetworkPolicy、HA 和镜像供应链边界限制。

## 目标

在用户提供的 Kubernetes 开发集群中运行 `j-store-boot` 单体组合运行时，并复用集群现有 PostgreSQL、Prometheus、Alertmanager 与 Grafana。目标是形成可重复、可回滚、无仓库凭据的开发部署流程，而不是宣称生产发布。

## 验收标准

1. 应用运行在独立 `jstore` namespace，使用 Java 25、非 root、只读根文件系统、资源边界与 startup/readiness/liveness 探针。
2. 应用使用独立 PostgreSQL database/role，不覆盖已有 `j_store` 数据；凭据与 JWT/HMAC Secret 不进入 Git。
3. 既有 `redis.redis.svc` 不可用时，部署脚本使用 `jstore` namespace 内带认证和 PVC 的隔离 Redis，不修改共享 `redis` namespace。
4. 开发集群没有镜像 registry 或 containerd 导入权限时，脚本可把本地构建的 `app.jar` 写入专用 PVC，并用固定 Java 25 基础镜像运行；此路径必须明确标记为非生产制品交付方式。
5. Prometheus Operator 通过 ServiceMonitor 抓取 `/actuator/prometheus`；现有 Grafana sidecar 自动加载 J-Store dashboard，且无需修改 Helm 管理的 monitoring 资源。
6. 默认不创建 Ingress、NodePort 或 LoadBalancer；人工访问应用使用 `kubectl port-forward`。
7. 部署脚本必须要求显式 context、只写固定 namespace、清理一次性 loader，并在失败时保留可诊断状态而不修改现有 monitoring/PostgreSQL/Redis 工作负载。

## 质量边界

- 目标集群当前 Flannel 不提供 NetworkPolicy 执行证据；清单仍定义最小策略，但不能把 API 接受策略对象视为隔离通过。
- 开发 PVC、本地 JAR 注入、单副本应用与单副本 Redis 不满足生产 HA、供应链签名、镜像扫描或灾备要求。
- 数据库与 PVC 清理是破坏性操作，不纳入普通回滚；默认回滚只缩容/删除应用工作负载并保留数据。
