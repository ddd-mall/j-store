# j-store Project Overview

## 项目定位

j-store 是一个 Kotlin/Spring Boot 电商后端项目，按 DDD 有界上下文拆分为 Gradle 多模块。核心交易链路已拆分为 Catalog、Store/Offer、Inventory/ATP、WMS、Trade/Checkout 与 Order，并通过版本化集成消息和 Outbox 协作。

完整的权威事实、上下文关系、聚合边界和跨服务一致性协议见 [领域建模说明](domain-modeling.md)。

## 开发阶段与变更策略

本项目当前处于内部开发期，尚未发布公开稳定版本，也没有需要保留的生产数据。除非已批准的 requirement/delta 明确规定兼容或迁移要求，否则仓库内接口、集成契约、领域模型和数据库结构均按当前目标形态直接演进。

- 接口或契约变化应在同一变更中直接更新所有仓库内调用方、测试和文档；不得仅为旧内部版本保留弃用别名、适配器、兼容端点/字段/事件或新旧逻辑分支。
- 数据结构变化应更新当前基线、初始化脚本和相关验证，并允许开发环境重建；不得仅为保留可丢弃的开发数据新增增量回填脚本、兼容视图、双列或双读/双写逻辑。
- “无需向后兼容”不表示可以留下不一致状态；当前版本的 API、消息、持久化映射、测试夹具和文档必须在同一变更内收敛。
- 若某项接口已有仓库外消费者、某环境包含不可丢弃数据，或变更面向发布/生产，则该事项不再适用上述默认值，必须在规格中明确兼容、迁移、回滚策略并取得相应人工批准。

## 技术栈

- Kotlin 2.4.10，Java 25
- Spring Boot 3.5.16，Spring Data JPA，PostgreSQL，Redis
- Gradle Kotlin DSL；外部坐标集中在 `gradle/libs.versions.toml`，解析约束由 `j-store-dependencies-platform` 统一提供
- 测试栈包含 JUnit 5、Kotlin test、Kotest、Kotest property、Mockito、Spring Boot Test
- `j-store-outbox-spring` 和部分 infrastructure/boot 集成测试使用嵌入式 PostgreSQL
- `j-store-user-infrastructure` 的 Redis 集成测试使用测试依赖携带的嵌入式 Redis，不要求本机预装服务

## Gradle 模块

当前 `settings.gradle.kts` 注册了这些模块：

