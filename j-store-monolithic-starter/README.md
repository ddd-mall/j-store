# J-Store 单体应用多数据源配置指南

## 概述

本项目演示了如何在Spring Boot单体应用中使用JPA配置多个数据源，让不同的业务模块连接到不同的数据库实例。

## 架构设计

### 模块划分

- **订单模块（Order）**: 连接到 `j_store_order` 数据库
  - 订单表（sale_order）
  - 订单项表（order_item）
  - 订单库存表（inventory）

- **商品模块（Goods）**: 连接到 `j_store_goods` 数据库
  - 商品库存表（inventory）
  - SPU表（spu）
  - SKU表（sku）

### 技术栈

- Spring Boot 3.3.10
- Spring Data JPA
- Hibernate
- PostgreSQL
- Kotlin 2.1.20

## 配置说明

### 1. 数据源配置（application.yml）

```yaml
spring:
  datasource:
    # 订单数据源（标记为@Primary主数据源）
    order:
      url: jdbc:postgresql://localhost:5432/j_store_order
      username: postgres
      password: postgres
      driver-class-name: org.postgresql.Driver
      hikari:
        maximum-pool-size: 10
        minimum-idle: 5
    
    # 商品数据源
    goods:
      url: jdbc:postgresql://localhost:5432/j_store_goods
      username: postgres
      password: postgres
      driver-class-name: org.postgresql.Driver
      hikari:
        maximum-pool-size: 10
        minimum-idle: 5
```

### 2. 数据源配置类

#### OrderDataSourceConfig.kt

为订单模块配置独立的数据源、EntityManagerFactory和TransactionManager：

```kotlin
@Configuration
@EnableJpaRepositories(
    basePackages = [
        "com.jstore.order.domain.order.persistence",
        "com.jstore.order.domain.inventory.persistent"
    ],
    entityManagerFactoryRef = "orderEntityManagerFactory",
    transactionManagerRef = "orderTransactionManager"
)
class OrderDataSourceConfig {
    // 配置订单数据源Bean
    @Primary
    @Bean
    fun orderDataSource(): DataSource { ... }
    
    @Primary
    @Bean
    fun orderEntityManagerFactory(): LocalContainerEntityManagerFactoryBean { ... }
    
    @Primary
    @Bean
    fun orderTransactionManager(): PlatformTransactionManager { ... }
}
```

#### GoodsDataSourceConfig.kt

为商品模块配置独立的数据源、EntityManagerFactory和TransactionManager：

```kotlin
@Configuration
@EnableJpaRepositories(
    basePackages = [
        "com.jstore.goods.domain.inventory.persistence",
        "com.jstore.goods.domain.commodity.persistence"
    ],
    entityManagerFactoryRef = "goodsEntityManagerFactory",
    transactionManagerRef = "goodsTransactionManager"
)
class GoodsDataSourceConfig {
    // 配置商品数据源Bean
    @Bean
    fun goodsDataSource(): DataSource { ... }
    
    @Bean
    fun goodsEntityManagerFactory(): LocalContainerEntityManagerFactoryBean { ... }
    
    @Bean
    fun goodsTransactionManager(): PlatformTransactionManager { ... }
}
```

### 3. 主应用配置

```kotlin
@SpringBootApplication(
    scanBasePackages = [
        "com.jstore.monolithic",
        "com.jstore.order",
        "com.jstore.goods"
    ],
    exclude = [DataSourceAutoConfiguration::class] // 排除默认数据源自动配置
)
@EnableJpaAuditing
class JStoreMonolithicApplication
```

## 关键要点

### 1. @Primary注解
- 订单数据源标记为`@Primary`，作为主数据源
- 当Spring需要注入DataSource但没有指定qualifier时，会使用主数据源

### 2. basePackages配置
- 每个`@EnableJpaRepositories`必须指定不同的`basePackages`
- 确保Repository接口不会被多个配置类扫描

### 3. 事务管理
```kotlin
// 使用订单数据源的事务
@Transactional("orderTransactionManager")
fun orderOperation() { ... }

// 使用商品数据源的事务
@Transactional("goodsTransactionManager")
fun goodsOperation() { ... }

// 默认使用主数据源（order）的事务
@Transactional
fun defaultOperation() { ... }
```

### 4. 跨数据源事务
⚠️ **注意**：不同数据源之间无法使用简单的`@Transactional`实现分布式事务。

如需跨数据源事务，需要考虑：
- 使用分布式事务解决方案（如Seata、Atomikos）
- 使用最终一致性方案（如Saga模式、TCC模式）
- 使用消息队列实现异步最终一致性

## 数据库准备

### 创建数据库

```sql
-- 创建订单数据库
CREATE DATABASE j_store_order;

-- 创建商品数据库
CREATE DATABASE j_store_goods;
```

