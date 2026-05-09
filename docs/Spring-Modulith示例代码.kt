// Spring Modulith 实战示例代码

// ========================================
// 1. 订单模块 - 公共 API
// ========================================

// 文件：com/jstore/order/Order.kt
package com.jstore.order

import java.math.BigDecimal
import java.time.Instant

/**
 * 订单聚合根 - 对外公开的领域模型
 */
data class Order(
    val id: String,
    val goodsId: String,
    val quantity: Int,
    val amount: BigDecimal,
    val status: OrderStatus,
    val createdAt: Instant = Instant.now()
)

enum class OrderStatus {
    PENDING, PAID, SHIPPED, COMPLETED, CANCELLED
}

// 文件：com/jstore/order/OrderService.kt
package com.jstore.order

/**
 * 订单服务接口 - 对外公开的 API
 */
interface OrderService {
    fun createOrder(request: CreateOrderRequest): Order
    fun getOrder(orderId: String): Order?
    fun cancelOrder(orderId: String): Order
}

data class CreateOrderRequest(
    val goodsId: String,
    val quantity: Int,
    val userId: String
)


// ========================================
// 2. 订单模块 - 事件定义
// ========================================

// 文件：com/jstore/order/events/OrderCreatedEvent.kt
package com.jstore.order.events

import java.math.BigDecimal
import java.time.Instant

/**
 * 订单创建事件 - 用于模块间通信
 */
data class OrderCreatedEvent(
    val orderId: String,
    val goodsId: String,
    val quantity: Int,
    val amount: BigDecimal,
    val userId: String,
    val occurredAt: Instant = Instant.now()
)

// 文件：com/jstore/order/events/OrderCancelledEvent.kt
package com.jstore.order.events

import java.time.Instant

data class OrderCancelledEvent(
    val orderId: String,
    val goodsId: String,
    val quantity: Int,
    val occurredAt: Instant = Instant.now()
)


// ========================================
// 3. 订单模块 - 内部实现（其他模块不可访问）
// ========================================

// 文件：com/jstore/order/internal/OrderServiceImpl.kt
package com.jstore.order.internal

import com.jstore.order.*
import com.jstore.order.events.OrderCreatedEvent
import com.jstore.order.events.OrderCancelledEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*

/**
 * 订单服务实现 - 内部实现，不对外暴露
 */
@Service
internal class OrderServiceImpl(
    private val orderRepository: OrderRepository,
    private val events: ApplicationEventPublisher  // 事件发布器
) : OrderService {

    @Transactional
    override fun createOrder(request: CreateOrderRequest): Order {
        // 1. 创建订单实体
        val order = OrderEntity(
            id = UUID.randomUUID().toString(),
            goodsId = request.goodsId,
            quantity = request.quantity,
            userId = request.userId,
            amount = BigDecimal("99.99"),  // 实际应该从商品服务获取
            status = OrderStatus.PENDING
        )

        // 2. 保存到数据库
        val savedOrder = orderRepository.save(order)

        // 3. 发布订单创建事件（异步通知其他模块）
        // Spring Modulith 会自动处理事件的持久化和重试
        events.publishEvent(OrderCreatedEvent(
            orderId = savedOrder.id,
            goodsId = savedOrder.goodsId,
            quantity = savedOrder.quantity,
            amount = savedOrder.amount,
            userId = savedOrder.userId
        ))

        // 4. 返回领域模型（不暴露实体）
        return savedOrder.toDomain()
    }

    @Transactional
    override fun cancelOrder(orderId: String): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalArgumentException("Order not found: $orderId") }

        order.status = OrderStatus.CANCELLED
        val savedOrder = orderRepository.save(order)

        // 发布订单取消事件，通知其他模块（如库存模块需要恢复库存）
        events.publishEvent(OrderCancelledEvent(
            orderId = savedOrder.id,
            goodsId = savedOrder.goodsId,
            quantity = savedOrder.quantity
        ))

        return savedOrder.toDomain()
    }

    override fun getOrder(orderId: String): Order? {
        return orderRepository.findById(orderId)
            .map { it.toDomain() }
            .orElse(null)
    }
}

// 文件：com/jstore/order/internal/OrderEntity.kt
package com.jstore.order.internal

