# 可观测性基础建设需求

## 状态

工程实现已完成；容器端到端 smoke 和独立安全/运维验收仍待执行。

## 背景

当前根应用使用 Spring Boot 默认控制台文本日志，仅配置根日志级别；仓库没有日志采集器、集中存储、查询或告警配置。集成消息已经携带 `messageId`、`correlationId` 和 `causationId`，Outbox 也已经定义 Micrometer 指标与健康阈值，但运行入口没有 Actuator、指标注册表或导出器，指标会在没有 `MeterRegistry` 时退化为 Noop。

本规格复用 [Outbox 生产化加固](../outbox-production-hardening/requirement.md) 的 R11-R13，不改变既有指标、阈值和健康状态语义，只负责让这些信号进入实际采集与展示链路。

集中采集会放大敏感数据风险，因此必须先建立安全边界，再扩大日志覆盖面。实施审计进一步确认，原订单过期定时任务子系统只有测试 Controller、测试 handler 和压测 SQL，没有任何业务调用者；项目尚未上线，故直接删除该完整垂直切片及其表和 Redis Lua 资源，不为无效能力维护日志兼容层。

## 目标

建立一个可在本地重复验证、可向容器和分布式部署演进的最小日志闭环：

1. 日志不泄露凭据、个人信息或原始业务载荷。
2. 应用以稳定机器可读格式输出日志，并能关联 HTTP 请求、Trace 和集成消息。
3. 本地参考环境能够可靠采集、保存、查询日志，并展示现有 Outbox 运行信号。

## 优先级评选

候选项按三个维度各 1-5 分评估：风险降低、后续工作的前置价值、故障诊断价值。总分越高越优先；同分时，安全风险和依赖顺序优先。

| 候选任务 | 风险降低 | 前置价值 | 诊断价值 | 总分 | 结论 |
|---|---:|---:|---:|---:|---|
| 日志安全基线与敏感数据治理 | 5 | 5 | 4 | 14 | 第 1 优先级 |
| 结构化日志与端到端上下文关联 | 4 | 5 | 5 | 14 | 第 2 优先级，依赖安全基线 |
| 集中采集、查询与运行信号闭环 | 4 | 4 | 5 | 13 | 第 3 优先级，依赖结构化输出 |
| 生产级高可用、长期归档与灾备 | 3 | 2 | 4 | 9 | 后续阶段 |
| 动态日志级别、采样和成本优化 | 2 | 1 | 3 | 6 | 有真实流量数据后实施 |
| 全业务域日志补齐 | 2 | 3 | 4 | 9 | 先建立统一契约，再按故障场景扩展 |

## 行为需求

### OBS-R1：日志安全基线

- 系统不得记录密码、JWT、验证码、密钥、完整手机号、完整地址、支付敏感数据或未经审查的消息/任务原始载荷。
- 生产源码不得保留只由 demo、测试 Controller 或测试 handler 驱动的任务子系统；未来引入真实调度能力时，任务日志只能记录允许的标识和状态，不得依赖包含原始载荷的对象 `toString()`。
- 日志字段规范必须区分允许字段、需脱敏字段和禁止字段，并覆盖异常、HTTP、认证、任务和集成消息场景。
- 相关回归测试必须先确认现有原始内容会被捕获，再实现最小修复。

### OBS-R2：结构化日志契约

- 非交互运行环境必须向 `stdout/stderr` 输出逐行 JSON，不直接从业务线程同步写入远程日志后端。
- 每条日志至少包含时间、级别、logger、消息、`service.name`、服务版本和部署环境；异常日志必须保留错误类型和可诊断堆栈。
- 应优先使用 Spring Boot 3.5 原生 ECS JSON 能力，避免引入只为 JSON 编码服务的额外运行时依赖。
- 根组合应用的服务名不得继续误导性地标识为仅订单服务；未来拆分部署时，每个可部署单元必须拥有独立稳定的 `service.name`。

### OBS-R3：HTTP 与 Trace 关联

