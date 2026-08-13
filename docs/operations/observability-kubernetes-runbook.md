# Kubernetes 可观测性运行手册

## 适用范围与前置检查

本手册部署独立的 `jstore-observability` 参考命名空间，不替换集群现有 monitoring 栈。它适合开发/集成环境验证多节点日志采集、安全边界与持久化，不是 Loki/Prometheus 生产高可用方案。

要求：

1. Kubernetes 1.28+、默认动态 StorageClass、Linux 节点和支持 NetworkPolicy 的 CNI。
2. `kubectl`、OpenSSL、POSIX shell 可用；操作者对本命名空间和本清单中的 ClusterRole/Binding 有明确授权。
3. 先确认上下文：`kubectl config current-context`。脚本强制要求显式传入相同上下文。
4. 生产证书必须由受信 CA/cert-manager/Secret 管理系统提供；`--generate-development-tls` 只允许隔离开发环境。

## 工作负载接入契约

需要采集日志的 Pod 添加以下标签；不带启用标签的 Pod 不会被 Alloy 读取：

```yaml
metadata:
  labels:
    app.kubernetes.io/name: j-store-order
    jstore.logs/enabled: "true"
    jstore.logs/environment: development
```

需要 Prometheus 抓取的 Pod 还应添加 `jstore.metrics/enabled=true` 以及标准 scrape 注解。其 namespace 必须由集群管理员显式标记：

```bash
kubectl label namespace <app-namespace> jstore.observability/metrics-access=true
```

完整片段见 `deploy/kubernetes/observability/examples/jstore-workload-metadata.yaml`。应用使用 `observability` profile 向 stdout 输出 ECS JSON；不得把 Secret 值、请求体或原始消息载荷写入日志。

## 安装

先只创建命名空间：

```bash
kubectl apply -f deploy/kubernetes/observability/base/namespace.yaml
```

从外部秘密管理系统提供强随机口令和 CA/证书。以下开发示例不会把 Secret 写入仓库，但会在目标集群创建 Secret：

```bash
export GRAFANA_ADMIN_PASSWORD='<至少 20 位随机值>'
export LOKI_GATEWAY_PASSWORD='<独立的至少 20 位随机值>'
bash ./scripts/kubernetes-observability-secrets.sh \
  --context "$(kubectl config current-context)" \
  --generate-development-tls
unset GRAFANA_ADMIN_PASSWORD LOKI_GATEWAY_PASSWORD
```

生产环境省略开发证书开关，改用 `--tls-cert`、`--tls-key` 和 `--ca-cert`。随后校验并部署：

```bash
kubectl kustomize deploy/kubernetes/observability/base >/tmp/jstore-observability.yaml
kubectl apply --dry-run=server -f /tmp/jstore-observability.yaml
kubectl apply -f /tmp/jstore-observability.yaml
kubectl -n jstore-observability rollout status daemonset/alloy --timeout=5m
kubectl -n jstore-observability rollout status statefulset/loki --timeout=5m
kubectl -n jstore-observability rollout status statefulset/prometheus --timeout=5m
kubectl -n jstore-observability rollout status statefulset/grafana --timeout=5m
```

部署完成后运行隔离 smoke；它会创建合成日志 DaemonSet、短暂停止 Loki、重启同一 Alloy Pod 内的容器来验证 WAL，并在退出时恢复 Loki、删除合成工作负载：

```bash
bash ./scripts/kubernetes-observability-smoke.sh \
  --context "$(kubectl config current-context)"
```

如果该命令在最后报告 CNI 未执行 NetworkPolicy，安全门禁仍为失败。`--skip-network-policy-enforcement` 只用于继续验证日志/TLS/WAL 等其余能力，不能作为网络隔离通过的证据。

远程开发集群的 Docker Hub 镜像源不稳定时，可显式改用 `deploy/kubernetes/observability/overlays/development-registry-mirror`。该 overlay 使用 DaoCloud 公共镜像代理，只改写 registry、不改变版本、权限或数据配置；它不应进入生产供应链，使用前必须重新核对上游 digest 和镜像来源。不得据此修改全局 containerd 配置。

