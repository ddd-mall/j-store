# Kubernetes 开发部署运行手册

## 范围

本手册在固定 `jstore` namespace 部署单副本 `j-store-boot`，复用集群已有 PostgreSQL、Prometheus、Alertmanager 与 Grafana。目标集群没有应用镜像 registry 时，使用一次性 loader 把本地 `app.jar` 写入专用 PVC；这只适用于开发环境，不是生产制品交付方案。

部署创建独立的 PostgreSQL database `j_store_codex`、role `jstore_app`、带认证的 Redis StatefulSet，以及应用运行 Secret。不会修改已有 `j_store` database、共享 `redis` namespace 或 Helm 管理的 `monitoring` 工作负载。

## 构建与部署

使用 Java 25 构建：

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew :j-store-boot:bootJar --no-daemon --console=plain
```

先核对目标，再运行：

```bash
kubectl config current-context
bash ./scripts/kubernetes-development-deploy.sh \
  --context "$(kubectl config current-context)" \
  --jar j-store-boot/build/libs/app.jar
```

首次运行生成凭据；再次运行复用 namespace 中现有 Secret，避免 Redis 与数据库密码在滚动过程中失配。脚本不打印凭据。JAR 本地与 PVC 端 SHA-256 不一致时不会启动应用。

## 验证与访问

```bash
kubectl -n jstore get deploy,statefulset,pod,pvc,svc,servicemonitor
kubectl get --raw \
  /api/v1/namespaces/jstore/services/http:j-store:8080/proxy/actuator/health/readiness
kubectl get --raw \
  /api/v1/namespaces/jstore/services/http:j-store:8080/proxy/actuator/prometheus | head
```

应用不创建公网入口。需要访问 HTTP API 时：

```bash
kubectl -n jstore port-forward service/j-store 8080:8080
```

现有 Grafana sidecar 会自动发现 `grafana_dashboard=1` 的 dashboard ConfigMap。开发集群可通过 Grafana 现有入口打开 `J-Store Runtime`；dashboard UID 为 `j-store-runtime`。Prometheus 通过 ServiceMonitor 抓取应用指标。

## 更新与回滚

- 更新：重新构建 JAR并重复部署命令；loader 会原子替换 PVC 中的 `app.jar`，Deployment annotation 使用 SHA-256 触发重新创建 Pod。
- 暂停：`kubectl -n jstore scale deployment/j-store --replicas=0`，保留数据库、Secret、Redis 与 artifact PVC。
- 恢复：`kubectl -n jstore scale deployment/j-store --replicas=1`。
- 完全删除 namespace 会同时删除 Secret、Redis 数据和 artifact PVC，但不会删除 PostgreSQL database/role；这是破坏性操作，必须单独确认。数据库删除不属于普通回滚。

## 已知边界

- Flannel 环境没有 NetworkPolicy 执行证据；清单表达了预期策略，但不能据此宣称网络隔离已生效。
- 单副本应用、Redis 和本地 PVC 不满足生产 HA、备份或 RPO/RTO。
- PVC JAR 交付缺少生产所需的镜像签名、SBOM、漏洞扫描与不可变 registry；正式部署必须切换为受控应用镜像。
- 当前复用 Grafana/Prometheus 观测指标；应用日志已输出 ECS JSON 到 stdout，但在接入独立 Loki datasource 前不会出现在现有 Grafana 中。
