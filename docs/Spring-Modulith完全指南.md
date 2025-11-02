# Spring Modulith 完全指南

## 什么是 Spring Modulith？

Spring Modulith 是 Spring 官方推出的一个框架，用于**在单体应用（Monolith）中实现模块化架构**。它帮助开发者在不采用微服务的情况下，构建结构清晰、边界明确的模块化应用。

### 核心理念

**模块化单体（Modular Monolith）**：
- 保持单体应用的简单性（单一部署单元、无分布式复杂性）
- 拥有微服务的优点（清晰的模块边界、独立开发、低耦合）
- 必要时可以轻松拆分为微服务

## Spring Modulith 的主要作用

### 1. 模块边界验证 ⭐ 核心功能

自动验证模块之间的依赖关系是否符合架构规则。

**问题场景：**
```
j-store-order 模块不应该直接依赖 j-store-goods 的内部实现类
但开发者可能会不小心引用了内部类
```

**Spring Modulith 解决方案：**
- 自动扫描代码，检测跨模块的非法依赖
- 在测试阶段就发现架构违规
- 强制执行模块封装

### 2. 事件驱动的模块间通信

提供异步事件机制，实现模块解耦。

**传统方式：**
```kotlin
// 订单服务直接调用商品服务 ❌ 紧耦合
class OrderService(
    private val goodsService: GoodsService  // 直接依赖
) {
    fun createOrder() {
        // ...
        goodsService.reduceStock(goodsId, quantity)  // 直接调用
    }
}
```

**Spring Modulith 方式：**
```kotlin
// 订单服务发布事件 ✅ 松耦合
class OrderService(
    private val events: ApplicationEventPublisher
) {
    fun createOrder() {
        // ...
        events.publishEvent(OrderCreatedEvent(orderId, goodsId, quantity))
    }
}

// 商品服务监听事件（在另一个模块中）
@ApplicationModuleListener
fun handle(event: OrderCreatedEvent) {
    reduceStock(event.goodsId, event.quantity)
}
```

### 3. 事件外发（Event Externalization）

将内部事件发布到外部消息队列（Kafka、RabbitMQ），支持最终一致性。

### 4. 模块可视化文档

自动生成模块依赖图和文档，清晰展示系统架构。

### 5. 集成测试支持

提供测试工具，方便进行模块级别的集成测试。

## 核心概念

### 1. Application Module（应用模块）

一个模块就是一个 **package**，代表一个业务领域。

**模块结构示例：**
```
com.jstore.order/              # 订单模块（Application Module）
├── Order.kt                   # 公共 API（可被其他模块访问）
├── OrderService.kt            # 公共 API
├── internal/                  # 内部实现（不可被其他模块访问）
│   ├── OrderRepository.kt
│   └── OrderValidator.kt
└── events/                    # 模块事件
    └── OrderCreatedEvent.kt

com.jstore.goods/              # 商品模块
└── ...
```

**关键规则：**
- 其他模块**只能**访问模块根 package 下的类（公共 API）
- 其他模块**不能**访问 `internal` 包下的类
- 通过事件进行模块间通信

### 2. 模块类型

#### Named Interface（命名接口）
显式定义的子包，作为模块的对外接口：
```
com.jstore.order/
├── api/                       # 对外 API
├── spi/                       # 服务提供者接口
└── internal/                  # 内部实现
```

#### Open Module（开放模块）
所有非 `internal` 的类都可以被访问。

#### Closed Module（封闭模块）
使用 `@ApplicationModule(type = CLOSED)` 标记，只有明确标记的接口才能被访问。

### 3. 事件机制

#### 同步事件（默认）
```kotlin
events.publishEvent(OrderCreatedEvent(...))  // 立即处理
```

#### 异步事件
```kotlin
@ApplicationModuleListener
@Async
fun handle(event: OrderCreatedEvent) { ... }
```

#### 事务性事件
```kotlin
@TransactionalEventListener(phase = AFTER_COMMIT)
fun handle(event: OrderCreatedEvent) { ... }
```

## 在您的项目中使用 Spring Modulith

### 当前项目结构分析

您的项目已经有了**模块化的基础**：
```
j-store/
├── j-store-order/             # 订单领域模块
├── j-store-order-boot/        # 订单服务启动模块
├── j-store-goods/             # 商品领域模块
├── j-store-goods-boot/        # 商品服务启动模块
├── j-store-common/            # 通用模块
└── j-store-application/       # 主应用（单体部署）
```

**适合使用 Spring Modulith 的场景：**
1. ✅ `j-store-application` 作为单体应用，整合多个领域模块
2. ✅ 验证 order、goods 模块之间的依赖关系
3. ✅ 使用事件实现模块间解耦

### 步骤1：添加依赖

您的 `libs.versions.toml` 已经定义了 Spring Modulith BOM，现在需要在具体模块中使用。