import com.jstore.order.Order
import com.jstore.order.OrderStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

/**
 * 订单实体 - JPA 实体，内部使用
 */
@Entity
@Table(name = "orders")
internal class OrderEntity(
    @Id
    var id: String,

    @Column(nullable = false)
    var goodsId: String,

    @Column(nullable = false)
    var quantity: Int,

    @Column(nullable = false)
    var userId: String,

    @Column(nullable = false)
    var amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
) {
    // 转换为领域模型
    fun toDomain() = Order(
        id = id,
        goodsId = goodsId,
        quantity = quantity,
        amount = amount,
        status = status,
        createdAt = createdAt
    )
}

// 文件：com/jstore/order/internal/OrderRepository.kt
package com.jstore.order.internal

import org.springframework.data.jpa.repository.JpaRepository

/**
 * 订单仓储 - 内部使用
 */
internal interface OrderRepository : JpaRepository<OrderEntity, String>


// ========================================
// 4. 商品模块 - 公共 API
// ========================================

// 文件：com/jstore/goods/Goods.kt
package com.jstore.goods

import java.math.BigDecimal

/**
 * 商品聚合根
 */
data class Goods(
    val id: String,
    val name: String,
    val price: BigDecimal,
    val stock: Int
)

// 文件：com/jstore/goods/GoodsService.kt
package com.jstore.goods

/**
 * 商品服务接口
 */
interface GoodsService {
    fun getGoods(goodsId: String): Goods?
    fun checkStock(goodsId: String, quantity: Int): Boolean
}


// ========================================
// 5. 商品模块 - 事件监听（模块间通信）
// ========================================

// 文件：com/jstore/goods/internal/InventoryEventListener.kt
package com.jstore.goods.internal

import com.jstore.order.events.OrderCreatedEvent
import com.jstore.order.events.OrderCancelledEvent
import org.springframework.modulith.ApplicationModuleListener
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

/**
 * 库存事件监听器 - 监听订单事件，更新库存
 *
 * 注意：
 * 1. 使用 @ApplicationModuleListener 而不是普通的 @EventListener
 * 2. Spring Modulith 会自动处理事件持久化、重试和幂等性
 * 3. 如果监听器抛出异常，事件会被重试
 */
@Component
internal class InventoryEventListener(
    private val inventoryService: InventoryService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 监听订单创建事件，扣减库存
     */
    @ApplicationModuleListener
    fun onOrderCreated(event: OrderCreatedEvent) {
        logger.info("收到订单创建事件: orderId={}, goodsId={}, quantity={}",
            event.orderId, event.goodsId, event.quantity)

        try {
            // 扣减库存
            inventoryService.reduceStock(event.goodsId, event.quantity)

            logger.info("库存扣减成功: goodsId={}, quantity={}",
                event.goodsId, event.quantity)
        } catch (e: Exception) {
            logger.error("库存扣减失败: goodsId={}, quantity={}",
                event.goodsId, event.quantity, e)
            // 抛出异常，Spring Modulith 会重试
            throw e
        }
    }

    /**
     * 监听订单取消事件，恢复库存
     */
    @ApplicationModuleListener
    fun onOrderCancelled(event: OrderCancelledEvent) {
        logger.info("收到订单取消事件: orderId={}, goodsId={}, quantity={}",
            event.orderId, event.goodsId, event.quantity)

        // 恢复库存
        inventoryService.restoreStock(event.goodsId, event.quantity)

        logger.info("库存恢复成功: goodsId={}, quantity={}",
            event.goodsId, event.quantity)
    }
}

// 文件：com/jstore/goods/internal/InventoryService.kt
package com.jstore.goods.internal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 库存服务 - 内部实现
 */
@Service
internal class InventoryService(
    private val goodsRepository: GoodsRepository
) {

    @Transactional
    fun reduceStock(goodsId: String, quantity: Int) {
        val goods = goodsRepository.findById(goodsId)
            .orElseThrow { IllegalArgumentException("Goods not found: $goodsId") }

        if (goods.stock < quantity) {
            throw IllegalStateException("库存不足: goodsId=$goodsId, stock=${goods.stock}, required=$quantity")
        }

        goods.stock -= quantity
        goodsRepository.save(goods)
    }

    @Transactional
    fun restoreStock(goodsId: String, quantity: Int) {
        val goods = goodsRepository.findById(goodsId)
            .orElseThrow { IllegalArgumentException("Goods not found: $goodsId") }

        goods.stock += quantity
        goodsRepository.save(goods)
    }
}


