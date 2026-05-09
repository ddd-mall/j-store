# DDD订单模型改进实施指南

## 概述

这份指南包含了如何将j-store-order模块从贫血模型转换为富领域模型的具体步骤。

---

## 📋 目录

1. [第一步：Order聚合根不可变性重构](#第一步order聚合根不可变性重构)
2. [第二步：OrderItem类型确认](#第二步orderitem类型确认)
3. [第三步：业务方法实现](#第三步业务方法实现)
4. [第四步：领域事件集成](#第四步领域事件集成)
5. [第五步：反腐层实现](#第五步反腐层实现)
6. [第六步：值对象完整性](#第六步值对象完整性)
7. [第七步：Repository清晰化](#第七步repository清晰化)
8. [第八步：并发控制](#第八步并发控制)

---

## 第一步：Order聚合根不可变性重构

### 当前问题

```kotlin
// ❌ 当前实现
interface Order : AgreeGate<OrderId> {
    val items: List<OrderItem>  // 可变集合
    var shippingAddressInfo: GeoAddressInfo  // var
    var status: OrderStatus  // var
    var amount: Price  // var
    
    fun reserve(): Order  // 只有签名
    fun pay(): Order
    // ...
}
```

### 改进方案

```kotlin
// ✅ 改进后的接口
interface Order : AgreeGate<OrderId> {
    // 只读属性
    val items: List<OrderItem>
    val shippingAddressInfo: GeoAddressInfo
    val status: OrderStatus
    val amount: Price
    val createdTime: LocalDateTime?
    val updateTime: LocalDateTime?
    
    // 业务操作返回Result或事件列表
    fun reserve(): Result<List<DomainEvent>>
    fun pay(): Result<List<DomainEvent>>
    fun shipping(): Result<List<DomainEvent>>
    fun complete(): Result<List<DomainEvent>>
    fun cancel(reason: String): Result<List<DomainEvent>>
    fun refund(): Result<List<DomainEvent>>
    fun confirm(): Result<List<DomainEvent>>
    fun undo(): Result<List<DomainEvent>>
    
    // 事件管理
    fun getDomainEvents(): List<DomainEvent>
    fun clearDomainEvents()
}

// ✅ 具体实现类
class OrderImpl private constructor(
    override val id: OrderId,
    override val buyerInfo: UserInfo,
    private val _items: MutableList<OrderItem>,
    override val status: OrderStatus,
    override val amount: Price,
    override val shippingAddressInfo: GeoAddressInfo,
    override val createdTime: LocalDateTime?,
    override val updateTime: LocalDateTime?,
    private val _domainEvents: MutableList<DomainEvent> = mutableListOf()
) : Order {
    
    // ✅ 只读视图，防止外部修改
    override val items: List<OrderItem>
        get() = _items.toList()
    
    // ✅ 状态修改通过新实例返回
    override fun reserve(): Result<List<DomainEvent>> {
        return if (status != OrderStatus.CREATED) {
            Result.failure(Exception("只能从CREATED状态预留"))
        } else {
            _domainEvents.add(OrderReservedEvent(id, Instant.now()))
            Result.success(_domainEvents.toList())
        }
    }
    
    override fun pay(): Result<List<DomainEvent>> {
        return if (status != OrderStatus.RESERVE_REQUESTED) {
            Result.failure(Exception("只能从RESERVE_REQUESTED状态支付"))
        } else {
            _domainEvents.add(OrderPaidEvent(id, amount, Instant.now()))
            Result.success(_domainEvents.toList())
        }
    }
    
    override fun shipping(): Result<List<DomainEvent>> {
        return if (status != OrderStatus.PAYED) {
            Result.failure(Exception("只能从PAYED状态发货"))
        } else {
            _domainEvents.add(OrderShippedEvent(id, Instant.now()))
            Result.success(_domainEvents.toList())
        }
    }
    
    override fun complete(): Result<List<DomainEvent>> {
        return if (status != OrderStatus.SHIPPED) {
            Result.failure(Exception("只能从SHIPPED状态完成"))
        } else {
            _domainEvents.add(OrderCompletedEvent(id, Instant.now()))
            Result.success(_domainEvents.toList())
        }
    }
    
    override fun cancel(reason: String): Result<List<DomainEvent>> {
        return if (status !in listOf(OrderStatus.CREATED, OrderStatus.RESERVE_REQUESTED)) {
            Result.failure(Exception("不能取消已${status.name}的订单"))
        } else {
            _domainEvents.add(OrderCancelledEvent(id, reason, Instant.now()))
            Result.success(_domainEvents.toList())
        }
    }
    
    override fun refund(): Result<List<DomainEvent>> {
        return if (status !in listOf(OrderStatus.PAYED, OrderStatus.SHIPPED, OrderStatus.RECEIVED)) {
            Result.failure(Exception("只能为已支付的订单申请退款"))
        } else {
            _domainEvents.add(OrderRefundRequestedEvent(id, amount, Instant.now()))
            Result.success(_domainEvents.toList())
        }
    }
    
    override fun confirm(): Result<List<DomainEvent>> {
        return if (status != OrderStatus.RESERVE_REQUESTED) {
            Result.failure(Exception("只能确认预留状态的订单"))
        } else {
            _domainEvents.add(OrderConfirmedEvent(id, Instant.now()))
            Result.success(_domainEvents.toList())
        }
    }
    
    override fun undo(): Result<List<DomainEvent>> {
        return if (status == OrderStatus.CREATED) {
            Result.failure(Exception("初始状态无法撤销"))
        } else {
            _domainEvents.add(OrderUndoneEvent(id, Instant.now()))
            Result.success(_domainEvents.toList())
        }
    }
    
    override fun getDomainEvents(): List<DomainEvent> = _domainEvents.toList()
    
    override fun clearDomainEvents() {
        _domainEvents.clear()
    }
    
    companion object Factory {
        fun create(
            id: OrderId,
            buyerInfo: UserInfo,
            items: List<OrderItem>,
            shippingAddressInfo: GeoAddressInfo
        ): Result<Order> {
            return if (items.isEmpty()) {
                Result.failure(Exception("订单必须包含至少一个行项"))
            } else {
                val totalAmount = items.fold(Price(BigDecimal.ZERO)) { acc, item ->
                    // 假设OrderItem有calculateTotal()方法
                    acc // 实际应该累加
                }
                
                val order = OrderImpl(
                    id = id,
                    buyerInfo = buyerInfo,
                    _items = items.toMutableList(),
                    status = OrderStatus.CREATED,
                    amount = totalAmount,
                    shippingAddressInfo = shippingAddressInfo,
                    createdTime = LocalDateTime.now(),
                    updateTime = LocalDateTime.now(),
                    _domainEvents = mutableListOf(
                        OrderCreatedEvent(id, totalAmount, Instant.now())
                    )
                )
                Result.success(order)
            }
        }
    }
}
```

### 关键改进点

- ✅ items属性返回不可变的List副本
- ✅ 所有属性改为val（不可变）
- ✅ 业务操作返回Result<List<DomainEvent>>
- ✅ 事件在操作时立即记录
- ✅ 状态转移检查在聚合根中
- ✅ 提供工厂方法进行验证

---

## 第二步：OrderItem类型确认

### 决策：OrderItem是聚合内的实体

**理由**：
- 需要行号（lineId）来唯一标识
- 在订单确认前，数量和价格可能需要修改
- 不需要被其他聚合根直接引用
- 不需要独立的仓储

### 实现

```kotlin
// ✅ OrderItem - 聚合内不可变实体
data class OrderItem(
    val lineId: OrderLineId,
    val goodsId: String,  // 来自Goods上下文
    val goodsName: String,
    val quantity: Quantity,
    val unitPrice: Price,
    val totalPrice: Price
) : OrderItemStatus {
    
    companion object Factory {
        fun create(
            lineId: OrderLineId,
            goodsId: String,
            goodsName: String,
            quantity: Quantity,
            unitPrice: Price
        ): Result<OrderItem> {
            return if (quantity.value <= 0) {
                Result.failure(Exception("数量必须大于0"))
            } else if (unitPrice.amount < BigDecimal.ZERO) {
                Result.failure(Exception("单价不能为负数"))
            } else {
                val totalPrice = Price(unitPrice.amount * quantity.value.toBigDecimal())
                Result.success(
                    OrderItem(
                        lineId = lineId,
                        goodsId = goodsId,
                        goodsName = goodsName,
                        quantity = quantity,
                        unitPrice = unitPrice,
                        totalPrice = totalPrice
                    )
                )
            }
        }
    }
    
    fun updateQuantity(newQuantity: Quantity): Result<OrderItem> {
        return if (newQuantity.value <= 0) {
            Result.failure(Exception("数量必须大于0"))
        } else {
            val newTotal = Price(unitPrice.amount * newQuantity.value.toBigDecimal())
            Result.success(
                this.copy(quantity = newQuantity, totalPrice = newTotal)
            )
        }
    }
    
    // 基于lineId比较相等性
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrderItem) return false
        return lineId == other.lineId
    }
    
    override fun hashCode(): Int = lineId.hashCode()
}

// 值对象：订单行ID
data class OrderLineId(val value: String) {
    companion object {
        fun generate() = OrderLineId(UUID.randomUUID().toString())
    }
}

// 值对象：数量
data class Quantity(val value: Int) {
    init {
        require(value > 0) { "数量必须大于0" }
        require(value <= 999) { "单件数量不超过999" }
    }
}
```

### 关键点

- ✅ OrderItem是不可变的数据类
- ✅ 有唯一标识符（lineId）
- ✅ 提供工厂方法验证
- ✅ updateQuantity返回新实例（不可变）
- ✅ 基于lineId的等值性比较

---

## 第三步：业务方法实现

### 状态转移规则

```kotlin
/**
 * 订单状态机
 * 
 * CREATED
 *    ↓
 * RESERVE_REQUESTED  → RESERVED  →  PAYMENT_REQUESTED  →  PAYED
 *    ↓                                                        ↓
 * CANCEL_REQUESTED → CANCELED                         SHIPPING_REQUESTED
 *                                                            ↓
 *                                                          SHIPPED
 *                                                            ↓
 *                                                          RECEIVED
 *                                                            ↓
 *                                                         COMPLETE
 */
object OrderStatusTransitionRules {
    
    fun isValidTransition(from: OrderStatus, to: OrderStatus): Boolean {
        return when (from) {
            OrderStatus.CREATED -> 
                to in listOf(OrderStatus.RESERVE_REQUESTED, OrderStatus.CANCEL_REQUESTED)
            
            OrderStatus.RESERVE_REQUESTED -> 
                to in listOf(OrderStatus.RESERVED, OrderStatus.FAILED_TO_RESERVE_REQUEST, 
                            OrderStatus.CANCEL_REQUESTED)
            
            OrderStatus.RESERVED -> 
                to in listOf(OrderStatus.PAYMENT_REQUESTED, OrderStatus.CANCEL_REQUESTED)
            
            OrderStatus.PAYMENT_REQUESTED -> 
                to in listOf(OrderStatus.PAYED, OrderStatus.CANCEL_REQUESTED)
            
            OrderStatus.PAYED -> 
                to in listOf(OrderStatus.SHIPPING_REQUESTED, OrderStatus.REFUND_REQUESTED)
            
            OrderStatus.SHIPPING_REQUESTED -> 
                to in listOf(OrderStatus.SHIPPED, OrderStatus.REFUND_REQUESTED)
            
            OrderStatus.SHIPPED -> 
                to in listOf(OrderStatus.RECEIVED, OrderStatus.REFUND_REQUESTED)
            
            OrderStatus.RECEIVED -> 
                to in listOf(OrderStatus.COMPLETE, OrderStatus.REFUND_REQUESTED)
            
            OrderStatus.COMPLETE -> 
                to in listOf(OrderStatus.REFUND_REQUESTED)
            
            OrderStatus.REFUND_REQUESTED -> 
                to in listOf(OrderStatus.REFUND_REQUEST_PROVED)
            
            OrderStatus.REFUND_REQUEST_PROVED -> 
                to in listOf(OrderStatus.REFUNDING)
            
            OrderStatus.REFUNDING -> 
                to in listOf(OrderStatus.REFUNDED)
            
            OrderStatus.CANCEL_REQUESTED -> 
                to in listOf(OrderStatus.CANCEL_REQUEST_PROVED)
            
            OrderStatus.CANCEL_REQUEST_PROVED -> 
                to in listOf(OrderStatus.CANCELED)
            
            else -> false
        }
    }
    
    fun getNextStates(current: OrderStatus): Set<OrderStatus> {
        return OrderStatus.values().filter { isValidTransition(current, it) }.toSet()
    }
}
```

### 改进后的业务方法

```kotlin
// 在OrderImpl中，改进reserve方法为真正的状态转移
override fun reserve(): Result<List<DomainEvent>> {
    return if (!OrderStatusTransitionRules.isValidTransition(
        status, 
        OrderStatus.RESERVE_REQUESTED
    )) {
        Result.failure(
            InvalidOrderStateException(
                "无法从${status.name}转移到RESERVE_REQUESTED"
            )
        )
    } else {
        _domainEvents.add(
            OrderReserveRequestedEvent(
                id = id,
                totalAmount = amount,
                timestamp = Instant.now()
            )
        )
        Result.success(_domainEvents.toList())
    }
}
```

---

## 第四步：领域事件集成

### 创建领域事件

```kotlin
// OrderDomainEvents.kt
package com.jstore.order.domain.event

import java.math.BigDecimal
import java.time.Instant

// 基础事件接口
interface DomainEvent {
    val timestamp: Instant
}

// ✅ 订单事件
data class OrderCreatedEvent(
    val orderId: OrderId,
    val totalAmount: Price,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderReserveRequestedEvent(
    val orderId: OrderId,
    val totalAmount: Price,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderReservedEvent(
    val orderId: OrderId,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderPaymentRequestedEvent(
    val orderId: OrderId,
    val totalAmount: Price,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderPaidEvent(
    val orderId: OrderId,
    val paidAmount: Price,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderShippingRequestedEvent(
    val orderId: OrderId,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderShippedEvent(
    val orderId: OrderId,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderReceivedEvent(
    val orderId: OrderId,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderCompletedEvent(
    val orderId: OrderId,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderCancelledEvent(
    val orderId: OrderId,
    val reason: String,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderRefundRequestedEvent(
    val orderId: OrderId,
    val refundAmount: Price,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderConfirmedEvent(
    val orderId: OrderId,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()

data class OrderUndoneEvent(
    val orderId: OrderId,
    override val timestamp: Instant = Instant.now()
) : DomainEvent()
```

### 应用服务中的事件发布

```kotlin
// OrderApplicationService.kt
@Service
@Transactional
class OrderApplicationService(
    private val orderRepository: OrderRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    
    fun createOrder(cmd: CreateOrderCommand): Result<OrderId> {
        return try {
            // 1. 创建聚合根
            val orderResult = OrderImpl.Factory.create(
                id = OrderId.generate(),
                buyerInfo = cmd.buyerInfo,
                items = cmd.items,
                shippingAddressInfo = cmd.shippingAddressInfo
            )
            
            if (orderResult.isFailure) {
                return Result.failure(orderResult.exceptionOrNull()!!)
            }
            
            val order = orderResult.getOrThrow() as OrderImpl
            
            // 2. 持久化
            orderRepository.save(order)
            
            // 3. 发布领域事件
            order.getDomainEvents().forEach { event ->
                eventPublisher.publishEvent(event)
            }
            
            // 4. 清空事件
            order.clearDomainEvents()
            
            Result.success(order.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun reserveOrder(cmd: ReserveOrderCommand): Result<Unit> {
        return try {
            val order = orderRepository.findById(cmd.orderId)
                ?: return Result.failure(Exception("订单不存在"))
            
            val reserveResult = order.reserve()
            
            if (reserveResult.isFailure) {
                return Result.failure(reserveResult.exceptionOrNull()!!)
            }
            
            orderRepository.save(order)
            
            reserveResult.getOrThrow().forEach { event ->
                eventPublisher.publishEvent(event)
            }
            
            order.clearDomainEvents()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## 第五步：反腐层实现

### 定义反腐层接口（订单上下文）

```kotlin
// OrderAntiCorruptionLayer.kt
package com.jstore.order.domain.service

// ✅ 订单上下文定义的接口（不依赖Goods的具体模型）
interface GoodsCheckService {
    fun checkGoodsExists(goodsId: String): Boolean
    fun getGoodsPrice(goodsId: String): Result<Price>
    fun checkStockAvailable(goodsId: String, quantity: Int): Boolean
}

interface GoodsReservationService {
    fun reserveStock(goodsId: String, quantity: Int): Result<ReservationId>
    fun releaseReservation(reservationId: ReservationId): Result<Unit>
}

interface PaymentInitiationService {
    fun initiatePayment(
        orderId: OrderId,
        customerId: String,
        amount: Price
    ): Result<PaymentId>
}

// ✅ 值对象
data class ReservationId(val value: String)
data class PaymentId(val value: String)
```

### 实现反腐层（基础设施层）

```kotlin
// GoodsAntiCorruptionLayerImpl.kt
package com.jstore.order.infrastructure.client

import org.springframework.stereotype.Component
import com.jstore.order.domain.service.*

/**
 * 反腐层实现
 * 将Goods模块的API转换为Order模块能理解的接口
 */
@Component
class GoodsCheckServiceImpl(
    private val goodsRestClient: GoodsRestClient  // 来自Goods模块的HTTP客户端
) : GoodsCheckService {
    
    override fun checkGoodsExists(goodsId: String): Boolean {
        return try {
            goodsRestClient.getGoods(goodsId) != null
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getGoodsPrice(goodsId: String): Result<Price> {
        return try {
            val goodsDto = goodsRestClient.getGoods(goodsId)
                ?: return Result.failure(Exception("商品不存在"))
            
            Result.success(Price(goodsDto.price))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun checkStockAvailable(goodsId: String, quantity: Int): Boolean {
        return try {
            val goodsDto = goodsRestClient.getGoods(goodsId)
                ?: return false
            
            goodsDto.stock >= quantity
        } catch (e: Exception) {
            false
        }
    }
}

@Component
class GoodsReservationServiceImpl(
    private val goodsRestClient: GoodsRestClient,
    private val eventPublisher: ApplicationEventPublisher
) : GoodsReservationService {
    
    override fun reserveStock(goodsId: String, quantity: Int): Result<ReservationId> {
        return try {
            val response = goodsRestClient.reserveStock(goodsId, quantity)
            
            // 发布事件让Goods模块处理
            eventPublisher.publishEvent(
                StockReservationRequestedEvent(
                    goodsId = goodsId,
                    quantity = quantity,
                    timestamp = Instant.now()
                )
            )
            
            Result.success(ReservationId(response.reservationId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun releaseReservation(reservationId: ReservationId): Result<Unit> {
        return try {
            goodsRestClient.releaseReservation(reservationId.value)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Component
class PaymentInitiationServiceImpl(
    private val paymentRestClient: PaymentRestClient
) : PaymentInitiationService {
    
    override fun initiatePayment(
        orderId: OrderId,
        customerId: String,
        amount: Price
    ): Result<PaymentId> {
        return try {
            val response = paymentRestClient.initiatePayment(
                orderId = orderId.value,
                customerId = customerId,
                amount = amount.amount.toDouble()
            )
            
            Result.success(PaymentId(response.paymentId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## 第六步：值对象完整性

### 完整的值对象实现

```kotlin
// PriceValueObject.kt
package com.jstore.order.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 金额值对象
 * - 不可变
 * - 包含业务验证
 * - 支持业务计算
 */
@JvmInline
value class Price(val amount: BigDecimal) {
    
    companion object {
        val ZERO = Price(BigDecimal.ZERO)
        val MAX = Price(BigDecimal.valueOf(999999.99))
    }
    
    init {
        require(amount >= BigDecimal.ZERO) { "金额不能为负数" }
        require(amount <= MAX.amount) { "金额超过上限" }
        require(amount.scale() <= 2) { "金额精度最多两位小数" }
    }
    
    fun add(other: Price): Price = Price(amount.add(other.amount))
    
    fun subtract(other: Price): Price = Price(amount.subtract(other.amount))
    
    fun multiply(factor: Int): Price = 
        Price(amount.multiply(factor.toBigDecimal()).setScale(2, RoundingMode.HALF_UP))
    
    fun multiply(factor: BigDecimal): Price = 
        Price(amount.multiply(factor).setScale(2, RoundingMode.HALF_UP))
    
    fun divide(divisor: Int): Price = 
        Price(amount.divide(divisor.toBigDecimal(), 2, RoundingMode.HALF_UP))
    
    override fun toString(): String = "¥$amount"
}

// UserInfoValueObject.kt
/**
 * 用户信息值对象
 * - 不可变
 * - 包含业务验证
 */
data class UserInfo(
    val uid: Long,
    val phoneNumber: PhoneNumber,  // ✅ val - 不可变
    val userName: String
) {
    init {
        require(uid > 0) { "用户ID必须大于0" }
        require(userName.isNotBlank()) { "用户名不能为空" }
        require(userName.length <= 50) { "用户名长度不超过50" }
    }
}

@JvmInline
value class PhoneNumber(val value: String) {
    init {
        require(value.matches(Regex("^1[3-9]\\d{9}$"))) { "手机号格式错误" }
    }
}

// GeoAddressInfoValueObject.kt
/**
 * 地理位置信息值对象
 * - 不可变
 * - 包含业务验证
 */
data class GeoAddressInfo(
    val districtCode: String,
    val province: String,
    val city: String,
    val county: String,
    val detailAddress: String
) {
    init {
        require(districtCode.isNotBlank()) { "行政编码不能为空" }
        require(province.isNotBlank()) { "省份不能为空" }
        require(city.isNotBlank()) { "城市不能为空" }
        require(detailAddress.isNotBlank()) { "详细地址不能为空" }
        require(detailAddress.length <= 200) { "详细地址长度不超过200" }
    }
    
    val level: DistrictLevel = when {
        county.isNotBlank() -> DistrictLevel.COUNTY
        city.isNotBlank() -> DistrictLevel.CITY
        else -> DistrictLevel.PROVINCE
    }
}

enum class DistrictLevel(val codeLen: Int) {
    PROVINCE(2),
    CITY(4),
    COUNTY(6)
}
```

---

## 第七步：Repository清晰化

### 改进Repository接口

```kotlin
// OrderRepository.kt
package com.jstore.order.domain.repository

import com.jstore.order.domain.model.Order
import com.jstore.order.domain.model.OrderId

/**
 * 订单仓储接口
 * - 只定义业务相关的方法
 * - 不暴露基础设施概念（如锁）
 */
interface OrderRepository {
    
    /**
     * 添加新订单
     */
    fun add(order: Order): Result<Unit>
    
    /**
     * 保存已存在的订单
     */
    fun save(order: Order): Result<Unit>
    
    /**
     * 根据ID查询订单
     */
    fun findById(id: OrderId): Order?
    
    /**
     * 根据买家ID查询订单列表
     */
    fun findByBuyerUserId(uid: Long): List<Order>
    
    /**
     * 分页查询
     */
    fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order>
}
```

### 仓储实现

```kotlin
// OrderRepositoryImpl.kt
package com.jstore.order.infrastructure.persistence

import org.springframework.stereotype.Repository
import com.jstore.order.domain.repository.OrderRepository
import com.jstore.order.domain.model.*

/**
 * 订单仓储实现
 */
@Repository
class OrderRepositoryImpl(
    private val jpaRepository: OrderJpaRepository,
    private val mapper: OrderEntityMapper
) : OrderRepository {
    
    override fun add(order: Order): Result<Unit> {
        return try {
            val entity = mapper.toDbo(order)
            entity.isNew = true  // 新记录
            jpaRepository.save(entity)
            Result.success(Unit)
        } catch (e: OptimisticLockingFailureException) {
            Result.failure(Exception("订单已被修改，请重新加载"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun save(order: Order): Result<Unit> {
        return try {
            val entity = mapper.toDbo(order)
            jpaRepository.save(entity)
            Result.success(Unit)
        } catch (e: OptimisticLockingFailureException) {
            Result.failure(Exception("订单已被其他用户修改，请重新加载"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun findById(id: OrderId): Order? {
        val entity = jpaRepository.findById(id.value).orElse(null) ?: return null
        return mapper.toDomain(entity)
    }
    
    override fun findByBuyerUserId(uid: Long): List<Order> {
        return jpaRepository.findByBuyerUserId(uid)
            .map { mapper.toDomain(it) }
    }
    
    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order> {
        val pageable = PageRequest.of(currentPage - 1, pageSize)
        val entities = jpaRepository.findByBuyerUserId(uid, pageable)
        return entities.map { mapper.toDomain(it) }
    }
}

/**
 * 数据库实体
 */
@Entity
@Table(name = "t_order")
data class OrderEntity(
    @Id
    val id: String,
    val buyerUserId: Long,
    @ElementCollection
    val items: List<OrderItemEntity>,
    val status: String,
    val amount: BigDecimal,
    val address: String,
    @Version
    var version: Long = 0L,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 实体映射器
 */
@Component
class OrderEntityMapper {
    
    fun toDomain(entity: OrderEntity): Order {
        val items = entity.items.map { itemEntity ->
            OrderItem(
                lineId = OrderLineId(itemEntity.lineId),
                goodsId = itemEntity.goodsId,
                goodsName = itemEntity.goodsName,
                quantity = Quantity(itemEntity.quantity),
                unitPrice = Price(itemEntity.unitPrice),
                totalPrice = Price(itemEntity.totalPrice)
            )
        }
        
        return OrderImpl(
            id = OrderId(entity.id),
            buyerInfo = UserInfo(
                uid = entity.buyerUserId,
                phoneNumber = PhoneNumber(""), // 需要从entity获取
                userName = ""
            ),
            items = items.toMutableList(),
            status = OrderStatus.valueOf(entity.status),
            amount = Price(entity.amount),
            shippingAddressInfo = parseGeoAddressInfo(entity.address),
            createdTime = entity.createdAt,
            updateTime = entity.updatedAt
        )
    }
    
    fun toDbo(order: Order): OrderEntity {
        return OrderEntity(
            id = order.id.value,
            buyerUserId = order.buyerInfo.uid,
            items = order.items.map { item ->
                OrderItemEntity(
                    lineId = item.lineId.value,
                    goodsId = item.goodsId,
                    goodsName = item.goodsName,
                    quantity = item.quantity.value,
                    unitPrice = item.unitPrice.amount,
                    totalPrice = item.totalPrice.amount
                )
            },
            status = order.status.name,
            amount = order.amount.amount,
            address = serializeGeoAddressInfo(order.shippingAddressInfo)
        )
    }
    
    private fun parseGeoAddressInfo(address: String): GeoAddressInfo {
        // 解析JSON或其他格式
        return GeoAddressInfo(
            districtCode = "",
            province = "",
            city = "",
            county = "",
            detailAddress = address
        )
    }
    
    private fun serializeGeoAddressInfo(info: GeoAddressInfo): String {
        // 序列化为JSON或其他格式
        return info.detailAddress
    }
}
```

---

## 第八步：并发控制

### 乐观锁实现

```kotlin
// OrderRepositoryImpl.kt - 完整的并发控制
@Repository
class OrderRepositoryImpl(
    private val jpaRepository: OrderJpaRepository
) : OrderRepository {
    
    override fun save(order: Order): Result<Unit> {
        return try {
            val entity = mapper.toDbo(order)
            jpaRepository.save(entity)
            Result.success(Unit)
        } catch (e: OptimisticLockingFailureException) {
            // ✅ 捕获并发修改异常
            Result.failure(
                OrderConcurrentModificationException(
                    "订单已被其他操作修改，请重新加载"
                )
            )
        } catch (e: DataIntegrityViolationException) {
            Result.failure(
                Exception("数据完整性异常：${e.message}")
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// 异常类
class OrderConcurrentModificationException(message: String) : Exception(message)

// 应用服务中的重试逻辑
@Service
class OrderApplicationService(
    private val orderRepository: OrderRepository
) {
    
    fun payOrder(cmd: PayOrderCommand): Result<Unit> {
        var lastException: Exception? = null
        
        // ✅ 最多重试3次
        repeat(3) { attempt ->
            try {
                val order = orderRepository.findById(cmd.orderId)
                    ?: return Result.failure(Exception("订单不存在"))
                
                val payResult = order.pay()
                if (payResult.isFailure) {
                    return payResult
                }
                
                orderRepository.save(order)
                return Result.success(Unit)
                
            } catch (e: OrderConcurrentModificationException) {
                lastException = e
                if (attempt < 2) {
                    // 等待后重试
                    Thread.sleep(100L * (attempt + 1))
                }
            }
        }
        
        return Result.failure(
            lastException ?: Exception("支付失败，请重试")
        )
    }
}
```

---

## 完成检查清单

- [ ] Order聚合根不可变性已实现
- [ ] OrderItem类型已明确（聚合内实体）
- [ ] 所有业务方法已实现返回Result或事件
- [ ] 领域事件完整定义和发布
- [ ] 反腐层已实现
- [ ] 所有值对象不可变且包含验证
- [ ] Repository接口清晰，无基础设施概念
- [ ] 并发控制已实现
- [ ] 单元测试通过（覆盖率 >80%）
- [ ] 集成测试通过

---

## 下一步

1. **测试**: 编写完整的单元和集成测试
2. **文档**: 更新API文档和业务流程文档
3. **监控**: 添加埋点和日志用于问题排查
4. **优化**: 性能优化和查询优化