- `j-store-dependencies-platform`: 全模块共享的 Java Platform，统一导入 Spring Boot、JUnit、OpenTelemetry 及获批安全 BOM/constraint；不承载运行时代码。详细规则见 [依赖管理规范](steering/dependency-management-guidelines.md)。
- `j-store-common-core`: 不依赖 Spring 的共享领域基础类型、错误、Result、领域事件、地理地址、日志、工具类。
- `j-store-common-spring`: 仅保留通用 Spring 地理地址服务实现，不承载消息或 Outbox 基础设施。
- `j-store-messaging-core`: 框架无关的集成消息、handler、publisher、envelope 与 transport SPI。
- `j-store-outbox-core`: 框架无关的 Outbox 记录、仓储端口、目标规划和按 `transportId` 的路由 SPI。
- `j-store-messaging-local-spring`: 进程内领域事件与集成消息总线的 Spring 实现。
- `j-store-outbox-spring`: Transactional Outbox 的 Jackson/JPA、relay、调度、死信、监控和 Boot 自动配置。
- `j-store-integration-contracts`: 跨有界上下文的版本化集成命令/事件契约；依赖 `messaging-core`，不承载领域对象或基础设施实现。
- `j-store-order-domain`: 纯订单/售后领域模型、仓储与 ACL 端口；只依赖 `common-core`。
- `j-store-order-application`: 无框架的订单/售后用例编排、用例端口和集成消息 handler。
- `j-store-order-infrastructure`: 订单/售后 JPA、PostgreSQL 并发控制及 Catalog、Offer 查询 ACL 适配器。
- `j-store-order-boot`: 订单 HTTP Controller、Spring 事务用例装饰器与上下文 Bean 装配。
- `j-store-goods-api`: Catalog 对外的无价格商品资料快照查询契约。
- `j-store-goods-domain/application/infrastructure/boot`: Catalog 的 SPU、稳定 SKU、Product Type、类目/品牌引用、本地化内容、版本化款式、资料快照和 `DRAFT/PUBLISHED/ARCHIVED` 生命周期；不拥有销售状态或库存。
- `j-store-shop-api`: Store/Offer 对外的销售要约快照查询契约。
- `j-store-shop-domain/application/infrastructure/boot`: 店铺、商户成员和 `SalesOffer`；销售状态、成交价、渠道、有效期、限购与履约策略由此上下文权威管理，并签发持久化 `SaleAuthorization`。
- `j-store-inventory-domain/application/infrastructure/boot`: ATP 库存镜像、安全库存、渠道隔离量与订单 `StockReservation`；只有预留成功才构成库存承诺。
- `j-store-trade-domain/application/infrastructure/boot`: Trade Process、成交快照、销售授权与库存预留 Saga、失败补偿和取消释放；向 Order 只发布最终交易承诺结果。
- `j-store-warehouse-domain/application/infrastructure/boot`: WMS 实物库存权威及单调版本库存事件；Inventory 消费其事件维护销售库存镜像。
- `j-store-payment-domain/application/infrastructure/boot`: 支付单与退款用例、JPA/Outbox 以及 Spring 事务装配。
- `j-store-fulfillment-domain/application/infrastructure/boot`: 履约单用例、JPA/Outbox 以及 Spring 事务装配。
- `j-store-user-domain/application/infrastructure/boot`: 用户账户领域、注册登录用例、JPA/JWT/Redis 适配以及 Spring Web/事务装配。
- `j-store-user-api`: User 上下文发布的稳定用户资料查询契约，只包含用户 ID、昵称、已验证手机号和账号状态等标量快照。
- `j-store-user-client-spring`: 用户资料远程 HTTP 客户端与条件自动配置；单体使用进程内实现，微服务消费方通过 `jstore.user-query.mode=remote` 切换。
- `j-store-authentication-spring-sdk`: 基于 Spring MVC 的认证拦截器、当前用户参数解析、登录注解与自动配置，依赖 `j-store-user-domain`。
- `j-store-accounting-domain/application/infrastructure/boot`: 会计账户、期间、凭证、结算领域与用例、JPA/Outbox 以及 Spring 事务装配。
- `j-store-boot`: 当前主启动模块，组合各上下文 boot、公共 Spring 基础设施和认证 SDK，并承载数据库迁移与跨上下文事件翻译器。
- `j-store-admin-boot`: 管理端启动模块骨架，目前只有基础 Kotlin/JVM 配置和 `Main.kt`。

## 当前实现重点

- 订单：订单行冻结 Catalog 与 Offer 快照，持久化 `PENDING_OFFER → OFFER_AUTHORIZED → CONFIRMED/FAILED` Saga 状态；先取得销售授权，再请求 ATP 库存预留。
- Catalog：SPU、稳定 SKU、结构化 Product Type、类目/品牌引用、本地化内容、商品款式、草稿/发布/归档和完整资料快照；商品价格不通过 Catalog API 进入交易决策。
- Store/Offer：一个 SKU 可按店铺、渠道、市场分别定价和启停；授权时用数据库悲观锁校验店铺、Offer 版本、价格、有效期和限购，并签发有时效、可幂等、可释放的业务凭证。
- Inventory/ATP：按 `onHand - reserved - safetyStock - isolatedQuantity` 计算可承诺量；授权过期或 ATP 不足时拒绝预留。
- WMS：维护实物在库数量和来源版本；订单不直接锁 WMS 数据库，旧库存事件不会覆盖新镜像。
- 用户：用户注册、登录、强制下线、昵称和密码值对象、JWT 与 Redis token 基础设施，以及供业务上下文读取标量资料的本地/远程双部署查询能力。
- 订单用户快照：创建订单只接受认证上下文的用户 ID，通过 Order 本地 ACL 查询 ACTIVE 用户并冻结昵称和手机号；收货人联系方式保持独立语义。
- 店铺：商户、商户成员、角色权限、成员管理用例和其它上下文复用的商户授权服务。
- 支付与履约：支付单、退款、履约单的领域模型、集成消息处理、JPA/Outbox 与事务装配。
- 会计：账户、会计期间、分录、结算单等领域模型、JPA 仓储实现与事务装配。
- 事件基础设施：进程内领域事件监听、版本化集成消息，以及按一个或多个稳定 `transportId`（如 `local`、`kafka`、`rabbitmq`）规划的 Outbox 投递、消费幂等和监控；Outbox 使用提交后单飞唤醒与轮询兜底，领域事件在事务提交后确认，并对历史投递与消费状态执行有预算的持续清理。
- 接口层：各上下文 `*-boot` 持有自己的 Controller 与 Spring 配置，根 `j-store-boot` 负责组合运行时。

