# 结账可靠异步基础需求

## 背景与目标

项目已有领域事件、集成消息、Transactional Outbox、本地 Inbox、顺序、重试和死信能力，但当前集成消息使用全局 transport 目标，且结账契约缺少统一的业务受理截止时间、投递等级和版本感知的稳定 ID。

本次变更交付订单—库存—支付可靠异步计划的第一切片：在保持默认本地集成行为的前提下，使集成消息能够按逻辑目的地规划物理投递，并把路由、业务时限和投递等级稳定写入 Outbox/Envelope。

## 行为需求

- CRA-R1：领域事件继续只表达本上下文事实，不得携带 Broker、Topic、重试或同步/异步模式。
- CRA-R2：`IntegrationCommand` 必须支持可选 `acceptBefore`；非空时不得早于 `occurredAt`。集成事件不具有命令受理截止时间。
- CRA-R3：稳定集成消息 ID 必须包含真实消息版本，并支持以稳定来源消息 ID 和业务键生成；相同输入必须得到相同 ID，不同版本必须得到不同 ID。
- CRA-R4：发布规划必须以消息的逻辑 `destination` 为路由键。显式路由可以为一个逻辑目的地配置一个或多个独立投递目标，每个目标包含 transport、物理 destination 和 delivery profile。
- CRA-R5：未配置显式路由时，必须使用全局 `jstore.messaging.targets` 默认投递规则；物理 destination 等于逻辑 destination，delivery profile 为 `STANDARD`。
- CRA-R6：路由定义中的逻辑目的地、transport、物理 destination 和 delivery profile 必须非空；同一逻辑目的地不得重复定义，同一路由内同一 transport 不得重复。
- CRA-R7：启动时必须校验所有默认目标及显式路由目标各自恰好存在一个投递通道；不得静默降级。
- CRA-R8：Outbox 必须分别持久化逻辑 destination、物理 destination、delivery profile、命令 acceptBefore 和 publishedAt；领域事件使用 `LOCAL_DOMAIN` profile 且无 acceptBefore。
- CRA-R9：外部 `IntegrationMessageEnvelope` 必须携带逻辑 destination、物理 destination、delivery profile 和 acceptBefore，使 Transport 不依赖业务消息类即可路由和执行通用校验。
- CRA-R10：Outbox 成功投递时必须记录 `publishedAt`；失败或待处理记录不得伪造发布时间。
- CRA-R11：数据库迁移必须完整创建逻辑 destination、delivery profile、acceptBefore 和 publishedAt，并保证新模型的非空约束与索引可由 PostgreSQL 验证；不要求兼容未上线版本的消息载荷或公开 API。
- CRA-R12：Checkout 关键命令契约必须能够携带 `acceptBefore`；库存预留成功事实必须携带 reservation 过期时间。
- CRA-R13：订单、库存、支付现有默认 local 链路不得因路由模型升级而发生行为回归。

## 质量目标

- 路由和消息 ID 为框架无关模型，测试不依赖 Spring。
- Outbox PO 与领域模型新增字段必须完整往返。
- PostgreSQL 迁移测试必须验证新字段、约束、索引及必要的数据初始化。
- 集成契约序列化测试必须覆盖新增时间字段。
- 相关模块测试和全量回归必须通过。

## 非范围

- 不选择或接入具体 Kafka/RabbitMQ 产品。
- 不实现 Broker 入站 Consumer。
- 不实现 Checkout Process Manager、两阶段关单和支付机构取消协议。
- 不引入 Outbox 提交后唤醒或 CDC。
- 不把 transport 过期等同于业务命令过期；本切片只可靠传播 `acceptBefore`。