// ========================================
// 6. 测试 - 验证模块架构
// ========================================

// 文件：src/test/kotlin/com/jstore/ModularityTest.kt
package com.jstore

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * 模块化架构测试
 *
 * 验证：
 * 1. 模块结构是否符合规范
 * 2. 模块依赖是否合理
 * 3. 是否有违反架构规则的依赖
 */
class ModularityTest {

    private val modules = ApplicationModules.of(JStoreApplication::class.java)

    @Test
    fun `验证所有模块符合架构规范`() {
        // 这会检查：
        // - 所有模块是否正确识别
        // - 是否有循环依赖
        // - 是否有违反封装的依赖（如访问 internal 包）
        modules.verify()
    }

    @Test
    fun `生成模块依赖文档`() {
        // 生成以下文档：
        // - target/spring-modulith-docs/modules.adoc (AsciiDoc)
        // - target/spring-modulith-docs/components.puml (PlantUML)
        Documenter(modules)
            .writeDocumentation()
            .writeModulesAsPlantUml()
    }

    @Test
    fun `验证订单模块不直接依赖商品模块内部实现`() {
        // 确保 order 模块不能访问 goods.internal 包
        modules.getModuleByName("order")
            .verifyNoDependencies("goods.internal")
    }

    @Test
    fun `打印所有模块信息`() {
        modules.forEach { module ->
            println("""
                模块名称: ${module.name}
                包名: ${module.basePackage}
                依赖的模块: ${module.dependencies.joinToString { it.name }}
            """.trimIndent())
        }
    }
}


// ========================================
// 7. 集成测试 - 测试事件驱动流程
// ========================================

// 文件：src/test/kotlin/com/jstore/order/OrderIntegrationTest.kt
package com.jstore.order

import com.jstore.goods.GoodsService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.modulith.test.Scenario

/**
 * 订单模块集成测试
 * 使用 @ApplicationModuleTest 只启动相关模块
 */
@ApplicationModuleTest
class OrderIntegrationTest {

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var goodsService: GoodsService

    @Test
    fun `创建订单应该发布订单创建事件`(scenario: Scenario) {
        // Given: 准备测试数据
        val request = CreateOrderRequest(
            goodsId = "goods-123",
            quantity = 2,
            userId = "user-456"
        )

        // When: 创建订单
        scenario.stimulate {
            orderService.createOrder(request)
        }
        // Then: 验证事件被发布，并且被处理
        .andWaitForEventOfType(OrderCreatedEvent::class.java)
        .matchingMapped { it.orderId }
        .toArrive()

        // 验证库存被扣减（通过事件监听器）
        val goods = goodsService.getGoods("goods-123")
        // assert(goods?.stock == expectedStock)
    }
}


// ========================================
// 8. 配置文件
// ========================================

/*
# application.yml
spring:
  modulith:
    # 启用事件持久化
    events:
      jdbc:
        enabled: true
      # 事件完成后的处理策略
      completion-mode: delete  # 或 update
      # 启动时重新发布未完成的事件
      republish-outstanding-events-on-restart: true

  datasource:
    url: jdbc:postgresql://localhost:5432/jstore
    username: postgres
    password: postgres

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

logging:
  level:
    org.springframework.modulith: DEBUG
*/


// ========================================
// 9. 构建配置
// ========================================

/*
// build.gradle.kts

dependencies {
    // Spring Modulith BOM
    implementation(platform(libs.spring.modulith.bom))

    // Spring Modulith 核心
    implementation(libs.spring.modulith.starter.core)

    // 事件持久化（使用 JDBC）
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")

    // 测试支持
    testImplementation(libs.spring.modulith.starter.test)

    // 可选：事件外发到 Kafka
    // implementation("org.springframework.modulith:spring-modulith-events-kafka")

    // 其他依赖...
}
*/

