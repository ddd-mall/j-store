# 设计：订单四层模块与事务装饰器

## 模块依赖

```text
j-store-order-boot
  -> j-store-order-application
  -> j-store-order-infrastructure
  -> j-store-order-domain

j-store-order-infrastructure -> j-store-order-domain
j-store-order-application    -> j-store-order-domain
j-store-order-domain         -> j-store-common-core
```

根 `j-store-boot` 保留整站启动类、统一 Flyway、跨上下文 translator 和订单过期任务；它通过依赖 `j-store-order-boot` 组合订单 Controller 与事务装配。

## 事务边界

订单 application 定义 `OrderUseCase` 与 `AfterSaleUseCase`，纯实现继续负责“加载 → 领域行为 → 保存 → 发布领域事件”。

订单 boot 提供 Spring 事务装饰器：

```text
Controller / IntegrationMessageHandler
    -> Transactional*UseCase (Spring TransactionTemplate)
        -> pure application service
            -> repository adapter
            -> DomainEventPublisher (Outbox, MANDATORY)
```

事务装饰器是唯一对 Web/消息装配暴露的主用例 Bean。Repository 不自行开启一个比用例更窄的新事务；售后仓储中的编程式事务和事件发布职责迁回应用用例边界。

## 事件队列

应用层通过 `RecordsDomainEvents.pendingDomainEvents()` 获取稳定快照，逐条写入 Outbox；全部成功后按事件 ID 确认。异常时不确认，外层 Spring 事务回滚领域数据与 Outbox。公共契约以 `ddd-foundation-refactor` 变更规格为准。

## Spring Modulith 评估

基于当前 Spring Modulith 2.1 官方能力（模块 DAG/API 包验证、模块切片测试、结构文档、运行时验证和事件发布注册表），当前不直接引入，原因如下：

1. 本次核心目标是 Gradle 物理模块的编译期依赖隔离，Gradle 已能比包级扫描更早阻断错误方向依赖。
2. 当前其他上下文仍采用 domain/application 混合模块，立即启用会形成两套粒度不一致的模块模型。
3. 项目已有自定义本地事件/Outbox/集成消息生命周期，不应在同一变更中叠加另一套事件发布注册表。

官方能力依据：[结构验证](https://docs.spring.io/spring-modulith/reference/verification.html)、[模块集成测试](https://docs.spring.io/spring-modulith/reference/testing.html)、[事件发布注册表](https://docs.spring.io/spring-modulith/reference/events.html)。

本次以确定性的仓库契约测试守护四层依赖。以下条件满足后重新评估 Spring Modulith：

- 两个以上上下文完成相同四层拆分；
- 需要自动限制上下文公开包、生成上下文关系文档或进行模块切片集成测试；
- 明确只采用其结构验证/可观测性，而不与现有 Outbox 生命周期重复。

## 单体与微服务部署就绪度

| 能力 | 本次状态 | 说明 |
|---|---|---|
| 模块化单体 | 就绪 | 根 `j-store-boot` 依赖 `j-store-order-boot`，使用 `jstore.messaging.mode=local` 组合运行。 |
| 订单独立编译/装配边界 | 就绪 | domain/application/infrastructure/boot 已物理隔离；Controller 和事务装饰器可被独立启动器复用。 |
| 聚合与 Outbox 原子提交 | 就绪 | 用例级 Spring 事务覆盖仓储写入和 Outbox 发布，失败整体回滚。 |
| 微服务集群运行 | 尚未就绪 | 仍缺订单独立 `SpringBootApplication`、独立配置/迁移交付物，以及唯一的 Broker 出站适配器和对应入站 consumer。现有 `broker`/`hybrid` 模式在缺少 Transport 时会正确快速失败，不应视为已交付集群能力。 |

后续微服务化应新增独立 launcher/deployment 模块，而不是让 domain/application 依赖 Spring；同时复用现有 integration-contracts、Outbox 和消费幂等协议。

## 迁移与回滚

- 删除旧 `j-store-order` 并迁移源码/测试，不保留兼容 Gradle alias。
- 数据库结构和外部契约不变，不新增 Flyway migration。
- 回滚需要整体恢复旧模块和依赖图，不能只撤销事务装饰器。