## 架构边界

依赖方向必须遵守 DDD 分层约束：

```text
boot/interface -> application -> domain -> common-core
               -> infrastructure -> domain
```

实际依赖大体遵循以下形态：

- 领域模块依赖 `j-store-common-core`，不应依赖 Spring、JPA、Boot 或基础设施模块。
- 基础设施模块依赖对应领域模块，并引入 Spring Data JPA、Redis、WebClient 等框架细节。
- 每个上下文的 `*-boot` 组合该上下文并提供用例级事务边界，根 `j-store-boot` 只组合整站运行时。
- 聚合内部和同一上下文协作使用 `LocalDomainEventBus`；跨上下文协作使用 `j-store-integration-contracts` 中的集成命令/事件，查询仍可使用 ACL 接口。
- `canSell` 仅可作为 Catalog、Offer 与 ATP 的组合查询/决策结果，不得持久化为权威布尔字段。
- 跨服务一致性依赖两个持久化承诺：Store 的 `SaleAuthorization` 和 Inventory 的 `StockReservation`；不依赖 JVM 线程锁或跨库事务。

## 领域实现习惯

- 聚合根实现 `AggregateRoot<I>`，实体实现 `Entity<I>`，ID 类型实现 `Identifier`；需要产生事件的聚合同时实现 `RecordsDomainEvents`，通常继承 `EventRecordingAggregateRoot<I>`。
- 预期业务失败使用 `Result<T, BusinessError>` 返回，不用异常表达。
- 仓储接口放领域模块，仓储实现和 PO 放 infrastructure 模块。
- 应用服务只编排用例，业务规则落在聚合、实体、值对象或领域服务中。
- 领域事件由聚合产生；应用用例保存聚合后写入 Outbox，二者由 boot 的事务装饰器纳入同一事务。
- PO/JPA Repository 只出现在 infrastructure/outbox-spring/boot 等基础设施边界内。

## 数据库与运行时

- `j-store-boot/src/main/resources/db/migration/` 保存当前 Flyway 基线及已有迁移事实，`db/init/` 保存初始化快照；内部开发期的后续结构迭代默认按上文策略维护当前基线/快照，而不是为可丢弃的开发数据累积兼容性迁移。
- `docker-compose.postgres.yml` 提供本地 PostgreSQL 与 Redis 服务，连接信息见 [README.md](../README.md)。
- `j-store-boot/src/main/resources/application-local.properties` 与 README 中的本地服务配置对应。

## 测试现状

仓库已有较多单元测试、应用服务测试、JPA 仓储测试与属性测试，尤其集中在订单、商品、用户、会计、认证 SDK、公共事件基础设施。新增功能应先补测试，再实现行为。

- 领域规则优先用快速单元测试和 Kotest property 覆盖。
- 应用服务优先使用 fake repository 或 mock ACL 验证用例编排。
- PO 与领域对象转换、JPA 查询、事务、Outbox、Spring 装配用更窄的集成测试覆盖。
- 涉及 Testcontainers 的测试需要本地 Docker 可用。

## 常用命令

```bash
./gradlew test
./gradlew :j-store-order-domain:test :j-store-order-application:test
./gradlew :j-store-order-infrastructure:test :j-store-order-boot:test
./gradlew :j-store-goods-domain:test :j-store-goods-application:test :j-store-goods-boot:test
./gradlew :j-store-payment-domain:test :j-store-payment-application:test :j-store-payment-boot:test
./gradlew :j-store-fulfillment-domain:test :j-store-fulfillment-application:test :j-store-fulfillment-boot:test
./gradlew :j-store-user-api:test :j-store-user-application:test :j-store-user-client-spring:test :j-store-user-boot:test
./gradlew :j-store-shop-domain:test :j-store-shop-application:test :j-store-shop-infrastructure:test :j-store-shop-boot:test
./gradlew :j-store-inventory-domain:test :j-store-inventory-application:test :j-store-inventory-infrastructure:test
./gradlew :j-store-warehouse-domain:test :j-store-warehouse-application:test :j-store-warehouse-infrastructure:test
./gradlew :j-store-accounting-domain:test :j-store-accounting-application:test :j-store-accounting-boot:test
./gradlew :j-store-messaging-core:test :j-store-outbox-core:test
./gradlew :j-store-messaging-local-spring:test :j-store-outbox-spring:test
./gradlew :j-store-authentication-spring-sdk:test
./gradlew :j-store-boot:bootJar
```

本地 PostgreSQL/Redis 说明见 [README.md](../README.md)。
