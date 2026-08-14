# 生产可观测性应用闭环设计

## 决策概览

| 决策 | 选择 | 原因 |
|---|---|---|
| 配置激活 | 根配置无默认 profile；保留显式 `local`，新增 `production` profile group 自动组合 `observability` | 防止生产误加载开发配置，同时保持本地入口清晰 |
| 通用运行时 | 新增 `j-store-observability-spring` | 统一 Spring Boot 可观测性依赖和 HTTP correlation 自动配置，不依赖业务、Outbox、JPA 或 Servlet 运行时 |
| HealthIndicator 所在模块 | 下沉至 `j-store-outbox-spring`，Actuator 使用可选编译依赖 | 指标和健康语义归组件所有；无 Actuator 或无 Outbox health bean 时不注册 |
| 探针关系 | liveness 只含 `livenessState`，readiness 只含 `readinessState`；新增 `operations` group 只含 `outbox` | Outbox 退化需要诊断和告警，但通常不表示进程应重启或 HTTP 必须摘流 |
| 告警信号 | 使用 `jstore_outbox_alert{reason,transportId="all"}`，补充有限枚举 `scheduler_failure` | 复用 Outbox 已计算的阈值事实，避免 Prometheus 维护第二套阈值 |

## 配置结构

`application.properties` 只保存所有环境共享的服务名、优雅停机、Flyway 行为和 profile group：

```properties
spring.application.name=j-store
spring.profiles.group.production=observability
```

`application-local.properties` 继续承载开发默认值，但连接池名从历史 `j-store-order` 修正为 `j-store`。新增 `application-production.properties`：

- 数据库 URL、用户名、密码全部要求 `JSTORE_DB_*`；
- Redis host、port、password 要求 `JSTORE_REDIS_*`；
- JWT 与手机号验证 secret 必须来自环境；
- 关闭 Open Session in View；
- production schema、pool size、Outbox 阈值允许通过显式环境变量覆盖，但默认值必须是非敏感运行策略而非开发连接信息。

## Outbox HealthIndicator

`OutboxHealthIndicator` 只负责把既有快照映射为 Actuator `Health`，不重新查询数据库或计算阈值：

```text
OutboxOperationalHealth.snapshot()
        |
        +-- HEALTHY  -> UP
        +-- DEGRADED -> DEGRADED
        +-- FAILED   -> DOWN
        +-- NOT_RUN  -> UNKNOWN
```

details 使用固定键：`observedAt`、`oldestReadyLagSeconds`、`expiredLockCount`、`deadLetterCount`、`activeAlerts`、`scheduler`、`transports`。transport map 只暴露 transport ID、状态和三个有限计数/延迟，不包含消息内容。

Bean 由 Outbox 自动配置导入，通过 Actuator classpath 和 `OutboxOperationalHealth` 双重条件注册，因此 Outbox 或 Actuator 任一缺失时都不会影响应用上下文。

## Health group

`application-observability.properties` 明确：

```properties
management.endpoint.health.group.liveness.include=livenessState
management.endpoint.health.group.readiness.include=readinessState
```

通用 profile 只定义日志、指标、Trace、端点暴露和标准 liveness/readiness，不引用可选组件。聚合应用另行选择 `outbox-observability` profile；该 profile 定义 operations group、自定义 `DEGRADED` 状态顺序和 HTTP 映射。无 Outbox 的微服务只启用通用 profile，不会因缺少 health contributor 启动失败。

## 模块与依赖边界

```text
业务 *-boot ──> j-store-observability-spring ──> Spring Boot Actuator/Tracing
业务 *-boot ──> j-store-outbox-spring ──> outbox-core/messaging-core
                                    └─(Actuator 可选)─> OutboxHealthIndicator
deploy/kubernetes/observability ──> Collector/Prometheus/Loki/Grafana
```

`j-store-observability-spring` 通过 Spring Boot AutoConfiguration 为 Servlet 应用条件注册 `CorrelationIdFilter`。Web API 使用 `compileOnly`，因此 worker 型微服务不会仅因引入通用观测模块而获得 Tomcat/MVC。Actuator、Micrometer Tracing bridge 和 Prometheus registry 由该模块向部署单元提供；各组件仍在自身模块注册专属 meter、health 和 span。

## 告警规则

新增四条规则：

- `JStoreOutboxReadyLagHigh`
- `JStoreOutboxDeadLettersPresent`
- `JStoreOutboxExpiredLocksPresent`
- `JStoreOutboxSchedulerFailing`

四条规则均使用 `jstore_outbox_alert` 的 `reason` 有限枚举与 `transportId="all"`。应用把 scheduler 达到失败阈值映射为 `scheduler_failure`，Prometheus 不重复硬编码阈值；该 gauge 直接读取内存中的 scheduler state，不为一次指标抓取额外查询 Outbox 仓储。规则不依赖某个动态 transport，transport 级诊断留给 dashboard。

## 验证与回退

- RED：先添加配置、状态映射和告警契约测试，确认当前实现失败。
- GREEN：增加最小配置、HealthIndicator 和 YAML 规则。
- REFACTOR：保持 mapper 无 I/O，details 构造函数职责单一。
- 模块边界：契约测试覆盖通用模块的 Servlet/非 Servlet 条件装配，以及 Outbox health 的组件自有条件装配。
- 回退时可移除 production profile 与 HealthIndicator 配置；已有日志、指标及 local profile 不受影响。告警规则移除不会改变业务写入或数据库状态。

## 后续切片

1. Java Agent/当前 Micrometer bridge 重复埋点 spike 与 OTLP ADR。
2. HA Collector、Tempo/Jaeger 和端到端订单 Trace。
3. 不可变 OCI 镜像、双副本、RollingUpdate、PDB 与连接预算。
4. Alertmanager 外部通知路由及真实故障演练。
