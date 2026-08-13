# Kubernetes 可观测性验证记录

## 结论

工程实现与本地质量门禁通过，目标 Kubernetes API 的 server-side dry-run 通过；远程集群的完整 smoke 未通过，因此本变更不能被表述为已满足生产部署门禁。

## 已验证证据

- 分支基线：`codex/kubernetes-observability` 的创建点与 `origin/develop` 均为 `daf184ab9bb3f3bf811ae2158de704df6762b2a8`。
- 本地 JDK 25 质量门禁：治理契约、spec-dev 28 项、governance 22 项、tooling 28 项、Spotless、依赖许可证、全量 Gradle 回归测试与发布制品许可证均通过。
- Kubernetes 清单契约：8 项测试通过；`kubectl kustomize` 成功。
- 目标 API：在用户提供的开发集群 Kubernetes `v1.28.15` 上，使用一次性替代 namespace 对 34 个渲染资源执行 `kubectl apply --dry-run=server`，结果通过。
- Alloy：`grafana/alloy:v1.18.0 validate --stability.level=experimental` 通过。
- Prometheus：`promtool v3.13.2 check config` 通过并加载 1 个规则文件；`check rules` 识别并通过 5 条规则。
- 安全/运行时局部证据：restricted Pod Security 拒绝了早期 hostPath 方案，因此最终实现改为有上限的 `emptyDir`，没有降低 namespace 安全等级；Loki gateway、Loki、Prometheus 与 master 节点 Alloy 曾分别达到 Ready。

## 未通过与残余风险

1. 目标集群仅检测到 Flannel CNI，未检测到可执行标准 NetworkPolicy 的策略引擎。API 接受 NetworkPolicy 对象不代表数据面实施隔离；必须先引入并验证兼容的策略能力，或在具备该能力的集群复测。
2. 工作节点在并发拉取参考栈镜像时一度停止上报 kubelet 心跳并变为 `NotReady`。节点在清理隔离工作负载后恢复 `Ready`，但全节点 Alloy、Grafana、端到端日志元数据和 WAL 故障恢复 smoke 未形成通过证据。
3. 参考后端为单副本与本地 PVC，只验证持久化边界，不提供生产 HA、对象存储、备份或已批准的 RPO/RTO。
4. 告警规则已加载，但通知路由、抑制、静默和升级链路尚未接入目标环境 Alertmanager。
5. 独立安全/隐私与运维评审仍未完成；实现者不批准自己的生产门禁。

## 清理证据

本次验证只使用 `jstore-observability` 与一次性 `jstore-observability-dryrun` namespace。已删除隔离工作负载、Secret、PVC、`alloy-log-reader`/`jstore-prometheus-discovery` ClusterRole 与 Binding，以及 `/tmp` 验证文件；没有修改既有 `monitoring`、Redis、PostgreSQL、Grafana 或全局 containerd/CNI 配置。隔离 PVC 中只有本次合成验证数据，删除后不可恢复。
