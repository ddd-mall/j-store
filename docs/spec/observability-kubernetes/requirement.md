# Kubernetes 可观测性部署需求

## 状态

工程实现完成；目标集群完整 smoke 尚未通过，残余环境风险见 `verification.md`。

## 背景与目标

现有 Compose 参考栈已经验证应用结构化日志、Alloy、Loki、Prometheus 与 Grafana 的单机闭环，但不能直接代表多节点 Kubernetes 集群能力。本迭代把应用侧既有日志契约扩展为一套可重复部署、默认不公开暴露、可在节点故障和采集后端短时不可用时诊断的 Kubernetes 参考拓扑。

目标是：

1. 每个 Linux 节点运行一个受限 Alloy 日志采集实例，只采集显式选择的工作负载。
2. 日志携带 namespace、workload、pod、container 与 node 元数据，同时保持 Loki 标签低基数。
3. 集群内 Loki、Prometheus、Grafana 具备持久化、资源边界、健康检查和最小网络访问面。
4. 凭据与 TLS 私钥不进入 Git；访问 Loki 写入端必须通过 TLS 与认证。
5. 提供可重复的静态检查、server-side dry-run 和隔离命名空间 smoke 流程。
6. 为采集器不可用、日志丢弃/重试、Loki 不可用和业务指标抓取失败提供基础告警规则。

## 行为需求

### KOBS-R1：节点级日志采集

- Alloy 必须以 DaemonSet 运行，并通过当前节点字段选择器避免多个实例重复采集同一 Pod。
- 仅采集带 `jstore.logs/enabled=true` 标签的 Pod；应用仍只写 stdout/stderr，不引入采集 Sidecar。
- 采集配置必须解析既有 ECS JSON，保留 correlation、trace 与消息标识，并补充 Kubernetes 元数据。
- `service_name`、`environment`、`namespace`、`container` 可作为受控低基数标签；pod、node、trace、correlation 和消息标识只能作为结构化元数据。

### KOBS-R2：可靠性与容量边界

- 每个 Alloy 实例必须启用有限发送队列和 WAL；WAL 使用受 `sizeLimit` 约束的节点临时卷，在同一 Pod 的容器进程重启后继续存在，且不得为了跨 Pod 持久化而降低 Pod Security 等级或引入特权 hostPath。
- Loki、Prometheus 与 Grafana 必须使用 PVC，不得把持久数据写入容器可写层。
- 所有工作负载必须声明 CPU/内存 requests、limits，并配置存活、就绪和启动检查。
- Prometheus 必须加载可观测性自身健康告警；通知路由仍由目标环境的 Alertmanager 或等价系统负责。
- 必须记录默认容量、保留期、扩容前提、备份责任和恢复步骤。

### KOBS-R3：最小权限与网络边界

- Alloy 使用独立 ServiceAccount；RBAC 只能读取发现和日志流所需的 Pod 元数据与 `pods/log`，不得读取 Secret、不得使用通配符权限。
- 默认拒绝命名空间 ingress/egress，再按数据流开放 DNS、Kubernetes API、日志写入、指标抓取和 Grafana 查询。
- 所有容器必须关闭特权模式、禁止提权、丢弃不需要的 capabilities，并使用只读根文件系统；仅初始化 Alloy 节点 WAL 目录的容器可获得 `CHOWN`。
- Loki、Prometheus、Alloy 和 Grafana 均不得以 NodePort、LoadBalancer 或 Ingress 默认公开。

### KOBS-R4：Secret、认证与 TLS

- 仓库不得包含 Secret 清单、默认口令、私钥或真实证书；部署前由脚本在目标上下文生成或导入所需 Secret。
- Alloy 到 Loki gateway 必须使用 TLS 1.2+、校验服务端 CA，并使用 Basic Auth；禁止 `insecure_skip_verify`。
- Grafana 管理员凭据必须来自 Secret。运维访问使用受 Kubernetes API 鉴权和 TLS 保护的 `kubectl port-forward`；若未来增加 Ingress，必须另行评审身份认证、TLS 与访问策略。

### KOBS-R5：可验证部署

- Kustomize 输出必须可被客户端 schema 检查，并在目标集群通过 server-side dry-run。
- smoke 必须证明：目标节点均有 Alloy、合成 Pod 日志进入 Loki、关键 Kubernetes 元数据存在、Prometheus 能抓取采集器/后端指标、后端恢复后 WAL 内日志可查询。
- smoke 使用独立命名空间和合成数据；清理必须精确删除本次命名空间，不修改既有 monitoring、Redis、PostgreSQL 或 Grafana 资源。

## 质量目标

- **安全**：无仓库凭据；无默认公网入口；RBAC 不含 Secret 或通配符；TLS 不跳过验证。
- **可靠性**：后端短时不可用不阻塞应用；同一采集 Pod 的容器进程重启后 WAL 可恢复；PVC 与保留期有明确上限。Pod 重建与节点故障期间未发送 WAL 的保证必须作为残余风险记录。
- **可运维性**：固定镜像版本、资源/探针/PVC 齐全，运行手册包含部署、查询、升级、故障和清理。
- **可移植性**：基础清单不绑定远程开发集群的节点名或 StorageClass；依赖默认动态 StorageClass 和支持 NetworkPolicy 的 CNI。

## 非范围与决策边界

- 本迭代提供单副本、持久化的集群参考后端，不把单副本 Loki/Prometheus 宣称为生产高可用。
- Loki 对象存储、多副本拓扑、Prometheus HA/长期 remote-write、跨地域归档和备份实现需要容量、RPO/RTO 与基础设施成本决策，不能在未知约束下伪造。
- 不替换集群现有 monitoring 栈，也不自动修改其 Grafana；参考栈使用独立命名空间和名称。
- 不在本迭代实现完整 SLO、值班与事故管理流程。
