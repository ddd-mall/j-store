# j-store Project Overview

## 项目定位

j-store 是一个 Kotlin/Spring Boot 电商后端项目，按 DDD 有界上下文拆分为 Gradle 多模块。当前代码重点在订单、商品、用户、会计、认证 SDK、领域事件/Outbox 基础设施；店铺、仓储、admin boot 目前仍接近骨架状态。

## 技术栈

- Kotlin 2.3.0，Java 25
- Spring Boot 3.5.16，Spring Data JPA，PostgreSQL，Redis
- Gradle Kotlin DSL，依赖版本集中在 `gradle/libs.versions.toml`
- 测试栈包含 JUnit 5、Kotlin test、Kotest、Kotest property、Mockito、Spring Boot Test
- `j-store-common-spring` 和部分 infrastructure/boot 集成测试使用嵌入式 PostgreSQL
- `j-store-boot` 仍包含少量 Java 代码，主要是订单过期定时任务相关实现

## Gradle 模块

当前 `settings.gradle.kts` 注册了这些模块：

- `j-store-common-core`: 不依赖 Spring 的共享领域基础类型、错误、Result、领域事件、地理地址、日志、工具类。
- `j-store-common-spring`: Spring/JPA 集成、领域事件监听注册、Transactional Outbox、事件消费记录、地理地址服务代理。
- `j-store-integration-contracts`: 跨有界上下文的版本化集成命令/事件契约；只依赖 `common-core`，不承载领域对象或基础设施实现。
- `j-store-order`: 订单上下文的领域层与应用层，包含订单聚合、订单项、收货信息、状态流转、订单服务和库存事件处理。
- `j-store-order-infrastructure`: 订单仓储 JPA 实现、订单 PO、收货信息 PO 转换、订单到商品 API 的 ACL 实现。
- `j-store-goods-api`: 商品上下文对外查询契约，目前用于订单侧获取商品快照。
- `j-store-goods`: 商品上下文的领域层与应用层，包含商品/款式/sku、库存、商品快照、库存事件。
- `j-store-goods-infrastructure`: 商品、款式、sku、商品快照的 JPA PO、Spring Data Repository 与仓储实现。
- `j-store-user`: 用户账户上下文的领域层与应用层，包含注册、登录、昵称、密码、token 相关端口。
- `j-store-user-infrastructure`: 用户账户 JPA 仓储、BCrypt 密码哈希、JWT token、Redis token store。
- `j-store-authentication-spring-sdk`: 基于 Spring MVC 的认证拦截器、当前用户参数解析、登录注解与自动配置，依赖 `j-store-user`。
- `j-store-accounting`: 会计上下文的领域层与应用层，包含账户、会计期间、分录、结算单、订单/支付/店铺 ACL 端口。
- `j-store-accounting-infrastructure`: 会计账户、会计期间、分录、结算单的 JPA PO 与仓储实现。当前未被 `j-store-boot` 依赖。
- `j-store-boot`: 当前主启动模块，装配订单、商品、用户、公共 Spring 基础设施和认证 SDK，提供控制器、配置、数据库迁移、跨上下文事件翻译器、订单过期定时任务。
- `j-store-admin-boot`: 管理端启动模块骨架，目前只有基础 Kotlin/JVM 配置和 `Main.kt`。
- `j-store-shop` / `j-store-shop-infrastructure`: 店铺模块骨架，当前只有少量占位代码。
- `j-store-warehouse` / `j-store-warehouse-infrastructure`: 仓储模块骨架，当前只有占位入口。

## 当前实现重点

- 订单：订单聚合、创建/支付/取消/退款相关命令、订单状态规则、商品快照版本校验、库存确认/不足事件处理。
- 商品：SPU、SKU、商品款式、草稿/发布流程、商品快照、库存预占/确认/释放事件。
- 用户：用户注册、登录、强制下线、昵称和密码值对象、JWT 与 Redis token 基础设施。
- 会计：账户、会计期间、分录、结算单等领域模型和 JPA 仓储实现，boot 接入仍需后续确认。
- 公共事件基础设施：进程内领域事件监听、版本化集成消息、按 `local`/`broker`/`hybrid` 部署模式规划的 Outbox 投递目标、事件消费幂等记录及监控。
- 接口层：`j-store-boot` 目前有订单和用户控制器，商品主要通过配置和服务装配参与流程。

## 架构边界

依赖方向必须遵守 DDD 分层约束：

```text
boot/interface -> infrastructure -> domain/application -> common-core
```

实际依赖大体遵循以下形态：

- 领域模块依赖 `j-store-common-core`，不应依赖 Spring、JPA、Boot 或基础设施模块。
- 基础设施模块依赖对应领域模块，并引入 Spring Data JPA、Redis、WebClient 等框架细节。
- `j-store-boot` 负责组合当前运行时需要的上下文和基础设施模块。
- 聚合内部和同一上下文协作使用 `LocalDomainEventBus`；跨上下文协作使用 `j-store-integration-contracts` 中的集成命令/事件，查询仍可使用 ACL 接口。

## 领域实现习惯

- 聚合根实现 `AgreeGate<I>`，实体实现 `Entity<I>`，ID 类型实现 `Identify`。
- 预期业务失败使用 `Result<T, BusinessError>` 返回，不用异常表达。
- 仓储接口放领域模块，仓储实现和 PO 放 infrastructure 模块。
- 应用服务只编排用例，业务规则落在聚合、实体、值对象或领域服务中。
- 领域事件由聚合发布，仓储保存聚合时处理事件持久化或发布。
- PO/JPA Repository 只出现在 infrastructure/common-spring/boot 等基础设施边界内。

## 数据库与运行时

- `j-store-boot/src/main/resources/db/migration/` 保存 Flyway 风格迁移脚本，当前包含 Outbox、用户账户、会计表、事件消费记录等迁移。
- `docker-compose.postgres.yml` 提供本地 PostgreSQL 与 Redis 服务，连接信息见 [README.md](../README.md)。
- `j-store-boot/src/main/resources/application-local.properties` 与 README 中的本地服务配置对应。
- 订单过期定时任务目前位于 `j-store-boot/src/main/java/com/jstore/order/expired/`，并使用 Redis Lua 脚本资源。

## 测试现状

仓库已有较多单元测试、应用服务测试、JPA 仓储测试与属性测试，尤其集中在订单、商品、用户、会计、认证 SDK、公共事件基础设施。新增功能应先补测试，再实现行为。

- 领域规则优先用快速单元测试和 Kotest property 覆盖。
- 应用服务优先使用 fake repository 或 mock ACL 验证用例编排。
- PO 与领域对象转换、JPA 查询、事务、Outbox、Spring 装配用更窄的集成测试覆盖。
- 涉及 Testcontainers 的测试需要本地 Docker 可用。

## 常用命令

```bash
./gradlew test
./gradlew :j-store-order:test
./gradlew :j-store-goods:test
./gradlew :j-store-user:test
./gradlew :j-store-accounting:test
./gradlew :j-store-common-spring:test
./gradlew :j-store-authentication-spring-sdk:test
./gradlew :j-store-boot:bootJar
```

本地 PostgreSQL/Redis 说明见 [README.md](../README.md)。