**修改 `j-store-application/build.gradle.kts`：**
```kotlin
dependencies {
    // 添加 Spring Modulith 依赖
    implementation(platform(libs.spring.modulith.bom))
    implementation(libs.spring.modulith.starter.core)
    
    // 测试依赖
    testImplementation(libs.spring.modulith.starter.test)
    
    // 现有依赖...
    implementation(project(":j-store-order"))
    implementation(project(":j-store-goods"))
    // ...
}
```

### 步骤2：定义模块结构

重构包结构，使其符合 Spring Modulith 规范。

**推荐结构：**
```
com.jstore/                           # 应用根包
├── order/                            # 订单模块
│   ├── Order.kt                      # 公共 API
│   ├── OrderService.kt               # 公共 API
│   ├── internal/                     # 内部实现（其他模块不可访问）
│   │   ├── OrderRepository.kt
│   │   ├── OrderEntity.kt
│   │   └── OrderValidator.kt
│   └── events/                       # 模块事件
│       ├── OrderCreatedEvent.kt
│       └── OrderCompletedEvent.kt
│
├── goods/                            # 商品模块
│   ├── Goods.kt                      # 公共 API
│   ├── GoodsService.kt               # 公共 API
│   ├── internal/                     # 内部实现
│   │   ├── GoodsRepository.kt
│   │   └── GoodsEntity.kt
│   └── events/
│       └── StockReducedEvent.kt
│
└── JStoreApplication.kt              # 主应用类
```

### 步骤3：标记模块（可选）

使用 `@ApplicationModule` 注解显式标记模块：

```kotlin
// com/jstore/order/package-info.kt
@ApplicationModule(
    displayName = "Order Module",
    allowedDependencies = ["goods"]  // 声明允许依赖的模块
)
package com.jstore.order

import org.springframework.modulith.ApplicationModule
```

### 步骤4：使用事件进行模块通信

**订单模块发布事件：**
```kotlin
// com/jstore/order/OrderService.kt
package com.jstore.order

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val events: ApplicationEventPublisher
) {
    @Transactional
    fun createOrder(request: CreateOrderRequest): Order {
        // 1. 创建订单
        val order = Order(...)
        
        // 2. 发布订单创建事件（异步通知其他模块）
        events.publishEvent(OrderCreatedEvent(
            orderId = order.id,
            goodsId = request.goodsId,
            quantity = request.quantity
        ))
        
        return order
    }
}
```

**商品模块监听事件：**
```kotlin
// com/jstore/goods/internal/StockEventListener.kt
package com.jstore.goods.internal

import com.jstore.order.events.OrderCreatedEvent
import org.springframework.modulith.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
internal class StockEventListener(
    private val goodsService: GoodsService
) {
    // 使用 @ApplicationModuleListener 标记事件监听器
    // Spring Modulith 会自动处理事件的持久化和重试
    @ApplicationModuleListener
    fun onOrderCreated(event: OrderCreatedEvent) {
        // 扣减库存
        goodsService.reduceStock(event.goodsId, event.quantity)
    }
}
```

**事件定义：**
```kotlin
// com/jstore/order/events/OrderCreatedEvent.kt
package com.jstore.order.events

import java.time.Instant

data class OrderCreatedEvent(
    val orderId: String,
    val goodsId: String,
    val quantity: Int,
    val occurredAt: Instant = Instant.now()
)
```

### 步骤5：编写架构测试

验证模块边界是否被正确遵守：

```kotlin
// src/test/kotlin/com/jstore/ModularityTest.kt
package com.jstore

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTest {
    
    private val modules = ApplicationModules.of(JStoreApplication::class.java)
    
    @Test
    fun `验证模块结构`() {
        // 验证所有模块是否符合规范
        modules.verify()
    }
    
    @Test
    fun `生成模块文档`() {
        // 生成模块依赖图和文档
        Documenter(modules)
            .writeDocumentation()
            .writeModulesAsPlantUml()
    }
    
    @Test
    fun `验证订单模块不依赖商品模块内部实现`() {
        modules.getModuleByName("order")
            .verifyNoDependencies("goods.internal")
    }
}
```

### 步骤6：事件持久化（可选）

启用事件持久化，确保事件不会丢失：

```yaml
# application.yml
spring:
  modulith:
    events:
      # 启用事件发布注册表（持久化事件）
      jdbc:
        enabled: true
      # 事件完成后保留的时间
      completion-mode: delete
      # 重试配置
      republish-outstanding-events-on-restart: true
```

需要添加数据库依赖（您已有 JPA）：
```kotlin
// Spring Modulith 会自动创建事件表
implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
```

### 步骤7：将事件外发到消息队列

如果需要将内部事件发布到 Kafka 等外部系统：

