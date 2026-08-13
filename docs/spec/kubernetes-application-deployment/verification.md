# J-Store Kubernetes 开发部署验证

## 已验证结果

- `:j-store-boot:bootJar` 在 Java 25 工具链下成功，生成的 Spring Boot JAR 为 105,205,780 字节；上传前后 SHA-256 一致。
- 目标 Kubernetes `v1.28.15` 对 14 个渲染资源的 server-side dry-run 通过。
- 应用 Deployment 与 Redis StatefulSet 均为 `1/1 Ready`，两个 PVC 均为 `Bound`。
- 应用启动日志确认使用 Java `25.0.4`，激活 `local,observability`，并以 ECS JSON 输出到 stdout。
- PostgreSQL 保留已有 `j_store`，另建 `j_store_codex`；Flyway 最新记录为 `20260812:true`，共成功应用 12 个迁移。
- 隔离 Redis 的认证探针与人工 `PING` 均返回 `PONG`。
- readiness 返回 `{"status":"UP"}`；Prometheus endpoint 返回 JVM 指标。
- 现有 Prometheus 查询 `up{namespace="jstore"}` 返回应用 target 值 `1`。
- 现有 Grafana API 返回 dashboard UID `j-store-runtime`、标题 `J-Store Runtime`。
- 应用和 Redis 只有 ClusterIP，没有新增 Ingress、NodePort 或 LoadBalancer。

## 运行观察

- Redis 镜像首次拉取约 2 分钟；Amazon Corretto 25 镜像首次拉取约 18 分 25 秒，期间 worker 短暂 `NotReady` 后恢复。镜像成功缓存后应用 Pod 无需重复拉取。
- 105 MiB JAR 通过 Kubernetes exec 写入 PVC 约 12 分钟。该路径可用于开发验证，但证明了生产环境必须使用正式 registry 与不可变镜像。

## 残余风险

1. 集群当前只有 Flannel，NetworkPolicy 缺少数据面执行证据；网络隔离门禁未通过。
2. 指标已进入现有 Grafana/Prometheus；日志仍只有 Kubernetes stdout。若要在 Grafana 查询日志，需另行部署/接入 Loki datasource 与日志采集器。
3. 单副本 Redis、应用与 local-path PVC 不提供高可用和备份保证。
4. 开发 JAR/PVC 交付没有生产镜像供应链保证，不能作为发布方案。
