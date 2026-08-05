# 需求：订单上下文分层模块拆分与事务修复

## 目标

将订单上下文从混合的 `j-store-order` 模块拆分为纯领域、纯应用、基础设施和 Spring 装配四个模块，并修复订单聚合保存与 Outbox 事件写入不在同一事务中的数据一致性问题。

本项目尚未上线，本变更允许删除旧模块和破坏内部 Gradle/API 兼容性；订单 HTTP 与集成消息的既有外部行为不应因分层重构改变。

## 验收标准

### R1 模块边界

1. `j-store-order-domain` 只承载订单/售后领域模型、领域端口、聚合仓储接口及 ACL 端口。
2. `j-store-order-application` 承载用例编排和集成消息 handler，只依赖 domain、integration-contracts 和 common-core。
3. `j-store-order-infrastructure` 承载 JPA、PostgreSQL、Redis 和外部查询 ACL 适配器，只能向内依赖订单 domain。
4. `j-store-order-boot` 承载 Controller、Spring Bean 装配和应用用例事务装饰器。
5. 删除旧 `j-store-order` Gradle 模块；根 `j-store-boot` 通过 `j-store-order-boot` 组合订单上下文。
6. domain/application 源码不得 import Spring、Jakarta Persistence 或 Hibernate。

### R2 事务与事件原子性

1. 每个会修改订单或售后聚合的应用用例必须在 Spring 事务边界内执行。
2. 聚合保存与由该聚合产生的 Outbox 记录必须属于同一事务。
3. 业务保存失败或事件写入失败时，事务必须整体回滚。
4. 事件只有在全部写入 Outbox 成功后才可从聚合事件队列清除。
5. `OrderCompletedEvent` 等既有领域事件不得因应用服务遗漏而丢失。
6. 查询用例使用只读事务；集成消息 handler 调用同一事务化用例端口。

### R3 行为保持

1. 订单、售后 HTTP 路径、请求和响应结构保持不变。
2. 订单、售后领域规则、错误码和集成消息契约保持不变。
3. 既有 PostgreSQL 映射、并发控制、Outbox relay 和幂等消费语义保持不变。

### R4 架构守护与 Spring Modulith 决策

1. CI 必须自动校验四层 Gradle 依赖方向及 domain/application 框架纯净性。
2. 必须记录是否引入 Spring Modulith 的决定、收益、代价和重新评估条件。
3. 不得同时维护两套职责重叠、结论可能冲突的模块边界门禁。

## 质量目标

- 数据完整性：业务状态与 Outbox 不允许部分提交。
- 可维护性：事务技术不进入订单领域或应用源码。
- 可测试性：纯领域和应用测试无需启动 Spring；事务装配使用窄 Spring 集成测试。
- 可演进性：四层模块可作为其他上下文拆分模板，并为未来独立订单服务提供装配边界。