不要提交 `/tmp/jstore-observability.yaml` 或从集群导出的 Secret。

## 安全访问与查询

清单不创建 Ingress、NodePort 或 LoadBalancer。使用 Kubernetes API 隧道访问 Grafana：

```bash
kubectl -n jstore-observability port-forward service/grafana 3000:3000
```

浏览 `http://127.0.0.1:3000`，使用 Secret 中的管理员身份。端口转发通道由 Kubernetes API 的身份认证、RBAC 和 TLS 保护；本地 HTTP 只绑定回环地址。

常用 LogQL：

```logql
{service_name="j-store-order", environment="development", namespace="j-store"} | json
{service_name="j-store-order"} | correlation_id="<合成 ID>"
{service_name="j-store-order"} | trace_id="<trace ID>"
```

高基数的 pod、node、trace、correlation 和消息标识是 structured metadata，不应加入 Loki 标签。

## 健康、容量与恢复

```bash
kubectl -n jstore-observability get daemonset,statefulset,deployment,pod,pvc
kubectl -n jstore-observability top pod
kubectl -n jstore-observability logs daemonset/alloy --tail=100
```

- Alloy 每节点默认发送队列 32 MiB，WAL 最长 segment age 为 1 小时，位于上限 1 GiB 的 `emptyDir`。它能跨同一 Pod 的容器进程重启保留，但 Pod 重建或节点故障会丢失未发送 WAL；清单为保持 restricted Pod Security 不使用 hostPath，也不读取容器运行时 socket。
- Loki 与 Prometheus 默认各申请 20 GiB、保留 7 天；Prometheus 的 TSDB 使用上限为 16 GB。Grafana 申请 5 GiB。
- PVC 只是持久化，不等同备份。生产 overlay 必须定义快照、恢复演练、RPO/RTO 和 StorageClass；默认 StorageClass 不支持扩容时需迁移 PVC。
- 关注 `loki_write_batch_retries_total`、`loki_write_dropped_entries_total`、`loki_write_dropped_bytes_total` 和 Alloy target 错误。Loki 恢复后，WAL 边界内日志应继续发送。
- Prometheus 预置 Alloy target、日志丢弃/重试、Loki 和业务指标 target 告警规则；基础清单未配置 Alertmanager 通知目标。生产 overlay 必须接入已有 Alertmanager 或等价通知系统，并由运维团队验证路由、抑制与升级链路。
- Alloy 的 Kubernetes API 采集不读取节点日志文件，但会增加 API/kubelet 网络和 CPU；上线前按 Pod 数与日志吞吐压测。

## 更新、回退与清理

更新镜像或 Alloy 配置前先运行配置验证、server-side dry-run 与隔离 smoke。DaemonSet 滚动更新 `maxUnavailable=1`，后端为单副本，更新时存在查询中断窗口。

保留 PVC 回退工作负载：

```bash
kubectl delete -k deploy/kubernetes/observability/base --cascade=foreground
```

Kustomize 删除 StatefulSet 后 PVC 默认仍保留。完全删除是破坏性操作，必须先确认命名空间和 PVC：

```bash
kubectl -n jstore-observability get pvc
kubectl delete namespace jstore-observability
```

删除 namespace 会删除本参考栈 Secret、PVC 与数据，不可恢复；不得对现有 `monitoring` 或业务命名空间执行该命令。ClusterRole/Binding 是集群级资源，若仅删除 namespace，应再按清单精确删除或使用 `kubectl delete -k`。

## 已知边界

- 标准 NetworkPolicy 不能以 Service 名称匹配 Kubernetes API，基础清单把 Alloy/Prometheus 出站限制为 TCP 443，但仍允许该端口的任意地址。生产 overlay 应按 API Service CIDR 或 CNI FQDN/实体能力进一步收敛。
- 单副本 Loki、Prometheus 和 Grafana 不满足生产 HA。对象存储、复制因子、remote-write、备份与容量方案需要独立架构和成本决策。
- NetworkPolicy 只有在 CNI 实现支持时才会生效；安装前必须验证实际 CNI 行为。
- 独立安全/隐私与运维评审仍为生产门禁，实施者不能批准自己的变更。
