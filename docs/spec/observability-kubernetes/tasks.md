# Kubernetes 可观测性部署迭代计划

## 任务

- [x] KOBS-T1：建立 Kubernetes 部署、安全、可靠性与验证契约。（KOBS-R1-R5）
- [x] KOBS-T2：增加清单契约测试，先暴露 DaemonSet、RBAC、TLS、网络隔离、PVC 与资源边界缺口。（KOBS-R1-R5）
- [x] KOBS-T3：实现 Alloy DaemonSet、当前节点发现、元数据处理、节点 WAL 和最小 RBAC。（KOBS-R1-R3）
- [x] KOBS-T4：实现 Loki gateway/Loki、Prometheus、Grafana 的受限 StatefulSet、PVC、资源、探针、告警和数据源 provisioning。（KOBS-R2-R4）
- [x] KOBS-T5：实现 default-deny 与最小 allow NetworkPolicy，提供无仓库凭据的 Secret 引导脚本。（KOBS-R3-R4）
- [x] KOBS-T6：编写 Kubernetes 运行手册与工作负载接入契约。（KOBS-R1-R5）
- [x] KOBS-T7a：通过本地静态检查、Kustomize 契约、Alloy 配置与 Prometheus 配置/规则校验。（KOBS-R1-R5）
- [x] KOBS-T7b：在 Kubernetes 1.28.15 目标 API 上通过 34 个资源的 server-side dry-run。（KOBS-R1-R5）
- [ ] KOBS-T7c：完成目标集群隔离 smoke。当前被工作节点镜像拉取失稳和 Flannel 无 NetworkPolicy 执行能力阻塞，不能记为通过。（KOBS-R1-R5）
- [x] KOBS-T7d：清理本次隔离验证的 namespace、集群级 RBAC 和临时文件，不修改既有 monitoring/Redis/PostgreSQL。（KOBS-R5）

## 退出门禁

- 每个目标 Linux 节点恰有一个 Ready Alloy，且没有重复采集证据。
- 合成日志可按 service/environment/namespace 查询，并能返回 pod/container/node、correlation/trace 结构化字段。
- 未认证 Loki gateway 请求失败；受信 CA 与凭据请求成功；仓库扫描没有 Secret/私钥/真实凭据。
- Loki、Prometheus、Grafana 使用 PVC，所有工作负载具备资源上限、探针和受限 securityContext。
- 目标集群 dry-run 与隔离 smoke 通过，临时命名空间已精确清理。
- 独立安全/隐私与运维评审仍需由非实现者完成；实现者不自行批准生产退出门禁。