```kotlin
// 添加依赖
implementation("org.springframework.modulith:spring-modulith-events-kafka")

// 配置 application.yml
spring:
  modulith:
    events:
      externalization:
        enabled: true
```

```kotlin
// 标记需要外发的事件
@Externalized("order-events")  // 发送到 Kafka topic: order-events
data class OrderCreatedEvent(...)
```

## Spring Modulith 的优势

### 1. 渐进式架构演进
```
单体应用 → 模块化单体（Spring Modulith） → 微服务
```
- 初期：快速开发，简单部署
- 中期：模块化重构，保持边界
- 后期：必要时拆分为微服务（已有清晰边界）

### 2. 降低分布式复杂性
- ✅ 无网络调用开销
- ✅ 无服务注册发现
- ✅ 无分布式事务
- ✅ 简化的部署和运维

### 3. 强制架构约束
- 编译时检查模块依赖
- 测试时验证架构规则
- 防止代码腐化

### 4. 改善代码质量
- 清晰的模块边界
- 低耦合高内聚
- 便于理解和维护

## 实际应用示例

### 场景：订单创建流程

**不使用 Spring Modulith（传统方式）：**
```kotlin
@Service
class OrderService(
    private val goodsService: GoodsService,      // 紧耦合
    private val inventoryService: InventoryService,  // 紧耦合
    private val notificationService: NotificationService  // 紧耦合
) {
    @Transactional
    fun createOrder(request: CreateOrderRequest): Order {
        // 1. 检查库存
        goodsService.checkStock(request.goodsId, request.quantity)
        
        // 2. 创建订单
        val order = orderRepository.save(Order(...))
        
        // 3. 扣减库存
        inventoryService.reduce(request.goodsId, request.quantity)
        
        // 4. 发送通知
        notificationService.sendOrderConfirmation(order)
        
        return order
    }
}
```

**使用 Spring Modulith（事件驱动）：**
```kotlin
@Service
class OrderService(
    private val events: ApplicationEventPublisher  // 只依赖事件
) {
    @Transactional
    fun createOrder(request: CreateOrderRequest): Order {
        // 1. 创建订单
        val order = orderRepository.save(Order(...))
        
        // 2. 发布事件（异步处理）
        events.publishEvent(OrderCreatedEvent(order))
        
        return order  // 立即返回
    }
}

// 其他模块通过监听事件来处理
@ApplicationModuleListener
fun reduceInventory(event: OrderCreatedEvent) { ... }

@ApplicationModuleListener
fun sendNotification(event: OrderCreatedEvent) { ... }
```

**优势：**
- ✅ 订单模块不依赖其他模块
- ✅ 各模块可以独立开发和测试
- ✅ 易于添加新的事件监听器
- ✅ 支持重试和持久化

## 最佳实践

### 1. 包结构规范
```
com.jstore.{module}/
├── {Entity}.kt              # 领域模型（公共）
├── {Service}.kt             # 服务接口（公共）
├── api/                     # REST API（公共）
├── events/                  # 事件定义（公共）
└── internal/                # 所有内部实现
    ├── {Repository}.kt
    ├── {ServiceImpl}.kt
    └── ...
```

### 2. 事件命名规范
- 使用过去式：`OrderCreatedEvent`（不是 `CreateOrderEvent`）
- 包含时间戳：`occurredAt: Instant`
- 不可变：使用 `data class` + `val`

### 3. 模块大小
- 单个模块不宜过大（建议 < 20 个类）
- 按业务能力划分，不是按技术层次

### 4. 依赖方向
```
应用层（Application）
    ↓ 依赖
领域层（Domain Modules: order, goods）
    ↓ 依赖
基础设施层（Infrastructure: common, common-spring）
```

## 何时使用 Spring Modulith？

### ✅ 适合的场景
1. 单体应用需要模块化
2. 团队规模中小型（< 50 人）
3. 业务复杂度中等
4. 希望保留微服务架构的灵活性
5. 暂时不想处理分布式系统的复杂性

### ❌ 不适合的场景
1. 已经是微服务架构
2. 需要独立扩展某个模块
3. 不同模块使用不同技术栈
4. 团队分布在不同地理位置，需要完全独立部署

## 总结

Spring Modulith 是 Spring 官方提供的**模块化单体架构**解决方案，核心价值在于：

1. **架构约束**：强制模块边界，防止代码腐化
2. **事件驱动**：模块间松耦合通信
3. **渐进式演进**：从单体到微服务的平滑过渡
4. **降低复杂性**：享受模块化收益，避免分布式代价

对于您的 `j-store` 项目，Spring Modulith 可以：
- 确保 `order` 和 `goods` 模块保持清晰边界
- 通过事件实现订单和库存的解耦
- 在单体应用中实践领域驱动设计（DDD）
- 为未来可能的微服务拆分做好准备

**推荐使用场景：** 当 `j-store-application` 作为单体应用部署时，整合多个领域模块。