### 表结构

应用启动时，Hibernate会自动根据Entity创建表结构（hibernate.hbm2ddl.auto=update）。

生产环境建议：
1. 设置 `hibernate.hbm2ddl.auto=validate`
2. 使用Flyway或Liquibase管理数据库迁移

## 运行应用

### 1. 确保PostgreSQL已启动

```bash
# 检查PostgreSQL状态
psql -U postgres -l
```

### 2. 启动应用

```bash
cd j-store-monolithic-starter
../gradlew bootRun
```

或在IDE中运行 `JStoreMonolithicApplication.kt`

### 3. 测试API

```bash
# 测试状态
curl http://localhost:8080/api/demo/status

# 测试订单数据源
curl http://localhost:8080/api/demo/orders

# 测试商品库存数据源
curl http://localhost:8080/api/demo/inventory

# 测试商品SPU数据源
curl http://localhost:8080/api/demo/spus
```

## 项目结构

```
j-store-monolithic-starter/
├── src/main/kotlin/com/jstore/monolithic/
│   ├── JStoreMonolithicApplication.kt          # 主应用类
│   ├── config/
│   │   ├── OrderDataSourceConfig.kt            # 订单数据源配置
│   │   └── GoodsDataSourceConfig.kt            # 商品数据源配置
│   └── controller/
│       └── DemoController.kt                   # 示例控制器
├── src/main/resources/
│   └── application.yml                         # 应用配置
└── build.gradle.kts                            # 构建配置

j-store-order-infrastructure/
└── src/main/kotlin/com/jstore/order/domain/
    ├── order/persistence/                      # 订单持久化层
    │   ├── OrderPO.kt
    │   ├── OrderItemPO.kt
    │   └── OrderPOJpaRepository.kt
    └── inventory/persistent/                   # 订单库存持久化层
        ├── InventoryPO.kt
        └── InventoryPOJpaRepository.kt

j-store-goods-infrastructure/
└── src/main/kotlin/com/jstore/goods/domain/
    ├── inventory/persistence/                  # 商品库存持久化层
    │   ├── InventoryPO.kt
    │   └── InventoryPOJpaRepository.kt
    └── commodity/persistence/                  # 商品SPU/SKU持久化层
        ├── SpuPO.kt
        ├── SkuPO.kt
        ├── SpuPOJpaRepository.kt
        └── SkuPOJpaRepository.kt
```

## 常见问题

### Q1: 如何添加新的数据源？

1. 在`application.yml`中添加数据源配置
2. 创建新的DataSourceConfig类
3. 配置对应的Repository扫描包路径
4. 创建对应的Entity和Repository

### Q2: 如何在Service层指定使用哪个数据源？

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderPOJpaRepository // 自动使用订单数据源
) {
    @Transactional("orderTransactionManager")
    fun createOrder() { ... }
}

@Service
class GoodsService(
    private val inventoryRepository: InventoryPOJpaRepository // 自动使用商品数据源
) {
    @Transactional("goodsTransactionManager")
    fun updateInventory() { ... }
}
```

### Q3: Repository扫描冲突怎么办？

确保每个`@EnableJpaRepositories`的`basePackages`不重叠：
- 订单：`com.jstore.order.domain.**.persistence`
- 商品：`com.jstore.goods.domain.**.persistence`

### Q4: 如何切换到其他数据库（如MySQL）？

1. 修改`application.yml`中的JDBC URL和driver-class-name
2. 修改DataSourceConfig中的Hibernate方言
3. 添加对应数据库的依赖

```kotlin
// MySQL示例
properties["hibernate.dialect"] = "org.hibernate.dialect.MySQLDialect"
```

## 最佳实践

1. **明确划分模块边界**：确保订单和商品模块的Repository在不同的包下
2. **避免跨库JOIN**：不同数据源的数据不能在SQL层面JOIN
3. **事务边界清晰**：明确指定事务管理器
4. **监控连接池**：为不同数据源配置合适的连接池参数
5. **日志隔离**：可以为不同数据源配置不同的日志级别

## 性能优化建议

1. **连接池调优**：根据实际负载调整Hikari连接池参数
2. **批量操作**：使用JPA batch操作减少数据库交互
3. **懒加载配置**：合理配置实体关联的加载策略
4. **查询优化**：使用JPQL或Criteria API优化查询
5. **缓存策略**：考虑使用Redis或二级缓存

## 扩展阅读

- [Spring Boot Multi-DataSource官方文档](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-access.configure-custom-datasource)
- [JPA多数据源配置详解](https://www.baeldung.com/spring-data-jpa-multiple-databases)
- [分布式事务解决方案](https://seata.io/zh-cn/)

## 许可证

本项目采用 [MIT License](LICENSE)