- 每个入口 HTTP 请求必须获得受长度和字符集约束的 `correlation_id`；合法上游值可以延续，无合法值时由服务生成，并在响应头返回。
- 启用追踪时，结构化日志必须自动包含 `trace_id` 和 `span_id`，并使用 W3C Trace Context 传播。
- 上下文必须在请求完成后清理；并发请求、线程复用和异常路径不得串用上一请求的标识。
- 远程 HTTP 客户端必须使用 Spring 自动配置的 Builder，以获得统一观测和上下文传播能力。

### OBS-R4：消息关联

- 发布和消费集成消息时，日志上下文必须包含已有的 `message_id`、`correlation_id`、可选 `causation_id` 和 `transport_id`。
- 消息上下文必须以作用域方式建立并可靠清理，重复消费或 handler 异常不得污染后续消息。
- 业务相关 ID 与 Trace ID 必须保持不同语义；现有以订单 ID 表示 Saga 关联的行为不得被错误解释为请求 Trace。

### OBS-R5：集中采集与查询

- 仓库必须提供可选的本地可观测性 Compose 覆盖层，不改变默认 PostgreSQL/Redis 开发流程。
- 参考栈必须能够采集应用容器 JSON 日志、补充容器/服务元数据并写入集中日志后端；采集端必须具有位置记录、有限磁盘缓冲、背压上限和健康指标。
- Loki 标签只能使用服务、环境、级别等低基数字段；订单 ID、用户 ID、消息 ID、correlation ID 和 trace ID 必须保留为结构化字段而非索引标签。
- 本地环境必须预置日志数据源和最小查询视图，能够按服务、级别、时间、correlation ID 和 trace ID 定位日志。

### OBS-R6：现有运行信号真正可用

- 根应用必须提供受控的 Actuator 健康和 Prometheus 指标端点；不得公开高风险诊断端点。
- Outbox 的积压、失败、死信、过期锁、最近调度成功和连续失败指标必须进入实际 `MeterRegistry`，不得在参考运行环境中回退为 Noop。
- 必须提供至少一个 Outbox 异常查询或 Dashboard，并验证阈值状态能够被运维人员发现。

## 质量目标

- **安全与隐私**：集中采集前完成禁止字段检查；日志系统和 Dashboard 不保存真实凭据，示例数据必须为合成数据。
- **可靠性**：在后端短暂不可用且未超过配置缓冲容量时，采集器恢复后应继续投递；超过容量时必须产生可观测的丢弃或背压信号。
- **性能**：业务线程不得因远程日志后端不可用而阻塞；结构化日志和追踪开销必须通过基线测试或受控压测记录。
- **兼容性**：默认本地业务启动方式保持可用；可观测性栈通过显式 Compose 覆盖层启用。
- **可维护性**：字段、标签和敏感数据策略必须集中定义，不允许各业务模块自创同义字段。
- **可验证性**：每个迭代必须有自动化测试或可重复 smoke test，并记录命令和结果。

## 非范围

- 第一阶段不承诺生产 Loki/Elastic 集群的高可用、跨地域灾备或长期归档。
- 不在本阶段绑定云厂商托管日志产品。
- 不要求一次性为所有领域服务添加业务日志；只覆盖公共入口、消息边界和已经确认的关键故障路径。
- 不把日志当作审计账本、业务事件存储或 exactly-once 消息凭证。
- 不在第一阶段建设完整 SLO、值班和事故管理体系。

## 参考依据

- [Kubernetes Logging Architecture](https://kubernetes.io/docs/concepts/cluster-administration/logging/)
- [OpenTelemetry Logging](https://opentelemetry.io/docs/specs/otel/logs/)
- [Spring Boot 3.5 Structured Logging](https://docs.spring.io/spring-boot/3.5/reference/features/logging.html#features.logging.structured)
- [Spring Boot 3.5 Tracing](https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html)
- [Fluent Bit Buffering](https://docs.fluentbit.io/manual/administration/buffering-and-storage)
- [Grafana Loki Labels](https://grafana.com/docs/loki/latest/get-started/labels/)
