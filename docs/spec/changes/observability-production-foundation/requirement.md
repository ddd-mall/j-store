# 生产可观测性应用闭环需求

## 背景与现状

J-Store 已具备 ECS JSON 日志、Actuator/Prometheus、W3C Trace Context、Outbox 指标以及 Kubernetes 开发参考栈，但仍存在三个直接影响生产配置正确性和故障发现的缺口：

1. 根配置默认激活 `local`，未显式选择 profile 时会加载开发数据库、Redis、JWT 和 100% Trace 采样语义。
2. `OutboxOperationalHealth` 只提供内部快照和指标，没有进入 Actuator health，运维无法从标准健康端点判断投递退化。
3. Prometheus 已加载规则文件，但没有 Outbox 积压、死信、过期锁或 scheduler 连续失败告警。

本变更是 `docs/spec/observability-production-distributed/iteration-plan.md` 中迭代 1 的首个可独立交付切片，不宣称完成生产级分布式部署。

## 目标

### OPF-R1：Profile 必须显式选择

- 应用在未提供 active profile 时不得自动激活 `local` 或 `production`。
- 本地运行继续通过显式 `local` profile 获得现有开发配置。
- `production` profile 必须自动启用 `observability`，使用稳定的 `j-store` 服务和连接池身份，并要求数据库、Redis 与安全凭据由外部环境提供。
- production 配置不得为密码、JWT secret、数据库地址或 Redis 地址提供开发默认值。

### OPF-R2：Outbox 必须提供标准健康组件

- Outbox 启用且 `OutboxOperationalHealth` 存在时，Actuator 必须注册名为 `outbox` 的 `HealthIndicator`。
- `HEALTHY` 映射为 `UP`，`DEGRADED` 映射为自定义 `DEGRADED`，`FAILED` 映射为 `DOWN`，尚未运行映射为 `UNKNOWN`。
- health details 只能包含状态、延迟/数量、告警原因、scheduler 时间和有限 transport 状态；不得包含 payload、聚合 ID、用户标识、凭据或异常正文。
- `outbox` 不得进入 liveness 或 readiness；应通过独立 `operations` health group 查询，避免共享依赖或积压触发重启/全量摘流。

### OPF-R3：Outbox 异常必须形成可加载告警

- Prometheus 规则必须覆盖全局 Outbox ready lag、dead letter、expired lock 和 scheduler 连续失败。
- 告警必须使用现有低基数指标和有限枚举标签，不得按 event type、aggregate ID、message ID 或用户标识聚合。
- 规则恢复条件必须随对应 gauge 恢复为正常而自动解除。
- 参考栈的清单契约和 Prometheus 规则语法验证必须覆盖新增规则。

### OPF-R4：可观测性必须支持独立部署单元复用

- 通用 Spring 可观测性能力必须由独立模块提供，业务 `*-boot` 不得复制 correlation filter、Actuator、Tracing 和 Prometheus 依赖装配。
- 通用模块不得依赖 Outbox、JPA 或任何业务有界上下文；非 Web 应用引入通用模块时不得被强制引入 Servlet Web 运行时。
- Outbox 指标、状态映射和 HealthIndicator 必须由 `j-store-outbox-spring` 拥有，并在 Actuator 与 `OutboxOperationalHealth` 同时存在时条件装配。
- 通用 observability profile 不得引用 `outbox` health contributor；Outbox operations group 必须由明确使用 Outbox 的部署单元选择。
- 集群侧 Agent、Collector、Prometheus、Loki、Grafana 和告警规则继续属于部署层，不进入 JVM 通用模块。

## 非目标

- 不在本切片引入 OTLP exporter、OpenTelemetry Collector 或 Trace 后端。
- 不把当前单副本 Loki/Prometheus/Grafana 宣称为生产 HA。
- 不修改生产数据库、远程 Kubernetes 集群、Secret 或权限。
- 不建立 Alertmanager 外部通知接收方；真实通知路由仍需平台 owner 在后续环境迭代批准。

## 验收标准

1. 静态配置测试证明根配置没有默认 active profile，production 不含开发默认连接或 secret，Kubernetes 开发清单仍显式使用 `local,observability`。
2. 单元测试覆盖四种 Outbox 状态映射、sanitized details，以及 liveness/readiness/operations group 配置。
3. Kubernetes 可观测性契约识别四条 Outbox 告警，并确认表达式只使用批准的低基数 gauge。
4. `:j-store-boot:test`、Kubernetes tooling tests 和适用质量门禁通过；任何未运行检查明确记录。
5. 模块契约证明 `j-store-observability-spring` 不依赖业务或 Outbox 模块；Servlet 应用自动获得 correlation filter，非 Servlet 上下文不注册该过滤器。
6. Outbox 模块测试证明 HealthIndicator 能随 Actuator/Outbox 条件装配；根应用不再持有该实现或通用过滤器实现。
