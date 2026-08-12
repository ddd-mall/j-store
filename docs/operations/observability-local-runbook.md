# 本地可观测性运行手册

## 适用范围

本手册用于启动 J-Store、PostgreSQL、Redis、Grafana Alloy、Loki、Prometheus 和 Grafana 的本地参考栈。它证明日志与 Outbox 指标的纵向闭环，不代表生产容量、高可用、认证或长期归档方案。

镜像已固定为 Alloy `1.18.0`、Loki `3.6.12`、Prometheus `3.13.2` 和 Grafana `13.1.0`。升级前必须重新执行配置验证与 smoke test。

## 前置条件

1. JDK 25、Docker Engine 与 Docker Compose v2 可用。
2. 将 `.env.example` 复制为未跟踪的 `.env`，替换全部 `change-me` 值；不得提交 `.env`。
3. Windows 或 macOS 的 Docker Desktop 必须允许 Linux 容器访问 `/var/run/docker.sock`。该只读挂载仍等同于较高主机可见权限，只应用于受信任的本地开发机。

## 基线迁移重建

本变更按内部开发期策略直接修改了 `V20260507__baseline_j_store_boot_schema.sql`。如果当前 Compose project 的 PostgreSQL 卷已经执行过旧版基线，Flyway 会因 checksum 不一致拒绝启动；必须先备份任何仍需保留的开发数据，再重建本地卷。

以下命令是破坏性操作，会删除当前 Compose project 的 PostgreSQL 和可观测性命名卷。只在仓库根目录确认目标 project 后执行：

```powershell
docker compose -f docker-compose.postgres.yml -f docker-compose.observability.yml down --volumes
```

全新环境或尚未执行旧版基线的环境不需要此步骤。不得在包含不可丢弃数据的环境执行该命令。

## 构建与启动

```powershell
.\gradlew.bat :j-store-boot:bootJar
docker compose -f docker-compose.postgres.yml -f docker-compose.observability.yml config --quiet
docker compose -f docker-compose.postgres.yml -f docker-compose.observability.yml up -d --build
```

等待以下默认端点就绪：

- J-Store：`http://localhost:8080/actuator/health`
- Loki：`http://localhost:3100/ready`
- Alloy：`http://localhost:12345/-/ready`
- Prometheus：`http://localhost:9090/-/ready`
- Grafana：`http://localhost:3000/api/health`

若 `.env` 修改了 `JSTORE_PORT`、`LOKI_PORT`、`ALLOY_PORT`、`PROMETHEUS_PORT` 或 `GRAFANA_PORT`，手工访问时使用对应端口；自动 smoke test 会按“进程环境变量 → `.env` → 默认值”解析实际地址。

Grafana 使用 `.env` 中的 `GRAFANA_ADMIN_USER/GRAFANA_ADMIN_PASSWORD` 登录。Loki 和 Prometheus 数据源以及 `J-Store Observability` Dashboard 会自动 provision，无需点击创建。

## 查询

产生一条带合成关联 ID 的安全日志：

```powershell
$correlationId = "smoke-$([guid]::NewGuid().ToString('N'))"
Invoke-WebRequest http://localhost:8080/actuator/health -Headers @{"X-Correlation-ID"=$correlationId}
```

在 Grafana Explore 中查询：

```logql
{service_name="j-store"} | json | correlation_id="<合成 ID>"
```

按 Trace ID 查询：

```logql
{service_name="j-store"} | json | trace_id="<trace ID>"
```

Prometheus 中验证应用与 Outbox：

```promql
up{job="j-store"}
jstore_outbox_oldest_ready_lag{transportId="all"}
jstore_outbox_entries{status="DEAD_LETTER",transportId="all"}
jstore_outbox_expired_locks{transportId="all"}
jstore_outbox_scheduler_consecutive_failures
```

Actuator 默认只暴露 `health` 与 `prometheus`。不得把 `env`、`heapdump`、`configprops` 等端点加入本地默认暴露清单并原样带到共享环境。

## 自动 smoke test

```powershell
.\scripts\observability-smoke.ps1 -KeepRunning
```

脚本验证服务就绪、唯一 correlation ID 可从 Loki 查询、Prometheus 抓取成功、Grafana 数据源健康，并在 Loki 暂停期间重启 Alloy 后验证 WAL 续传。脚本不会生成或持久化真实凭据，依赖当前 `.env`。

## 容量、保留与故障信号

- Loki 与 Prometheus 默认保留 7 天；本地命名卷不会按磁盘剩余空间自动清理。
- Alloy 的发送队列限制为 32 MiB，溢出时不阻塞应用；实验性 WAL 的 segment 最长保留 1 小时。WAL 是按时间而非按字节限制，日志峰值很高时必须关注 `alloy_data` 卷大小。
- 重点监控 `loki_write_batch_retries_total`、`loki_write_dropped_entries_total`、`loki_write_dropped_bytes_total`、`loki_source_docker_target_parsing_errors_total` 和 `loki_process_dropped_lines_total`。
- Loki 不可用时应用继续写 stdout；Alloy 在队列/WAL 边界内重试。达到边界后允许丢弃并通过上述指标显式暴露，不允许反压业务线程或无限占用磁盘。

查看卷容量：

```powershell
docker system df -v
docker compose -f docker-compose.postgres.yml -f docker-compose.observability.yml exec alloy du -sh /var/lib/alloy
docker compose -f docker-compose.postgres.yml -f docker-compose.observability.yml exec loki du -sh /loki
```

## 停止、恢复与清理

保留数据停止：

```powershell
docker compose -f docker-compose.postgres.yml -f docker-compose.observability.yml down
```

服务恢复后 Alloy 使用 positions 与 WAL 从已确认位置继续；Loki、Prometheus 和 Grafana 使用命名卷恢复。

删除本参考栈数据是破坏性操作。先确认当前 Compose project 与卷名，再显式执行：

```powershell
docker compose -f docker-compose.postgres.yml -f docker-compose.observability.yml down --volumes
```

该命令同时删除本 Compose project 的 PostgreSQL 数据卷，不可恢复。若只清理可观测性卷，应先用 `docker volume ls` 确认精确名称，再逐个删除；不得使用宽泛通配符。

## 已知边界

- Loki、Prometheus 和 Grafana 端口仅面向本地开发；共享或生产环境必须增加网络隔离、认证、TLS 与权限模型。
- Docker socket 采集器具有高权限可见性，生产应改为节点级受限采集部署。
- Alloy Loki WAL/queue 仍是实验特性，升级 Alloy 时必须重新验证。
- 安全日志规范见 `docs/operations/logging-policy.md`，任何扩大采集范围的变更都必须先通过隐私审查。
